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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.Error;
import org.openworkflow.sdk.api.types.ErrorDetails;
import org.openworkflow.sdk.api.types.ErrorTitle;
import org.openworkflow.sdk.api.types.ErrorType;
import org.openworkflow.sdk.api.types.RetryPolicy;
import org.openworkflow.sdk.api.types.UriTemplate;
import org.openworkflow.sdk.api.types.Use;
import org.openworkflow.sdk.api.types.UseAuthentications;
import org.openworkflow.sdk.api.types.UseErrors;
import org.openworkflow.sdk.api.types.UseRetries;
import org.openworkflow.sdk.fluent.spec.BaseTryTaskBuilder.RetryPolicyBuilder;

public class UseBuilder {

  private final Use use;

  UseBuilder() {
    this.use = new Use();
    this.use.setAuthentications(new UseAuthentications());
  }

  public UseBuilder secrets(final String... secrets) {
    if (secrets != null) {
      this.use.setSecrets(List.of(secrets));
    }
    return this;
  }

  public UseBuilder authentications(Consumer<UseAuthenticationsBuilder> authenticationsConsumer) {
    final UseAuthenticationsBuilder builder = new UseAuthenticationsBuilder();
    authenticationsConsumer.accept(builder);
    final UseAuthentications useAuthentications = builder.build();
    this.use
        .getAuthentications()
        .getAdditionalProperties()
        .putAll(useAuthentications.getAdditionalProperties());
    return this;
  }

  public UseBuilder errors(Consumer<UseErrorsBuilder> errorsConsumer) {
    final UseErrorsBuilder builder = new UseErrorsBuilder();
    errorsConsumer.accept(builder);
    final UseErrors built = builder.build();
    if (this.use.getErrors() == null) {
      this.use.setErrors(new UseErrors());
    }
    this.use.getErrors().getAdditionalProperties().putAll(built.getAdditionalProperties());
    return this;
  }

  public UseBuilder retries(Consumer<UseRetriesBuilder> retriesConsumer) {
    final UseRetriesBuilder retriesBuilder = new UseRetriesBuilder();
    retriesConsumer.accept(retriesBuilder);
    final UseRetries built = retriesBuilder.build();
    if (this.use.getRetries() == null) {
      this.use.setRetries(new UseRetries());
    }
    this.use.getRetries().getAdditionalProperties().putAll(built.getAdditionalProperties());
    return this;
  }

  public Use build() {
    return use;
  }

  public static final class UseErrorsBuilder {
    private final UseErrors useErrors;

    UseErrorsBuilder() {
      this.useErrors = new UseErrors();
    }

    public UseErrorsBuilder error(String name, Consumer<UseErrorBuilder> errorConsumer) {
      final UseErrorBuilder errorBuilder = new UseErrorBuilder();
      errorConsumer.accept(errorBuilder);
      this.useErrors.withAdditionalProperty(name, errorBuilder.build());
      return this;
    }

    public UseErrors build() {
      return useErrors;
    }
  }

  public static final class UseErrorBuilder {
    private final Error error;

    UseErrorBuilder() {
      this.error = new Error();
    }

    public UseErrorBuilder type(String expression) {
      ErrorType errorType = new ErrorType();
      try {
        errorType.withLiteralErrorType(new UriTemplate().withLiteralUri(new URI(expression)));
      } catch (URISyntaxException ex) {
        errorType.withExpressionErrorType(expression);
      }
      this.error.setType(errorType);
      return this;
    }

    public UseErrorBuilder type(URI errorType) {
      this.error.setType(
          new ErrorType().withLiteralErrorType(new UriTemplate().withLiteralUri(errorType)));
      return this;
    }

    public UseErrorBuilder status(int status) {
      this.error.setStatus(status);
      return this;
    }

    public UseErrorBuilder title(String expression) {
      this.error.setTitle(new ErrorTitle().withExpressionErrorTitle(expression));
      return this;
    }

    public UseErrorBuilder detail(String expression) {
      this.error.setDetail(new ErrorDetails().withExpressionErrorDetails(expression));
      return this;
    }

    public Error build() {
      return error;
    }
  }

  public static final class UseRetriesBuilder {
    private final Map<String, RetryPolicy> retries = new LinkedHashMap<>();

    UseRetriesBuilder() {}

    public UseRetriesBuilder retry(String name, Consumer<RetryPolicyBuilder> configurer) {
      final RetryPolicyBuilder policyBuilder = new RetryPolicyBuilder();
      configurer.accept(policyBuilder);
      this.retries.put(name, policyBuilder.build());
      return this;
    }

    public UseRetries build() {
      final UseRetries useRetries = new UseRetries();
      useRetries.getAdditionalProperties().putAll(this.retries);
      return useRetries;
    }
  }
}
