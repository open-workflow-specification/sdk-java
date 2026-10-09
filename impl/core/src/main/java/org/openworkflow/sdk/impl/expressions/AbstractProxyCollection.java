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

abstract class AbstractProxyCollection<T> {

  protected Collection<T> values;

  protected AbstractProxyCollection(Collection<T> values) {
    this.values = values;
  }

  public int size() {
    return values.size();
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public boolean contains(Object o) {
    return values.contains(o);
  }

  public boolean remove(Object o) {
    return values.remove(o);
  }

  public boolean containsAll(Collection<?> c) {
    return values.containsAll(c);
  }

  public boolean retainAll(Collection<?> c) {
    return values.retainAll(c);
  }

  public boolean removeAll(Collection<?> c) {
    return values.removeAll(c);
  }

  public void clear() {
    values.clear();
  }

  public boolean addAll(Collection<? extends T> c) {
    return values.addAll(c);
  }

  public boolean add(T e) {
    return values.add(e);
  }
}
