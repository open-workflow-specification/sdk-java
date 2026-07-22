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
package io.serverlessworkflow.impl.events;

import io.cloudevents.CloudEvent;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CloudEventUtils {

  private CloudEventUtils() {}

  public static OffsetDateTime toOffset(Date date) {
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static Map<String, Object> extensions(CloudEvent event) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String name : event.getExtensionNames()) {
      result.put(name, event.getExtension(name));
    }
    return result;
  }

  public static URI source() {
    return URI.create("io.serverlessworkflow.sdk-java");
  }
}
