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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JsonUtilsStaticInitTest {

  private ObjectMapperFactory originalFactory;

  @BeforeEach
  public void setUp() {
    // Capture the original factory (or fallback) to restore it later
    // so we don't pollute other tests running in the same JVM.
    originalFactory = ObjectMapperFactoryProvider.instance().get();
  }

  @AfterEach
  public void tearDown() {
    // Restore the provider to its original state
    ObjectMapperFactoryProvider.instance().setFactory(originalFactory);
  }

  @Test
  public void testMapperReflectsDynamicFactoryOverride() {
    ObjectMapper initialMapper = JsonUtils.mapper();
    ObjectMapper customMapper = new ObjectMapper();

    assertNotSame(
        initialMapper,
        customMapper,
        "Initial mapper and custom mapper should be different instances");
    ObjectMapperFactoryProvider.instance().setFactory(() -> customMapper);
    ObjectMapper fetchedMapper = JsonUtils.mapper();

    assertSame(
        customMapper,
        fetchedMapper,
        "JsonUtils.mapper() failed to return the dynamically injected ObjectMapper. Static caching trap is present!");
  }
}
