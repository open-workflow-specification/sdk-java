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
package org.openworkflow.sdk.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowPosition;
import org.openworkflow.sdk.types.Errors;

class HttpErrorTranslationTest {

  @Test
  void fromError_shouldPreserveTimeoutClassificationAndTaskPosition() {
    TaskContext task = mock(TaskContext.class);
    WorkflowPosition position = mock(WorkflowPosition.class);
    when(task.position()).thenReturn(position);
    when(position.jsonPointer()).thenReturn("do/0/callApi");
    for (Exception cause :
        List.of(
            new TimeoutException("deadline"),
            new SocketTimeoutException("read"),
            new HttpTimeoutException("request"))) {
      var error =
          WorkflowHttpHelper.fromError(new CompletionException(cause), task).getWorkflowError();
      assertThat(error.status()).isEqualTo(Errors.TIMEOUT.status());
      assertThat(error.instance()).isEqualTo("do/0/callApi");
    }
  }
}
