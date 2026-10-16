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
package org.openworkflow.sdk.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import org.openworkflow.sdk.api.types.jackson.JacksonMixInModule;
import org.openworkflow.sdk.serialization.BeanDeserializerModifierWithValidation;
import org.openworkflow.sdk.serialization.URIDeserializer;
import org.openworkflow.sdk.serialization.URISerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

class ObjectMapperFactory {

  private static final ObjectMapper jsonMapper = configureJson();

  private static final ObjectMapper yamlMapper = configureYaml();

  public static ObjectMapper jsonMapper() {
    return jsonMapper;
  }

  public static ObjectMapper yamlMapper() {
    return yamlMapper;
  }

  private static ObjectMapper configureJson() {
    return configure(JsonMapper.builder()).build();
  }

  private static ObjectMapper configureYaml() {
    return configure(YAMLMapper.builder())
        .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
        .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
        .build();
  }

  private static <V extends ObjectMapper, T extends MapperBuilder<V, T>> T configure(T builder) {
    SimpleModule validationModule = new SimpleModule("JakartaValidationModule");
    validationModule.addDeserializer(URI.class, new URIDeserializer());
    validationModule.addSerializer(URI.class, new URISerializer());
    validationModule.setDeserializerModifier(new BeanDeserializerModifierWithValidation());
    return builder
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .addModule(validationModule)
        .changeDefaultPropertyInclusion(
            v ->
                v.withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
        .addModule(new JacksonMixInModule())
        .findAndAddModules();
  }

  private ObjectMapperFactory() {}
}
