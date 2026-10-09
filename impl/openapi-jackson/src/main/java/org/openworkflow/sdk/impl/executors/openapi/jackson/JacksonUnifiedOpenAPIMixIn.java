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
package org.openworkflow.sdk.impl.executors.openapi.jackson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JacksonUnifiedOpenAPIMixIn {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class Server {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class RequestBody {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class Parameter {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class PathItem {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class HttpOperation {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class Operation {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class MediaType {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public class Components {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Content(@JsonProperty("application/json") MediaType applicationJson) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Schema(
      String type,
      Map<String, Schema> properties,
      List<String> required,
      @JsonProperty("$ref") String ref,
      @JsonProperty("default") Object _default) {}
}
