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
import java.util.Objects;

public class ClasspathResource implements ExternalResourceHandler {

  private String path;

  public ClasspathResource(String path) {
    this.path = path;
  }

  @Override
  public InputStream open() throws IOException {
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    if (is == null) {
      throw new IOException("Cannot load resource with path: " + path + " from classpath");
    }
    return is;
  }

  @Override
  public String name() {
    return path;
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
    ClasspathResource other = (ClasspathResource) obj;
    return Objects.equals(path, other.path);
  }
}
