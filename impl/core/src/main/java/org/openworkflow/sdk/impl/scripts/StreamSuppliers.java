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
package org.openworkflow.sdk.impl.scripts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.openworkflow.sdk.impl.WorkflowUtils;

public record StreamSuppliers(StreamSupplier outputStream, StreamSupplier errorStream)
    implements AutoCloseable {

  public static StreamSuppliers from(
      Process process, ExecutorService service, boolean keepOut, boolean keepError) {
    return new StreamSuppliers(
        keepOut
            ? new InputStreamSupplier(process.getInputStream(), service)
            : new DiscardStreamSupplier(),
        keepError
            ? new InputStreamSupplier(process.getErrorStream(), service)
            : new DiscardStreamSupplier());
  }

  public static StreamSuppliers from(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
    return new StreamSuppliers(
        new ByteArrayStreamSupplier(stdout), new ByteArrayStreamSupplier(stderr));
  }

  public void close() {
    outputStream.close();
    errorStream.close();
  }

  private static class ByteArrayStreamSupplier implements StreamSupplier {

    private String value;
    private final ByteArrayOutputStream stream;

    public ByteArrayStreamSupplier(ByteArrayOutputStream stream) {
      this.stream = stream;
    }

    @Override
    public String get() {
      if (value == null) {
        value = stream.toString().trim();
      }
      return value;
    }

    @Override
    public void close() {
      WorkflowUtils.safeClose(stream);
    }
  }

  private static class InputStreamSupplier implements StreamSupplier {

    private CompletableFuture<String> value;
    private InputStream stream;

    public InputStreamSupplier(InputStream stream, ExecutorService executorService) {
      this.stream = stream;
      this.value =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                  throw new UncheckedIOException(ex);
                }
              },
              executorService);
    }

    @Override
    public String get() {
      return value.join();
    }

    @Override
    public void close() {
      WorkflowUtils.safeClose(stream);
    }
  }

  private static class DiscardStreamSupplier implements StreamSupplier {
    @Override
    public String get() {
      throw new UnsupportedOperationException("Stream information was discarded");
    }

    @Override
    public void close() {}
  }
}
