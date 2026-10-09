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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.net.URI;
import org.openworkflow.sdk.api.types.jackson.JacksonMixInModule;
import org.openworkflow.sdk.serialization.BeanDeserializerModifierWithValidation;
import org.openworkflow.sdk.serialization.URIDeserializer;
import org.openworkflow.sdk.serialization.URISerializer;

class ObjectMapperFactory {

  private static final ObjectMapper jsonMapper = configure(new ObjectMapper());

  private static final ObjectMapper yamlMapper =
      configure(
          new ObjectMapper(
              new YAMLFactory()
                  .enable(Feature.MINIMIZE_QUOTES)
                  .enable(Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)));

  public static ObjectMapper jsonMapper() {
    return jsonMapper;
  }

  public static ObjectMapper yamlMapper() {
    return yamlMapper;
  }

  private static ObjectMapper configure(ObjectMapper mapper) {
    SimpleModule validationModule = new SimpleModule();
    validationModule.addDeserializer(URI.class, new URIDeserializer());
    validationModule.addSerializer(URI.class, new URISerializer());
    validationModule.setDeserializerModifier(new BeanDeserializerModifierWithValidation());

    return mapper
        .setDefaultPropertyInclusion(Include.NON_NULL)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
        .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .registerModule(validationModule)
        .registerModule(new JacksonMixInModule())
        .findAndRegisterModules();
  }

  private ObjectMapperFactory() {}
}
