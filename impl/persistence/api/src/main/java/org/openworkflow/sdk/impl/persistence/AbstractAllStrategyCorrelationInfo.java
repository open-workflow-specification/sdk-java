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
package org.openworkflow.sdk.impl.persistence;

import io.cloudevents.CloudEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.events.EventRegistrationBuilder;
import org.openworkflow.sdk.impl.scheduler.AllStrategyCorrelationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAllStrategyCorrelationInfo implements AllStrategyCorrelationInfo {

  protected final WorkflowDefinition definition;
  private final PersistenceExecutor executor;
  private final Map<EventRegistrationBuilder, String> reg2IdMapping = new HashMap<>();
  private final Map<String, EventRegistrationBuilder> id2RegMapping = new HashMap<>();

  private static final Logger logger =
      LoggerFactory.getLogger(AbstractAllStrategyCorrelationInfo.class);

  private int counter;
  private CompletableFuture<Collection<Map<EventRegistrationBuilder, CloudEvent>>>
      completableFuture;
  private Consumer<Map<EventRegistrationBuilder, CloudEvent>> starter;

  public AbstractAllStrategyCorrelationInfo(
      WorkflowDefinition definition, PersistenceExecutor executor) {
    this.definition = definition;
    this.executor = executor;
    this.completableFuture = CompletableFuture.completedFuture(List.of());
  }

  @Override
  public void correlate(EventRegistrationBuilder reg, CloudEvent event) {
    queueCorrelation(operations -> eventAdded(operations, reg2IdMapping.get(reg), event), starter);
  }

  @Override
  public void init(
      Collection<EventRegistrationBuilder> regs,
      Consumer<Map<EventRegistrationBuilder, CloudEvent>> starter) {
    regs.forEach(
        reg -> {
          String id = generateIdFromReg(reg);
          id2RegMapping.put(id, reg);
          reg2IdMapping.put(reg, id);
        });
    this.starter = starter;
    queueCorrelation(operations -> startupCheck(operations), starter);
  }

  private void queueCorrelation(
      Function<CorrelationOperations, Collection<Map<EventRegistrationBuilder, CloudEvent>>>
          function,
      Consumer<Map<EventRegistrationBuilder, CloudEvent>> starter) {
    synchronized (this) {
      this.completableFuture =
          completableFuture
              .thenCompose(v -> executor.execute(() -> doTransaction(function), definition))
              .exceptionally(
                  ex -> {
                    logger.error(
                        "Exception processing correlation task for definition {}",
                        definition.id(),
                        ex);
                    return List.of();
                  });
      completableFuture.thenAccept(events -> events.forEach(starter));
    }
  }

  private Collection<Map<EventRegistrationBuilder, CloudEvent>> eventAdded(
      CorrelationOperations operations, String reg, CloudEvent event) {
    logger.debug(
        "Received event {} for definition {} and registration {}", event, definition.id(), reg);
    Map<String, Collection<CloudEvent>> events = initMap();
    operations.retrieveEvents(events);
    events.get(reg).add(event);
    Collection<Map<EventRegistrationBuilder, CloudEvent>> result = checkCorrelation(events);
    operations.storeEvent(reg, event);
    markProcessed(operations, result);
    return result;
  }

  private Map<String, Collection<CloudEvent>> initMap() {
    return id2RegMapping.keySet().stream()
        .collect(Collectors.toMap(k -> k, k -> new LinkedHashSet<>()));
  }

  private Collection<Map<EventRegistrationBuilder, CloudEvent>> startupCheck(
      CorrelationOperations operations) {
    logger.debug("Checking cloud events for definition {}", definition.id());
    operations.clearProcessed();
    Map<String, Collection<CloudEvent>> events = initMap();
    operations.retrieveEvents(events);
    Collection<Map<EventRegistrationBuilder, CloudEvent>> result = checkCorrelation(events);
    markProcessed(operations, result);
    return result;
  }

  private final Collection<Map<EventRegistrationBuilder, CloudEvent>> checkCorrelation(
      Map<String, Collection<CloudEvent>> events) {
    logger.debug("Stored CloudEvents for definition {} are {}", definition.id(), events);
    Collection<Map<EventRegistrationBuilder, CloudEvent>> result = new ArrayList<>();
    Map<String, Iterator<CloudEvent>> iteratingEvents =
        events.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().iterator()));
    boolean notDone = true;
    while (notDone) {
      Map<EventRegistrationBuilder, CloudEvent> row = new HashMap<>();
      for (Entry<String, Iterator<CloudEvent>> item : iteratingEvents.entrySet()) {
        Iterator<CloudEvent> iter = item.getValue();
        if (!iter.hasNext()) {
          notDone = false;
          break;
        }
        row.put(id2RegMapping.get(item.getKey()), iter.next());
        iter.remove();
      }
      if (notDone) {
        result.add(row);
      }
    }
    return result;
  }

  private void markProcessed(
      CorrelationOperations operations,
      Collection<Map<EventRegistrationBuilder, CloudEvent>> result) {
    if (!result.isEmpty()) {
      Map<String, Collection<String>> processed = new HashMap<>();
      for (Map<EventRegistrationBuilder, CloudEvent> item : result) {
        for (Entry<EventRegistrationBuilder, CloudEvent> entry : item.entrySet()) {
          processed
              .computeIfAbsent(reg2IdMapping.get(entry.getKey()), k -> new ArrayList<>())
              .add(entry.getValue().getId());
        }
      }
      operations.markAsProcessed(processed);
    }
  }

  public void addMetadata(
      WorkflowInstance instance, Map<EventRegistrationBuilder, CloudEvent> events) {
    logger.debug("Starting instance {} with events {}", instance.id(), events);
    instance.addMetadataIfAbsent(
        AbstractPersistenceInstanceWriter.CLOUD_EVENT_IDS,
        () ->
            events.entrySet().stream()
                .collect(
                    Collectors.toMap(
                        e -> reg2IdMapping.get(e.getKey()), v -> v.getValue().getId())));
  }

  protected abstract Collection<Map<EventRegistrationBuilder, CloudEvent>> doTransaction(
      Function<CorrelationOperations, Collection<Map<EventRegistrationBuilder, CloudEvent>>>
          function);

  protected String generateIdFromReg(EventRegistrationBuilder reg) {
    final String separator = ":";
    return definition.id().toString(separator) + separator + ++counter;
  }
}
