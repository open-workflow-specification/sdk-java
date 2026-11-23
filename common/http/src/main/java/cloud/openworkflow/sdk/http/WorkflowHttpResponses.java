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
package cloud.openworkflow.sdk.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Raw HTTP serialization */
public final class WorkflowHttpResponses {
  private static final String HTTP_NEW_LINE = "\r\n";

  public static byte[] responseAsByteArray(WorkflowHttpResponse<?> response) {
    boolean isHttp2 = response.isHttp2();
    byte[] content = response.body();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      StringBuilder sb =
          new StringBuilder(
              isHttp2
                  ? "HTTP/2 " + response.status()
                  : "HTTP/1.1 " + response.status() + " " + response.reasonPhrase());
      sb.append(HTTP_NEW_LINE);
      for (Map.Entry<String, ? extends List<?>> entry : response.headers().entrySet()) {
        String headerName = entry.getKey();
        for (Object value : entry.getValue()) {
          if (headerName.equalsIgnoreCase("Transfer-Encoding")
              && value instanceof String str
              && str.equalsIgnoreCase("chunked")) {
            addHeader(sb, "Content-Length", content == null ? 0 : content.length);
          } else {
            addHeader(sb, headerName, value);
          }
        }
      }
      sb.append(HTTP_NEW_LINE);
      out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));

      if (content != null) {
        out.write(content);
      }
      return out.toByteArray();
    } catch (IOException io) {
      throw new UncheckedIOException(io);
    }
  }

  private static void addHeader(StringBuilder sb, String name, Object value) {
    sb.append(name).append(": ").append(value).append(HTTP_NEW_LINE);
  }

  private WorkflowHttpResponses() {}
}
