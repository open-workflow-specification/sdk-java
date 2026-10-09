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
package org.openworkflow.sdk.impl.jackson;

import static org.openworkflow.sdk.impl.WorkflowUtils.loadFirst;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.cloudevents.jackson.JsonFormat;
import java.util.Objects;
import java.util.function.Supplier;

public class ObjectMapperFactoryProvider implements Supplier<ObjectMapperFactory> {

  private static ObjectMapperFactoryProvider instance = new ObjectMapperFactoryProvider();

  public static ObjectMapperFactoryProvider instance() {
    return instance;
  }

  private volatile ObjectMapperFactory objectMapperFactory;

  private ObjectMapperFactoryProvider() {}

  public synchronized void setFactory(ObjectMapperFactory objectMapperFactory) {
    this.objectMapperFactory = Objects.requireNonNull(objectMapperFactory);
  }

  @Override
  public ObjectMapperFactory get() {
    if (objectMapperFactory == null) {
      synchronized (this) {
        if (objectMapperFactory == null) {
          objectMapperFactory =
              loadFirst(ObjectMapperFactory.class).orElseGet(DefaultObjectMapperFactory::new);
        }
      }
    }
    return objectMapperFactory;
  }

  /** Internal default private factory lazy initialized. */
  private static class DefaultObjectMapperFactory implements ObjectMapperFactory {

    private final ObjectMapper mapper;

    DefaultObjectMapperFactory() {
      this.mapper =
          new ObjectMapper()
              .findAndRegisterModules()
              .registerModule(JsonFormat.getCloudEventJacksonModule())
              .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
              .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @Override
    public ObjectMapper get() {
      return mapper;
    }
  }
}
