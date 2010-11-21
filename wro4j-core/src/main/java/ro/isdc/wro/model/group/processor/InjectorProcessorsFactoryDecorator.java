/**
 * Copyright Alex Objelean
 */
package ro.isdc.wro.model.group.processor;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.model.resource.processor.ProcessorsFactory;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.util.StopWatch;


/**
 * Decorator for {@link ProcessorsFactory} responsible for processing @Inject annotations of processors provided by
 * decorated factory.
 *
 * @author Alex Objelean
 * @created 21 Nov 2010
 */
public class InjectorProcessorsFactoryDecorator
  implements ProcessorsFactory {
  private static final Logger LOG = LoggerFactory.getLogger(InjectorProcessorsFactoryDecorator.class);
  private ProcessorsFactory decorated;
  private Injector injector;

  public InjectorProcessorsFactoryDecorator(final ProcessorsFactory decorated, final Injector injector) {
    if (decorated == null) {
      throw new IllegalArgumentException("processorsFactory cannot be null!");
    }
    if (injector == null) {
      throw new IllegalArgumentException("injector cannot be null!");
    }
    this.decorated = decorated;
    this.injector = injector;
  }

  /**
   * {@inheritDoc}
   */
  public Collection<ResourcePreProcessor> getPreProcessors() {
    scanPreProcessors();
    return decorated.getPreProcessors();
  }


  /**
   * Scan all preProcessors of decorated factory.
   */
  protected void scanPreProcessors() {
    // TODO ensure that it is not called to often
    final StopWatch watch = new StopWatch();
    watch.start("scan pre processors");
    for (final ResourcePreProcessor processor : decorated.getPreProcessors()) {
      injector.inject(processor);
    }
    watch.stop();
    LOG.debug(watch.prettyPrint());
  }


  /**
   * {@inheritDoc}
   */
  public Collection<ResourcePostProcessor> getPostProcessors() {
    scanPostProcessors();
    return decorated.getPostProcessors();
  }


  protected void scanPostProcessors() {
    final StopWatch watch = new StopWatch();
    watch.start("scan post processors");
    // TODO ensure that it is not called to often
    for (final ResourcePostProcessor processor : decorated.getPostProcessors()) {
      injector.inject(processor);
    }
    watch.stop();
    LOG.debug(watch.prettyPrint());
  }
}