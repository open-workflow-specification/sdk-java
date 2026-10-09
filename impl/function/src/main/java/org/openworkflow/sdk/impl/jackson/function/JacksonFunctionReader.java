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
package org.openworkflow.sdk.impl.jackson.function;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.openworkflow.sdk.api.WorkflowFormat;
import org.openworkflow.sdk.api.types.Task;
import org.openworkflow.sdk.impl.FunctionReader;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;

public class JacksonFunctionReader implements FunctionReader {

  @Override
  public Task apply(ExternalResourceHandler handler) {
    try (InputStream in = handler.open()) {
      return WorkflowFormat.fromFileName(handler.name()).mapper().readValue(in, Task.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
