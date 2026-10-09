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
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public class ProxyMap implements Map<String, Object> {

  private final Map<String, Object> map;
  private final UnaryOperator<Object> function;

  public ProxyMap(Map<String, Object> map, UnaryOperator<Object> function) {
    this.map = map;
    this.function = function;
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  @Override
  public Object get(Object key) {
    return processValue(map.get(key));
  }

  @Override
  public Object put(String key, Object value) {
    return map.put(key, processValue(value));
  }

  @Override
  public Object remove(Object key) {
    return map.remove(key);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Object> m) {
    map.putAll(m);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public Set<String> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<Object> values() {
    return new ProxyCollection(map.values(), function);
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return new ProxyEntrySet(map.entrySet());
  }

  private class ProxyEntrySet extends AbstractProxyCollection<Entry<String, Object>>
      implements Set<Entry<String, Object>> {

    public ProxyEntrySet(Set<Entry<String, Object>> entrySet) {
      super(entrySet);
    }

    @Override
    public Iterator<Entry<String, Object>> iterator() {
      return new ProxyEntryIterator(values.iterator());
    }

    @Override
    public Object[] toArray() {
      return processEntries(values.toArray());
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return processEntries(values.toArray(a));
    }

    private <T> T[] processEntries(T[] array) {
      for (int i = 0; i < array.length; i++) {
        array[i] = (T) new ProxyEntry((Entry<String, Object>) array[i]);
      }
      return array;
    }
  }

  private class ProxyEntry implements Entry<String, Object> {

    private Entry<String, Object> entry;

    private ProxyEntry(Entry<String, Object> entry) {
      this.entry = entry;
    }

    @Override
    public String getKey() {
      return entry.getKey();
    }

    @Override
    public Object getValue() {
      return processValue(entry.getValue());
    }

    @Override
    public Object setValue(Object value) {
      return entry.setValue(value);
    }
  }

  private class ProxyEntryIterator implements Iterator<Entry<String, Object>> {

    private Iterator<Entry<String, Object>> iter;

    public ProxyEntryIterator(Iterator<Entry<String, Object>> iter) {
      this.iter = iter;
    }

    @Override
    public boolean hasNext() {
      return iter.hasNext();
    }

    @Override
    public Entry<String, Object> next() {
      return new ProxyEntry(iter.next());
    }

    @Override
    public void remove() {
      iter.remove();
    }
  }

  private <T> Object processValue(T obj) {
    return function.apply(obj);
  }
}
