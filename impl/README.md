[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/open-workflow-specification/sdk-java)

# Open Workflow Specification — Java SDK (Reference Implementation)

A lightweight, non-blocking, reactive **runtime** for the [Open Workflow](https://openworkflow.io/) specification. Use it to load, validate, and execute workflows written in YAML/JSON—or build them programmatically with our Fluent DSL.

---

## Contents

* [Features](#features)
* [Modules](#modules)
* [Installation](#installation)
* [Quick Start](#quick-start)
* [Detailed Walkthrough](#detailed-walkthrough)
* [Fluent Java DSL](#fluent-java-dsl)
* [Mermaid Diagrams](#mermaid-diagrams)
* [Lifecycle Events](#lifecycle-events)
* [Errors & Auth](#errors--auth)
* [Development](#development)
* [License](#license)

---

## Features

This reference implementation can run workflows consisting of:

**Tasks**

* `Switch`
* `Set`
* `Do`
* `Raise`
* `Listen`
* `Emit`
* `Fork`
* `For` / `ForEach`
* `Try` (with `Catch`/`Retry`)
* `Wait`
* `Call`
  * **HTTP**
    * Basic auth
    * Bearer auth
    * OAuth2 / OIDC auth
    * Digest auth (via Fluent DSL)
  * **OpenAPI**
  * **Function**
  * **gRPC**
* `Run`
  * **Container**
  * **Script**
    * `Python`
    * `Javascript`
  * **Workflow** 
  * **Shell**

**Schema Validation**

* Input / Output validation

**Expressions**

* Input / Output / Export
* Special keywords: `runtime`, `workflow`, `task`, …

**Error Definitions**

* Standard & custom error types

**Lifecycle Events**

* `Pending`, `Started`, `Suspended`, `Faulted`, `Resumed`, `Cancelled`, `Completed`

---

## Modules

This SDK is modular by design—pull in only what you need:

* **openworkflow-impl-core**
  Workflow engine & core interfaces. Depends on generated types and CloudEvents SDK.

* **openworkflow-impl-jackson**
  👉 **Most users add this one.**
  
  Adds Jackson integration, JQ expressions, JSON Schema validation, and CloudEvents (de)serialization.
  
  Internally, this module is an aggregation of smaller modules to allow part replacement if needed:
    * **openworkflow-impl-jq** JQ expression implementation
    * **openworkflow-impl-model** Jackson model implementation
    * **openworkflow-impl-validation** Schema validation implementation
    * **openworkflow-impl-lifecycle-events** Enable publication of lifecycle events as Json cloud events. 
    * **openworkflow-impl-function** Function support. 
    * **openworkflow-impl-template-resolver** JaxRS URI template implementation. 
    * **openworkflow-impl-json** Json common utilities shared by all modules in this list
    * **openworkflow-impl-cron** Cron parsing
    * **openworkflow-impl-http** HTTP `Call` task handler, based on Jax-RS client
    * **openworkflow-impl-jackson-jwt** Optimized entity converter for Jackson model and OAuth2/OIDC helpers for HTTP calls.
    * **openworkflow-impl-openapi** OpenAPI support using Jackson
    * **openworkflow-impl-openapi-base** Base OpenAPI support intended to be extended for a specific JSON library
    * **openworkflow-impl-grpc** gRPC support
    * **openworkflow-impl-script-python** Python support
    * **openworkflow-impl-script-js** Javascript support
    * **openworkflow-impl-container** Docker container support  

There are also companion modules/docs for:

* **Fluent DSL** (programmatic builder)
* **Mermaid** (diagramming workflows)

Links below.

---

## Installation

### Maven

```xml
<!-- Core + Jackson (YAML, JQ, JSON Schema, CloudEvents) -->
<dependency>
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-impl-jackson</artifactId>
</dependency>

<!-- Add if you use HTTP Call tasks -->
<dependency>
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-impl-http</artifactId>
</dependency>

<!-- Add if your HTTP calls require OAuth2/OIDC -->
<dependency>
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-impl-jackson-jwt</artifactId>
</dependency>
```

### Gradle (Kotlin/Groovy)

```gradle
implementation("org.openworkflow.sdk:openworkflow-impl-jackson")
implementation("org.openworkflow.sdk:openworkflow-impl-http")        // if using HTTP
implementation("org.openworkflow.sdk:openworkflow-impl-jackson-jwt") // if using OAuth2/OIDC
```

> Requires **Java 17+**.

---

## Quick Start

We’ll run a simple workflow that performs an HTTP GET. See the full YAML in
[examples/simpleGet/src/main/resources/get.yaml]().

### Blocking execution

```java
try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
  var output =
      appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath("get.yaml"))
          .instance(Map.of("petId", 10))
          .start()
          .join();
  logger.info("Workflow output is {}", output);
}
```

### Non-blocking execution

```java
try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
  appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath("get.yaml"))
      .instance(Map.of("petId", 10))
      .start()
      .thenAccept(output -> logger.info("Workflow output is {}", output));
}
```

Example output:

```text
Workflow output is {"id":10,"category":{"id":10,"name":"string"},"name":"doggie",...}
```

Full examples:

* Blocking: [examples/simpleGet/src/main/java/org/openworkflow/sdk/impl/BlockingExample.java]()
* Non-blocking: [examples/simpleGet/src/main/java/org/openworkflow/sdk/impl/NotBlockingExample.java]()

---

## Event publish/subscribe example

We’ll coordinate two workflows—one **listens** for high temperatures and the other **emits** temperature events:

* `listen.yaml` waits for an event with `temperature > 38` (non-blocking)
* `emit.yaml` emits events using a workflow parameter

Create an application (customize thread pools, etc.). `WorkflowApplication` is `AutoCloseable`, so use try-with-resources:

```java
try (WorkflowApplication appl = WorkflowApplication.builder().build()) {
  WorkflowDefinition listen = appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath("listen.yaml"));
  WorkflowDefinition emit   = appl.workflowDefinition(WorkflowReader.readWorkflowFromClasspath("emit.yaml"));

  // Start the listener (non-blocking)
  listen.instance(Map.of())
        .start()
        .thenAccept(out -> logger.info("Waiting instance completed with {}", out));

  // Emit a low temperature (ignored)
  emit.instance(Map.of("temperature", 35)).start().join();

  // Emit a high temperature (completes the waiting workflow)
  emit.instance(Map.of("temperature", 39)).start().join();
}
```

You’ll see:

```
Waiting instance completed with [{"temperature":39}]
```

Source: `examples/events/src/main/java/events/EventExample.java`

---

## Workflow execution control

As shown in previous examples, to start a new workflow instance, first a [WorkflowInstance](https://github.com/open-workflow-specification/sdk-java/blob/main/impl/core/src/main/java/org/openworkflow/sdk/impl/WorkflowInstance.java) is created from a [WorkflowDefinition](https://github.com/open-workflow-specification/sdk-java/blob/main/impl/core/src/main/java/org/openworkflow/sdk/impl/WorkflowDefinition.java#L74), specifying the desired input, and then start method is invoked over it. Start method returns a CompletableFuture, which might be used to obtain the output, either synchronously or asynchronously.

Once started, and before it completes, a workflow instance execution can be suspended or cancelled. Once cancelled, a workflow instance is done, while a suspended one might be resumed. 

## Persistence 

Workflow progress might be recorded into DB. See [details](persistence/README.md)

## Fluent Java DSL

Prefer building workflows programmatically with type-safe builders and recipes?
👉 **Docs:** [https://github.com/open-workflow-specification/sdk-java/blob/main/fluent/README.md](https://github.com/open-workflow-specification/sdk-java/blob/main/fluent/README.md)

Highlights:

* Chainable builders for `Call HTTP`, `Listen/Emit`, `Try/Catch/Retry`, `Switch`, `Fork`, etc.
* Reusable “recipes” (e.g., `http().GET().acceptJSON().endpoint("${ ... }")`)
* Static helpers for auth (`basic(...)`, `bearer(...)`, `oidc(...)`, …) and standard error types.

---

## Mermaid Diagrams

Generate Mermaid diagrams for your workflows right from the SDK.
👉 **Docs:** [https://github.com/open-workflow-specification/sdk-java/blob/main/mermaid/README.md](https://github.com/open-workflow-specification/sdk-java/blob/main/mermaid/README.md)

Great for docs, PRs, and visual reviews.

---

## Lifecycle Events

Every workflow publishes CloudEvents you can subscribe to (in-memory or your own broker):

* `org.openworkflow.workflow.*` → `pending`, `started`, `suspended`, `faulted`, `resumed`, `cancelled`, `completed`
* `org.openworkflow.task.*` → `started`, `suspended`, `resumed`, `cancelled`, `completed`

See `impl` tests/examples for consuming and asserting on these events.

---

## Errors & Auth

**Errors**

* Raise standard errors with versioned URIs (e.g., `runtime`, `communication`, `timeout`, …) or custom ones.
* Fluent helpers (e.g., `serverError()`, `timeoutError()`) set both **type URI** and default **HTTP status**; override as needed.

**Authentication (HTTP Call)**

* Basic / Bearer / Digest
* OAuth2 / OpenID Connect (client credentials, etc.)
* Fluent helpers: `basic("user","pass")`, `bearer("token")`, `oidc(authority, grant, clientId, clientSecret)`, …

---

## Development

**Prereqs**

* Java 17+
* Maven (or Gradle)

**Build & Verify**

```bash
mvn -B clean verify
```

**Formatting & Lint (CI parity)**

```bash
mvn -B -DskipTests spotless:check checkstyle:check
```

**Run examples**

* See `examples/` directory for runnable samples.

---

## License

Apache License 2.0. See `LICENSE` for details.

---

*Questions or ideas? PRs and issues welcome!*
