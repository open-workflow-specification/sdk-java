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
import java.util.function.Supplier;

public record StreamSuppliers(Supplier<String> outputStream, Supplier<String> errorStream) {

  public static StreamSuppliers from(Process process) {
    return new StreamSuppliers(
        new InputStreamSupplier(process.getInputStream()),
        new InputStreamSupplier(process.getErrorStream()));
  }

  public static StreamSuppliers from(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
    return new StreamSuppliers(
        new ByteArrayStreamSupplier(stdout), new ByteArrayStreamSupplier(stderr));
  }

  private static class ByteArrayStreamSupplier implements Supplier<String> {

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
  }

  private static class InputStreamSupplier implements Supplier<String> {

    private String value;
    private final InputStream stream;

    public InputStreamSupplier(InputStream stream) {
      this.stream = stream;
    }

    @Override
    public String get() {
      if (value == null) {
        try {
          value = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
          throw new UncheckedIOException(ex);
        }
      }
      return value;
    }
  }
}
