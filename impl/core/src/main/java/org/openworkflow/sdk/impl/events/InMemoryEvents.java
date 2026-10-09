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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.openworkflow.sdk.impl.DefaultExecutorServiceFactory;
import org.openworkflow.sdk.impl.ExecutorServiceFactory;

/*
 * Straightforward implementation of in memory event broker.
 * User might invoke publish to simulate event reception.
 */
public class InMemoryEvents extends AbstractTypeConsumer implements EventPublisher {

  private final ExecutorServiceFactory serviceFactory;
  private final Map<String, Consumer<CloudEvent>> topicMap = new ConcurrentHashMap<>();
  private final AtomicReference<Consumer<CloudEvent>> allConsumerRef = new AtomicReference<>();

  public InMemoryEvents() {
    this(new DefaultExecutorServiceFactory());
  }

  public InMemoryEvents(ExecutorServiceFactory serviceFactory) {
    this.serviceFactory = serviceFactory;
  }

  @Override
  public void register(String topicName, Consumer<CloudEvent> consumer) {
    topicMap.put(topicName, consumer);
  }

  @Override
  protected void unregister(String topicName) {
    topicMap.remove(topicName);
  }

  @Override
  public CompletableFuture<Void> publish(CloudEvent ce) {
    return CompletableFuture.runAsync(
        () -> {
          Consumer<CloudEvent> allConsumer = allConsumerRef.get();
          if (allConsumer != null) {
            allConsumer.accept(ce);
          }
          Consumer<CloudEvent> consumer = topicMap.get(ce.getType());
          if (consumer != null) {
            consumer.accept(ce);
          }
        },
        serviceFactory.get());
  }

  @Override
  protected void registerToAll(Consumer<CloudEvent> consumer) {
    allConsumerRef.set(consumer);
  }

  @Override
  protected void unregisterFromAll() {
    allConsumerRef.set(null);
  }

  @Override
  public void close() throws Exception {
    topicMap.clear();
    serviceFactory.close();
  }
}
