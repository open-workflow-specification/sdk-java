# Common API

## Introduction

This module provides core interfaces and utilities that can be reused by other workflow engine implementations. It contains foundational abstractions for service prioritization and ordering.

## Dependency

```xml
<dependency>
    <groupId>cloud.openworkflow.sdk</groupId>
    <artifactId>openworkflow-common-api</artifactId>
    <version>8.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Example

```java
import cloud.openworkflow.sdk.common.ServicePriority;

public class MyService implements ServicePriority<MyService> {
    
    @Override
    public int priority() {
        return 500; // Higher priority than default (1000)
    }
    
    public static void main(String[] args) {
        MyService service1 = new MyService();
        MyService service2 = new MyService() {
            @Override
            public int priority() {
                return 1500;
            }
        };
        
        // Compare services by priority
        int comparison = service1.compareTo(service2);
        System.out.println("Service1 has higher priority: " + (comparison < 0));
    }
}
```
