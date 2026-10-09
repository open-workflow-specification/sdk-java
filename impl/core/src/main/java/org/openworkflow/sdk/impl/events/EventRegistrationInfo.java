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
package org.openworkflow.sdk.impl.events;

import io.cloudevents.CloudEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowContext;

public record EventRegistrationInfo(
    CompletableFuture<?> completableFuture, Collection<EventRegistration> registrations) {

  public static final <T> EventRegistrationInfo build(
      EventRegistrationBuilderCollection builderInfo,
      BiConsumer<CloudEvent, CompletableFuture<T>> consumer,
      EventConsumer eventConsumer,
      WorkflowContext workflow,
      TaskContext task) {
    Collection<EventRegistration> registrations = new ArrayList<>();
    CompletableFuture<T>[] futures =
        builderInfo.registrations().stream()
            .map(reg -> toCompletable(reg, registrations, consumer, eventConsumer, workflow, task))
            .toArray(size -> new CompletableFuture[size]);
    return new EventRegistrationInfo(
        builderInfo.isAnd() ? CompletableFuture.allOf(futures) : CompletableFuture.anyOf(futures),
        registrations);
  }

  private static final <T> CompletableFuture<T> toCompletable(
      EventRegistrationBuilder regBuilder,
      Collection<EventRegistration> registrations,
      BiConsumer<CloudEvent, CompletableFuture<T>> ceConsumer,
      EventConsumer eventConsumer,
      WorkflowContext workflow,
      TaskContext task) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    registrations.add(
        eventConsumer.register(
            regBuilder, ce -> ceConsumer.accept((CloudEvent) ce, future), workflow, task));
    return future;
  }
}
