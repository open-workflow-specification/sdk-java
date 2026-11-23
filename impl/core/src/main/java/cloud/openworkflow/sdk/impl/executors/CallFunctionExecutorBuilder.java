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
package cloud.openworkflow.sdk.impl.executors;

import cloud.openworkflow.sdk.api.types.CallFunction;
import cloud.openworkflow.sdk.api.types.Catalog;
import cloud.openworkflow.sdk.api.types.FunctionArguments;
import cloud.openworkflow.sdk.api.types.Task;
import cloud.openworkflow.sdk.api.types.TaskBase;
import cloud.openworkflow.sdk.api.types.Use;
import cloud.openworkflow.sdk.impl.WorkflowDefinition;
import cloud.openworkflow.sdk.impl.WorkflowMutablePosition;
import cloud.openworkflow.sdk.impl.WorkflowUtils;
import cloud.openworkflow.sdk.impl.WorkflowValueResolver;
import cloud.openworkflow.sdk.impl.resources.ResourceLoader;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallFunctionExecutorBuilder implements CallableTaskBuilder<CallFunction> {

  private static final String FUNCTION_FILE = "/function.yaml";
  private static final String CATALOG_PREFIX = "main/functions/";
  private static final Pattern CAPTURE_FUNCTION_NAME =
      Pattern.compile("/main/functions/([^/]+)(?:/[^/]+)*/function\\.yaml");

  @Override
  public CallableTaskFactory init(
      CallFunction task, WorkflowDefinition definition, WorkflowMutablePosition position) {
    String functionPosition;
    String functionName = task.getCall();
    Use use = definition.workflow().getUse();
    int indexOf = functionName.indexOf('@');
    Task function = null;
    if (indexOf > 0) {
      // Catalog function
      URI catalogEndpoint;
      String catalogName = functionName.substring(indexOf + 1);
      ResourceLoader loader = definition.resourceLoader();
      if (catalogName.equalsIgnoreCase("default")) {
        catalogEndpoint = definition.application().defaultCatalogURI();
      } else {
        if (use == null || use.getCatalogs() == null) {
          throw new IllegalStateException(
              "Using catalog " + catalogName + ", but there is not catalog definition");
        }
        Catalog catalog = use.getCatalogs().getAdditionalProperties().get(catalogName);
        if (catalog == null) {
          throw new IllegalStateException(
              "Catalog "
                  + catalogName
                  + " is not included in Catalog dictionary: "
                  + use.getCatalogs().getAdditionalProperties());
        }
        catalogEndpoint = loader.uri(catalog.getEndpoint());
      }

      functionPosition = functionName.substring(0, indexOf);
      int sep = functionPosition.indexOf(":");
      if (sep < 0) {
        throw new IllegalArgumentException(
            "Invalid function name "
                + functionName
                + ". It has to be of the format <function name>:<function version>");
      }
      function =
          definition
              .resourceLoader()
              .loadURI(
                  WorkflowUtils.concatURI(
                      catalogEndpoint, pathFromFunctionName(functionPosition, sep)),
                  (n, b) -> from(definition, n, b));
      functionPosition = functionPosition.substring(0, sep);
    } else if (use != null
        && use.getFunctions() != null
        && use.getFunctions().getAdditionalProperties().get(functionName) instanceof Task found) {
      function = found;
      functionPosition = functionName;
    } else {
      // try to load function if function name is an uri
      URI functionUri = URI.create(functionName);
      function = definition.resourceLoader().loadURI(functionUri, (n, b) -> from(definition, n, b));
      String functionPath = functionUri.getPath();
      Matcher functionMatcher = CAPTURE_FUNCTION_NAME.matcher(functionPath);
      if (functionMatcher.find()) {
        functionPosition = functionMatcher.group(1);
      } else {
        int lastIndexOf = functionPath.lastIndexOf("/");
        functionPosition =
            lastIndexOf == -1 ? functionPath : functionPath.substring(lastIndexOf + 1);
      }
    }
    TaskExecutorBuilder<? extends TaskBase> executorBuilder =
        definition
            .application()
            .taskFactory()
            .getTaskExecutor(position.copy().addProperty(functionPosition), function, definition);
    FunctionArguments functionArgs = task.getWith();
    WorkflowValueResolver<?> args =
        functionArgs != null
            ? WorkflowUtils.buildMapResolver(
                definition.application(), functionArgs.getAdditionalProperties())
            : (w, t, m) -> Map.of();
    return () -> this.build(executorBuilder, args);
  }

  private String pathFromFunctionName(String functionName, int sep) {
    StringBuilder sb = new StringBuilder(functionName);
    sb.setCharAt(sep, '/');
    sb.insert(0, CATALOG_PREFIX);
    sb.append(FUNCTION_FILE);
    return sb.toString();
  }

  private Task from(WorkflowDefinition definition, String name, byte[] content) {
    return definition
        .application()
        .functionReader()
        .map(v -> v.apply(name, content))
        .orElseThrow(() ->
            new IllegalStateException(
                "No converter from external resource to function found. Make sure a dependency that includes an implementation of FunctionReader is included"));
  }

  @Override
  public boolean accept(Class<? extends TaskBase> clazz) {
    return clazz.equals(CallFunction.class);
  }

  private CallableTask build(
      TaskExecutorBuilder<? extends TaskBase> executorBuilder, WorkflowValueResolver<?> args) {
    TaskExecutor<? extends TaskBase> executor = executorBuilder.build();
    return (w, t, m) ->
        executor
            .apply(
                w,
                Optional.of(t),
                w.definition().application().modelFactory().fromAny(args.apply(w, t, m)))
            .thenApply(o -> o.output());
  }
}
