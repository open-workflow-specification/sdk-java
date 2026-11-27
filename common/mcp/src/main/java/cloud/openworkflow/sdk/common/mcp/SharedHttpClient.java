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
package cloud.openworkflow.sdk.common.mcp;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Hands the MCP transport one process-wide HTTP client, so calls reuse connections and the
 * transport, which never closes the client it builds, does not leave one behind per call.
 */
final class SharedHttpClient {
  private static final HttpClient CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

  /** A builder whose settings are fixed above; {@link #build()} returns the shared client. */
  static final HttpClient.Builder BUILDER =
      new HttpClient.Builder() {
        @Override
        public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
          return this;
        }

        @Override
        public HttpClient.Builder connectTimeout(Duration duration) {
          return this;
        }

        @Override
        public HttpClient.Builder sslContext(SSLContext sslContext) {
          return this;
        }

        @Override
        public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
          return this;
        }

        @Override
        public HttpClient.Builder executor(Executor executor) {
          return this;
        }

        @Override
        public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
          return this;
        }

        @Override
        public HttpClient.Builder version(HttpClient.Version version) {
          return this;
        }

        @Override
        public HttpClient.Builder priority(int priority) {
          return this;
        }

        @Override
        public HttpClient.Builder proxy(ProxySelector proxySelector) {
          return this;
        }

        @Override
        public HttpClient.Builder authenticator(Authenticator authenticator) {
          return this;
        }

        @Override
        public HttpClient.Builder localAddress(InetAddress localAddr) {
          return this;
        }

        @Override
        public HttpClient build() {
          return CLIENT;
        }
      };

  private SharedHttpClient() {}
}
