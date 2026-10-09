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
package org.openworkflow.sdk.generator.graalvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.codemodel.JDefinedClass;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ReflectNativeFileHandler {

  private final String groupId;
  private final String artifactId;
  private final ArrayNode rootObject;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public ReflectNativeFileHandler(String groupId, String artifactId) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.rootObject = objectMapper.createArrayNode();
  }

  public ReflectNativeFileHandler addClasses(Stream<String> classNames) {
    classNames.forEach(this::addClass);
    return this;
  }

  public ReflectNativeFileHandler addClasses(Iterable<String> classNames) {
    classNames.forEach(this::addClass);
    return this;
  }

  public ReflectNativeFileHandler addClass(String refClassName) {
    rootObject.add(buildObjectFromClass(refClassName));
    return this;
  }

  public ReflectNativeFileHandler addClass(JDefinedClass definedClass) {
    addClass(definedClass.fullName());
    return this;
  }

  private static JsonNode buildObjectFromClass(String refClassName) {
    return objectMapper
        .createObjectNode()
        .put("name", refClassName)
        .put("queryAllPublicConstructors", true)
        .put("queryAllDeclaredConstructors", true)
        .put("queryAllPublicMethods", true)
        .put("queryAllDeclaredMethods", true)
        .put("allPublicConstructors", true)
        .put("allDeclaredConstructors", true)
        .put("allPublicMethods", true)
        .put("allDeclaredMethods", true)
        .put("allPublicFields", true)
        .put("allDeclaredFields", true)
        .put("allPublicClasses", true)
        .put("allDeclaredClasses", true);
  }

  public void generate(Path rootPath) throws IOException {
    try (Writer out =
        Files.newBufferedWriter(
            Files.createDirectories(
                    rootPath
                        .resolve("META-INF")
                        .resolve("native-image")
                        .resolve(groupId)
                        .resolve(artifactId))
                .resolve("reflect-config.json"))) {
      objectMapper.writeValue(out, rootObject);
    }
  }
}
