[![Gitpod ready-to-code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/open-workflow-specification/sdk-java)

# Open Workflow Specification — Java SDK (Reference Implementation)- Persistence

Workflow persistence aim is to be able to restore workflow instances execution in the event of a JVM stop. To do that, progress of every running instance is persisted into the underlying DB by using life cycle events. Later on, when a new JVM is instantiated, the application is expected to manually start those instances that are not longer being executed by any other JVM, using the information previously stored. 

Currently, persistence structure has been layout for key-value store dbs, plus one concrete implementation using [H2 MVStore](mvstore/README.md). Next step will do the same for relational dbs (table layout plus concrete implementation using Postgresql). 

Map of key values has been given precedence because, when persisting the status of a running workflow instance, the number of writes are usually large, while read only operations are only performed when the JVM starts up. This give a performance edge for this kind of db over relational ones.

---

*Questions or ideas? PRs and issues welcome!*
