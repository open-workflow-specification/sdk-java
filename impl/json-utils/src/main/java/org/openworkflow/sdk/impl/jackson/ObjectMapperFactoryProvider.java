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

import io.cloudevents.jackson3.JsonFormat;
import java.util.Objects;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

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
          JsonMapper.builder()
              .addModule(JsonFormat.getCloudEventJacksonModule())
              .findAndAddModules()
              .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
              .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
              .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
              .build();
    }

    @Override
    public ObjectMapper get() {
      return mapper;
    }
  }
}
