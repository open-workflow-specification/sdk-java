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
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.openworkflow.sdk.api.types.CorrelateProperty;
import org.openworkflow.sdk.api.types.EventFilter;
import org.openworkflow.sdk.api.types.EventFilterCorrelate;
import org.openworkflow.sdk.api.types.EventProperties;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTypeConsumer
    implements EventConsumer<TypeEventRegistration, TypeEventRegistrationBuilder> {

  private static final Logger logger = LoggerFactory.getLogger(AbstractTypeConsumer.class);

  private static final CloudEventPredicate ALWAYS_TRUE = (ce, wf, t) -> true;

  protected abstract void registerToAll(Consumer<CloudEvent> consumer);

  protected abstract void unregisterFromAll();

  protected abstract void register(String topicName, Consumer<CloudEvent> consumer);

  protected abstract void unregister(String topicName);

  private Map<String, CloudEventConsumer> registrations = new ConcurrentHashMap<>();

  @Override
  public TypeEventRegistrationBuilder listen(
      EventFilter register, WorkflowApplication application) {
    EventProperties properties = register.getWith();
    String type = properties.getType();
    CloudEventPredicate cePredicate =
        application.cloudEventPredicateFactory().build(application, properties);
    Collection<CloudEventPredicate> correlationPredicates =
        buildCorrelationPredicates(register.getCorrelate(), application);
    return new TypeEventRegistrationBuilder(type, cePredicate, correlationPredicates);
  }

  private Collection<CloudEventPredicate> buildCorrelationPredicates(
      EventFilterCorrelate correlate, WorkflowApplication application) {
    if (correlate == null) {
      return List.of();
    }
    Map<String, CorrelateProperty> additionalProperties = correlate.getAdditionalProperties();
    if (additionalProperties == null || additionalProperties.isEmpty()) {
      return List.of();
    }
    Collection<CloudEventPredicate> predicates = new ArrayList<>();
    for (Map.Entry<String, CorrelateProperty> entry : additionalProperties.entrySet()) {
      predicates.add(CorrelationPredicate.from(entry.getKey(), entry.getValue(), application));
    }
    return predicates;
  }

  @Override
  public Collection<TypeEventRegistrationBuilder> listenToAll(WorkflowApplication application) {
    return List.of(new TypeEventRegistrationBuilder(null, ALWAYS_TRUE, List.of()));
  }

  private static class CloudEventConsumer extends AbstractCollection<TypeEventRegistration>
      implements Consumer<CloudEvent> {
    private Collection<TypeEventRegistration> registrations = new CopyOnWriteArrayList<>();

    @Override
    public void accept(CloudEvent ce) {
      logger.debug("Received cloud event {}", ce);
      WorkflowModel eventModel = null;
      for (TypeEventRegistration registration : registrations) {
        if (!registration.predicate().test(ce, registration.workflow(), registration.task())) {
          continue;
        }
        Collection<CloudEventPredicate> correlationPredicates =
            registration.correlationPredicates();
        if (!correlationPredicates.isEmpty()) {
          if (eventModel == null && registration.hasModelAwareCorrelation()) {
            eventModel = registration.workflow().definition().application().modelFactory().from(ce);
          }
          if (!testCorrelation(ce, registration, eventModel)) {
            continue;
          }
        }
        registration.consumer().accept(ce);
      }
    }

    private boolean testCorrelation(
        CloudEvent ce, TypeEventRegistration registration, WorkflowModel eventModel) {
      Collection<CloudEventPredicate> predicates = registration.correlationPredicates();
      for (CloudEventPredicate pred : predicates) {
        if (pred instanceof ModelAwareCloudEventPredicate ma) {
          if (!ma.test(eventModel, registration.workflow(), registration.task())) {
            return false;
          }
        } else {
          if (!pred.test(ce, registration.workflow(), registration.task())) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public boolean add(TypeEventRegistration registration) {
      return registrations.add(registration);
    }

    @Override
    public boolean remove(Object registration) {
      return registrations.remove(registration);
    }

    @Override
    public Iterator<TypeEventRegistration> iterator() {
      return registrations.iterator();
    }

    @Override
    public int size() {
      return registrations.size();
    }
  }

  @Override
  public TypeEventRegistration register(
      TypeEventRegistrationBuilder builder,
      Consumer<CloudEvent> ce,
      WorkflowContext workflow,
      TaskContext task) {
    if (builder.type() == null) {
      registerToAll(ce);
      return new TypeEventRegistration(null, ce, ALWAYS_TRUE, workflow, task);
    } else {
      TypeEventRegistration registration =
          new TypeEventRegistration(
              builder.type(),
              ce,
              builder.cePredicate(),
              builder.correlationPredicates(),
              workflow,
              task);
      registrations
          .computeIfAbsent(
              registration.type(),
              k -> {
                CloudEventConsumer consumer = new CloudEventConsumer();
                register(k, consumer);
                return consumer;
              })
          .add(registration);
      return registration;
    }
  }

  @Override
  public void unregister(TypeEventRegistration registration) {
    if (registration.type() == null) {
      unregisterFromAll();
    } else {
      registrations.computeIfPresent(
          registration.type(),
          (k, v) -> {
            v.remove(registration);
            if (v.isEmpty()) {
              unregister(registration.type());
              return null;
            } else {
              return v;
            }
          });
    }
    cleanupCorrelationState(registration);
  }

  private void cleanupCorrelationState(TypeEventRegistration registration) {
    for (CloudEventPredicate pred : registration.correlationPredicates()) {
      if (pred instanceof CorrelationPredicate cp) {
        cp.stateKey(registration.task())
            .ifPresent(key -> registration.workflow().instance().removeMetadata(key));
      }
    }
  }
}
