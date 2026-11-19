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
package org.openworkflow.sdk.http;

import java.util.concurrent.CompletableFuture;

/** Executes resolved HTTP requests without a workflow engine. */
public interface HttpTransport extends AutoCloseable {

  /**
   * Returns a buffered response for every HTTP status; transport failures complete exceptionally
   * with the underlying transport error. Cancelling the returned future abandons delivery, not the
   * network exchange. The exchange may still reach the server and complete. Buffered responses need
   * no close. Invalid requests and synchronous preparation failures may throw before returning a
   * future, following native request validation behavior.
   *
   * <p>The response's optional request metadata describes the effective request associated with the
   * final response, including redirects, when observable by the transport. An empty optional means
   * it is unavailable; callers must not substitute the initial request as if it were the final one.
   */
  CompletableFuture<HttpResponseData> execute(HttpRequestData request);

  /** Closes clients created by this transport; injected clients remain owned by the caller. */
  @Override
  void close();
}
