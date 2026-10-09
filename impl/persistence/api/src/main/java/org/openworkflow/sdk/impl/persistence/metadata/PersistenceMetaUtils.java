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
package org.openworkflow.sdk.impl.persistence.metadata;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openworkflow.sdk.impl.WorkflowInstanceData;

public class PersistenceMetaUtils {

  private PersistenceMetaUtils() {}

  public static Stream<Entry<String, Object>> durableMetadataAsStream(
      WorkflowInstanceData instance) {
    return instance.metadata().entrySet().stream()
        .filter(
            e ->
                e.getValue() != null
                    && !e.getValue().getClass().isAnnotationPresent(MetaTransient.class));
  }

  public static Map<String, Object> durableMetadata(WorkflowInstanceData instance) {
    return durableMetadataAsStream(instance)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }
}
