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
package org.openworkflow.sdk.impl.test;

import static org.openworkflow.sdk.api.WorkflowReader.readWorkflowFromClasspath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowDefinition;
import org.openworkflow.sdk.impl.WorkflowInstance;
import org.openworkflow.sdk.impl.lifecycle.TraceExecutionListener;
import org.openworkflow.sdk.impl.persistence.DefaultPersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.PersistenceApplicationBuilder;
import org.openworkflow.sdk.impl.persistence.PersistenceInstanceHandlers;
import org.openworkflow.sdk.impl.persistence.mvstore.MVStorePersistenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(DBGenerator.class);

  public static void main(String[] args) throws IOException {
    runInstance("db-samples/running_v1.db", false);
    runInstance("db-samples/suspended_v1.db", true);
  }

  private static void runInstance(String dbName, boolean suspend) throws IOException {
    LOG.info("---> Generating db samples at {}", dbName);
    Files.deleteIfExists(Path.of(dbName));
    try (PersistenceInstanceHandlers factories =
            DefaultPersistenceInstanceHandlers.from(new MVStorePersistenceStore(dbName));
        WorkflowApplication application =
            PersistenceApplicationBuilder.builder(
                    WorkflowApplication.builder().withListener(new TraceExecutionListener()),
                    factories.writer())
                .build()) {
      WorkflowDefinition definition =
          application.workflowDefinition(
              readWorkflowFromClasspath("workflows-samples/set-listen-to-any.yaml"));
      WorkflowInstance instance = definition.instance(Map.of());
      instance.start();
      if (suspend) {
        instance.suspend();
      }
    }
  }
}
