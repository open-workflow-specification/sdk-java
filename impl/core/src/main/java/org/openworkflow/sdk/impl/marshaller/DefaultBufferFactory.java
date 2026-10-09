/*
 * Copyright 2020-Present The Open Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openworkflow.sdk.impl.marshaller;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.ServiceLoader;

public class DefaultBufferFactory implements WorkflowBufferFactory {

  private final Collection<CustomObjectMarshaller> marshallers;

  private static class DefaultBufferFactoryHolder {
    private static DefaultBufferFactory instance =
        new DefaultBufferFactory(
            ServiceLoader.load(CustomObjectMarshaller.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted()
                .toList());
  }

  public static DefaultBufferFactory factory() {
    return DefaultBufferFactoryHolder.instance;
  }

  protected DefaultBufferFactory(Collection<CustomObjectMarshaller> marshallers) {
    this.marshallers = marshallers;
  }

  @Override
  public WorkflowInputBuffer input(InputStream input) {
    return new DefaultInputBuffer(input, marshallers);
  }

  @Override
  public WorkflowOutputBuffer output(OutputStream output) {
    return new DefaultOutputBuffer(output, marshallers);
  }
}
