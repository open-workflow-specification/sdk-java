/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.impl;

import static io.serverlessworkflow.impl.WorkflowUtils.loadFirst;
import static io.serverlessworkflow.impl.WorkflowUtils.safeClose;

import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.additional.NamedWorkflowAdditionalObject;
import io.serverlessworkflow.impl.additional.WorkflowAdditionalObject;
import io.serverlessworkflow.impl.auth.AuthProviderFactory;
import io.serverlessworkflow.impl.auth.DefaultAuthProviderFactory;
import io.serverlessworkflow.impl.config.ConfigManager;
import io.serverlessworkflow.impl.config.ConfigSecretManager;
import io.serverlessworkflow.impl.config.SecretManager;
import io.serverlessworkflow.impl.config.SystemPropertyConfigManager;
import io.serverlessworkflow.impl.events.CloudEventPredicateFactory;
import io.serverlessworkflow.impl.events.DefaultCloudEventPredicateFactory;
import io.serverlessworkflow.impl.events.EmitSourceResolver;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.events.InMemoryEvents;
import io.serverlessworkflow.impl.executors.CallableTaskProxyBuilder;
import io.serverlessworkflow.impl.executors.DefaultTaskExecutorFactory;
import io.serverlessworkflow.impl.executors.TaskExecutorFactory;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.RuntimeDescriptor;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListenerAdapter;
import io.serverlessworkflow.impl.lifecycle.ce.DefaultLifeCycleCloudEventFactory;
import io.serverlessworkflow.impl.lifecycle.ce.WorkflowLifeCycleCloudEventFactory;
import io.serverlessworkflow.impl.resources.DefaultResourceLoaderFactory;
import io.serverlessworkflow.impl.resources.ExternalResourceHandler;
import io.serverlessworkflow.impl.resources.ResourceLoaderFactory;
import io.serverlessworkflow.impl.resources.URITemplateResolver;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;
import io.serverlessworkflow.impl.scheduler.CronResolver;
import io.serverlessworkflow.impl.scheduler.CronResolverFactory;
import io.serverlessworkflow.impl.scheduler.DefaultWorkflowScheduler;
import io.serverlessworkflow.impl.scheduler.InMemoryAllStrategyCorrelationInfo;
import io.serverlessworkflow.impl.scheduler.WorkflowScheduler;
import io.serverlessworkflow.impl.schema.SchemaValidator;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WorkflowApplication implements AutoCloseable {

  private final String id;
  private final TaskExecutorFactory taskFactory;
  private final ExpressionFactory exprFactory;
  private final ResourceLoaderFactory resourceLoaderFactory;
  private final SchemaValidatorFactory schemaValidatorFactory;
  private final WorkflowInstanceIdFactory idFactory;
  private final List<Collection<WorkflowExecutionCompletableListener>> listenersByPriority;
  private final Map<WorkflowDefinitionId, WorkflowDefinition> definitions;
  private final WorkflowPositionFactory positionFactory;
  private final ExecutorServiceFactory executorFactory;
  private final RuntimeDescriptorFactory runtimeDescriptorFactory;
  private final EventConsumer<?, ?> eventConsumer;
  private final Collection<EventPublisher> eventPublishers;
  private final boolean lifeCycleCEPublishingEnabled;
  private final boolean lifeCycleStatusChangeEnabled;
  private final WorkflowModelFactory modelFactory;
  private final WorkflowModelFactory contextFactory;
  private final WorkflowScheduler scheduler;
  private final Map<String, WorkflowAdditionalObject<?>> additionalObjects;
  private final Map<String, Supplier<?>> additionalObjectSuppliers;
  private final AuthProviderFactory authProviderFactory;
  private final ConfigManager configManager;
  private final SecretManager secretManager;
  private final SchedulerListener schedulerListener;
  private final Optional<URITemplateResolver> templateResolver;
  private final Optional<FunctionReader> functionReader;
  private final URI defaultCatalogURI;
  private final WorkflowValueResolver<URI> defaultEventSource;
  private final Collection<CallableTaskProxyBuilder> callableProxyBuilders;
  private final CloudEventPredicateFactory cloudEventPredicateFactory;
  private final AllStrategyCorrelationInfoFactory allStrategyCorrelationInfoFactory;
  private final WorkflowLifeCycleCloudEventFactory lifeCycleCloudEventFactory;
  private final ScheduledExecutorService schedulerExecutorService;

  private WorkflowApplication(Builder builder) {
    this.taskFactory = builder.taskFactory;
    this.exprFactory = new CompositeExpressionFactory(builder.exprFactories);
    this.resourceLoaderFactory = builder.resourceLoaderFactory;
    this.schemaValidatorFactory = builder.schemaValidatorFactory;
    this.positionFactory = builder.positionFactory;
    this.idFactory = builder.idFactory;
    this.runtimeDescriptorFactory = builder.descriptorFactory;
    this.executorFactory = builder.executorFactory;
    this.listenersByPriority = groupByPriority(new LinkedHashSet<>(builder.listeners));
    this.definitions = new ConcurrentHashMap<>();
    this.eventConsumer = builder.eventConsumer;
    this.eventPublishers = builder.eventPublishers;
    this.lifeCycleCEPublishingEnabled = builder.lifeCycleCEPublishingEnabled;
    this.lifeCycleStatusChangeEnabled = builder.lifeCycleStatusChangeEnabled;
    this.modelFactory = builder.modelFactory;
    this.contextFactory = builder.contextFactory;
    this.scheduler = builder.scheduler;
    this.schedulerListener = builder.schedulerListener;
    this.additionalObjects = builder.additionalObjects;
    this.additionalObjectSuppliers = builder.additionalObjectSuppliers;
    this.authProviderFactory = builder.authProviderFactory;
    this.configManager = builder.configManager;
    this.secretManager = builder.secretManager;
    this.templateResolver = builder.templateResolver;
    this.functionReader = builder.functionReader;
    this.defaultCatalogURI = builder.defaultCatalogURI;
    this.defaultEventSource = builder.defaultEventSource;
    this.id = builder.id;
    this.callableProxyBuilders = builder.callableProxyBuilders;
    this.cloudEventPredicateFactory = builder.cloudEventPredicateFactory;
    this.allStrategyCorrelationInfoFactory = builder.allStrategyCorrelationInfoFactory;
    this.lifeCycleCloudEventFactory = builder.lifeCycleCloudEventFactory;
    this.schedulerExecutorService = builder.schedulerExecutorService;
  }

  public TaskExecutorFactory taskFactory() {
    return taskFactory;
  }

  public static Builder builder() {
    return new Builder();
  }

  public ExpressionFactory expressionFactory() {
    return exprFactory;
  }

  public SchemaValidatorFactory validatorFactory() {
    return schemaValidatorFactory;
  }

  public ResourceLoaderFactory resourceLoaderFactory() {
    return resourceLoaderFactory;
  }

  public Collection<WorkflowExecutionCompletableListener> listeners() {
    return this.listenersByPriority.stream().flatMap(x -> x.stream()).toList();
  }

  public Collection<EventPublisher> eventPublishers() {
    return eventPublishers;
  }

  public WorkflowInstanceIdFactory idFactory() {
    return idFactory;
  }

  List<Collection<WorkflowExecutionCompletableListener>> listenersByPriority() {
    return listenersByPriority;
  }

  private static List<Collection<WorkflowExecutionCompletableListener>> groupByPriority(
      Collection<WorkflowExecutionCompletableListener> listeners) {
    if (listeners.isEmpty()) {
      return List.of();
    }
    List<Collection<WorkflowExecutionCompletableListener>> result = new ArrayList<>();
    Iterator<WorkflowExecutionCompletableListener> iter = listeners.iterator();
    List<WorkflowExecutionCompletableListener> currentList = new ArrayList<>();
    WorkflowExecutionCompletableListener currentListener = iter.next();
    int currentPriority = currentListener.priority();
    currentList.add(currentListener);
    while (iter.hasNext()) {
      currentListener = iter.next();
      if (currentListener.priority() != currentPriority) {
        result.add(currentList);
        currentList = new ArrayList<>();
        currentPriority = currentListener.priority();
      }
      currentList.add(currentListener);
    }
    if (!currentList.isEmpty()) {
      result.add(currentList);
    }
    return result;
  }

  public static class Builder {

    private static final class EmptySchemaValidatorHolder {
      private static final SchemaValidatorFactory instance =
          new SchemaValidatorFactory() {
            private final SchemaValidator NoValidation =
                new SchemaValidator() {
                  @Override
                  public void validate(WorkflowModel node) {}
                };

            @Override
            public SchemaValidator getValidator(ExternalResourceHandler resource) {
              return NoValidation;
            }

            @Override
            public SchemaValidator getValidator(SchemaInline inline) {
              return NoValidation;
            }
          };
    }

    private String id;
    private TaskExecutorFactory taskFactory;
    private Collection<ExpressionFactory> exprFactories = new HashSet<>();
    private List<WorkflowExecutionCompletableListener> listeners =
        ServiceLoader.load(WorkflowExecutionListener.class).stream()
            .map(v -> new WorkflowExecutionListenerAdapter(v.get()))
            .collect(Collectors.toList());
    private List<CallableTaskProxyBuilder> callableProxyBuilders =
        loadFromServiceLoader(CallableTaskProxyBuilder.class);
    private ResourceLoaderFactory resourceLoaderFactory = DefaultResourceLoaderFactory.get();
    private SchemaValidatorFactory schemaValidatorFactory;
    private WorkflowPositionFactory positionFactory = () -> new QueueWorkflowPosition();
    private WorkflowInstanceIdFactory idFactory;
    private WorkflowScheduler scheduler;
    private ExecutorServiceFactory executorFactory = new DefaultExecutorServiceFactory();
    private EventConsumer<?, ?> eventConsumer;
    private Collection<EventPublisher> eventPublishers = new ArrayList<>();
    private RuntimeDescriptorFactory descriptorFactory =
        () -> new RuntimeDescriptor("reference impl", "1.0.0_alpha", Collections.emptyMap());
    private boolean lifeCycleCEPublishingEnabled = true;
    private boolean lifeCycleStatusChangeEnabled = true;
    private WorkflowModelFactory modelFactory;
    private WorkflowModelFactory contextFactory;
    private Map<String, WorkflowAdditionalObject<?>> additionalObjects = new HashMap<>();
    private Map<String, Supplier<?>> additionalObjectSuppliers = new HashMap<>();
    private AuthProviderFactory authProviderFactory;
    private SecretManager secretManager;
    private ConfigManager configManager;
    private SchedulerListener schedulerListener;
    private Optional<URITemplateResolver> templateResolver;
    private Optional<FunctionReader> functionReader;
    private URI defaultCatalogURI;
    private WorkflowValueResolver<URI> defaultEventSource;
    private CloudEventPredicateFactory cloudEventPredicateFactory;
    private AllStrategyCorrelationInfoFactory allStrategyCorrelationInfoFactory;
    private WorkflowLifeCycleCloudEventFactory lifeCycleCloudEventFactory;
    private CronResolverFactory cronResolverFactory;
    private ScheduledExecutorService schedulerExecutorService;

    private Builder() {
      ServiceLoader.load(NamedWorkflowAdditionalObject.class)
          .forEach(a -> additionalObjects.put(a.name(), a));
    }

    public Builder withId(String id) {
      this.id = id;
      return this;
    }

    public Builder withListener(WorkflowExecutionListener listener) {
      listeners.add(new WorkflowExecutionListenerAdapter(listener));
      return this;
    }

    public Builder withListener(WorkflowExecutionCompletableListener listener) {
      listeners.add(listener);
      return this;
    }

    public Builder withCallableProxy(CallableTaskProxyBuilder builder) {
      callableProxyBuilders.add(builder);
      return this;
    }

    public Builder withTaskExecutorFactory(TaskExecutorFactory factory) {
      this.taskFactory = factory;
      return this;
    }

    public Builder withExpressionFactory(ExpressionFactory factory) {
      this.exprFactories.add(factory);
      return this;
    }

    public Builder withScheduler(WorkflowScheduler scheduler) {
      this.scheduler = scheduler;
      return this;
    }

    public Builder withResourceLoaderFactory(ResourceLoaderFactory resourceLoader) {
      this.resourceLoaderFactory = resourceLoader;
      return this;
    }

    public Builder disableLifeCycleCEPublishing() {
      this.lifeCycleCEPublishingEnabled = false;
      return this;
    }

    public Builder disableStatusChangePublishing() {
      this.lifeCycleStatusChangeEnabled = false;
      return this;
    }

    public Builder withExecutorFactory(ExecutorServiceFactory executorFactory) {
      this.executorFactory = executorFactory;
      return this;
    }

    public Builder withPositionFactory(WorkflowPositionFactory positionFactory) {
      this.positionFactory = positionFactory;
      return this;
    }

    public Builder withSchemaValidatorFactory(SchemaValidatorFactory factory) {
      this.schemaValidatorFactory = factory;
      return this;
    }

    public Builder withIdFactory(WorkflowInstanceIdFactory factory) {
      this.idFactory = factory;
      return this;
    }

    public Builder withDescriptorFactory(RuntimeDescriptorFactory factory) {
      this.descriptorFactory = factory;
      return this;
    }

    public Builder withEventConsumer(EventConsumer<?, ?> eventConsumer) {
      this.eventConsumer = eventConsumer;
      return this;
    }

    public Builder withEventPublisher(EventPublisher eventPublisher) {
      this.eventPublishers.add(eventPublisher);
      return this;
    }

    public Builder withSecretManager(SecretManager secretManager) {
      this.secretManager = secretManager;
      return this;
    }

    public Builder withConfigManager(ConfigManager configManager) {
      this.configManager = configManager;
      return this;
    }

    public <T> Builder withAdditionalObject(
        String name, WorkflowAdditionalObject<T> additionalObject) {
      additionalObjects.put(name, additionalObject);
      return this;
    }

    public <T> Builder withAdditionalObject(String name, Supplier<T> additionalObject) {
      additionalObjectSuppliers.put(name, additionalObject);
      return this;
    }

    public Builder withAuthProviderFactory(AuthProviderFactory authProviderFactory) {
      this.authProviderFactory = authProviderFactory;
      return this;
    }

    public Builder withModelFactory(WorkflowModelFactory modelFactory) {
      this.modelFactory = modelFactory;
      return this;
    }

    public Builder withContextFactory(WorkflowModelFactory contextFactory) {
      this.contextFactory = contextFactory;
      return this;
    }

    public Builder withDefaultCatalogURI(String defaultCatalogURI) {
      return withDefaultCatalogURI(URI.create(defaultCatalogURI));
    }

    public Builder withDefaultCatalogURI(URI defaultCatalogURI) {
      this.defaultCatalogURI = defaultCatalogURI;
      return this;
    }

    public Builder withDefaultEventSource(URI defaultEventSource) {
      return withDefaultEventSource((workflow, task, model) -> defaultEventSource);
    }

    public Builder withDefaultEventSource(WorkflowValueResolver<URI> defaultEventSource) {
      this.defaultEventSource = defaultEventSource;
      return this;
    }

    public Builder withCloudEventPredicateFactory(
        CloudEventPredicateFactory cloudEventPredicateFactory) {
      this.cloudEventPredicateFactory = cloudEventPredicateFactory;
      return this;
    }

    public Builder withAllStrategyCorrelationInfoFactory(
        AllStrategyCorrelationInfoFactory allStrategyCorrelationInfoFactory) {
      this.allStrategyCorrelationInfoFactory = allStrategyCorrelationInfoFactory;
      return this;
    }

    public Builder withLifeCycleCloudEventFactory(
        WorkflowLifeCycleCloudEventFactory lifeCycleCloudEventFactory) {
      this.lifeCycleCloudEventFactory = lifeCycleCloudEventFactory;
      return this;
    }

    public Builder withCronResolverFactory(CronResolverFactory cronResolverFactory) {
      this.cronResolverFactory = cronResolverFactory;
      return this;
    }

    public WorkflowApplication build() {

      if (modelFactory == null) {
        modelFactory =
            loadFirst(WorkflowModelFactory.class)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "WorkflowModelFactory instance has to be set in WorkflowApplication or present in the classpath"));
      }
      if (contextFactory == null) {
        contextFactory = modelFactory;
      }
      ServiceLoader.load(ExpressionFactory.class).forEach(exprFactories::add);
      if (schemaValidatorFactory == null) {
        schemaValidatorFactory =
            loadFirst(SchemaValidatorFactory.class)
                .orElseGet(() -> EmptySchemaValidatorHolder.instance);
      }
      if (taskFactory == null) {
        taskFactory =
            loadFirst(TaskExecutorFactory.class).orElseGet(() -> DefaultTaskExecutorFactory.get());
      }
      ServiceLoader.load(EventPublisher.class).forEach(e -> eventPublishers.add(e));
      if (eventConsumer == null) {
        eventConsumer =
            loadFirst(EventConsumer.class)
                .orElseGet(
                    () -> {
                      InMemoryEvents inMemory = new InMemoryEvents(executorFactory);
                      if (eventPublishers.isEmpty()) {
                        eventPublishers.add(inMemory);
                      }
                      return inMemory;
                    });
      }
      if (idFactory == null) {
        idFactory = new MonotonicUlidWorkflowInstanceIdFactory();
      }

      if (scheduler == null) {
        if (cronResolverFactory == null) {
          cronResolverFactory =
              loadFirst(CronResolverFactory.class)
                  .orElseGet(
                      () ->
                          new CronResolverFactory() {
                            private CronResolver emptyResolver =
                                new CronResolver() {
                                  @Override
                                  public Optional<Duration> nextExecution() {
                                    throw new UnsupportedOperationException(
                                        "Missing CronResolverFactory, please add serverlessworkflow-impl-cron dependency to your classpath");
                                  }
                                };

                            @Override
                            public CronResolver parseCron(String cron) {
                              return emptyResolver;
                            }
                          });
        }
        schedulerExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduler = new DefaultWorkflowScheduler(schedulerExecutorService, cronResolverFactory);
      }
      schedulerListener = new SchedulerListener(scheduler);
      listeners.add(schedulerListener);

      if (configManager == null) {
        configManager =
            loadFirst(ConfigManager.class).orElseGet(() -> new SystemPropertyConfigManager());
      }
      if (secretManager == null) {
        secretManager =
            loadFirst(SecretManager.class).orElseGet(() -> new ConfigSecretManager(configManager));
      }
      templateResolver = loadFirst(URITemplateResolver.class);
      functionReader = loadFirst(FunctionReader.class);
      if (cloudEventPredicateFactory == null) {
        cloudEventPredicateFactory =
            loadFirst(CloudEventPredicateFactory.class)
                .orElseGet(() -> new DefaultCloudEventPredicateFactory());
      }
      if (lifeCycleCloudEventFactory == null) {
        lifeCycleCloudEventFactory = new DefaultLifeCycleCloudEventFactory();
      }
      if (allStrategyCorrelationInfoFactory == null) {
        allStrategyCorrelationInfoFactory =
            definition -> InMemoryAllStrategyCorrelationInfo.instance();
      }

      if (defaultCatalogURI == null) {
        defaultCatalogURI = URI.create("https://github.com/serverlessworkflow/catalog");
      }
      if (defaultEventSource == null) {
        defaultEventSource = new EmitSourceResolver();
      }
      Collections.sort(listeners);
      Collections.sort(callableProxyBuilders);
      if (id == null) {
        id = idFactory.get();
      }
      if (authProviderFactory == null) {
        authProviderFactory = DefaultAuthProviderFactory.factory();
      }

      return new WorkflowApplication(this);
    }

    private <T> List<T> loadFromServiceLoader(Class<T> clazz) {
      return ServiceLoader.load(clazz).stream().map(Provider::get).collect(Collectors.toList());
    }
  }

  public Map<WorkflowDefinitionId, WorkflowDefinition> workflowDefinitions() {
    return Collections.unmodifiableMap(definitions);
  }

  public WorkflowDefinition workflowDefinition(Workflow workflow) {
    return definitions.computeIfAbsent(
        WorkflowDefinitionId.of(workflow), k -> WorkflowDefinition.of(this, workflow));
  }

  @Override
  public void close() {
    safeClose(executorFactory);
    for (EventPublisher eventPublisher : eventPublishers) {
      safeClose(eventPublisher);
    }
    safeClose(eventConsumer);

    for (WorkflowDefinition definition : definitions.values()) {
      safeClose(definition);
    }
    definitions.clear();

    if (!listenersByPriority.isEmpty()) {
      for (Collection<WorkflowExecutionCompletableListener> listeners : listenersByPriority) {
        for (WorkflowExecutionCompletableListener listener : listeners) {
          safeClose(listener);
        }
        listeners.clear();
      }
      listenersByPriority.clear();
    }
    if (this.schedulerExecutorService != null) {
      schedulerExecutorService.shutdownNow();
    }
  }

  public WorkflowPositionFactory positionFactory() {
    return positionFactory;
  }

  public WorkflowModelFactory modelFactory() {
    return modelFactory;
  }

  public WorkflowModelFactory contextFactory() {
    return contextFactory;
  }

  public RuntimeDescriptorFactory runtimeDescriptorFactory() {
    return runtimeDescriptorFactory;
  }

  @SuppressWarnings("rawtypes")
  public EventConsumer eventConsumer() {
    return eventConsumer;
  }

  public ExecutorService executorService() {
    return executorFactory.get();
  }

  public boolean isLifeCycleCEPublishingEnabled() {
    return lifeCycleCEPublishingEnabled;
  }

  public boolean isStatusChangePublishingEnabled() {
    return lifeCycleStatusChangeEnabled;
  }

  public WorkflowScheduler scheduler() {
    return scheduler;
  }

  public ConfigManager configManager() {
    return configManager;
  }

  public SecretManager secretManager() {
    return secretManager;
  }

  SchedulerListener schedulerListener() {
    return schedulerListener;
  }

  public Optional<URITemplateResolver> templateResolver() {
    return templateResolver;
  }

  public Optional<FunctionReader> functionReader() {
    return functionReader;
  }

  public CloudEventPredicateFactory cloudEventPredicateFactory() {
    return cloudEventPredicateFactory;
  }

  public URI defaultCatalogURI() {
    return defaultCatalogURI;
  }

  public WorkflowValueResolver<URI> defaultEventSource() {
    return defaultEventSource;
  }

  public String id() {
    return id;
  }

  public <T> Optional<T> additionalObject(String name) {
    return Optional.ofNullable(additionalObjectSuppliers.get(name)).map(v -> (T) v.get());
  }

  public <T> Optional<T> additionalObject(
      String name, WorkflowContext workflowContext, TaskContext taskContext) {
    return Optional.ofNullable(additionalObjects.get(name))
        .map(v -> (T) v.apply(workflowContext, taskContext));
  }

  public AuthProviderFactory authProviderFactory() {
    return authProviderFactory;
  }

  public Collection<CallableTaskProxyBuilder> callableProxyBuilders() {
    return callableProxyBuilders;
  }

  public AllStrategyCorrelationInfoFactory allStrategyCorrelationInfoFactory() {
    return allStrategyCorrelationInfoFactory;
  }

  public WorkflowLifeCycleCloudEventFactory lifeCycleCloudEventFactory() {
    return lifeCycleCloudEventFactory;
  }
}
