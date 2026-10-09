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
package org.openworkflow.sdk.impl.model.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.openworkflow.sdk.impl.CollectionConversionUtils;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowModelCollection;
import org.openworkflow.sdk.impl.jackson.JsonUtils;

@JsonSerialize(using = JacksonModelCollectionSerializer.class)
@JsonDeserialize(using = JacksonModelCollectionDeserializer.class)
public class JacksonModelCollection implements WorkflowModelCollection {

  protected ArrayNode node;

  JacksonModelCollection() {
    this.node = JsonUtils.array();
  }

  JacksonModelCollection(ArrayNode node) {
    this.node = node;
  }

  @Override
  public <T> Optional<T> as(Class<T> clazz) {
    if (node == null) return Optional.empty();

    if (clazz.isInstance(node)) return Optional.of(clazz.cast(node));
    if (clazz.isInstance(this)) return Optional.of(clazz.cast(this));

    return CollectionConversionUtils.as(node, clazz, JsonUtils::convertValue);
  }

  @Override
  public int size() {
    return node.size();
  }

  @Override
  public boolean isEmpty() {
    return node.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    throw new UnsupportedOperationException("contains() is not supported yet");
  }

  @Override
  public Iterator<WorkflowModel> iterator() {
    return new WrapperIterator(node.iterator());
  }

  private class WrapperIterator implements Iterator<WorkflowModel> {

    private Iterator<JsonNode> iterator;

    public WrapperIterator(Iterator<JsonNode> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {

      return iterator.hasNext();
    }

    @Override
    public WorkflowModel next() {
      return new JacksonModel(iterator.next());
    }
  }

  private List<WorkflowModel> toModelList() {
    List<WorkflowModel> models = new ArrayList<>(node.size());
    node.forEach(n -> models.add(new JacksonModel(n)));
    return models;
  }

  @Override
  public Object[] toArray() {
    return toModelList().toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return toModelList().toArray(a);
  }

  @Override
  public boolean add(WorkflowModel e) {
    node.add(
        e.as(JsonNode.class).orElseThrow(() -> new IllegalArgumentException("Not a json node")));
    return true;
  }

  @Override
  public boolean remove(Object o) {
    int size = node.size();
    node.removeIf(i -> i.equals(o));
    return node.size() < size;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new UnsupportedOperationException("containsAll() is not supported yet");
  }

  @Override
  public boolean addAll(Collection<? extends WorkflowModel> c) {
    c.forEach(this::add);
    return true;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    int size = node.size();
    c.forEach(o -> node.removeIf(i -> i.equals(o)));
    return node.size() < size;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("retainAll() is not supported yet");
  }

  @Override
  public void clear() {
    node.removeAll();
  }

  @Override
  public String toString() {
    return node.toPrettyString();
  }

  @Override
  public Object asJavaObject() {
    return JsonUtils.toJavaValue(node);
  }

  @Override
  public Class<?> objectClass() {
    return ArrayNode.class;
  }

  @Override
  public int hashCode() {
    return Objects.hash(node);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof JacksonModelCollection)) return false;
    JacksonModelCollection other = (JacksonModelCollection) obj;
    return Objects.equals(node, other.node);
  }
}
