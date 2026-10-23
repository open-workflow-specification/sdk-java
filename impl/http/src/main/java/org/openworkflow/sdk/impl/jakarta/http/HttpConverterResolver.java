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
package org.openworkflow.sdk.impl.jakarta.http;

import java.util.Optional;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;

public class HttpConverterResolver {

  public static final String HTTP_MODEL_CONVERTER = "httpModelConverter";

  private static class DefaultHolder {
    private static final HttpModelConverter converter =
        new HttpModelConverter() {
          @Override
          public Class<?> responseType() {
            return Object.class;
          }
        };
  }

  public static HttpModelConverter converter(
      WorkflowApplication app,
      Optional<WorkflowContext> workflowContext,
      Optional<TaskContext> taskContext) {
    return app.<HttpModelConverter>additionalObject(
            HTTP_MODEL_CONVERTER, workflowContext, taskContext)
        .orElseGet(() -> DefaultHolder.converter);
  }

  private HttpConverterResolver() {}
}
