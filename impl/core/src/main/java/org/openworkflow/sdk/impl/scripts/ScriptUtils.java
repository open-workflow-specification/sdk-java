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
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.openworkflow.sdk.api.types.RunTaskConfiguration;
import org.openworkflow.sdk.api.types.RunTaskConfiguration.ProcessReturnType;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowError;
import org.openworkflow.sdk.impl.WorkflowException;
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
      case null -> model;
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

  public static void addEnviromment(ProcessBuilder builder, Map<String, Object> env) {
    for (Map.Entry<String, Object> entry : env.entrySet()) {
      builder.environment().put(entry.getKey(), (String) entry.getValue());
    }
  }

  private static boolean keepOut(ProcessReturnType type, boolean dumpOutput) {
    return dumpOutput || type == ProcessReturnType.ALL || type == ProcessReturnType.STDOUT;
  }

  private static boolean keepError(ProcessReturnType type) {
    return logger.isDebugEnabled()
        || type == ProcessReturnType.ALL
        || type == ProcessReturnType.STDERR;
  }

  public static WorkflowModel buildResultFromProcess(
      ProcessBuilder builder,
      ProcessReturnType type,
      String stdin,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    WorkflowApplication app = workflowContext.definition().application();
    boolean dumpOutput = app.configManager().scriptOutput2SystemOutput();
    boolean keepOut = keepOut(type, dumpOutput);
    boolean keepError = keepError(type);
    if (!keepOut) {
      builder.redirectOutput(Redirect.DISCARD);
    }
    if (!keepError) {
      builder.redirectError(Redirect.DISCARD);
    }
    Process process = uncheckedStart(builder, taskContext);
    try (StreamSuppliers suppliers =
        StreamSuppliers.from(process, app.executorService(), keepOut, keepError)) {
      addStdin(process, stdin, taskContext);
      int code = process.waitFor();
      if (dumpOutput) {
        System.out.println(suppliers.outputStream().get());
      }
      return modelFromOutput(code, suppliers, type, app.modelFactory(), model);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return model;
    }
  }

  private static Process uncheckedStart(ProcessBuilder builder, TaskContext taskContext) {
    try {
      return builder.start();
    } catch (IOException e) {
      throw new WorkflowException(WorkflowError.runtime(taskContext, e).build());
    }
  }

  private static void addStdin(Process process, String stdin, TaskContext taskContext) {
    if (stdin != null) {
      try (OutputStream out = process.getOutputStream()) {
        out.write(stdin.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        if (process.toHandle().isAlive()) {
          logger.warn(
              "Unexpected IO exception while writing to process input while the process is still alive, does the process close the standard input before ending?",
              e);
        }
      }
    }
  }

  private ScriptUtils() {}
}
