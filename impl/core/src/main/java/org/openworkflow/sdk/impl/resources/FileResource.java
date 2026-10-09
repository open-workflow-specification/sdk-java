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
package org.openworkflow.sdk.impl.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;

class FileResource implements ExternalResourceHandler {

  private Path path;

  public FileResource(Path path) {
    this.path = path;
  }

  @Override
  public InputStream open() throws IOException {
    return Files.newInputStream(path);
  }

  @Override
  public String name() {
    return path.getFileName().toString();
  }

  @Override
  public boolean shouldReload(Instant lastUpdate) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class)
          .lastModifiedTime()
          .toInstant()
          .isAfter(lastUpdate);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    FileResource other = (FileResource) obj;
    return Objects.equals(path, other.path);
  }
}
