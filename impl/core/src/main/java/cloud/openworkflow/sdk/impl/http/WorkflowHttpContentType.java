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
package cloud.openworkflow.sdk.impl.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class WorkflowHttpContentType {

  public static final String APPLICATION_JSON_TYPE = "application/json";
  public static final String APPLICATION_X_WWW_FORM_URLENCODED =
      "application/x-www-form-urlencoded";
  public static final String HEADER_NAME = "Content-Type";

  public static boolean isURLEncodedDataType(String contentType) {
    return formatContentType(contentType).contains("/x-www-form-urlencoded");
  }

  public static boolean isTextMediaType(String contentType) {
    contentType = formatContentType(contentType);
    return contentType.startsWith("text/")
        || contentType.endsWith("+xml")
        || contentType.contains("/xml")
        || contentType.contains("/javascript")
        || contentType.contains("/html");
  }

  public static boolean isJsonMediaType(String contentType) {
    contentType = formatContentType(contentType);
    return contentType.endsWith("/json") || contentType.endsWith("+json");
  }

  private static String formatContentType(String contentType) {
    int indexOf = contentType.indexOf(';');
    if (indexOf != -1) {
      contentType = contentType.substring(0, indexOf);
    }
    return contentType.trim().toLowerCase(Locale.ROOT);
  }

  public static Charset charset(String contentType) {
    if (contentType != null) {
      for (String parameter : contentType.split(";")) {
        String[] pair = parameter.trim().split("=", 2);
        if (pair.length == 2 && "charset".equalsIgnoreCase(pair[0].trim())) {
          String name = pair[1].trim();
          if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1);
          }
          return Charset.forName(name);
        }
      }
    }
    return StandardCharsets.UTF_8;
  }
}
