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

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.openworkflow.sdk.http.*;
import org.openworkflow.sdk.impl.*;
import org.openworkflow.sdk.impl.http.WorkflowHttpHelper;

/** SDK conversion and task error enrichment only; no HTTP response state. */
final class JakartaWorkflowBodyConverter implements HttpBodyConverter {
  private final WorkflowModelFactory factory;
  private final HttpModelConverter converter;
  private final TaskContext task;

  JakartaWorkflowBodyConverter(
      WorkflowModelFactory factory, HttpModelConverter converter, TaskContext task) {
    this.factory = factory;
    this.converter = converter;
    this.task = task;
  }

  public <T> Optional<T> convert(HttpResponse response, Class<T> type) {
    if (type != WorkflowModel.class) return HttpBodyConverter.BYTES.convert(response, type);
    try {
      return Optional.of(
          type.cast(
              converter.buildModel(
                  factory, response.nativeResponse(Response.class).orElseThrow())));
    } catch (ProcessingException ex) {
      throw WorkflowHttpHelper.fromError(ex, task);
    }
  }
}
