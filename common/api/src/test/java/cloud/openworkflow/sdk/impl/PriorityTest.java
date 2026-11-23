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
package cloud.openworkflow.sdk.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriorityTest {

  @Test
  void testOrder() {

    TopPriority top = new TopPriority();
    MediumPriority medium = new MediumPriority();
    LowestPriority lowest = new LowestPriority();

    List list = new ArrayList<>(Arrays.asList(medium, lowest, top));
    Collections.sort(list);
    assertThat(list).containsExactly(top, medium, lowest);
  }
}
