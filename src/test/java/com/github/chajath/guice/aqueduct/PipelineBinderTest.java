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

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link PipelineBinder}. */
@RunWith(JUnit4.class)
public class PipelineBinderTest {
  abstract static class TestPipeline implements Chained<TestPipeline> {
    TestPipeline next;

    @Override
    public void setNext(TestPipeline next) {
      this.next = next;
    }

    public abstract long process(long input);
  }

  private static class IdentityPipeline extends TestPipeline {
    @Override
    public long process(long input) {
      return input;
    }
  }

  private static class DoublePipeline extends TestPipeline {
    @Override
    public long process(long input) {
      return next.process(input) * 2;
    }
  }

  private static class IncPipeline extends TestPipeline {
    @Override
    public long process(long input) {
      return next.process(input) + 1;
    }
  }

  @Test
  public void testBinding() {
    AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            PipelineBinder<TestPipeline> testPipelineBinder =
                new PipelineBinder<>(
                    binder(), TypeLiteral.get(TestPipeline.class), new IdentityPipeline());
            testPipelineBinder.addBinding().to(IncPipeline.class);
            testPipelineBinder.addBinding().to(DoublePipeline.class);
          }
        };

    TestPipeline testPipeline = Guice.createInjector(testModule).getInstance(TestPipeline.class);
    assertThat(testPipeline.process(10)).isEqualTo(21 /* (10 * 2) + 1 */);
  }

  @Test
  public void testBinding_withNoBinding_shouldInjectNoOp() {
    AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            new PipelineBinder<>(
                binder(), TypeLiteral.get(TestPipeline.class), new IdentityPipeline());
          }
        };

    TestPipeline testPipeline = Guice.createInjector(testModule).getInstance(TestPipeline.class);
    assertThat(testPipeline.process(10)).isEqualTo(10 /* identity */);
  }
}
