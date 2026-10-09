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
package org.openworkflow.sdk.impl.executors.script.js;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.scripts.ScriptContext;
import org.openworkflow.sdk.impl.scripts.ScriptLanguageId;
import org.openworkflow.sdk.impl.scripts.ScriptRunner;
import org.openworkflow.sdk.impl.scripts.ScriptUtils;
import org.openworkflow.sdk.impl.scripts.StreamSuppliers;

/**
 * JavaScript implementation of the {@link ScriptRunner} interface that executes JavaScript scripts
 * using GraalVM Polyglot API.
 */
public class JavaScriptScriptTaskRunner implements ScriptRunner {

  @Override
  public ScriptLanguageId identifier() {
    return ScriptLanguageId.JS;
  }

  /*
   * Configures the process.env object in the JavaScript context with the provided environment
   * variables.
   *
   * @param context the GraalVM context
   * @param envs the environment variables to set
   */
  private void configureProcessEnv(Context context, Map<String, Object> envs) {
    String js = ScriptLanguageId.JS.getLang();
    Value bindings = context.getBindings(js);
    Value process = context.eval(js, "({ env: {} })");

    for (var entry : envs.entrySet()) {
      process.getMember("env").putMember(entry.getKey(), entry.getValue());
    }
    bindings.putMember("process", process);
  }

  @Override
  public WorkflowModel runScript(
      ScriptContext scriptContext,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try (Context ctx =
        Context.newBuilder()
            .err(stderr)
            .out(stdout)
            .useSystemExit(true)
            .allowCreateProcess(false)
            .option("engine.WarnInterpreterOnly", "false")
            .build()) {
      scriptContext.args().forEach(ctx.getBindings(identifier().getLang())::putMember);
      configureProcessEnv(ctx, scriptContext.envs());
      ctx.eval(Source.create(identifier().getLang(), scriptContext.code()));
      return ScriptUtils.modelFromOutput(
          0,
          StreamSuppliers.from(stdout, stderr),
          scriptContext.returnType(),
          workflowContext.definition().application().modelFactory(),
          model);
    }
  }
}
