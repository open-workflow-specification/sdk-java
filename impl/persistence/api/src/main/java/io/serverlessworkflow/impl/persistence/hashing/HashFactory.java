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
package io.serverlessworkflow.impl.persistence.hashing;

import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface HashFactory {

  HashMappingCoordinator mapCoordinator(
      Function<String, Map<String, Map<HashIndex, byte[]>>> retriever,
      Consumer<Map<String, List<HashMappingInfo>>> writer);

  Optional<HashItem> fromData(byte[] data);

  Optional<HashItem> fromBuffer(byte id, WorkflowInputBuffer buffer);

  HashIndex indexFromBytes(byte[] bytes);

  HashIndex indexFromString(String str);

  HashIndex newIndex();
}
