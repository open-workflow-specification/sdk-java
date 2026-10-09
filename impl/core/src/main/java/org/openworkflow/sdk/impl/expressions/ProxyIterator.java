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

import java.util.Iterator;
import java.util.function.UnaryOperator;

class ProxyIterator implements Iterator<Object> {

  private Iterator<Object> iter;
  private final UnaryOperator<Object> function;

  public ProxyIterator(Iterator<Object> iter, UnaryOperator<Object> function) {
    this.iter = iter;
    this.function = function;
  }

  @Override
  public boolean hasNext() {
    return iter.hasNext();
  }

  @Override
  public Object next() {
    return function.apply(iter.next());
  }

  @Override
  public void remove() {
    iter.remove();
  }
}
