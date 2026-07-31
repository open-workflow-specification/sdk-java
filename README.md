[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/open-workflow-specification/sdk-java)

# Open Workflow Specification - Java SDK

Provides the Java API for the [Serverless Workflow Specification](https://github.com/open-workflow-specification/specification)

With the SDK you can:

* Read workflow JSON and YAML definitions
* Write workflow definitions in JSON and YAML formats. 
* Test your workflow definitions using the reference implementation. 


## Status

|                                     Latest Releases                                    |                        Conformance to spec version                       |
| :------------------------------------------------------------------------------------: | :----------------------------------------------------------------------: |
| [7.x](https://github.com/open-workflow-specification/sdk-java/releases/tag/7.5.0.Final)| [v1.0.0](https://github.com/open-workflow-specification/specification/tree/1.0.x) |
| [5.x](https://github.com/open-workflow-specification/sdk-java/releases/tag/5.1.0.Final)|  [v0.8](https://github.com/open-workflow-specification/specification/tree/0.8.x)  |
| [4.x](https://github.com/open-workflow-specification/sdk-java/releases/tag/4.1.0.Final)|  [v0.8](https://github.com/open-workflow-specification/specification/tree/0.8.x)  |
| [3.0.0.Final](https://github.com/open-workflow-specification/sdk-java/releases/tag/3.0.0.Final)|  [v0.7](https://github.com/open-workflow-specification/specification/tree/0.7.x)  |
| [2.0.0.Final](https://github.com/open-workflow-specification/sdk-java/releases/tag/2.0.0.Final)|  [v0.6](https://github.com/open-workflow-specification/specification/tree/0.6.x)  |
| [1.0.3.Final](https://github.com/open-workflow-specification/sdk-java/releases/tag/1.0.3.Final)|  [v0.5](https://github.com/open-workflow-specification/specification/tree/0.5.x)  |

> **Note:** `6.0.0.Final` (planned for spec **v0.9**) is intentionally **skipped** to leave room for anyone who wants to work on it.

## JDK Version

| SDK Version | JDK Version |
| :---: | :---: |
| 7.0.0 and after | 17 |
| 5.0.0 and after | 11 |
| 4.0.x and before | 8 | 

## Getting Started


### Building SNAPSHOT locally

To build project and run tests locally:

```
git clone https://github.com/open-workflow-specification/sdk-java.git
mvn clean install
```

The project uses [Google's code styleguide](https://google.github.io/styleguide/javaguide.html).
Your changes should be automatically formatted during the build.

### Maven projects:

Add the following dependencies to your pom.xml `dependencies` section:

```xml
<dependency>
    <groupId>io.serverlessworkflow</groupId>
    <artifactId>serverlessworkflow-api</artifactId>
    <version>RELEASE_VERSION</version>
</dependency>
```

### Gradle projects:

 Add the following dependencies to your build.gradle `dependencies` section:

```text
implementation("io.serverlessworkflow:serverlessworkflow-api:RELEASE_VERSION")
```

## How to Use 

There are, roughly speaking, two kind of users of this SDK:
 * Those ones interested on implementing their own runtime using Java.
 * Those ones interested on using the provided runtime reference implementation. 

### Implementing your own runtime 

For those ones interested on implementing their own runtime, this SDK provides an easy way to load an in memory representation of a given workflow definition.
This in-memory representation consists of a hierarchy of POJOS directly generated from the Open Workflow specification [schema](api/src/main/resources/schema/workflow.yaml), which ensures the internal representation is aligned with the specification schema. The root of the hierarchy is `io.serverlessworkflow.api.types.Workflow` class

### Reading workflow definition from JSON/YAML source

You can read a Workflow definition from JSON/YAML source:

Let's say you have a simple YAML based workflow definition in a file name `simple.yaml` located in your working dir:

```yaml
document:
  dsl: 1.0.0-alpha1
  namespace: default
  name: implicit-sequence
do:
  setRed:
    set:
      colors: '${ .colors + [ "red" ] }'
  setGreen:
    set:
      colors: '${ .colors + [ "green" ] }'
  setBlue:
    set:
      colors: '${ .colors + [ "blue" ] }'

```

To parse it and get a Workflow instance you can do:

``` java

try (InputStream in = new FileInputStream("simple.yaml")) {
   Workflow workflow = WorkflowReader.readWorkflow (in, WorkflowFormat.YAML);
   // Once you have the Workflow instance you can use its API to inspect it
}
```
By default, Workflows are not validated against the schema (performance being the priority). If you want to enable validation, you can do that by using: 

``` java
try (InputStream in = new FileInputStream("simple.yaml")) {
   Workflow workflow = WorkflowReader.validation().readWorkflow (in, WorkflowFormat.YAML);
   // Once you have the Workflow instance you can use its API to inspect it
}
```

For additional reading helper methods, including the one to read a workflow definition from classpath, check [WorkflowReader](api/src/main/java/io/serverlessworkflow/api/WorkflowReader.java) class. 

### Writing workflow definition to a JSON/YAML target

Given a Workflow instance, you can store it using JSON or YAML format. 
For example, to store a workflow using json format in a file called `simple.json`, you write

``` java
try (OutputStream out = new FileOutputStream("simple.json")) {
    WorkflowWriter.writeWorkflow(out, workflow, WorkflowFormat.JSON);
}

```
For additional writing helper methods, check [WorkflowWriter](api/src/main/java/io/serverlessworkflow/api/WorkflowWriter.java) class. 

### Reference implementation

The reference implementation provides a ready-to-use runtime that supports the Open Workflow Specification. It includes a workflow execution engine, validation utilities, and illustrative examples to help you quickly test and deploy your workflows. For details on usage, configuration, and supported features, see [readme](impl/README.md). 

