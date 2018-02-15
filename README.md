# Guice Aqueduct

Guice Aqueduct is a Guice binder to configure `Chained` instances to form a pipeline. For each `Chained` item, next in pipeline will be set via `Chained.setNext` call at injection time. For the last item in line, `noOp` instance configured from the constructor will be set as the next item.

For example, suppose we have a `Chained` pipeline class:

```java
abstract class Pipeline implements Chained<Pipeline> {
  Pipeline next;

  @Override
  public void setNext(Pipeline next) {
    this.next = next;
  }

  abstract void process();
}

class PipelineA extends Pipeline {
  @Override
  void process() {
    // Pre-process logic.
    next.process();
    // Post-process logic.
  }
}

class PipelineB extends Pipeline {
  @Override
  void process() {
    // Pre-process logic.
    next.process();
    // Post-process logic.
  }
}

class NoOpPipeline extends Pipeline {
  @Override
  void process() {
    // No-Op terminal node. this.next here will not have been set.
  }
}
```

Now, when we inject a pipeline through the following calls inside a module:

```java
class MyPipelineModule extends AbstractModule {
  @Override
  protected void configure() {
    PipelineBinder<Pipeline> myPipelineBinder = new PipelineBinder<>(
        binder(), TypeLiteral.get(Pipeline.class), new NoOpPipeline());
    myPipelineBinder.addBinding().to(PipelineA.class);
    myPipelineBinder.addBinding().to(PipelineB.class);
  }
}
```

The injected `Pipeline` instance will be an instance of `PipelineA`, whose `next` value is an instance of `PipelineB`, followed by `NoOpPipeline`.

Note that instead of implementing `Chained.setNext`, one could extend `AbstractPipeline` which does the same thing as what `Pipeline` class does in the example above.

Disclaimer: This is not an official Google product.
