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

import com.github.os72.protocjar.Protoc;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

public class ProtocCheckCondition implements ExecutionCondition {

  private static boolean protocAvailable = false;

  static {
    try {
      protocAvailable = Protoc.runProtoc(new String[] {"--version"}) == 0;
    } catch (Exception e) {
      // ignore
    }
    if (!protocAvailable) {
      protocAvailable = isBinaryAvailable("protoc", "--version");
    }
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return AnnotationUtils.findAnnotation(context.getElement(), DisabledIfProtocUnavailable.class)
        .map(
            annotation ->
                protocAvailable
                    ? ConditionEvaluationResult.enabled(
                        "Protoc is available for the current platform.")
                    : ConditionEvaluationResult.disabled(
                        "Test disabled: Protoc not currently available not found."))
        .orElse(ConditionEvaluationResult.enabled("No @DisabledIfProtocUnavailable found."));
  }
}
