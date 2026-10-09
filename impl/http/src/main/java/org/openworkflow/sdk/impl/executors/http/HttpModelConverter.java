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
package org.openworkflow.sdk.impl.executors.http;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.LoggerFactory;

public interface HttpModelConverter {

  default Entity<?> toEntity(WorkflowModel model) {
    return Entity.json(model.as(model.objectClass()).orElseThrow());
  }

  Class<?> responseType();

  default WorkflowError.Builder errorFromResponse(
      WorkflowError.Builder errorBuilder, Response response) {
    errorBuilder.title(response.getStatusInfo().getReasonPhrase());
    try {
      Object details = response.readEntity(responseType());
      if (details != null) {
        errorBuilder.details(details.toString());
      }
    } catch (Exception ex) {
      LoggerFactory.getLogger(HttpModelConverter.class)
          .debug("Problem extracting error from http response", ex);
    }
    return errorBuilder;
  }
}
