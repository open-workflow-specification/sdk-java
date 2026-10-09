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
package org.openworkflow.sdk.impl.model.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.openworkflow.sdk.impl.jackson.JsonUtils;
import org.openworkflow.sdk.impl.marshaller.CustomObjectMarshaller;
import org.openworkflow.sdk.impl.marshaller.WorkflowInputBuffer;
import org.openworkflow.sdk.impl.marshaller.WorkflowOutputBuffer;

public abstract class AbstractJacksonMarshaller<T> implements CustomObjectMarshaller<T> {

  @Override
  public void write(WorkflowOutputBuffer buffer, T object) {
    try {
      buffer.writeBytes(JsonUtils.mapper().writeValueAsBytes(object));
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public T read(WorkflowInputBuffer buffer, Class<? extends T> clazz) {
    try {
      return JsonUtils.mapper().readValue(buffer.readBytes(), clazz);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
