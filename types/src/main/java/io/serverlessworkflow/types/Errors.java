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
package io.serverlessworkflow.types;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Standard error types with a configurable spec version.
 *
 * @see <a
 *     href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#standard-error-types">DSL
 *     Reference - Standard Error Types</a>
 */
public final class Errors {

  private Errors() {}

  private static final AtomicReference<Supplier<String>> VERSION_SUPPLIER =
      new AtomicReference<>(() -> "1.0.0");

  private static final AtomicReference<String> BASE_PATTERN =
      new AtomicReference<>("https://serverlessworkflow.io/spec/%s/errors/");

  public static void setSpecVersion(String version) {
    Objects.requireNonNull(version, "version");
    VERSION_SUPPLIER.set(() -> version);
  }

  public static void setVersionSupplier(Supplier<String> supplier) {
    VERSION_SUPPLIER.set(Objects.requireNonNull(supplier, "supplier"));
  }

  public static void setBasePattern(String basePattern) {
    if (basePattern == null || !basePattern.contains("%s")) {
      throw new IllegalArgumentException("basePattern must include %s placeholder for the version");
    }
    BASE_PATTERN.set(basePattern);
  }

  private static URI uriFor(String slug) {
    String base = String.format(BASE_PATTERN.get(), VERSION_SUPPLIER.get().get());
    return URI.create(base + slug);
  }

  public static final class Standard {
    private final String slug;
    private final int defaultStatus;

    private Standard(String slug, int defaultStatus) {
      this.slug = slug;
      this.defaultStatus = defaultStatus;
    }

    public URI uri() {
      return uriFor(slug);
    }

    public int status() {
      return defaultStatus;
    }

    public Standard withStatus(int override) {
      return new Standard(slug, override);
    }

    @Override
    public String toString() {
      return uri().toString();
    }
  }

  public static final Standard RUNTIME = new Standard("runtime", 500);
  public static final Standard COMMUNICATION = new Standard("communication", 500);
  public static final Standard AUTHENTICATION = new Standard("authentication", 401);
  public static final Standard AUTHORIZATION = new Standard("authorization", 403);
  public static final Standard DATA = new Standard("data", 422);
  public static final Standard TIMEOUT = new Standard("timeout", 408);
  public static final Standard NOT_IMPLEMENTED = new Standard("not-implemented", 501);
  public static final Standard VALIDATION = new Standard("validation", 400);
}
