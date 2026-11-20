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
package cloud.openworkflow.sdk.impl.executors.script.python;

import cloud.openworkflow.sdk.impl.TaskContext;
import cloud.openworkflow.sdk.impl.WorkflowContext;
import cloud.openworkflow.sdk.impl.WorkflowModel;
import cloud.openworkflow.sdk.impl.scripts.ScriptContext;
import cloud.openworkflow.sdk.impl.scripts.ScriptLanguageId;
import cloud.openworkflow.sdk.impl.scripts.ScriptRunner;
import cloud.openworkflow.sdk.impl.scripts.ScriptUtils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PythonScriptTaskRunner implements ScriptRunner {

  private static final Logger logger = LoggerFactory.getLogger(PythonScriptTaskRunner.class);

  @Override
  public ScriptLanguageId identifier() {
    return ScriptLanguageId.PYTHON;
  }

  @Override
  public WorkflowModel runScript(
      ScriptContext scriptContext,
      WorkflowContext workflowContext,
      TaskContext taskContext,
      WorkflowModel model) {
    ProcessBuilder builder = new ProcessBuilder("python", "-c", scriptContext.code());
    ScriptUtils.addEnviromment(builder, scriptContext.envs());
    scriptContext.args().forEach((k, v) -> addArg(builder.command(), k, v));
    logger.debug("Invoking python with command line {}", builder.command());
    return ScriptUtils.buildResultFromProcess(
        builder,
        scriptContext.returnType(),
        scriptContext.stdin(),
        workflowContext,
        taskContext,
        model);
  }

  private void addArg(List<String> list, String name, Object value) {
    if (value instanceof Boolean bool) {
      if (bool) {
        list.add("--" + name);
      }
    } else if (value != null) {
      list.add("--" + name);
      list.add(value.toString());
    }
  }
}
