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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.openworkflow.sdk.impl.expressions.DateTimeDescriptor;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

class DateTimeDescriptorTest {

  @Test
  void serializeDate() {
    DateTimeDescriptor descriptor = DateTimeDescriptor.from(Instant.now());

    JsonNode node = JsonUtils.fromValue(descriptor);
    assertThat(node.get("iso8601").isTextual()).isTrue();
    assertThat(node.get("epoch").isObject()).isTrue();
  }
}
