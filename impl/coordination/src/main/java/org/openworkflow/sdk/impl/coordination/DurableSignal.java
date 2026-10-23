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
package org.openworkflow.sdk.impl.coordination;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.openworkflow.sdk.impl.TaskContextData;

public class DurableSignal implements CoordinationPrimitive {

  private final String name;
  private final Predicate<TaskContextData> senderMatcher;
  private final Predicate<TaskContextData> receiverMatcher;
  private final AtomicLong version = new AtomicLong(0);
  private volatile boolean sent;
  private volatile Object value;
  private volatile CompletableFuture<Object> signalFuture = new CompletableFuture<>();

  public DurableSignal(
      String name,
      Predicate<TaskContextData> senderMatcher,
      Predicate<TaskContextData> receiverMatcher) {
    this.name = name;
    this.senderMatcher = senderMatcher;
    this.receiverMatcher = receiverMatcher;
  }

  public boolean matchesSender(TaskContextData task) {
    return senderMatcher.test(task);
  }

  public boolean matchesReceiver(TaskContextData task) {
    return receiverMatcher.test(task);
  }

  public void send(Object value) {
    this.value = value;
    this.sent = true;
    version.incrementAndGet();
    signalFuture.complete(value);
  }

  public CompletableFuture<Object> await() {
    return signalFuture;
  }

  public boolean isSent() {
    return sent;
  }

  public Object value() {
    return value;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public long version() {
    return version.get();
  }

  @Override
  public Map<String, Object> serialize() {
    Map<String, Object> map = new HashMap<>();
    map.put("sent", sent);
    if (value != null) {
      map.put("value", value);
    }
    return map;
  }

  @Override
  public void restore(Map<String, Object> state) {
    this.sent = Boolean.TRUE.equals(state.get("sent"));
    this.value = state.get("value");
    version.incrementAndGet();
    if (sent && !signalFuture.isDone()) {
      signalFuture.complete(value);
    }
  }
}
