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
package io.serverlessworkflow.impl;

import io.serverlessworkflow.api.types.DurationInline;
import io.serverlessworkflow.api.types.ExportAs;
import io.serverlessworkflow.api.types.InputFrom;
import io.serverlessworkflow.api.types.OutputAs;
import io.serverlessworkflow.api.types.SchemaUnion;
import io.serverlessworkflow.api.types.SecretBasedAuthenticationPolicy;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.TaskTimeout;
import io.serverlessworkflow.api.types.Timeout;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.ExpressionUtils;
import io.serverlessworkflow.impl.resources.ResourceLoader;
import io.serverlessworkflow.impl.schema.SchemaValidator;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkflowUtils {

  private WorkflowUtils() {}

  private static final Logger logger = LoggerFactory.getLogger(WorkflowUtils.class);

  public static Optional<SchemaValidator> getSchemaValidator(
      SchemaValidatorFactory validatorFactory, ResourceLoader resourceLoader, SchemaUnion schema) {
    if (schema != null) {

      if (schema.getSchemaInline() != null) {
        return Optional.of(validatorFactory.getValidator(schema.getSchemaInline()));
      } else if (schema.getSchemaExternal() != null) {
        return Optional.of(
            resourceLoader.loadStatic(
                schema.getSchemaExternal().getResource(), validatorFactory::getValidator));
      }
    }
    return Optional.empty();
  }

  public static boolean isValid(String str) {
    return str != null && !str.isBlank();
  }

  public static Optional<WorkflowFilter> buildWorkflowFilter(
      WorkflowApplication app, InputFrom from) {
    return from != null
        ? Optional.of(buildFilterFromStrObject(app, from.getString(), from.getObject()))
        : Optional.empty();
  }

  public static Optional<WorkflowFilter> buildWorkflowFilter(WorkflowApplication app, OutputAs as) {
    return as != null
        ? Optional.of(buildFilterFromStrObject(app, as.getString(), as.getObject()))
        : Optional.empty();
  }

  public static Optional<WorkflowFilter> buildWorkflowFilter(WorkflowApplication app, ExportAs as) {
    return as != null
        ? Optional.of(
            buildFilterFromStrObject(
                app.expressionFactory(), app.contextFactory(), as.getString(), as.getObject()))
        : Optional.empty();
  }

  public static WorkflowFilter buildWorkflowFilter(WorkflowApplication app, Object obj) {
    return obj instanceof String str
        ? buildWorkflowFilter(app, str)
        : app.expressionFactory().buildFilter(ExpressionDescriptor.object(obj), app.modelFactory());
  }

  public static WorkflowFilter buildWorkflowFilter(
      WorkflowApplication app, String str, Map<String, Object> object) {
    return buildFilterFromStrObject(app, str, object);
  }

  public static WorkflowValueResolver<String> buildStringFilter(
      WorkflowApplication app, String expression, String literal) {
    return expression != null ? toExprString(app, expression) : toString(literal);
  }

  public static WorkflowValueResolver<String> buildStringFilter(
      WorkflowApplication app, String str) {
    return ExpressionUtils.isExpr(str) ? toExprString(app, str) : toString(str);
  }

  public static WorkflowValueResolver<String> buildCollectionFilter(
      WorkflowApplication app, String expression) {
    return expression != null ? toExprString(app, expression) : toString(expression);
  }

  private static WorkflowValueResolver<String> toExprString(
      WorkflowApplication app, String expression) {
    return app.expressionFactory().resolveString(ExpressionDescriptor.from(expression));
  }

  private static WorkflowValueResolver<String> toString(String literal) {
    return (w, t, m) -> literal;
  }

  private static WorkflowFilter buildFilterFromStrObject(
      WorkflowApplication app, String str, Object object) {
    return buildFilterFromStrObject(app.expressionFactory(), app.modelFactory(), str, object);
  }

  private static WorkflowFilter buildFilterFromStrObject(
      ExpressionFactory exprFactory, WorkflowModelFactory modelFactory, String str, Object object) {
    return exprFactory.buildFilter(new ExpressionDescriptor(str, object), modelFactory);
  }

  public static WorkflowValueResolver<Map<String, Object>> buildMapResolver(
      WorkflowApplication app, Map<String, Object> map) {
    return app.expressionFactory().resolveMap(ExpressionDescriptor.object(map));
  }

  public static WorkflowValueResolver<Map<String, Object>> buildMapResolver(
      WorkflowApplication app, String expr, Map<String, ?> map) {
    return app.expressionFactory().resolveMap(new ExpressionDescriptor(expr, map));
  }

  public static WorkflowFilter buildWorkflowFilter(WorkflowApplication app, String str) {
    return ExpressionUtils.isExpr(str)
        ? app.expressionFactory().buildFilter(ExpressionDescriptor.from(str), app.modelFactory())
        : (w, t, m) -> app.modelFactory().from(str);
  }

  public static WorkflowPredicate buildPredicate(WorkflowApplication app, String str) {
    return app.expressionFactory().buildPredicate(ExpressionDescriptor.from(str));
  }

  public static Optional<WorkflowFilter> optionalFilter(WorkflowApplication app, String str) {
    return str != null ? Optional.of(buildWorkflowFilter(app, str)) : Optional.empty();
  }

  public static Optional<WorkflowPredicate> optionalPredicate(WorkflowApplication app, String str) {
    return str != null ? Optional.of(buildPredicate(app, str)) : Optional.empty();
  }

  public static String toString(UriTemplate template) {
    URI uri = template.getLiteralUri();
    return uri != null ? uri.toString() : template.getLiteralUriTemplate();
  }

  public static void safeClose(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ex) {
        logger.warn("Error closing resource {}", closeable.getClass().getName(), ex);
      }
    }
  }

  public static void safeShutdown(ExecutorService service) {
    if (service != null && !service.isShutdown()) {
      try {
        service.shutdownNow();
        service.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException ex) {
        logger.warn("Thread was interrupted when awaiting service task termination", ex);
        Thread.currentThread().interrupt();
      }
    }
  }

  public static boolean whenExceptTest(
      Optional<WorkflowPredicate> whenFilter,
      Optional<WorkflowPredicate> exceptFilter,
      WorkflowContext workflow,
      TaskContext taskContext,
      WorkflowModel model) {
    return whenFilter.map(w -> w.test(workflow, taskContext, model)).orElse(true)
        && exceptFilter.map(w -> !w.test(workflow, taskContext, model)).orElse(true);
  }

  private static final class ZeroDelayResolverHolder {
    private static final WorkflowValueResolver<Duration> ZERO_DELAY_RESOLVER =
        (w, t, f) -> Duration.ZERO;
  }

  public static WorkflowValueResolver<Duration> fromTimeoutAfter(
      WorkflowApplication application, TimeoutAfter timeout) {
    if (timeout != null) {
      if (timeout.getDurationExpression() != null) {
        return (w, f, t) ->
            Duration.parse(
                application
                    .expressionFactory()
                    .resolveString(ExpressionDescriptor.from(timeout.getDurationExpression()))
                    .apply(w, f, t));
      } else if (timeout.getDurationLiteral() != null) {
        Duration duration = Duration.parse(timeout.getDurationLiteral());
        return (w, f, t) -> duration;
      } else if (timeout.getDurationInline() != null) {
        DurationInline inlineDuration = timeout.getDurationInline();
        return (w, t, f) ->
            Duration.ofDays(inlineDuration.getDays())
                .plus(
                    Duration.ofHours(inlineDuration.getHours())
                        .plus(Duration.ofMinutes(inlineDuration.getMinutes()))
                        .plus(Duration.ofSeconds(inlineDuration.getSeconds()))
                        .plus(Duration.ofMillis(inlineDuration.getMilliseconds())));
      }
    }
    return ZeroDelayResolverHolder.ZERO_DELAY_RESOLVER;
  }

  public static Optional<WorkflowValueResolver<Duration>> getTaskTimeout(
      WorkflowApplication appl, Workflow workflow, TaskBase task) {
    TaskTimeout timeout = task.getTimeout();
    if (timeout == null) {
      return Optional.empty();
    }
    Timeout timeoutDef = timeout.getTaskTimeoutDefinition();
    if (timeoutDef == null && timeout.getTaskTimeoutReference() != null) {
      timeoutDef =
          Objects.requireNonNull(
              Objects.requireNonNull(
                      workflow.getUse().getTimeouts(),
                      "Timeout reference "
                          + timeout.getTaskTimeoutReference()
                          + " specified, but use timeout was not defined")
                  .getAdditionalProperties()
                  .get(timeout.getTaskTimeoutReference()),
              "Timeout reference " + timeout.getTaskTimeoutReference() + "cannot be found");
    }
    return Optional.of(WorkflowUtils.fromTimeoutAfter(appl, timeoutDef.getAfter()));
  }

  public static final String secretProp(WorkflowContext context, String secretName, String prop) {
    return (String) secret(context, secretName).get(prop);
  }

  public static final Map<String, Object> secret(WorkflowContext context, String secretName) {
    return context.definition().application().secretManager().secret(secretName);
  }

  public static final String checkSecret(
      Workflow workflow, SecretBasedAuthenticationPolicy secretPolicy) {
    String secretName = secretPolicy.getUse();
    return workflow.getUse().getSecrets().stream()
        .filter(s -> s.equals(secretName))
        .findAny()
        .orElseThrow(() -> new IllegalStateException("Secret " + secretName + " does not exist"));
  }

  public static URI concatURI(URI base, String pathToAppend) {
    return !isValid(pathToAppend) ? base : concatURI(base, URI.create(pathToAppend));
  }

  public static URI concatURI(URI base, URI child) {
    if (child.isAbsolute()) {
      return child;
    }

    String basePath = base.getPath();
    if (!isValid(basePath)) {
      basePath = "/";
    } else if (!basePath.endsWith("/")) {
      basePath = basePath + "/";
    }

    String relPath = child.getPath();
    if (relPath == null) {
      relPath = "";
    } else {
      while (relPath.startsWith("/")) {
        relPath = relPath.substring(1);
      }
    }
    try {
      return new URI(
          base.getScheme(),
          base.getAuthority(),
          basePath + relPath,
          child.getQuery(),
          child.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(
          "Failed to build combined URI from base=" + base + " and child=" + child, e);
    }
  }

  public static WorkflowValueResolver<URI> getURISupplier(
      WorkflowApplication application, UriTemplate template) {
    if (template.getLiteralUri() != null) {
      return (w, t, n) -> template.getLiteralUri();
    } else if (template.getLiteralUriTemplate() != null) {
      return (w, t, n) ->
          application
              .templateResolver()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Need an uri template resolver to resolve uri template"))
              .resolveTemplates(template.getLiteralUriTemplate(), w, t, n);
    }
    throw new IllegalArgumentException("Invalid uritemplate definition " + template);
  }

  public static <T extends ServicePriority> Optional<T> loadFirst(Class<T> serviceClass) {
    return ServiceLoader.load(serviceClass).stream()
        .map(ServiceLoader.Provider::get)
        .sorted()
        .findFirst();
  }

  public static void validationError(
      Optional<WorkflowError.Builder> errorBuilder, TaskContextData taskContext) {
    validationError(errorBuilder, taskContext.position().jsonPointer().toString());
  }

  public static void validationError(
      Optional<WorkflowError.Builder> errorBuilder, WorkflowDefinitionId definition) {
    validationError(errorBuilder, definition.toString());
  }

  public static void validationError(
      Optional<WorkflowError.Builder> errorBuilder, WorkflowContextData workflowContext) {
    validationError(errorBuilder, workflowContext.instanceData().id());
  }

  private static void validationError(
      Optional<WorkflowError.Builder> errorBuilder, String instance) {
    if (errorBuilder.isPresent()) {
      throw new WorkflowException(errorBuilder.orElseThrow().instance(instance).build());
    }
  }
}
