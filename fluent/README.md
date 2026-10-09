# CNCF Open Workflow SDK Java — Fluent DSL

> A programmatic, type‑safe Java API for building and running Open Workflows (and agentic workflows) without writing YAML.

---

## 📦 Modules

| Module         | Purpose                                                                                       |
| -------------- | --------------------------------------------------------------------------------------------- |
| **spec**       | Core DSL implementing the [Open Workflow Specification](https://github.com/open-workflow-specification/specification). Purely compliant fluent API. |
| **func**       | Java‑centric “functional” DSL on top of **spec**: adds `Function<>`/`Predicate<>` support, `callFn` for Java method calls, and richer flow controls.    |
| **agentic**    | **Experimental** proof‑of‑concept DSL built on **func** for LangChain4j agentic workflows: `agent`, `sequence`, `loop`, `parallel`, etc.     |

---

## 🔧 Getting Started

Add the modules you need to your Maven `pom.xml` (replace versions as appropriate):

```xml
<!-- 
    Replace ${version.org.openworkflow.sdk} with the actual released version:
    https://github.com/open-workflow-specification/sdk-java/releases 
-->
<dependency>
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-fluent-spec</artifactId>
  <version>${version.org.openworkflow.sdk}</version>
</dependency>
<dependency>
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-fluent-func</artifactId>
  <version>${version.org.openworkflow.sdk}</version>
</dependency>
<dependency>  <!-- optional, experimental -->
  <groupId>org.openworkflow.sdk</groupId>
  <artifactId>openworkflow-fluent-agentic</artifactId>
  <version>${version.org.openworkflow.sdk}</version>
</dependency>
```

---

## 📖 Module Reference

### 1. Spec Fluent

Fully compliant with the CNCF Open Workflow spec.\
Use it when you want a 1:1 mapping of the YAML DSL in Java.

```java
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.spec.WorkflowBuilder;

Workflow wf = WorkflowBuilder
    .workflow("flowDo")
    .tasks(tasks ->
        tasks
          .set("initCtx", "$.foo = 'bar'")
          .forEach("item", f -> f
              .each("item")
              .at("$.list")
          )
    )
    .build();
```

> [!NOTE]
> We rename reserved keywords (`for`, `do`, `if`, `while`, `switch`, `try`) to safe identifiers (`forEach`, `tasks`, `when`, etc.).

#### Call Tasks — HTTP, OpenAPI, gRPC

The spec fluent DSL supports all three call task types defined by the specification:

| Call Type | DSL Factory | Shortcut |
|-----------|------------|----------|
| HTTP | `call(http().GET().endpoint(...))` | `DSL.http()` |
| OpenAPI | `call(openapi().document(...).operation(...))` | `DSL.openapi()` |
| gRPC | `call(grpc().proto(...).service(...).method(...))` | `DSL.grpc()` |

**HTTP call:**

```java
import static org.openworkflow.sdk.fluent.spec.dsl.DSL.*;

Workflow wf = WorkflowBuilder.workflow("myFlow", "myNs", "1.0")
    .tasks(call(
        http()
          .GET()
          .endpoint("https://petstore.swagger.io/v2/pet")
    ))
    .build();
```

**OpenAPI call:**

```java
Workflow wf = WorkflowBuilder.workflow("myFlow", "myNs", "1.0")
    .tasks(call(
        openapi()
          .document("https://api.example.com/openapi.yaml")
          .operation("getPetById")
          .parameter("id", "42")
    ))
    .build();
```

**gRPC call:**

```java
Workflow wf = WorkflowBuilder.workflow("myFlow", "myNs", "1.0")
    .tasks(call(
        grpc()
          .proto("workflows-samples/grpc/proto/person.proto")
          .service("Person", "localhost", 5011)
          .method("GetPerson")
    ))
    .build();
```

**gRPC call with arguments and authentication:**

```java
Workflow wf = WorkflowBuilder.workflow("myFlow", "myNs", "1.0")
    .tasks(call(
        grpc()
          .proto("proto/contributors.proto", basic("user", "pass"))
          .service("Contributors", "localhost", 5011)
          .method("GetContributor")
          .argument("github", "${ .github }")
    ))
    .build();
```

**gRPC call with a named task:**

```java
Workflow wf = WorkflowBuilder.workflow("myFlow", "myNs", "1.0")
    .tasks(call(
        "greet",
        grpc()
          .proto("proto/person.proto")
          .service("Person", "localhost", 5011)
          .method("GetPerson")
    ))
    .build();
```

##### `CallGrpcSpec` API Reference

| Method | Description |
|--------|-------------|
| `proto(String uri)` | Sets the proto definition endpoint (file path or URI) |
| `proto(String uri, AuthenticationConfigurer auth)` | Sets the proto endpoint with authentication; also applies the same authentication to the service configuration when no service-level authentication has been set yet |
| `service(String name, String host)` | Sets the gRPC service name and host |
| `service(String name, String host, int port)` | Sets the gRPC service name, host, and port |
| `method(String method)` | Sets the gRPC method name to call |
| `argument(String name, Object value)` | Adds a single method argument |
| `arguments(Map<String, Object> args)` | Adds multiple method arguments |
| `authentication(AuthenticationConfigurer auth)` | Sets or overrides the service-level authentication policy |

You can also use the low-level `CallGrpcConfigurer` lambda directly:

```java
Workflow wf = WorkflowBuilder.workflow("f", "ns", "1")
    .tasks(call((CallGrpcConfigurer) b -> b
        .proto("proto/service.proto")
        .service("Svc", "host")
        .method("DoThing")))
    .build();
```

---

### 2. Func Fluent

A Java‑first DSL that builds on **spec**, adding:

- `callFn`: invoke arbitrary Java `Function<>` handlers
- `Predicate<>` **guards** via `when(Predicate)`
- Built‑in `Function`/`Predicate` support instead of JQ expressions

```java
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.func.FuncWorkflowBuilder;

Workflow wf = FuncWorkflowBuilder
    .workflow("callJavaFlow")
    .tasks(tasks ->
        tasks.callFn("invokeHandler", call -> call
            // e.g. call.className("com.acme.Handler")
            //     .method("handle")
            //     .arg("key", "value")
            .function(ctx -> {
                // your code here
            })
        )
    )
    .build();
```

> [!WARNING]
> The **func** DSL is *not* spec‑compliant. It adds Java‑specific tasks and control‑flow extensions for in‑JVM execution.

---

### 3. Agentic Fluent *(Experimental)*

Built on **func** for LangChain4j agentic workflows. Adds:

- `agent(instance)`: invoke a LangChain4j agent
- `sequence(...)`: run agents in order
- `loop(cfg)`: retry or repeated agent calls
- `parallel(...)`: fork agent calls concurrently

```java
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.agentic.AgentWorkflowBuilder;

var scorer = AgentsUtils.newMovieExpert();
var editor = AgentsUtils.newMovieExpert();

Workflow wf = AgentWorkflowBuilder
    .workflow("retryFlow")
    .tasks(tasks -> tasks.loop(
        "reviewLoop",
        loop -> loop
          .maxIterations(5)
          .exitCondition(c -> c.readState("score", 0).doubleValue() > 0.75)
          .subAgents("reviewer", scorer, editor)
    ))
    .build();
```

---

## 🚀 Real‑World Example: Order Fulfillment

```java
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.fluent.agentic.AgentWorkflowBuilder;
import java.util.function.Predicate;

public class OrderFulfillment {

    static class InventoryAgent { /* … */ }
    static class NotificationAgent { /* … */ }
    static class ShippingAgent { /* … */ }

    public Workflow buildWorkflow() {

        Predicate<Object> inventoryOk = state ->
            Boolean.TRUE.equals(((java.util.Map<?,?>) state).get("inventoryAvailable"));

        return AgentWorkflowBuilder
            .workflow("OrderFulfillment")
            .tasks(tasks -> tasks

                // 1. initialize state
                .set("init", s -> s.expr("$.orderId = '.input.oriderId'"))

                // 2. check inventory
                .agent("checkInventory", new InventoryAgent())

                // 3. pull result into a flag
                .set("inventoryAvailable", s -> s.expr("$.checkInventory.available"))

                // 4. retry until in stock (max 3 attempts)
                .loop("retryIfOutOfStock", loop -> loop
                    .maxIterations(3)
                    .exitCondition(inventoryOk)
                    .subAgents("inventoryChecker", new InventoryAgent())
                )

                // 5. notify systems in parallel
                .parallel("notifyAll",
                    new NotificationAgent(),
                    new ShippingAgent()
                )

                // 6. mark order complete
                .set("complete", s -> s.expr("$.status = 'COMPLETED'"))
            )
            .build();
    }
}
```

---

## 🛠️ Next Steps & Roadmap

- **Error handling**: retries, back‑off, `onError` handlers
- **Timers & delays**: `wait`, per‑task `timeout`
- **Sub‑workflows** & composition: call one workflow from another
- **Event tasks**: `onEvent`, `sendEvent`
- **Human‑in‑the‑Loop**: approval/notification steps

Contributions welcome! Check out our [CONTRIBUTING.md](../CONTRIBUTING.md) and join the CNCF Slack channel for **Open Workflow**.

---

## 📜 License

Apache 2.0 © Open Workflow Authors
