# OpenAPI Jackson Reader

## Introduction

This module provides a Jackson-based implementation for reading OpenAPI specifications that other workflow engine implementations can reuse. It enables parsing of OpenAPI documents in JSON or YAML format using Jackson's serialization capabilities.

## Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-openapi-jackson</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Example

```java
import cloud.openworkflow.sdk.openapi.UnifiedOpenAPI;
import cloud.openworkflow.sdk.openapi.jackson.JacksonUnifiedOpenAPIReader;

// Read OpenAPI document from bytes
byte[] documentBytes = Files.readAllBytes(Path.of("api.yaml"));

// Create reader and parse document
JacksonUnifiedOpenAPIReader reader = new JacksonUnifiedOpenAPIReader();
UnifiedOpenAPI document = reader.read("api.yaml", documentBytes);

// Use the parsed document
System.out.println("OpenAPI version: " + document.openapi());
System.out.println("Servers: " + document.servers());
```

The reader automatically detects the format (JSON or YAML) based on the file extension and uses the appropriate Jackson mapper for parsing.
