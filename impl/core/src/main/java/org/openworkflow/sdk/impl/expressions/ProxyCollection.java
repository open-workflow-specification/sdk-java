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
package org.openworkflow.sdk.impl.expressions;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.UnaryOperator;

class ProxyCollection extends AbstractProxyCollection<Object> implements Collection<Object> {

  private final UnaryOperator<Object> function;

  public ProxyCollection(Collection<Object> values, UnaryOperator<Object> function) {
    super(values);
    this.function = function;
  }

  @Override
  public Iterator<Object> iterator() {
    return new ProxyIterator(values.iterator(), function);
  }

  @Override
  public Object[] toArray() {
    return processArray(values.toArray());
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return processArray(values.toArray(a));
  }

  private <S> S[] processArray(S[] array) {
    for (int i = 0; i < array.length; i++) {
      array[i] = (S) function.apply(array[i]);
    }
    return array;
  }
}
