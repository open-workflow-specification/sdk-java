/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.api.utils;

import io.serverlessworkflow.api.mapper.JsonObjectMapperFactory;
import io.serverlessworkflow.api.mapper.YamlObjectMapperFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class Utils {

  @SuppressWarnings("DefaultCharset")
  public static String getResourceFileAsString(String fileName) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try (InputStream is = classLoader.getResourceAsStream(fileName)) {
      if (is == null) return null;
      try (InputStreamReader isr = new InputStreamReader(is);
          BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static ObjectMapper getObjectMapper(String source) {
    return !source.trim().startsWith("{")
        ? YamlObjectMapperFactory.mapper()
        : JsonObjectMapperFactory.mapper();
  }

  public static JsonNode getNode(String source) {
    return getObjectMapper(source).readTree(source);
  }
}
