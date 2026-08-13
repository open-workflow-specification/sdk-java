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
package io.serverlessworkflow.fluent.spec.dsl;

import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.fluent.spec.AbstractEventConsumptionStrategyBuilder;
import io.serverlessworkflow.fluent.spec.DoTaskBuilder;
import io.serverlessworkflow.fluent.spec.EmitTaskBuilder;
import io.serverlessworkflow.fluent.spec.EventFilterBuilder;
import io.serverlessworkflow.fluent.spec.ForkTaskBuilder;
import io.serverlessworkflow.fluent.spec.OAuth2AuthenticationPolicyBuilder;
import io.serverlessworkflow.fluent.spec.OAuth2AuthenticationPolicyBuilder.OAuth2AuthenticationPropertiesEndpointsBuilder;
import io.serverlessworkflow.fluent.spec.ScheduleBuilder;
import io.serverlessworkflow.fluent.spec.TaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.TimeoutBuilder;
import io.serverlessworkflow.fluent.spec.TryTaskBuilder;
import io.serverlessworkflow.fluent.spec.configurers.AuthenticationConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.CallAsyncAPIConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.CallGrpcConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.CallHttpConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.CallOpenAPIConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.ForEachConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.ListenConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.RaiseConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.RetryConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.SetConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.SwitchConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.TasksConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.TryCatchConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.TryConfigurer;
import io.serverlessworkflow.fluent.spec.configurers.WorkflowConfigurer;
import io.serverlessworkflow.types.Errors;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * High-level Java DSL shortcuts for building workflows.
 *
 * <p>This class exposes small, composable helpers that:
 *
 * <ul>
 *   <li>Build HTTP and OpenAPI call specs.
 *   <li>Configure {@code use} blocks (secrets and authentications).
 *   <li>Define switch, listen, event, try/catch and raise recipes.
 *   <li>Compose tasks lists and common task patterns.
 * </ul>
 *
 * <p>All methods are static and designed to be used with static imports.
 */
public final class DSL {

  private DSL() {}

  // ---- Convenient shortcuts ----//

  /**
   * Create a new HTTP call specification to be used with {@link #call(CallHttpConfigurer)}
   *
   * <p>Typical usage:
   *
   * <pre>{@code
   * tasks(
   *   call(
   *     http()
   *       .get()
   *       .endpoint("http://service/api")
   *   )
   * );
   * }</pre>
   *
   * @return a new {@link CallHttpSpec} instance
   */
  public static CallHttpSpec http() {
    return new CallHttpSpec();
  }

  /**
   * Create a new OpenAPI call specification to be used with {@link #call(CallOpenAPIConfigurer)}.
   *
   * <p>Typical usage:
   *
   * <pre>{@code
   * tasks(
   *   call(
   *     openapi()
   *       .document("http://service/openapi.json")
   *       .operation("getUser")
   *   )
   * );
   * }</pre>
   *
   * @return a new {@link CallOpenAPISpec} instance
   */
  public static CallOpenAPISpec openapi() {
    return new CallOpenAPISpec();
  }

  public static CallGrpcSpec grpc() {
    return new CallGrpcSpec();
  }

  /**
   * Create a new AsyncAPI call specification to be used with {@link #call(CallAsyncAPIConfigurer)}.
   *
   * <p>Typical usage:
   *
   * <pre>{@code
   * tasks(
   *   call(
   *     asyncapi()
   *       .document("http://acme.org/asyncapi.yaml")
   *       .operation("greet")
   *       .message(Map.of("greeting", "hello"))
   *   )
   * );
   * }</pre>
   *
   * @return a new {@link CallAsyncAPISpec} instance
   */
  public static CallAsyncAPISpec asyncapi() {
    return new CallAsyncAPISpec();
  }

  public static WorkflowSpec workflow(String namespace, String name, String version) {
    return new WorkflowSpec().namespace(namespace).name(name).version(version);
  }

  public static WorkflowSpec workflow(String namespace, String name) {
    return new WorkflowSpec().namespace(namespace).name(name);
  }

  public static WorkflowSpec workflow() {
    return new WorkflowSpec();
  }

  /**
   * Convenience for defining a {@code use} block with a single secret.
   *
   * <p>Example:
   *
   * <pre>{@code
   * workflow()
   *   .use(secret("db-password"))
   *   .build();
   * }</pre>
   *
   * @param secret secret identifier to add to the workflow
   * @return a {@link UseSpec} preconfigured with the given secret
   */
  public static UseSpec secret(String secret) {
    return new UseSpec().secret(secret);
  }

  /**
   * Convenience for defining a {@code use} block with multiple secrets.
   *
   * <p>Example:
   *
   * <pre>{@code
   * workflow()
   *   .use(secrets("db-password", "api-key"))
   *   .build();
   * }</pre>
   *
   * @param secret one or more secret identifiers
   * @return a {@link UseSpec} preconfigured with the given secrets
   */
  public static UseSpec secrets(String... secret) {
    return new UseSpec().secrets(secret);
  }

  /**
   * Convenience for defining a single reusable authentication policy by name.
   *
   * <p>Example:
   *
   * <pre>{@code
   * workflow()
   *   .use(auth("basic-auth", basic("user", "pass")));
   * }</pre>
   *
   * @param name logical authentication policy name
   * @param auth authentication configurer (e.g. {@link #basic(String, String)})
   * @return a {@link UseSpec} preconfigured with the given authentication
   */
  public static UseSpec auth(String name, AuthenticationConfigurer auth) {
    return new UseSpec().auth(name, auth);
  }

  /**
   * Create an empty {@link UseSpec} for incremental configuration.
   *
   * <p>Example:
   *
   * <pre>{@code
   * workflow()
   *   .use(
   *     use()
   *       .secret("db-password")
   *       .auth("basic-auth", basic("u", "p"))
   *   );
   * }</pre>
   *
   * @return a new, empty {@link UseSpec}
   */
  public static UseSpec use() {
    return new UseSpec();
  }

  /**
   * Create an empty {@link SwitchSpec} for building switch cases.
   *
   * <p>Typical usage is via chaining methods on {@link SwitchSpec} and passing it to {@link
   * #switchCase(SwitchConfigurer)}.
   *
   * @return a new {@link SwitchSpec}
   */
  public static SwitchSpec cases() {
    return new SwitchSpec();
  }

  /**
   * Start building a {@code listen} specification without a predefined strategy.
   *
   * <p>Use methods on {@link ListenSpec} like {@code one()}, {@code any()} or {@code all()} and
   * then pass the spec to {@link #listen(ListenConfigurer)}.
   *
   * @return a new {@link ListenSpec}
   */
  public static ListenSpec to() {
    return new ListenSpec();
  }

  /**
   * Start building an event specification for use with {@code emit} tasks.
   *
   * <p>This method returns an {@link EventFilterSpec}, which is primarily used for event filtering
   * (for {@code listen} tasks) but is also reused here as a filter-like description of the event
   * that will be emitted. Configure the event type, source, and payload using {@link
   * EventFilterSpec} methods, then pass the resulting spec to {@link #emit(Consumer)}.
   *
   * @return a new {@link EventFilterSpec} instance for event emission
   */
  public static EventFilterSpec event() {
    return new EventFilterSpec();
  }

  /**
   * Start building a {@code try/catch} specification for use with {@link #tryCatch(TryConfigurer)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * tasks(
   *   tryCatch(
   *     tryCatch()
   *       .doTasks(...)
   *       .on(catchWhen("jqExpr", retryWhen(...), tasks(...)))
   *   )
   * );
   * }</pre>
   *
   * @return a new {@link TrySpec}
   */
  public static TrySpec tryCatch() {
    return new TrySpec();
  }

  /**
   * Build a {@link TryCatchConfigurer} that catches when a given expression matches, configures
   * retry, and runs the given tasks as the catch body.
   *
   * @param when jq-style condition expression
   * @param retry retry strategy configurer
   * @param doTasks one or more task configurers to execute in the catch block
   * @return a {@link TryCatchConfigurer} to be used inside {@link TrySpec}
   */
  public static TryCatchConfigurer catchWhen(
      String when, RetryConfigurer retry, TasksConfigurer... doTasks) {
    return c -> c.when(when).retry(retry).doTasks(tasks(doTasks));
  }

  /**
   * Build a {@link TryCatchConfigurer} that catches when the given expression does <em>not</em>
   * match, configures retry, and runs the given tasks as the catch body.
   *
   * @param when jq-style condition expression to exclude
   * @param retry retry strategy configurer
   * @param doTasks one or more task configurers to execute in the catch block
   * @return a {@link TryCatchConfigurer} to be used inside {@link TrySpec}
   */
  public static TryCatchConfigurer catchExceptWhen(
      String when, RetryConfigurer retry, TasksConfigurer... doTasks) {
    return c -> c.exceptWhen(when).retry(retry).doTasks(tasks(doTasks));
  }

  /**
   * Build a basic retry strategy that retries when the given condition is true and respects a
   * duration limit.
   *
   * @param when jq-style condition expression
   * @param limitDuration duration in ISO-8601 or spec-compatible format
   * @return a {@link RetryConfigurer} representing the retry strategy
   */
  public static RetryConfigurer retryWhen(String when, String limitDuration) {
    return r -> r.when(when).limit(l -> l.duration(limitDuration));
  }

  /**
   * Build a retry strategy that retries when the given condition does <em>not</em> hold and
   * respects a duration limit.
   *
   * @param when jq-style condition expression to exclude
   * @param limitDuration duration in ISO-8601 or spec-compatible format
   * @return a {@link RetryConfigurer} representing the retry strategy
   */
  public static RetryConfigurer retryExceptWhen(String when, String limitDuration) {
    return r -> r.exceptWhen(when).limit(l -> l.duration(limitDuration));
  }

  /**
   * Build an error filter for the {@code catch} clause based on a concrete error type URI and HTTP
   * status.
   *
   * @param errType error type URI
   * @param status expected HTTP status code
   * @return a consumer to configure {@link TryTaskBuilder.CatchErrorsBuilder}
   */
  public static Consumer<TryTaskBuilder.CatchErrorsBuilder> errorFilter(URI errType, int status) {
    return e -> e.type(errType.toString()).status(status);
  }

  /**
   * Build an error filter for the {@code catch} clause using a standard {@link Errors.Standard}
   * error and HTTP status.
   *
   * @param errType standard error enum
   * @param status expected HTTP status code
   * @return a consumer to configure {@link TryTaskBuilder.CatchErrorsBuilder}
   */
  public static Consumer<TryTaskBuilder.CatchErrorsBuilder> errorFilter(
      Errors.Standard errType, int status) {
    return e -> e.type(errType.toString()).status(status);
  }

  /**
   * Create an {@link AuthenticationConfigurer} that references a previously defined authentication
   * policy by name in the {@code use} block.
   *
   * <p>Equivalent to setting {@code authentication.use(name)}.
   *
   * @param authName the name of a reusable authentication policy
   * @return an {@link AuthenticationConfigurer} that sets {@code use(authName)}
   */
  public static AuthenticationConfigurer use(String authName) {
    return a -> a.use(authName);
  }

  /**
   * Build a BASIC authentication configurer with username and password.
   *
   * <p>Typical usage:
   *
   * <pre>{@code
   * auth("basic-auth", basic("user", "pass"))
   * }</pre>
   *
   * @param username BASIC auth username
   * @param password BASIC auth password
   * @return an {@link AuthenticationConfigurer} for BASIC authentication
   */
  public static AuthenticationConfigurer basic(String username, String password) {
    return a -> a.basic(b -> b.username(username).password(password));
  }

  public static AuthenticationConfigurer basic(String secret) {
    return a -> a.basic(b -> b.use(secret));
  }

  /**
   * Build a Bearer token authentication configurer.
   *
   * <p>Typical usage:
   *
   * <pre>{@code
   * auth("bearer-auth", bearer("token-123"))
   * }</pre>
   *
   * @param token bearer token
   * @return an {@link AuthenticationConfigurer} for Bearer authentication
   */
  public static AuthenticationConfigurer bearer(String token) {
    return a -> a.bearer(b -> b.token(token));
  }

  public static AuthenticationConfigurer bearerUse(String secret) {
    return a -> a.bearer(b -> b.use(secret));
  }

  /**
   * Build a Digest authentication configurer with username and password.
   *
   * @param username digest auth username
   * @param password digest auth password
   * @return an {@link AuthenticationConfigurer} for Digest authentication
   */
  public static AuthenticationConfigurer digest(String username, String password) {
    return a -> a.digest(d -> d.username(username).password(password));
  }

  public static AuthenticationConfigurer digest(String secret) {
    return a -> a.digest(d -> d.use(secret));
  }

  /**
   * Build an OpenID Connect (OIDC) authentication configurer without client credentials.
   *
   * @param authority OIDC authority/issuer URL
   * @param grant OAuth2 grant type
   * @return an {@link AuthenticationConfigurer} using OIDC
   */
  public static AuthenticationConfigurer oidc(
      String authority, OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant) {
    return a -> a.openIDConnect(o -> o.authority(authority).grant(grant));
  }

  /**
   * Build an OpenID Connect (OIDC) authentication configurer with client credentials.
   *
   * @param authority OIDC authority/issuer URL
   * @param grant OAuth2 grant type
   * @param clientId client identifier
   * @param clientSecret client secret
   * @return an {@link AuthenticationConfigurer} using OIDC with client credentials
   */
  public static AuthenticationConfigurer oidc(
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret) {
    return a ->
        a.openIDConnect(
            o ->
                o.authority(authority)
                    .grant(grant)
                    .client(c -> c.id(clientId).secret(clientSecret)));
  }

  public static AuthenticationConfigurer oidc(String secret) {
    return a -> a.openIDConnect(o -> o.use(secret));
  }

  /**
   * Alias for {@link #oidc(String, OAuth2AuthenticationData.OAuth2AuthenticationDataGrant)} using
   * OAuth2 semantics.
   *
   * @param authority OAuth2/OIDC authority URL
   * @param grant OAuth2 grant type
   * @return an {@link AuthenticationConfigurer} configured as OAuth2
   */
  public static AuthenticationConfigurer oauth2(
      String authority, OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant) {
    return a -> a.openIDConnect(o -> o.authority(authority).grant(grant));
  }

  /**
   * Alias for {@link #oidc(String, OAuth2AuthenticationData.OAuth2AuthenticationDataGrant, String,
   * String)} using OAuth2 naming.
   *
   * @param authority OAuth2/OIDC authority URL
   * @param grant OAuth2 grant type
   * @param clientId client identifier
   * @param clientSecret client secret
   * @return an {@link AuthenticationConfigurer} configured as OAuth2 with client credentials
   */
  public static AuthenticationConfigurer oauth2(
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret) {
    return a ->
        a.openIDConnect(
            o ->
                o.authority(authority)
                    .grant(grant)
                    .client(c -> c.id(clientId).secret(clientSecret)));
  }

  public static AuthenticationConfigurer oauth2(String secret) {
    return a -> a.openIDConnect(o -> o.use(secret));
  }

  /**
   * Build an OAuth2 authentication configurer with client credentials and explicit OAuth2 {@code
   * endpoints} (token, revocation, introspection).
   *
   * <p>Unlike the other {@code oauth2(...)} helpers, which delegate to OpenID Connect, this
   * overload produces a genuine OAuth2 authentication policy so that the OAuth2-specific {@code
   * endpoints} can be configured without hand-building the whole policy.
   *
   * <pre>{@code
   * oauth2(
   *     "https://auth.example.com/",
   *     OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS,
   *     "client-id",
   *     "client-secret",
   *     e -> e.token("/custom/token"));
   * }</pre>
   *
   * @param authority OAuth2 authority URL (the authorization server base URL)
   * @param grant OAuth2 grant type
   * @param clientId client identifier
   * @param clientSecret client secret
   * @param endpoints consumer that configures the OAuth2 endpoints (e.g. {@code e -> e.token(...)})
   * @return an {@link AuthenticationConfigurer} configured as OAuth2 with custom endpoints
   */
  public static AuthenticationConfigurer oauth2(
      String authority,
      OAuth2AuthenticationData.OAuth2AuthenticationDataGrant grant,
      String clientId,
      String clientSecret,
      Consumer<OAuth2AuthenticationPropertiesEndpointsBuilder> endpoints) {
    return a ->
        a.oauth2(
            o -> {
              o.authority(authority).grant(grant).client(c -> c.id(clientId).secret(clientSecret));
              o.endpoints(endpoints);
            });
  }

  /**
   * Build a fully customizable OAuth2 authentication configurer.
   *
   * <p>This overload exposes the complete {@link OAuth2AuthenticationPolicyBuilder} so that every
   * field of the <a
   * href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#oauth2-authentication">OAuth2
   * authentication</a> section of the Serverless Workflow DSL can be configured: {@code authority},
   * {@code grant}, {@code client}, {@code request} encoding, {@code issuers}, {@code scopes},
   * {@code audiences}, {@code username}, {@code password}, {@code subject}, {@code actor} and
   * {@code endpoints} (token, revocation, introspection).
   *
   * <p>Because the OAuth2-only {@code endpoints(...)} method is declared on {@link
   * OAuth2AuthenticationPolicyBuilder} (not on the shared {@link
   * io.serverlessworkflow.fluent.spec.OIDCBuilder}), call it first when chaining fluently:
   *
   * <pre>{@code
   * oauth2(o -> o.endpoints(e -> e.token("/custom/token"))
   *              .authority("https://auth.example.com/")
   *              .grant(OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS)
   *              .scopes("read", "write")
   *              .audiences("api://default")
   *              .client(c -> c.id("client-id").secret("client-secret")));
   * }</pre>
   *
   * @param cfg consumer that configures the OAuth2 authentication policy builder
   * @return an {@link AuthenticationConfigurer} configured as OAuth2
   */
  public static AuthenticationConfigurer oauth2(Consumer<OAuth2AuthenticationPolicyBuilder> cfg) {
    return a -> a.oauth2(cfg);
  }

  /**
   * Build a {@link RaiseSpec} for an error with a string type expression and HTTP status.
   *
   * @param errExpr error type expression (URI or logical id)
   * @param status HTTP status code
   * @return a {@link RaiseSpec} configured with type and status
   */
  public static RaiseSpec error(String errExpr, int status) {
    return new RaiseSpec().type(errExpr).status(status);
  }

  /**
   * Build a {@link RaiseSpec} for an error with a string type expression.
   *
   * @param errExpr error type expression (URI or logical id)
   * @return a {@link RaiseSpec} configured with type only
   */
  public static RaiseSpec error(String errExpr) {
    return new RaiseSpec().type(errExpr);
  }

  /**
   * Build a {@link RaiseSpec} for an error using a concrete error type URI.
   *
   * @param errType error type URI
   * @return a {@link RaiseSpec} configured with type only
   */
  public static RaiseSpec error(URI errType) {
    return new RaiseSpec().type(errType);
  }

  /**
   * Build a {@link RaiseSpec} for an error using a concrete error type URI and HTTP status.
   *
   * @param errType error type URI
   * @param status HTTP status code
   * @return a {@link RaiseSpec} configured with type and status
   */
  public static RaiseSpec error(URI errType, int status) {
    return new RaiseSpec().type(errType).status(status);
  }

  // --- Errors Recipes --- //

  /**
   * Build a {@link RaiseSpec} based on a standard error enum.
   *
   * @param std standard error
   * @return a {@link RaiseSpec} using the standard error URI and status
   */
  public static RaiseSpec error(Errors.Standard std) {
    return error(std.uri(), std.status());
  }

  /**
   * Build a {@link RaiseSpec} based on a standard error enum with an overridden status.
   *
   * @param std standard error
   * @param status HTTP status code
   * @return a {@link RaiseSpec} using the standard URI and custom status
   */
  public static RaiseSpec error(Errors.Standard std, int status) {
    return error(std.uri(), status);
  }

  /**
   * Shortcut for a generic server runtime error according to {@link Errors#RUNTIME}.
   *
   * @return a {@link RaiseSpec} representing a runtime server error
   */
  public static RaiseSpec serverError() {
    return error(Errors.RUNTIME);
  }

  /**
   * Shortcut for a communication error according to {@link Errors#COMMUNICATION}.
   *
   * @return a {@link RaiseSpec} representing a communication error
   */
  public static RaiseSpec communicationError() {
    return error(Errors.COMMUNICATION);
  }

  /**
   * Shortcut for a "not implemented" error according to {@link Errors#NOT_IMPLEMENTED}.
   *
   * @return a {@link RaiseSpec} representing a not-implemented error
   */
  public static RaiseSpec notImplementedError() {
    return error(Errors.NOT_IMPLEMENTED);
  }

  /**
   * Shortcut for an unauthorized error according to {@link Errors#AUTHENTICATION}.
   *
   * @return a {@link RaiseSpec} representing an authentication error
   */
  public static RaiseSpec unauthorizedError() {
    return error(Errors.AUTHENTICATION);
  }

  /**
   * Shortcut for a forbidden error according to {@link Errors#AUTHORIZATION}.
   *
   * @return a {@link RaiseSpec} representing an authorization error
   */
  public static RaiseSpec forbiddenError() {
    return error(Errors.AUTHORIZATION);
  }

  /**
   * Shortcut for a timeout error according to {@link Errors#TIMEOUT}.
   *
   * @return a {@link RaiseSpec} representing a timeout error
   */
  public static RaiseSpec timeoutError() {
    return error(Errors.TIMEOUT);
  }

  /**
   * Shortcut for a data error according to {@link Errors#DATA}.
   *
   * @return a {@link RaiseSpec} representing a data error
   */
  public static RaiseSpec dataError() {
    return error(Errors.DATA);
  }

  // ---- Tasks ----//

  /**
   * Create a {@link TasksConfigurer} that adds an HTTP call task using a low-level HTTP configurer.
   *
   * <p>Example:
   *
   * <pre>{@code
   * tasks(
   *   call(http -> http.method("GET").endpoint("..."))
   * );
   * }</pre>
   *
   * @param configurer low-level HTTP configurer
   * @return a {@link TasksConfigurer} that adds a CallHTTP task
   */
  public static TasksConfigurer call(CallHttpConfigurer configurer) {
    return list -> list.http(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an HTTP call task with an explicit name using a
   * low-level HTTP configurer.
   *
   * @param name the task name
   * @param configurer low-level HTTP configurer
   * @return a {@link TasksConfigurer} that adds a CallHTTP task
   */
  public static TasksConfigurer call(String name, CallHttpConfigurer configurer) {
    return list -> list.http(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an OpenAPI call task.
   *
   * <p>Example:
   *
   * <pre>{@code
   * tasks(
   *   call(openapi -> openapi.document("...").operation("getUser"))
   * );
   * }</pre>
   *
   * @param configurer OpenAPI configurer
   * @return a {@link TasksConfigurer} that adds a CallOpenAPI task
   */
  public static TasksConfigurer call(CallOpenAPIConfigurer configurer) {
    return list -> list.openapi(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a sub-workflow call task using a {@link
   * WorkflowConfigurer}.
   *
   * <p>
   *
   * <pre>{@code
   * tasks(
   *  subflow(workflow("org.acme", "sub-workflow", "0.1.0").input(...)
   * );
   * }</pre>
   *
   * @param configurer
   * @return
   */
  public static TasksConfigurer subflow(WorkflowConfigurer configurer) {
    return list -> list.workflow(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an OpenAPI call task with an explicit name.
   *
   * @param name the task name
   * @param configurer OpenAPI configurer
   * @return a {@link TasksConfigurer} that adds a CallOpenAPI task
   */
  public static TasksConfigurer call(String name, CallOpenAPIConfigurer configurer) {
    return list -> list.openapi(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an AsyncAPI call task.
   *
   * @param configurer AsyncAPI configurer
   * @return a {@link TasksConfigurer} that adds a CallAsyncAPI task
   */
  public static TasksConfigurer call(CallAsyncAPIConfigurer configurer) {
    return list -> list.asyncapi(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an AsyncAPI call task with an explicit name.
   *
   * @param name the task name
   * @param configurer AsyncAPI configurer
   * @return a {@link TasksConfigurer} that adds a CallAsyncAPI task
   */
  public static TasksConfigurer call(String name, CallAsyncAPIConfigurer configurer) {
    return list -> list.asyncapi(name, configurer);
  }

  public static TasksConfigurer call(CallGrpcConfigurer configurer) {
    return list -> list.grpc(configurer);
  }

  public static TasksConfigurer call(String name, CallGrpcConfigurer configurer) {
    return list -> list.grpc(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code set} task using a low-level configurer.
   *
   * @param configurer configurer for the set task
   * @return a {@link TasksConfigurer} that adds a SetTask
   */
  public static TasksConfigurer set(SetConfigurer configurer) {
    return list -> list.set(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code set} task with an explicit name using a
   * low-level configurer.
   *
   * @param name the task name
   * @param configurer configurer for the set task
   * @return a {@link TasksConfigurer} that adds a SetTask
   */
  public static TasksConfigurer set(String name, SetConfigurer configurer) {
    return list -> list.set(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code set} task using a raw expression.
   *
   * @param expr expression to apply in the set task
   * @return a {@link TasksConfigurer} that adds a SetTask
   */
  public static TasksConfigurer set(String expr) {
    return list -> list.set(expr);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code set} task with an explicit name using a raw
   * expression.
   *
   * @param name the task name
   * @param expr expression to apply in the set task
   * @return a {@link TasksConfigurer} that adds a SetTask
   */
  public static TasksConfigurer set(String name, String expr) {
    return list -> list.set(name, expr);
  }

  /**
   * Adds an {@code emit} task to the workflow's task sequence using a custom configurer. *
   *
   * <p>This method is typically used in conjunction with {@link #produced()} to fluently define the
   * properties of the event being emitted. *
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * emit(produced().type("my.custom.event").source("my/source"))
   * }</pre>
   *
   * @param configurer a consumer, such as an {@link EmitSpec}, that configures the {@link
   *     EmitTaskBuilder}
   * @return a {@link TasksConfigurer} to continue building the task list
   */
  public static TasksConfigurer emit(Consumer<EmitTaskBuilder> configurer) {
    return list -> list.emit(configurer);
  }

  /**
   * A convenient shortcut to add an {@code emit} task that only requires a CloudEvent type. *
   *
   * <p>Use this method when you only need to specify the {@code type} attribute of the emitted
   * event and do not need to configure additional properties like data or source.
   *
   * @param cloudEventType the {@code type} attribute of the CloudEvent to be emitted
   * @return a {@link TasksConfigurer} to continue building the task list
   */
  public static TasksConfigurer emit(String cloudEventType) {
    return list -> list.emit(new EmitSpec().type(cloudEventType));
  }

  /**
   * Starts building an event emission specification. *
   *
   * <p>This creates a new {@link EmitSpec} which acts as a fluent builder for the properties (e.g.,
   * type, source, data) of the CloudEvent to be emitted. The resulting spec can then be passed
   * directly to {@link #emit(Consumer)}.
   *
   * @return a new {@link EmitSpec} instance for fluent configuration
   */
  public static EmitSpec produced() {
    return new EmitSpec();
  }

  /**
   * @see #produced()
   */
  public static EmitSpec produced(String cloudEventType) {
    return new EmitSpec().type(cloudEventType);
  }

  /**
   * Create a {@link TasksConfigurer} that adds an {@code emit} task with an explicit name.
   *
   * @param name the task name
   * @param configurer consumer configuring {@link EmitTaskBuilder}
   * @return a {@link TasksConfigurer} that adds an EmitTask
   */
  public static TasksConfigurer emit(String name, Consumer<EmitTaskBuilder> configurer) {
    return list -> list.emit(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code listen} task.
   *
   * @param configurer listen configurer
   * @return a {@link TasksConfigurer} that adds a ListenTask
   */
  public static TasksConfigurer listen(ListenConfigurer configurer) {
    return list -> list.listen(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code listen} task with an explicit name.
   *
   * @param name the task name
   * @param configurer listen configurer
   * @return a {@link TasksConfigurer} that adds a ListenTask
   */
  public static TasksConfigurer listen(String name, ListenConfigurer configurer) {
    return list -> list.listen(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with an inline
   * duration builder.
   *
   * @param duration timeout builder consumer
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(Consumer<TimeoutBuilder> duration) {
    return list -> list.wait(w -> w.wait(duration));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with an inline
   * duration builder.
   *
   * @param name task name
   * @param duration timeout builder consumer
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(String name, Consumer<TimeoutBuilder> duration) {
    return list -> list.wait(name, w -> w.wait(duration));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with a duration
   * expression.
   *
   * @param durationExpression duration expression
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(String durationExpression) {
    return list -> list.wait(w -> w.wait(durationExpression));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with a duration
   * expression.
   *
   * @param name task name
   * @param durationExpression duration expression
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(String name, String durationExpression) {
    return list -> list.wait(name, w -> w.wait(durationExpression));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with seconds.
   *
   * <p>Example: {@code tasks(waitSeconds(30))}
   *
   * @param seconds wait duration in seconds
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitSeconds(int seconds) {
    return list -> list.wait(w -> w.wait(t -> t.duration(d -> d.seconds(seconds))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with seconds.
   *
   * <p>Example: {@code tasks(waitSeconds("pause", 30))}
   *
   * @param name task name
   * @param seconds wait duration in seconds
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitSeconds(String name, int seconds) {
    return list -> list.wait(name, w -> w.wait(t -> t.duration(d -> d.seconds(seconds))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with minutes.
   *
   * <p>Example: {@code tasks(waitMinutes(5))}
   *
   * @param minutes wait duration in minutes
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitMinutes(int minutes) {
    return list -> list.wait(w -> w.wait(t -> t.duration(d -> d.minutes(minutes))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with minutes.
   *
   * <p>Example: {@code tasks(waitMinutes("pause", 5))}
   *
   * @param name task name
   * @param minutes wait duration in minutes
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitMinutes(String name, int minutes) {
    return list -> list.wait(name, w -> w.wait(t -> t.duration(d -> d.minutes(minutes))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with hours.
   *
   * <p>Example: {@code tasks(waitHours(2))}
   *
   * @param hours wait duration in hours
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitHours(int hours) {
    return list -> list.wait(w -> w.wait(t -> t.duration(d -> d.hours(hours))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with hours.
   *
   * <p>Example: {@code tasks(waitHours("longPause", 2))}
   *
   * @param name task name
   * @param hours wait duration in hours
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitHours(String name, int hours) {
    return list -> list.wait(name, w -> w.wait(t -> t.duration(d -> d.hours(hours))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with days.
   *
   * <p>Example: {@code tasks(waitDays(1))}
   *
   * @param days wait duration in days
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitDays(int days) {
    return list -> list.wait(w -> w.wait(t -> t.duration(d -> d.days(days))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with days.
   *
   * <p>Example: {@code tasks(waitDays("dailyDelay", 1))}
   *
   * @param name task name
   * @param days wait duration in days
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitDays(String name, int days) {
    return list -> list.wait(name, w -> w.wait(t -> t.duration(d -> d.days(days))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with milliseconds.
   *
   * <p>Example: {@code tasks(waitMillis(500))}
   *
   * @param milliseconds wait duration in milliseconds
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitMillis(int milliseconds) {
    return list -> list.wait(w -> w.wait(t -> t.duration(d -> d.milliseconds(milliseconds))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with
   * milliseconds.
   *
   * <p>Example: {@code tasks(waitMillis("shortPause", 500))}
   *
   * @param name task name
   * @param milliseconds wait duration in milliseconds
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer waitMillis(String name, int milliseconds) {
    return list -> list.wait(name, w -> w.wait(t -> t.duration(d -> d.milliseconds(milliseconds))));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code wait} task configured with a Java {@link
   * java.time.Duration}.
   *
   * <p>The Duration is converted to DurationInline format with days, hours, minutes, seconds, and
   * milliseconds.
   *
   * <p>Example: {@code tasks(wait(Duration.ofMinutes(5).plusSeconds(30)))}
   *
   * @param duration wait duration as a Java Duration
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(Duration duration) {
    return list -> list.wait(w -> w.wait(duration));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a named {@code wait} task configured with a Java
   * {@link java.time.Duration}.
   *
   * <p>The Duration is converted to DurationInline format with days, hours, minutes, seconds, and
   * milliseconds.
   *
   * <p>Example: {@code tasks(wait("pause", Duration.ofMinutes(5).plusSeconds(30)))}
   *
   * @param name task name
   * @param duration wait duration as a Java Duration
   * @return a {@link TasksConfigurer} that adds a WaitTask
   */
  public static TasksConfigurer wait(String name, Duration duration) {
    return list -> list.wait(name, w -> w.wait(duration));
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code forEach} task.
   *
   * @param configurer for-each configurer
   * @return a {@link TasksConfigurer} that adds a ForEachTask
   */
  public static TasksConfigurer forEach(ForEachConfigurer configurer) {
    return list -> list.forEach(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code forEach} task with an explicit name.
   *
   * @param name the task name
   * @param configurer for-each configurer
   * @return a {@link TasksConfigurer} that adds a ForEachTask
   */
  public static TasksConfigurer forEach(String name, ForEachConfigurer configurer) {
    return list -> list.forEach(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code fork} task.
   *
   * @param configurer consumer configuring {@link ForkTaskBuilder}
   * @return a {@link TasksConfigurer} that adds a ForkTask
   */
  public static TasksConfigurer fork(Consumer<ForkTaskBuilder> configurer) {
    return list -> list.fork(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code fork} task with an explicit name.
   *
   * @param name the task name
   * @param configurer consumer configuring {@link ForkTaskBuilder}
   * @return a {@link TasksConfigurer} that adds a ForkTask
   */
  public static TasksConfigurer fork(String name, Consumer<ForkTaskBuilder> configurer) {
    return list -> list.fork(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code switch} task.
   *
   * @param configurer switch configurer
   * @return a {@link TasksConfigurer} that adds a SwitchTask
   */
  public static TasksConfigurer switchCase(SwitchConfigurer configurer) {
    return list -> list.switchCase(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code switch} task with an explicit name.
   *
   * @param name the task name
   * @param configurer switch configurer
   * @return a {@link TasksConfigurer} that adds a SwitchTask
   */
  public static TasksConfigurer switchCase(String name, SwitchConfigurer configurer) {
    return list -> list.switchCase(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code raise} task.
   *
   * @param configurer raise configurer
   * @return a {@link TasksConfigurer} that adds a RaiseTask
   */
  public static TasksConfigurer raise(RaiseConfigurer configurer) {
    return list -> list.raise(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code raise} task with an explicit name.
   *
   * @param name the task name
   * @param configurer raise configurer
   * @return a {@link TasksConfigurer} that adds a RaiseTask
   */
  public static TasksConfigurer raise(String name, RaiseConfigurer configurer) {
    return list -> list.raise(name, configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code try/catch} task.
   *
   * @param configurer try/catch configurer
   * @return a {@link TasksConfigurer} that adds a TryTask
   */
  public static TasksConfigurer tryCatch(TryConfigurer configurer) {
    return list -> list.tryCatch(configurer);
  }

  /**
   * Create a {@link TasksConfigurer} that adds a {@code try/catch} task with an explicit name.
   *
   * @param name the task name
   * @param configurer try/catch configurer
   * @return a {@link TasksConfigurer} that adds a TryTask
   */
  public static TasksConfigurer tryCatch(String name, TryConfigurer configurer) {
    return list -> list.tryCatch(name, configurer);
  }

  // ----- Tasks that requires tasks list --//

  /**
   * Main task list adapter to be used in {@code workflow().tasks()} consumers.
   *
   * <p>This wraps multiple {@link TasksConfigurer} into a single {@link Consumer} of {@link
   * DoTaskBuilder}.
   *
   * @param steps ordered list of task configurers
   * @return a consumer configuring {@link DoTaskBuilder} with the provided steps
   */
  public static Consumer<DoTaskBuilder> doTasks(TasksConfigurer... steps) {
    final Consumer<TaskItemListBuilder> tasks = tasks(steps);
    return d -> d.tasks(tasks);
  }

  /**
   * Task list for tasks that require a nested tasks list such as {@code for}, {@code try}, etc.
   *
   * <p>The configurers are applied in order and cloned defensively.
   *
   * @param steps ordered list of task configurers
   * @return a {@link TasksConfigurer} applying all given steps
   */
  public static TasksConfigurer tasks(TasksConfigurer... steps) {
    Objects.requireNonNull(steps, "Steps in a tasks are required");
    final List<TasksConfigurer> snapshot = List.of(steps.clone());
    return list -> snapshot.forEach(s -> s.accept(list));
  }

  /**
   * Build a {@link ForEachConfigurer} that uses a nested tasks list as its body.
   *
   * @param steps task configurers that make up the body of the for-each
   * @return a {@link ForEachConfigurer} with the given tasks
   */
  public static ForEachConfigurer forEach(TasksConfigurer... steps) {
    final Consumer<TaskItemListBuilder> tasks = DSL.tasks(steps);
    return f -> f.tasks(tasks);
  }

  /**
   * Recipe for {@link io.serverlessworkflow.api.types.ForkTask} where branches compete (first to
   * complete "wins").
   *
   * <p>Configures {@code compete(true)} and sets the branches tasks list.
   *
   * @param steps task configurers shared by all branches
   * @return a {@link Consumer} configuring {@link ForkTaskBuilder}
   */
  public static Consumer<ForkTaskBuilder> branchesCompete(TasksConfigurer... steps) {
    final Consumer<TaskItemListBuilder> tasks = DSL.tasks(steps);
    return f -> f.compete(true).branches(tasks);
  }

  /**
   * Recipe for {@link io.serverlessworkflow.api.types.ForkTask} where branches do not compete (all
   * branches are executed).
   *
   * <p>Configures {@code compete(false)} and sets the branches tasks list.
   *
   * @param steps task configurers shared by all branches
   * @return a {@link Consumer} configuring {@link ForkTaskBuilder}
   */
  public static Consumer<ForkTaskBuilder> branches(TasksConfigurer... steps) {
    final Consumer<TaskItemListBuilder> tasks = DSL.tasks(steps);
    return f -> f.compete(false).branches(tasks);
  }

  /**
   * Shortcut to configure a timeout in days.
   *
   * <p>Example: {@code timeout(timeoutDays(2))}
   *
   * @param days timeout duration in days
   * @return a consumer configuring {@link TimeoutBuilder}
   */
  public static Consumer<TimeoutBuilder> timeoutDays(int days) {
    return t -> t.duration(d -> d.days(days));
  }

  /**
   * Shortcut to configure a timeout in hours.
   *
   * <p>Example: {@code timeout(timeoutHours(1))}
   *
   * @param hours timeout duration in hours
   * @return a consumer configuring {@link TimeoutBuilder}
   */
  public static Consumer<TimeoutBuilder> timeoutHours(int hours) {
    return t -> t.duration(d -> d.hours(hours));
  }

  /**
   * Shortcut to configure a timeout in minutes.
   *
   * <p>Example: {@code timeout(timeoutMinutes(15))}
   *
   * @param minutes timeout duration in minutes
   * @return a consumer configuring {@link TimeoutBuilder}
   */
  public static Consumer<TimeoutBuilder> timeoutMinutes(int minutes) {
    return t -> t.duration(d -> d.minutes(minutes));
  }

  /**
   * Shortcut to configure a timeout in seconds.
   *
   * <p>Example: {@code timeout(timeoutSeconds(30))}
   *
   * @param seconds timeout duration in seconds
   * @return a consumer configuring {@link TimeoutBuilder}
   */
  public static Consumer<TimeoutBuilder> timeoutSeconds(int seconds) {
    return t -> t.duration(d -> d.seconds(seconds));
  }

  /**
   * Shortcut to configure a timeout in milliseconds.
   *
   * <p>Example: {@code timeout(timeoutMillis(500))}
   *
   * @param milliseconds timeout duration in milliseconds
   * @return a consumer configuring {@link TimeoutBuilder}
   */
  public static Consumer<TimeoutBuilder> timeoutMillis(int milliseconds) {
    return t -> t.duration(d -> d.milliseconds(milliseconds));
  }

  // ---- Schedules ----//

  /**
   * Shortcut to configure a schedule using an 'every' inline duration.
   *
   * <p>Example: {@code schedule(every(timeoutMinutes(15)))}
   *
   * @param duration consumer configuring the timeout (e.g., from {@link #timeoutMinutes(int)})
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> every(Consumer<TimeoutBuilder> duration) {
    return s -> s.every(duration);
  }

  /**
   * Shortcut to configure a schedule using an 'every' duration expression.
   *
   * <p>Example: {@code schedule(every("PT15M"))}
   *
   * @param durationExpression duration string expression
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> every(String durationExpression) {
    return s -> s.every(durationExpression);
  }

  /**
   * Shortcut to configure a schedule using a CRON expression.
   *
   * <p>Example: {@code schedule(cron("0 0 * * *"))}
   *
   * @param cron cron string expression
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> cron(String cron) {
    return s -> s.cron(cron);
  }

  /**
   * Shortcut to configure a schedule using an 'after' inline duration.
   *
   * <p>Example: {@code schedule(after(timeoutHours(1)))}
   *
   * @param duration consumer configuring the timeout (e.g., from {@link #timeoutHours(int)})
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> after(Consumer<TimeoutBuilder> duration) {
    return s -> s.after(duration);
  }

  /**
   * Shortcut to configure a schedule using an 'after' duration expression.
   *
   * <p>Example: {@code schedule(after("PT1H"))}
   *
   * @param durationExpression duration string expression
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> after(String durationExpression) {
    return s -> s.after(durationExpression);
  }

  /**
   * Shortcut to configure a schedule using an 'on' event consumption strategy.
   *
   * <p>Example: {@code schedule(on(one("my.event")))}
   *
   * @param strategy consumer configuring the event strategy
   * @return a consumer configuring {@link ScheduleBuilder}
   */
  public static Consumer<ScheduleBuilder> on(
      Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> strategy) {
    return s -> s.on(strategy);
  }

  /**
   * Creates a 'one' event consumption strategy using a specific event type string.
   *
   * @param eventType the event type
   * @return a consumer configuring the event strategy builder
   */
  public static Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> one(String eventType) {
    return one(event().type(eventType));
  }

  /**
   * Creates a 'one' event consumption strategy.
   *
   * @param filter consumer configuring the event filter
   * @return a consumer configuring the event strategy builder
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> one(
      Consumer<EventFilterBuilder> filter) {
    return b -> ((AbstractEventConsumptionStrategyBuilder) b).one(filter);
  }

  /**
   * Creates an 'all' event consumption strategy.
   *
   * @param filters consumers configuring the event filters
   * @return a consumer configuring the event strategy builder
   */
  @SafeVarargs
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> all(
      Consumer<EventFilterBuilder>... filters) {
    return b -> ((AbstractEventConsumptionStrategyBuilder) b).all(filters);
  }

  /**
   * Creates an 'any' event consumption strategy.
   *
   * @param filters consumers configuring the event filters
   * @return a consumer configuring the event strategy builder
   */
  @SafeVarargs
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Consumer<AbstractEventConsumptionStrategyBuilder<?, ?, ?>> any(
      Consumer<EventFilterBuilder>... filters) {
    return b -> ((AbstractEventConsumptionStrategyBuilder) b).any(filters);
  }
}
