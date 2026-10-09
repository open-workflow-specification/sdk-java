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
package org.openworkflow.sdk.impl.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class JWTTest {

  @Test
  public void testToStringCollection() {
    String[] test = {"javierito", "fulanito", "menganito"};
    assertThat(JWT.toCollection(test, String.class))
        .isEqualTo(List.of("javierito", "fulanito", "menganito"));
  }

  @Test
  public void testToIntCollection() {
    Integer[] test = {1, 2, 3};
    assertThat(JWT.toCollection(test, Integer.class)).isEqualTo(List.of(1, 2, 3));
  }
}
