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
package org.openworkflow.sdk.fluent.spec;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.ErrorDetails;
import org.openworkflow.sdk.api.types.ErrorTitle;
import org.openworkflow.sdk.api.types.ErrorType;
import org.openworkflow.sdk.api.types.RaiseTask;
import org.openworkflow.sdk.api.types.RaiseTaskConfiguration;
import org.openworkflow.sdk.api.types.RaiseTaskError;
import org.openworkflow.sdk.api.types.UriTemplate;

public abstract class BaseRaiseTaskBuilder<T extends BaseRaiseTaskBuilder<T>>
    extends TaskBaseBuilder<T> {

  protected final RaiseTask raiseTask;

  protected BaseRaiseTaskBuilder() {
    this.raiseTask = new RaiseTask();
    setTask(raiseTask);
  }

  public T error(Consumer<RaiseTaskErrorBuilder> consumer) {
    final RaiseTaskErrorBuilder raiseTaskErrorBuilder = new RaiseTaskErrorBuilder();
    consumer.accept(raiseTaskErrorBuilder);
    this.raiseTask.setRaise(new RaiseTaskConfiguration().withError(raiseTaskErrorBuilder.build()));
    return self();
  }

  // TODO: validation, one or the other

  public T error(String errorReference) {
    this.raiseTask.setRaise(
        new RaiseTaskConfiguration()
            .withError(new RaiseTaskError().withRaiseErrorReference(errorReference)));
    return self();
  }

  public RaiseTask build() {
    return this.raiseTask;
  }

  public static final class RaiseTaskErrorBuilder {
    private final org.openworkflow.sdk.api.types.Error error;

    private RaiseTaskErrorBuilder() {
      this.error = new org.openworkflow.sdk.api.types.Error();
    }

    public RaiseTaskErrorBuilder type(String expression) {
      ErrorType errorType = new ErrorType();
      try {
        errorType.withLiteralErrorType(new UriTemplate().withLiteralUri(new URI(expression)));
      } catch (URISyntaxException ex) {
        errorType.withExpressionErrorType(expression);
      }
      this.error.setType(errorType);
      return this;
    }

    public RaiseTaskErrorBuilder type(URI errorType) {
      this.error.setType(
          new ErrorType().withLiteralErrorType(new UriTemplate().withLiteralUri(errorType)));
      return this;
    }

    public RaiseTaskErrorBuilder status(int status) {
      this.error.setStatus(status);
      return this;
    }

    // TODO: change signature to Expression interface since literal and expressions are String

    public RaiseTaskErrorBuilder title(String expression) {
      this.error.setTitle(new ErrorTitle().withExpressionErrorTitle(expression));
      return this;
    }

    public RaiseTaskErrorBuilder detail(String expression) {
      this.error.setDetail(new ErrorDetails().withExpressionErrorDetails(expression));
      return this;
    }

    public RaiseTaskError build() {
      return new RaiseTaskError().withRaiseErrorDefinition(this.error);
    }
  }
}
