package ro.isdc.wro.cache.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import ro.isdc.wro.cache.CacheKey;
import ro.isdc.wro.cache.CacheStrategy;
import ro.isdc.wro.cache.CacheValue;
import ro.isdc.wro.cache.impl.LruMemoryCacheStrategy;
import ro.isdc.wro.cache.impl.MemoryCacheStrategy;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.support.ContextPropagatingCallable;
import ro.isdc.wro.manager.factory.BaseWroManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.group.processor.Injector;
import ro.isdc.wro.model.group.processor.InjectorBuilder;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.model.resource.support.change.ResourceWatcher;
import ro.isdc.wro.util.Function;
import ro.isdc.wro.util.ObjectDecorator;
import ro.isdc.wro.util.SchedulerHelper;
import ro.isdc.wro.util.WroTestUtils;


/**
 * @author Alex Objelean
 */
public class TestDefaultSynchronizedCacheStrategyDecorator {
  private static final String GROUP_NAME = "g1";
  private static final String RESOURCE_URI = "/test.js";
  
  private CacheStrategy<CacheKey, CacheValue> victim;
  @Mock
  private ResourceWatcher mockResourceWatcher;
  
  @BeforeClass
  public static void onBeforeClass() {
    assertEquals(0, Context.countActive());
  }
  
  @AfterClass
  public static void onAfterClass() {
    assertEquals(0, Context.countActive());
  }
  
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Context.set(Context.standaloneContext());
    victim = new DefaultSynchronizedCacheStrategyDecorator(new MemoryCacheStrategy<CacheKey, CacheValue>()) {
      @Override
      TimeUnit getTimeUnitForResourceWatcher() {
        // use milliseconds to make test faster
        return TimeUnit.MILLISECONDS;
      }
    };
    createInjector().inject(victim);
  }
  
  @After
  public void tearDown() {
    Context.unset();
    // have to reset it, otherwise a test fails when testing entire project.
    Mockito.reset(mockResourceWatcher);
  }
  
  public Injector createInjector() {
    final WroModel model = new WroModel().addGroup(new Group(GROUP_NAME).addResource(Resource.create(RESOURCE_URI)));
    final WroModelFactory modelFactory = WroTestUtils.simpleModelFactory(model);
    final UriLocatorFactory locatorFactory = WroTestUtils.createResourceMockingLocatorFactory();
    final BaseWroManagerFactory factory = new BaseWroManagerFactory().setModelFactory(modelFactory).setUriLocatorFactory(
        locatorFactory);
    factory.setProcessorsFactory(new SimpleProcessorsFactory());
    final Injector injector = InjectorBuilder.create(factory).setResourceWatcher(mockResourceWatcher).build();
    return injector;
  }
  
  @Test(expected = NullPointerException.class)
  public void cannotAcceptNullKey() {
    victim.get(null);
  }
  
  @Test
  public void shouldNotCheckForChangesWhenResourceWatcherPeriodIsNotSet()
      throws Exception {
    final CacheKey key = new CacheKey("g1", ResourceType.JS, true);
    victim.get(key);
    victim.get(key);
    verify(mockResourceWatcher, never()).check(key);
  }
  
  /**
   * Proves that even if the get() is invoked more times, the check is performed only after a certain period of time.
   */
  @Test
  public void shouldCheckOnlyAfterTimeout()
      throws Exception {
    final long updatePeriod = 10;
    final long delta = 5;
    Context.get().getConfig().setResourceWatcherUpdatePeriod(updatePeriod);
    final CacheKey key = new CacheKey("g1", ResourceType.JS, true);
    final long start = System.currentTimeMillis();
    do {
      victim.get(key);
    } while (System.currentTimeMillis() - start < updatePeriod - delta);
    verify(mockResourceWatcher, times(1)).tryAsyncCheck(key);
  }
  
  /**
   * This test does not pass consistently. TODO: rewrite it in order to make it always pass.
   */
  @Ignore
  @Test
  public void shouldCheckDifferentGroups()
      throws Exception {
    final long updatePeriod = 10;
    final long delta = 4;
    Context.get().getConfig().setResourceWatcherUpdatePeriod(updatePeriod);
    final CacheKey key1 = new CacheKey(GROUP_NAME, ResourceType.JS, true);
    final CacheKey key2 = new CacheKey(GROUP_NAME, ResourceType.CSS, true);
    final long start = System.currentTimeMillis();
    victim.get(key1);
    Thread.sleep(updatePeriod);
    do {
      victim.get(key1);
    } while (System.currentTimeMillis() - start < updatePeriod - delta);
    victim.get(key2);
    verify(mockResourceWatcher, times(2)).check(key1);
    verify(mockResourceWatcher, times(1)).check(key2);
  }
  
  @Test(expected = NullPointerException.class)
  public void cannotDecorateNullObject() {
    DefaultSynchronizedCacheStrategyDecorator.decorate(null);
  }
  
  @Test
  public void shouldDecorateCacheStrategy() {
    final CacheStrategy<CacheKey, CacheValue> original = new LruMemoryCacheStrategy<CacheKey, CacheValue>();
    victim = DefaultSynchronizedCacheStrategyDecorator.decorate(original);
    assertTrue(victim instanceof DefaultSynchronizedCacheStrategyDecorator);
    assertSame(original, ((ObjectDecorator<?>) victim).getDecoratedObject());
  }
  
  /**
   * Fix Issue 528: Redundant CacheStrategy decoration (which has unclear cause, but it is safe to prevent redundant
   * decoration anyway).
   */
  @Test
  public void shouldNotRedundantlyDecorateCacheStrategy() {
    final CacheStrategy<CacheKey, CacheValue> original = DefaultSynchronizedCacheStrategyDecorator.decorate(new LruMemoryCacheStrategy<CacheKey, CacheValue>());
    victim = DefaultSynchronizedCacheStrategyDecorator.decorate(original);
    assertTrue(victim instanceof DefaultSynchronizedCacheStrategyDecorator);
    assertSame(original, victim);
  }
  
  @Test
  public void shouldDestroySchedulerWhenStrategyIsDestroyed() {
    final SchedulerHelper scheduler = Mockito.mock(SchedulerHelper.class);
    victim = new DefaultSynchronizedCacheStrategyDecorator(new MemoryCacheStrategy<CacheKey, CacheValue>()) {
      SchedulerHelper newResourceWatcherScheduler() {
        return scheduler;
      };
    };
    victim.destroy();
    verify(scheduler).destroy();
  }
  
  @Test
  public void shouldReturnStaleValueWhileNewValueIsComputed() throws Exception {
    Context.get().getConfig().setResourceWatcherUpdatePeriod(1);
    final CacheKey key = new CacheKey("g1", ResourceType.JS);
    final CacheValue value1 = CacheValue.valueOf("1", "1");
    final CacheValue value2 = CacheValue.valueOf("2", "2");
    StaleCacheKeyAwareCacheStrategyDecorator<CacheKey, CacheValue> spy = Mockito.spy(StaleCacheKeyAwareCacheStrategyDecorator.decorate(new MemoryCacheStrategy<CacheKey, CacheValue>()));
    final AtomicInteger loadCounter = new AtomicInteger();
    final int timeout = 5;
    victim = new DefaultSynchronizedCacheStrategyDecorator(spy) {
      @Override
      protected CacheValue loadValue(CacheKey key) {
        try {
          CacheValue newValue = null;
          if (loadCounter.get() == 0) {
            newValue = value1;
          } else {
            // simulate slow operation
            try {
              Thread.sleep(timeout);
              newValue = value2;
            } catch (InterruptedException e) {
            }
          }
          return newValue;
        } finally {
          loadCounter.incrementAndGet();
        }
      }
      
      @Override
      TimeUnit getTimeUnitForResourceWatcher() {
        return TimeUnit.MILLISECONDS;
      }
    };
    createInjector().inject(victim);
    assertEquals(value1, victim.get(key));
    spy.markAsStale(key);
    final AtomicInteger oldValueCounter = new AtomicInteger();
    final AtomicInteger newValueCounter = new AtomicInteger();
    //still get old value while new value is loaded asynchronously
    WroTestUtils.runConcurrently(new ContextPropagatingCallable<Void>(new Callable<Void>() {
      public Void call()
          throws Exception {
        CacheValue value = victim.get(key);
        if (value1.equals(value)) {
          oldValueCounter.incrementAndGet();
        } else if (value2.equals(value)){
          newValueCounter.incrementAndGet();
        } 
        return null;
      }
    }), 200);
    System.out.println("loaded times: " + loadCounter.get());
    System.out.println("oldValue: " + oldValueCounter.get());
    System.out.println("newValue: " + newValueCounter.get());
    assertTrue(oldValueCounter.get() > 0);
    assertTrue(newValueCounter.get() > 0);
    assertEquals(value2, victim.get(key));
    assertEquals(2, loadCounter.get());
  }
}
