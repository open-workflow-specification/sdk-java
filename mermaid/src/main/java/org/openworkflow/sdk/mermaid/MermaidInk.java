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
package org.openworkflow.sdk.mermaid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.Deflater;

/**
 * Exports a mermaid workflow representation to a PNG or SVG file by encoding string and calling the
 * remote service mermaid.ink. Depends on the website to be available.
 */
public final class MermaidInk {

  static String encode(String mermaid) {
    Deflater deflater =
        new Deflater(Deflater.BEST_COMPRESSION, true); // 'true' => raw DEFLATE (pako-compatible)
    deflater.setInput(mermaid.getBytes(StandardCharsets.UTF_8));
    deflater.finish();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    while (!deflater.finished()) baos.write(buf, 0, deflater.deflate(buf));
    return "pako:" + Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
  }

  public static Path render(String mermaid, boolean svg, Path outFile) {
    String encoded = encode(mermaid);
    String base = svg ? "https://mermaid.ink/svg/" : "https://mermaid.ink/img/";
    String url = svg ? base + encoded : base + encoded + "?type=png";
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
    HttpResponse<byte[]> resp;

    try {
      resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to call mermaid.ink website", e);
    }

    if (resp.statusCode() != 200)
      throw new RuntimeException("mermaid.ink request failed: " + resp.statusCode());

    try {
      Files.write(outFile, resp.body());
    } catch (IOException e) {
      throw new RuntimeException("Failed to save file in the given path: " + outFile, e);
    }
    return outFile;
  }
}
