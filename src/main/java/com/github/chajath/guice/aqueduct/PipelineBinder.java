// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.chajath.guice.aqueduct;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Guice binder to configure {@link Chained} instances to form a pipeline. For each {@link Chained}
 * item, next in pipeline will be set via {@link Chained#setNext} call at injection time. For the
 * last item in line, {@code noOp} instance configured from the constructor will be set as the next
 * item.
 *
 * <p>For example, suppose we have a {@link Chained} pipeline class:
 *
 * <pre><code>
 * abstract class Pipeline implements{@code Chained<Pipeline>} {
 *   Pipeline next;
 *
 *  {@literal @}Override
 *   public void setNext(Pipeline next) {
 *     this.next = next;
 *   }
 *
 *   abstract void process();
 * }
 *
 * class PipelineA extends Pipeline {
 *  {@literal @}Override
 *   void process() {
 *     // Pre-process logic.
 *     next.process();
 *     // Post-process logic.
 *   }
 * }
 *
 * class PipelineB extends Pipeline {
 *  {@literal @}Override
 *   void process() {
 *     // Pre-process logic.
 *     next.process();
 *     // Post-process logic.
 *   }
 * }
 *
 * class NoOpPipeline extends Pipeline {
 *  {@literal @}Override
 *   void process() {
 *     // No-Op terminal node. this.next here will not have been set.
 *   }
 * }
 * </code></pre>
 *
 * <p>Now, when we inject a pipeline through the following calls inside a module:
 *
 * <pre><code>
 * class MyPipelineModule extends AbstractModule {
 *  {@literal @}Override
 *   protected void configure() {
 *    {@code PipelineBinder<Pipeline> myPipelineBinder = new PipelineBinder<>(
 *          binder(), TypeLiteral.get(Pipeline.class), new NoOpPipeline());}
 *     myPipelineBinder.addBinding().to(PipelineA.class);
 *     myPipelineBinder.addBinding().to(PipelineB.class);
 *   }
 * }
 * </code></pre>
 *
 * <p>The injected {@code Pipeline} instance will be an instance of {@code PipelineA}, whose next
 * value is an instance of {@code PipelineB}, followed by {@code NoOpPipeline}.
 *
 * <p>Note that instead of implementing {@link Chained#setNext}, one could extend {@link
 * AbstractPipeline} which does the same thing as what {@code Pipeline} class does in the example
 * above.
 */
public class PipelineBinder<T extends Chained<T>> {
  private final Binder binder;
  private final TypeLiteral<T> typeLiteral;
  private final List<Element> annotations = new ArrayList<>();

  public PipelineBinder(Binder binder, TypeLiteral<T> typeLiteral, T noOp) {
    this.binder = binder;
    this.typeLiteral = typeLiteral;
    binder.install(new PipelineModule(typeLiteral, noOp));
  }

  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  @BindingAnnotation
  @interface Element {
    int uniqueId();
  }

  private static final AtomicInteger nextUniqueId = new AtomicInteger(1);

  @AutoAnnotation
  private static Element element(int uniqueId) {
    return new AutoAnnotation_PipelineBinder_element(uniqueId);
  }

  public LinkedBindingBuilder<T> addBinding() {
    Element newElement = element(nextUniqueId.getAndIncrement());
    annotations.add(newElement);
    return binder.bind(Key.get(typeLiteral, newElement));
  }

  private class PipelineModule extends AbstractModule {
    private final TypeLiteral<T> typeLiteral;
    private final T noOp;

    PipelineModule(TypeLiteral<T> typeLiteral, T noOp) {
      this.typeLiteral = typeLiteral;
      this.noOp = noOp;
    }

    @Override
    protected void configure() {
      bind(typeLiteral)
          .toProvider(
              new Provider<T>() {
                // TODO(yiinho): Find a better way to inject pipeline elements.
                @Inject Injector injector;

                @Override
                public T get() {
                  if (annotations.isEmpty()) {
                    return noOp;
                  }
                  T first =
                      injector.getInstance(
                          Key.get(typeLiteral, Iterables.getFirst(annotations, null)));
                  T current = first;
                  for (Element element : Iterables.skip(annotations, 1)) {
                    T nextInPipeline = injector.getInstance(Key.get(typeLiteral, element));
                    current.setNext(nextInPipeline);
                    current = nextInPipeline;
                  }
                  current.setNext(noOp);
                  return first;
                }
              });
    }
  }
}
