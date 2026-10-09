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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.api.types.RunTaskConfiguration.ProcessReturnType;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelFactory;
import org.openworkflow.sdk.impl.executors.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptUtils {

  private static final Logger logger = LoggerFactory.getLogger(ScriptUtils.class);

  public static WorkflowModel modelFromOutput(
      int code,
      StreamSuppliers streamSuppliers,
      RunTaskConfiguration.ProcessReturnType returnType,
      WorkflowModelFactory modelFactory,
      WorkflowModel model) {
    if (code != 0) {
      logger.warn("Process call failed with code {}", code);
      if (logger.isDebugEnabled()) {
        logger.debug(streamSuppliers.errorStream().get());
      }
    }
    return switch (returnType) {
      case ALL ->
          modelFactory.fromAny(
              new ProcessResult(
                  code, streamSuppliers.outputStream().get(), streamSuppliers.errorStream().get()));
      case NONE -> model;
      case CODE -> modelFactory.from(code);
      case STDOUT -> modelFactory.from(streamSuppliers.outputStream().get());
      case STDERR -> modelFactory.from(streamSuppliers.errorStream().get());
    };
  }

  public static Process uncheckedStart(ProcessBuilder builder) {
    try {
      return builder.start();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void addEnviromment(ProcessBuilder builder, Map<String, Object> env) {
    for (Map.Entry<String, Object> entry : env.entrySet()) {
      builder.environment().put(entry.getKey(), (String) entry.getValue());
    }
  }

  public static WorkflowModel buildResultFromProcess(
      WorkflowDefinition definition, Process process, ProcessReturnType type, WorkflowModel model) {
    try {
      int code = process.waitFor();
      StreamSuppliers suppliers = StreamSuppliers.from(process);
      if (definition
          .application()
          .configManager()
          .config("org.openworkflow.sdk.impl.scripts.dumpOutput", Boolean.class)
          .orElse(true)) {
        System.out.println(suppliers.outputStream().get());
      }
      return modelFromOutput(code, suppliers, type, definition.application().modelFactory(), model);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return model;
    }
  }

  private ScriptUtils() {}
}
