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
package org.openworkflow.sdk.impl.test.junit;

import static org.openworkflow.sdk.impl.test.junit.BinaryCheckUtil.isBinaryAvailable;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

public class PythonCheckCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return AnnotationUtils.findAnnotation(context.getElement(), DisabledIfPythonUnavailable.class)
        .map(
            annotation ->
                isBinaryAvailable("python", "--version")
                    ? ConditionEvaluationResult.enabled("Python is available.")
                    : ConditionEvaluationResult.disabled("Test disabled: Python not found."))
        .orElse(ConditionEvaluationResult.enabled("No @DisabledIfBinaryUnavailable found."));
  }
}
