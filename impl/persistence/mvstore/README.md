[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/open-workflow-specification/sdk-java)

# Open Workflow Specification — Java SDK (Reference Implementation)- Persistence - MVStore


This document explains how to enable persistence using MVStore as underlying persistent mechanism. It is assumed that the reader is familiar with [standard workflow execution mechanism](../../README.md). 

To enable MVStore persistence, users should at least do the following things:

- Initialize a  MVStorePersistenceStore instance, passing the path of the file containing the persisted information
- Pass this MVStorePersitenceStore as argument of DefaultPersistenceInstanceHandlers.from. This will create PersistenceInstanceWriter and PersistenceInstanceReader. 
- Use the PersistenceInstanceWriter created in the previous step to decorate the existing WorkflowApplication builder. 

The code will look like this

----
    try (PersistenceInstanceHandlers handlers =
            DefaultPersistenceInstanceHandlers.from(new MVStorePersistenceStore("test.db"))
                .build();
        WorkflowApplication application =
            PersistenceApplicationBuilder.builder(
                    WorkflowApplication.builder(),
                    handlers.writer())
                .build(); )
     {
        // run workflow normally, the progress will be persisted
     }
----


If user wants to resume execution of all previously existing instances (typically after a server crash), he can use the reader created in the previous block to retrieve all stored instances. 

Once retrieved, calling `start` method will resume the execution after the latest completed task before the running JVM was stopped. 

----
      handlers.reader().scanAll(definition).forEach(WorkflowInstance::start);
----

---

*Questions or ideas? PRs and issues welcome!*
