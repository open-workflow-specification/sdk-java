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
package io.serverlessworkflow.impl.persistence.bigmap;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.executors.AbstractTaskExecutor;
import io.serverlessworkflow.impl.executors.TransitionInfo;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.TaskStatus;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import io.serverlessworkflow.impl.persistence.CompletedTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceInfo;
import io.serverlessworkflow.impl.persistence.PersistenceTaskInfo;
import io.serverlessworkflow.impl.persistence.RetriedTaskInfo;
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashIndex;
import io.serverlessworkflow.impl.persistence.hashing.HashItem;
import io.serverlessworkflow.impl.persistence.hashing.HashMappingCoordinator;
import io.serverlessworkflow.impl.persistence.hashing.HashMappingInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BytesMapInstanceTransaction
    extends BigMapInstanceTransaction<byte[], byte[], byte[], byte[], byte[], byte[]> {

  private static final byte VERSION_0 = 0;
  private static final byte VERSION_1 = 1;
  private static final byte VERSION_2 = 2;
  private static final byte VERSION_3 = 3;
  private static final byte[] PROCESSED_VALUE = {1};
  private static final String SEPARATOR = ":";

  private final WorkflowBufferFactory bufferFactory;
  private final HashFactory hashFactory;
  protected final HashMappingCoordinator hashCoordinator;

  protected BytesMapInstanceTransaction(WorkflowBufferFactory factory, HashFactory hashFactory) {
    this.bufferFactory = factory;
    this.hashFactory = hashFactory;
    this.hashCoordinator =
        HashMappingCoordinator.build(hashFactory, this::retrieveBlobData, this::writeBlobData);
  }

  private Map<String, Map<HashIndex, byte[]>> retrieveBlobData(String instanceId) {
    Map<String, Map<HashIndex, byte[]>> result = new HashMap<>();
    for (Map.Entry<String, byte[]> entry : blobData(instanceId).entrySet()) {
      String key = entry.getKey();
      int indexOf = key.indexOf(SEPARATOR);
      result
          .computeIfAbsent(key.substring(0, indexOf), __ -> new HashMap<>())
          .put(hashFactory.indexFromString(key.substring(indexOf + 1)), entry.getValue());
    }
    return result;
  }

  private void writeBlobData(Map<String, List<HashMappingInfo>> writeInfo) {
    for (Map.Entry<String, List<HashMappingInfo>> entry : writeInfo.entrySet()) {
      Map<String, byte[]> blobData = blobData(entry.getKey());
      for (HashMappingInfo info : entry.getValue()) {
        blobData.put(info.key() + SEPARATOR + info.index(), info.bytes());
      }
    }
  }

  protected abstract Map<String, byte[]> blobData(String instanceId);

  protected abstract void removeBlobData(String instanceId);

  @Override
  public void removeProcessInstance(WorkflowContextData workflowContext) {
    super.removeProcessInstance(workflowContext);
    String instanceId = workflowContext.instanceData().id();
    removeBlobData(instanceId);
    hashCoordinator.afterRemove(instanceId);
  }

  @Override
  protected byte[] marshallTaskCompleted(WorkflowContextData contextData, TaskContext taskContext) {

    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WorkflowOutputBuffer writer = bufferFactory.output(bytes)) {
      writer.writeByte(VERSION_3);
      writer.writeEnum(TaskStatus.COMPLETED);
      writer.writeInstant(taskContext.completedAt());
      writeLargeObject(contextData.instanceData(), writer, taskContext.output());
      writeLargeObject(contextData.instanceData(), writer, contextData.context());
      TransitionInfo transition = taskContext.transition();
      writer.writeBoolean(transition.isEndNode());
      AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
      if (next == null) {
        writer.writeBoolean(false);
      } else {
        writer.writeBoolean(true);
        writer.writeString(next.position().jsonPointer());
      }
      writer.writeInt(taskContext.iteration());
      writeMetadata(contextData.instanceData(), writer);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeMetadata(WorkflowInstanceData instanceData, WorkflowOutputBuffer writer) {
    Map<String, Object> additionalObjects = new HashMap<>(instanceData.metadata());
    writer.writeInt(additionalObjects.size());
    additionalObjects.forEach(
        (k, v) -> {
          writer.writeString(k);
          writeLargeObject(instanceData, writer, v);
        });
  }

  private void writeLargeObject(
      WorkflowInstanceData instanceData, WorkflowOutputBuffer writer, Object obj) {
    final byte[] bytes = MarshallingUtils.writeObject(bufferFactory, obj);
    hashFactory
        .fromData(bytes)
        .ifPresentOrElse(
            item -> writeLargeObject(item, instanceData, writer, bytes),
            () -> legacyWriteObject(writer, bytes));
  }

  @Override
  protected byte[] marshallStatus(WorkflowStatus status) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WorkflowOutputBuffer writer = bufferFactory.output(bytes)) {
      writer.writeByte(VERSION_0);
      writer.writeEnum(status);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected byte[] marshallInstance(WorkflowInstanceData instance) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WorkflowOutputBuffer writer = bufferFactory.output(bytes)) {
      writer.writeByte(VERSION_1);
      writer.writeInstant(instance.startedAt());
      writer.writeObject(instance.input());
      writeMetadata(instance, writer);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected byte[] marshallApplicationId(String id) {
    return MarshallingUtils.writeString(bufferFactory, id);
  }

  protected String unmarshallApplicationId(byte[] value) {
    return MarshallingUtils.readString(bufferFactory, value);
  }

  @Override
  protected byte[] marshallTaskRetried(
      WorkflowContextData workflowContext, TaskContext taskContext) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (WorkflowOutputBuffer writer = bufferFactory.output(bytes)) {
      writer.writeByte(VERSION_3);
      writer.writeEnum(TaskStatus.RETRIED);
      writer.writeInt(taskContext.retryAttempt());
      writeMetadata(workflowContext.instanceData(), writer);
    }
    return bytes.toByteArray();
  }

  @Override
  protected PersistenceTaskInfo unmarshallTaskInfo(String instanceId, byte[] taskData) {
    try (WorkflowInputBuffer buffer = bufferFactory.input(new ByteArrayInputStream(taskData))) {
      byte version = buffer.readByte();
      switch (version) {
        case VERSION_0:
          return readVersion0(buffer);
        case VERSION_1:
          return readVersion1(buffer);
        case VERSION_2:
          return readVersion2(buffer);
        case VERSION_3:
          return readVersion3(instanceId, buffer);
      }
      throw new UnsupportedOperationException("Unknown version " + version);
    }
  }

  private PersistenceTaskInfo readVersion3(String instanceId, WorkflowInputBuffer buffer) {
    TaskStatus taskStatus = buffer.readEnum(TaskStatus.class);
    switch (taskStatus) {
      case COMPLETED:
        return new CompletedTaskInfo(
            buffer.readInstant(),
            (WorkflowModel) readLargeObject(instanceId, buffer),
            (WorkflowModel) readLargeObject(instanceId, buffer),
            buffer.readBoolean(),
            buffer.readBoolean() ? buffer.readString() : null,
            buffer.readInt(),
            readMetadata(instanceId, buffer));
      case RETRIED:
        return new RetriedTaskInfo(buffer.readInt(), readMetadata(instanceId, buffer));
    }
    throw new UnsupportedOperationException("Unknown status " + taskStatus);
  }

  private Map<String, Object> readMetadata(String instanceId, WorkflowInputBuffer buffer) {
    int size = buffer.readInt();
    Map<String, Object> map = new HashMap<>(size);
    while (size-- > 0) {
      map.put(buffer.readString(), readLargeObject(instanceId, buffer));
    }
    return map;
  }

  private PersistenceTaskInfo readVersion2(WorkflowInputBuffer buffer) {
    TaskStatus taskStatus = buffer.readEnum(TaskStatus.class);
    switch (taskStatus) {
      case COMPLETED:
        return new CompletedTaskInfo(
            buffer.readInstant(),
            (WorkflowModel) buffer.readObject(),
            (WorkflowModel) buffer.readObject(),
            buffer.readBoolean(),
            buffer.readBoolean() ? buffer.readString() : null,
            buffer.readInt());
      case RETRIED:
        return new RetriedTaskInfo(buffer.readInt());
    }
    throw new UnsupportedOperationException("Unknown status " + taskStatus);
  }

  private PersistenceTaskInfo readVersion1(WorkflowInputBuffer buffer) {
    TaskStatus taskStatus = buffer.readEnum(TaskStatus.class);
    switch (taskStatus) {
      case COMPLETED:
        return readVersion0(buffer);
      case RETRIED:
        return new RetriedTaskInfo(buffer.readShort());
    }
    throw new UnsupportedOperationException("Unknown status " + taskStatus);
  }

  private PersistenceTaskInfo readVersion0(WorkflowInputBuffer buffer) {
    return new CompletedTaskInfo(
        buffer.readInstant(),
        (WorkflowModel) buffer.readObject(),
        (WorkflowModel) buffer.readObject(),
        buffer.readBoolean(),
        buffer.readBoolean() ? buffer.readString() : null);
  }

  private void writeLargeObject(
      HashItem item, WorkflowInstanceData instanceData, WorkflowOutputBuffer writer, byte[] bytes) {
    HashIndex index = hashCoordinator.calculateIndex(instanceData.id(), item, bytes);
    writer.writeByte(item.id());
    item.writeKey(writer);
    writer.writeBytes(index.toBytes());
  }

  private Object readLargeObject(String instanceId, WorkflowInputBuffer buffer) {
    return hashFactory
        .fromBuffer(buffer.readByte(), buffer)
        .map(
            item -> {
              try (WorkflowInputBuffer input =
                  bufferFactory.input(
                      new ByteArrayInputStream(
                          hashCoordinator
                              .readBytes(
                                  instanceId, item, hashFactory.indexFromBytes(buffer.readBytes()))
                              .orElseThrow()))) {
                return input.readObject();
              }
            })
        .orElseGet(() -> buffer.readObject());
  }

  private void legacyWriteObject(WorkflowOutputBuffer writer, byte[] bytes) {
    writer.writeByte(HashItem.HASHING_DISABLED).writeRawBytes(bytes);
  }

  @Override
  protected PersistenceInstanceInfo unmarshallInstanceInfo(String instanceId, byte[] instanceData) {
    try (WorkflowInputBuffer buffer = bufferFactory.input(new ByteArrayInputStream(instanceData))) {
      byte version = buffer.readByte(); // version byte not used at the moment

      return version == VERSION_1
          ? new PersistenceInstanceInfo(
              buffer.readInstant(),
              (WorkflowModel) buffer.readObject(),
              readMetadata(instanceId, buffer))
          : new PersistenceInstanceInfo(buffer.readInstant(), (WorkflowModel) buffer.readObject());
    }
  }

  @Override
  protected WorkflowStatus unmarshallStatus(byte[] statusData) {
    try (WorkflowInputBuffer buffer = bufferFactory.input(new ByteArrayInputStream(statusData))) {
      buffer.readByte(); // version byte not used at the moment
      return buffer.readEnum(WorkflowStatus.class);
    }
  }

  protected byte[] marshallCloudEvent(CloudEvent event) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        WorkflowOutputBuffer writer = bufferFactory.output(bytes)) {
      writer.writeEnum(event.getSpecVersion());
      writer.writeString(event.getId());
      writer.writeString(event.getType());
      writer.writeURI(event.getSource());
      writer.writeObject(event.getTime());
      writer.writeObject(event.getSubject());
      writer.writeObject(event.getDataSchema());
      writer.writeObject(event.getDataContentType());
      writer.writeObject(event.getData() == null ? null : event.getData().toBytes());
      MarshallingUtils.writeCloudEventExtensions(writer, event);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected CloudEvent unmarshallCloudEvent(byte[] eventData) {
    try (ByteArrayInputStream bytes = new ByteArrayInputStream(eventData);
        WorkflowInputBuffer reader = bufferFactory.input(bytes)) {
      CloudEventBuilder builder =
          CloudEventBuilder.fromSpecVersion(reader.readEnum(SpecVersion.class));
      builder.withId(reader.readString());
      builder.withType(reader.readString());
      builder.withSource(reader.readURI());
      builder.withTime((OffsetDateTime) reader.readObject());
      builder.withSubject((String) reader.readObject());
      builder.withDataSchema((URI) reader.readObject());
      builder.withDataContentType((String) reader.readObject());
      builder.withData((byte[]) reader.readObject());
      MarshallingUtils.readCloudEventExtensions(reader, eventData, builder);
      return builder.build();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected byte[] processedValue() {
    return PROCESSED_VALUE;
  }
}
