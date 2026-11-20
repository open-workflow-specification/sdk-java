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
package cloud.openworkflow.sdk.impl.executors.openapi.jackson;

import static org.assertj.core.api.Assertions.*;

import cloud.openworkflow.sdk.api.WorkflowFormat;
import cloud.openworkflow.sdk.http.*;
import cloud.openworkflow.sdk.impl.executors.openapi.*;
import cloud.openworkflow.sdk.impl.executors.openapi.OpenAPIClient.ServerFallback;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

/** Compiled third-party usage example using the optional SDK Jackson reader. */
class StandaloneOpenAPITest {
  private static final String DOCUMENT =
      """
      {
        "openapi":"3.0.3",
        "servers":[{"url":"%s"}],
        "paths":{"/pets/{id}":{"post":{
          "operationId":"save",
          "parameters":[
            {"name":"id","in":"path","required":true,"schema":{"type":"string"}},
            {"name":"q","in":"query","required":false,"schema":{"type":"string"}},
            {"name":"lang","in":"query","required":true,"schema":{"default":"en"}},
            {"name":"unused","in":"query","schema":{"default":"ignored"}},
            {"name":"Authorization","in":"header","schema":{"type":"string"}},
            {"name":"cookieValue","in":"cookie","schema":{"type":"string"}}
          ],
          "requestBody":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Pet"}}}}
        }}},
        "components":{"schemas":{"Pet":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}}}
      }
      """;

  private UnifiedOpenAPI read(String server) {
    return new JacksonUnifiedOpenAPIReader()
        .read("api.json", DOCUMENT.formatted(server).getBytes(StandardCharsets.UTF_8));
  }

  private byte[] json(Map<String, Object> value) {
    return WorkflowFormat.JSON.mapper().writeValueAsBytes(value);
  }

  private record TrackingResponse(int status, java.util.concurrent.atomic.AtomicInteger closes)
      implements HttpResponse {
    public byte[] body() {
      return new byte[0];
    }

    public Map<String, List<String>> headers() {
      return Map.of();
    }

    public void close() {
      closes.incrementAndGet();
    }
  }

  @Test
  void closesAnUndeliverableResponseIfStatusInspectionFails() {
    for (boolean fatal : new boolean[] {false, true}) {
      Throwable failure =
          fatal ? new AssertionError("status") : new IllegalStateException("status");
      Throwable cleanup =
          fatal ? new AssertionError("cleanup") : new IllegalStateException("cleanup");
      var closes = new java.util.concurrent.atomic.AtomicInteger();
      HttpTransport transport =
          new HttpTransport() {
            public CompletableFuture<HttpResponse> execute(HttpRequestInfo<byte[]> request) {
              return CompletableFuture.completedFuture(
                  new HttpResponse() {
                    public int status() {
                      if (failure instanceof Error error) throw error;
                      throw (RuntimeException) failure;
                    }

                    public byte[] body() {
                      return new byte[0];
                    }

                    public Map<String, List<String>> headers() {
                      return Map.of();
                    }

                    public void close() {
                      closes.incrementAndGet();
                      if (cleanup instanceof Error error) throw error;
                      throw (RuntimeException) cleanup;
                    }
                  });
            }

            public void close() {}
          };
      var result =
          withServers(List.of("https://first.test", "https://second.test"))
              .execute(
                  Map.of("id", 1),
                  this::json,
                  transport,
                  ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE);
      assertThat(catchThrowableOfType(CompletionException.class, result::join).getCause())
          .isSameAs(failure);
      assertThat(closes.get()).isEqualTo(1);
      assertThat(failure.getSuppressed()).isEqualTo(new Throwable[] {cleanup});
    }
  }

  @Test
  void singleAttemptCancellationReachesTheTransportDeliveryFuture() {
    for (boolean fallbackEnabled : new boolean[] {false, true}) {
      var pending = new CompletableFuture<HttpResponse>();
      HttpTransport transport =
          new HttpTransport() {
            public CompletableFuture<HttpResponse> execute(HttpRequestInfo<byte[]> request) {
              return pending;
            }

            public void close() {}
          };
      var api =
          withServers(
              fallbackEnabled
                  ? List.of("https://only.test")
                  : List.of("https://one.test", "https://two.test"));
      var result =
          api.execute(
              Map.of("id", 1),
              this::json,
              transport,
              fallbackEnabled ? ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE : ServerFallback.NONE);
      assertThat(result.cancel(true)).isTrue();
      assertThat(pending.isCancelled()).isTrue();
    }
  }

  @Test
  void cleanupFailureDoesNotVetoSelectedFallback() {
    for (boolean fatal : new boolean[] {false, true}) {
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      HttpTransport transport =
          new HttpTransport() {
            public CompletableFuture<HttpResponse> execute(HttpRequestInfo<byte[]> request) {
              int status = calls.incrementAndGet() == 1 ? 503 : 200;
              return CompletableFuture.completedFuture(
                  new HttpResponse() {
                    public int status() {
                      return status;
                    }

                    public byte[] body() {
                      return new byte[0];
                    }

                    public Map<String, List<String>> headers() {
                      return Map.of();
                    }

                    public void close() {
                      if (fatal) throw new AssertionError("cleanup");
                      throw new IllegalStateException("cleanup");
                    }
                  });
            }

            public void close() {}
          };
      var response =
          withServers(List.of("https://first.test", "https://second.test"))
              .execute(
                  Map.of("id", 1),
                  this::json,
                  transport,
                  ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE)
              .join();
      assertThat(response.status()).isEqualTo(200);
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @Test
  void fallbackOwnsDiscardedAndCancelledResponsesButNotDeliveredResponses() {
    for (boolean cancelled : new boolean[] {false, true}) {
      var discarded = new TrackingResponse(503, new java.util.concurrent.atomic.AtomicInteger());
      var last = new TrackingResponse(200, new java.util.concurrent.atomic.AtomicInteger());
      var pending = new CompletableFuture<HttpResponse>();
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      HttpTransport transport =
          new HttpTransport() {
            public CompletableFuture<HttpResponse> execute(HttpRequestInfo<byte[]> request) {
              if (calls.incrementAndGet() == 1) return CompletableFuture.completedFuture(discarded);
              assertThat(discarded.closes().get()).isEqualTo(1);
              return pending;
            }

            public void close() {
              fail("borrowed transport");
            }
          };
      var result =
          withServers(List.of("https://first.test", "https://second.test"))
              .execute(
                  Map.of("id", 1),
                  this::json,
                  transport,
                  ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE);
      if (cancelled) assertThat(result.cancel(true)).isTrue();
      pending.complete(last);
      if (cancelled) assertThat(last.closes().get()).isEqualTo(1);
      else {
        assertThat(result.join()).isSameAs(last);
        assertThat(last.closes().get()).isEqualTo(0);
        result.join().close();
        assertThat(last.closes().get()).isEqualTo(1);
      }
    }
  }

  @Test
  void discoversTheSdkReaderFromTheExistingArtifact() {
    var reader =
        java.util.ServiceLoader.load(
                cloud.openworkflow.sdk.impl.additional.NamedSuppliedAdditionalObject.class)
            .stream()
            .map(java.util.ServiceLoader.Provider::get)
            .filter(
                provider -> provider.name().equals(UnifiedOpenAPIReader.UNIFIED_OPEN_API_READER))
            .findFirst()
            .orElseThrow()
            .get();
    assertThat(reader instanceof JacksonUnifiedOpenAPIReader).isTrue();
  }

  @Test
  void bindsInheritedReferencesAndSharedParameterNames() {
    var document =
        new JacksonUnifiedOpenAPIReader()
            .read(
                "api.yaml",
                """
                openapi: 3.0.3
                servers:
                  - url: https://example.test
                paths:
                  /items/{id}:
                    parameters:
                      - name: id
                        in: path
                        required: true
                      - $ref: '#/components/parameters/IdAlias'
                      - name: lang
                        in: query
                        required: true
                        schema: {default: en}
                    post:
                      operationId: save
                      parameters:
                        - name: id
                          in: query
                          required: true
                        - name: lang
                          in: query
                          required: true
                          schema: {default: fr}
                components:
                  parameters:
                    IdAlias:
                      $ref: '#/components/parameters/Id'
                    Id:
                      name: id
                      in: header
                      required: true
                """
                    .getBytes(StandardCharsets.UTF_8));
    var api = new OpenAPIClient(document, "save");
    var operation = api.operation();
    var reconstructed =
        new OperationDefinition(
            document, operation.getOperation(), operation.getPath(), operation.getMethod());
    assertThat(reconstructed.getParameters()).isEqualTo(operation.getParameters());
    var request = api.prepare(0, Map.of("id", "shared", "extra", "body"), this::json);
    assertThat(request.uri()).isEqualTo(URI.create("https://example.test/items/shared"));
    assertThat(request.headers()).isEqualTo(Map.of("id", "shared"));
    assertThat(request.queryParams()).isEqualTo(Map.of("id", "shared", "lang", "fr"));
    assertThat(WorkflowFormat.JSON.mapper().readValue(request.body(), Map.class))
        .isEqualTo(Map.of("extra", "body"));
  }

  @Test
  void selectsOperationThenPathThenRootServers() {
    var document =
        new JacksonUnifiedOpenAPIReader()
            .read(
                "api.yaml",
                """
                openapi: 3.0.3
                servers:
                  - url: /root
                paths:
                  /items:
                    servers:
                      - url: /path
                    get:
                      operationId: operation
                      servers:
                        - url: /operation
                    post:
                      operationId: path
                  /other:
                    get:
                      operationId: root
                """
                    .getBytes(StandardCharsets.UTF_8));
    for (var expected :
        Map.of(
                "operation", "https://example.test/operation/items",
                "path", "https://example.test/path/items",
                "root", "https://example.test/root/other")
            .entrySet()) {
      var api =
          new OpenAPIClient(
              document, expected.getKey(), URI.create("https://example.test/api.yaml"));
      var operation = api.operation();
      var reconstructed =
          new OperationDefinition(
              document, operation.getOperation(), operation.getPath(), operation.getMethod());
      assertThat(reconstructed.getServers()).isEqualTo(operation.getServers());
      assertThat(api.prepare(0, Map.of(), this::json).uri())
          .isEqualTo(URI.create(expected.getValue()));
    }
  }

  @Test
  void rejectsServerUrisWithoutAnAbsoluteHost() {
    for (String server : List.of("https:/api", "https:api", "https:///api")) {
      var api = withServers(List.of(server));
      assertThatExceptionOfType(IllegalArgumentException.class)
          .as(server)
          .isThrownBy(() -> api.prepare(0, Map.of("id", 1), this::json));
    }
    var bound =
        OpenAPIRequest.bind(
            withServers(List.of("https://example.test")).operation(), Map.of("id", 1));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> bound.prepare(URI.create("/relative"), this::json));
  }

  @Test
  void fallbackOwnsSerializedBodySnapshot() {
    var api = withServers(List.of("http://first", "http://second"));
    byte[] encoded = "body".getBytes(StandardCharsets.UTF_8);
    List<HttpRequestInfo<byte[]>> requests = new ArrayList<>();
    CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> first = new CompletableFuture<>();
    HttpTransport transport =
        new HttpTransport() {
          public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
              cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> request) {
            requests.add(request);
            return requests.size() == 1
                ? first
                : CompletableFuture.completedFuture(
                    new HttpResponseData(200, Map.of(), new byte[0]));
          }

          public void close() {
            fail("must remain borrowed");
          }
        };
    var result =
        api.execute(
            Map.of("id", 1),
            ignored -> encoded,
            transport,
            ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE);
    encoded[0] = 'X';
    first.complete(new HttpResponseData(503, Map.of(), new byte[0]));
    assertThat(result.join().status()).isEqualTo(200);
    assertThat(requests.size()).isEqualTo(2);
    assertThat(requests.get(1).body()).isEqualTo("body".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void packagesReflectionMetadataWithStandaloneReader() throws Exception {
    try (var stream =
        getClass()
            .getResourceAsStream(
                "/META-INF/native-image/cloud.openworkflow.sdk/openworkflow-impl-openapi/reflect-config.json")) {
      assertThat(stream).isNotNull();
      var registrations = WorkflowFormat.JSON.mapper().readValue(stream, List.class);
      var names = registrations.stream().map(entry -> ((Map<?, ?>) entry).get("name")).toList();
      assertThat(names.contains(UnifiedOpenAPI.class.getName())).isTrue();
      for (Class<?> nested : UnifiedOpenAPI.class.getDeclaredClasses()) {
        assertThat(names.contains(nested.getName())).as(nested.getName()).isTrue();
      }
    }
  }

  @Test
  void documentToRequestToInjectedJdkTransport() throws Exception {
    try (MockWebServer server = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      server.start();
      server.enqueue(new MockResponse().setResponseCode(201).setBody("saved"));
      OpenAPIClient api = new OpenAPIClient(read(server.url("/v1").toString()), "save");
      HttpRequestInfo<byte[]> request =
          api.prepare(
              0,
              Map.of(
                  "id",
                  "café /+",
                  "q",
                  "tea + café",
                  "name",
                  "N",
                  "Authorization",
                  "Bearer resolved"),
              this::json);
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.uri().getRawPath()).isEqualTo("/v1/pets/caf%C3%A9%20%2F%2B");
      assertThat(request.queryParams()).isEqualTo(Map.of("q", "tea + café", "lang", "en"));
      cloud.openworkflow.sdk.http.HttpResponse response =
          transport.execute(request).get(10, TimeUnit.SECONDS);
      assertThat(response.status()).isEqualTo(201);
      assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("saved");
      var received = server.takeRequest(10, TimeUnit.SECONDS);
      assertThat(received).isNotNull();
      assertThat(received.getHeader("Authorization")).isEqualTo("Bearer resolved");
      assertThat(received.getPath().contains("q=tea%20%2B%20caf%C3%A9")).isTrue();
      assertThat(
              WorkflowFormat.JSON.mapper().readValue(received.getBody().readByteArray(), Map.class))
          .isEqualTo(Map.of("name", "N"));
    }
  }

  @Test
  void validatesDocumentOperationAndRequiredPath() {
    var reader = new JacksonUnifiedOpenAPIReader();
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> reader.read("broken.json", "{".getBytes(StandardCharsets.UTF_8)));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new OpenAPIClient(read("http://localhost"), "absent"))
        .withMessageContaining("absent");
    OpenAPIClient api = new OpenAPIClient(read("http://localhost"), "save");
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> api.prepare(0, Map.of(), this::json))
        .withMessageContaining("id");
    Map<String, Object> nullId = new HashMap<>();
    nullId.put("id", null);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> api.prepare(0, nullId, this::json));
  }

  @Test
  void retainsRequiredDefaultsLeftoverBodyAndUnvalidatedBodyProperties() {
    OpenAPIClient api = new OpenAPIClient(read("http://localhost"), "save");
    // Body required fields and cookies were never validated/placed by the SDK.
    HttpRequestInfo<byte[]> request =
        api.prepare(0, Map.of("id", 3, "extra", 4, "cookieValue", "c"), this::json);
    assertThat(request.queryParams()).isEqualTo(Map.of("lang", "en"));
    assertThat(WorkflowFormat.JSON.mapper().readValue(request.body(), Map.class))
        .isEqualTo(Map.of("extra", 4, "cookieValue", "c"));
    assertThat(api.operation().getParameters().stream().filter(p -> p.in().equals("body")).count())
        .isEqualTo(1);
  }

  @Test
  void readsSwaggerJsonAndOpenApiYamlAndLocalReferences() {
    var reader = new JacksonUnifiedOpenAPIReader();
    for (String format : List.of("json", "yaml")) {
      String text =
          """
          {"swagger":"2.0","host":"localhost:1234","schemes":["http"],"basePath":"/v2",
          "paths":{"/pet":{"post":{"operationId":"add","parameters":[{"name":"body","in":"body","schema":{"$ref":"#/definitions/Pet"}}]}}},
          "definitions":{"Pet":{"properties":{"name":{"type":"string"}}}}}
          """;
      var document = reader.read("api." + format, text.getBytes(StandardCharsets.UTF_8));
      var api = new OpenAPIClient(document, "add");
      assertThat(document.swaggerVersion()).isEqualTo(UnifiedOpenAPI.SwaggerVersion.SWAGGER_V2);
      assertThat(api.prepare(0, Map.of("name", "pet"), this::json).uri())
          .isEqualTo(URI.create("http://localhost:1234/v2/pet"));
      assertThat(api.operation().getParameters().getFirst().in()).isEqualTo("body");
    }
    var yaml =
        reader.read(
            "api.yaml",
            """
            openapi: 3.0.3
            servers:
              - url: /v1
            paths:
              /pet:
                get:
                  operationId: get
            """
                .getBytes(StandardCharsets.UTF_8));
    var api = new OpenAPIClient(yaml, "get", URI.create("http://localhost/spec/api.yaml"));
    assertThat(api.prepare(0, Map.of(), this::json).uri())
        .isEqualTo(URI.create("http://localhost/v1/pet"));
    assertThat(api.prepare(0, Map.of(), this::json).body()).isNull();
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new OpenAPIClient(yaml, "get").prepare(0, Map.of(), this::json));
  }

  private OpenAPIClient withServers(List<String> servers) {
    UnifiedOpenAPI d = read("http://localhost");
    return new OpenAPIClient(
        new UnifiedOpenAPI(
            d.swagger(),
            servers.stream().map(UnifiedOpenAPI.Server::new).toList(),
            d.host(),
            d.basePath(),
            d.schemes(),
            d.paths(),
            d.components(),
            d.definitions(),
            d.parameters()),
        "save");
  }

  @Test
  void fallbackIsExplicitAndPreservesHttpResponsesAndTransportErrors() throws Exception {
    try (MockWebServer first = new MockWebServer();
        MockWebServer second = new MockWebServer();
        JdkHttpTransport transport = new JdkHttpTransport()) {
      first.start();
      second.start();
      var api = withServers(List.of(first.url("/").toString(), second.url("/").toString()));
      first.enqueue(new MockResponse().setResponseCode(503).setBody("first"));
      assertThat(
              api.execute(Map.of("id", 1), this::json, transport, ServerFallback.NONE)
                  .get(10, TimeUnit.SECONDS)
                  .status())
          .isEqualTo(503);
      assertThat(second.getRequestCount()).isEqualTo(0);
      first.enqueue(new MockResponse().setResponseCode(503));
      second.enqueue(new MockResponse().setResponseCode(409).setBody("conflict"));
      cloud.openworkflow.sdk.http.HttpResponse finalResponse =
          api.execute(
                  Map.of("id", 1),
                  this::json,
                  transport,
                  ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE)
              .get(10, TimeUnit.SECONDS);
      assertThat(finalResponse.status()).isEqualTo(409);
      assertThat(new String(finalResponse.body(), StandardCharsets.UTF_8)).isEqualTo("conflict");
      assertThat(second.getRequestCount()).isEqualTo(1);
      assertThat(second.takeRequest(10, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
    }
    IOException failure = new IOException("network failure");
    HttpTransport failed =
        new HttpTransport() {
          public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
              cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> request) {
            return CompletableFuture.failedFuture(failure);
          }

          public void close() {
            fail("OpenAPI does not own the transport");
          }
        };
    var api = withServers(List.of("http://first", "http://second"));
    assertThat(
            catchThrowableOfType(
                    CompletionException.class,
                    () ->
                        api.execute(
                                Map.of("id", 1),
                                this::json,
                                failed,
                                ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE)
                            .join())
                .getCause())
        .isSameAs(failure);
    List<URI> attempted = new ArrayList<>();
    HttpTransport recovering =
        new HttpTransport() {
          public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
              cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> request) {
            attempted.add(request.uri());
            return attempted.size() == 1
                ? CompletableFuture.failedFuture(failure)
                : CompletableFuture.completedFuture(
                    new HttpResponseData(200, Map.of(), new byte[0]));
          }

          public void close() {
            fail("OpenAPI does not own the transport");
          }
        };
    assertThat(
            api.execute(
                    Map.of("id", 2),
                    this::json,
                    recovering,
                    ServerFallback.ON_HTTP_OR_TRANSPORT_FAILURE)
                .join()
                .status())
        .isEqualTo(200);
    assertThat(attempted)
        .isEqualTo(List.of(URI.create("http://first/pets/2"), URI.create("http://second/pets/2")));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> withServers(List.of()).prepare(0, Map.of("id", 1), this::json));
  }

  @Test
  void concurrentInvocationsShareOnlyOperationDefinition() {
    var api = new OpenAPIClient(read("http://localhost"), "save");
    List<HttpRequestInfo<byte[]>> requests = new ArrayList<>();
    List<CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse>> pending = new ArrayList<>();
    HttpTransport transport =
        new HttpTransport() {
          public CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse> execute(
              cloud.openworkflow.sdk.http.HttpRequestInfo<byte[]> request) {
            requests.add(request);
            var result = new CompletableFuture<cloud.openworkflow.sdk.http.HttpResponse>();
            pending.add(result);
            return result;
          }

          public void close() {
            fail("must remain borrowed");
          }
        };
    var first =
        api.execute(
            Map.of("id", "one", "name", "first", "Authorization", "Bearer one"),
            this::json,
            transport,
            ServerFallback.NONE);
    var second =
        api.execute(
            Map.of("id", "two", "name", "second", "Authorization", "Bearer two"),
            this::json,
            transport,
            ServerFallback.NONE);
    assertThat(first.isDone()).isFalse();
    assertThat(second.isDone()).isFalse();
    pending.get(1).complete(new HttpResponseData(202, Map.of(), new byte[0]));
    pending.get(0).complete(new HttpResponseData(201, Map.of(), new byte[0]));
    assertThat(first.join().status()).isEqualTo(201);
    assertThat(second.join().status()).isEqualTo(202);
    assertThat(requests.get(0).uri().getPath()).isEqualTo("/pets/one");
    assertThat(requests.get(1).uri().getPath()).isEqualTo("/pets/two");
    assertThat(requests.get(0).headers().get("Authorization")).isEqualTo("Bearer one");
    assertThat(requests.get(1).headers().get("Authorization")).isEqualTo("Bearer two");
    assertThat(WorkflowFormat.JSON.mapper().readValue(requests.get(0).body(), Map.class))
        .isEqualTo(Map.of("name", "first"));
    assertThat(WorkflowFormat.JSON.mapper().readValue(requests.get(1).body(), Map.class))
        .isEqualTo(Map.of("name", "second"));
  }
}
