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
package org.openworkflow.sdk.impl;

public class StringBufferWorkflowPosition implements WorkflowMutablePosition {

  private StringBuilder sb;

  StringBufferWorkflowPosition() {
    this("");
  }

  private StringBufferWorkflowPosition(String str) {
    this.sb = new StringBuilder(str);
  }

  public StringBufferWorkflowPosition copy() {
    return new StringBufferWorkflowPosition(this.jsonPointer());
  }

  @Override
  public WorkflowMutablePosition addIndex(int index) {
    sb.append('/').append(index);
    return this;
  }

  @Override
  public WorkflowMutablePosition addProperty(String prop) {
    sb.append('/').append(prop);
    return this;
  }

  @Override
  public String jsonPointer() {
    return sb.toString();
  }

  @Override
  public String toString() {
    return "StringBufferWorkflowPosition [sb=" + sb + "]";
  }

  @Override
  public WorkflowMutablePosition back() {
    int indexOf = sb.lastIndexOf("/");
    if (indexOf != -1) {
      sb.substring(0, indexOf);
    }
    return this;
  }

  @Override
  public Object last() {
    int indexOf = sb.lastIndexOf("/");
    return indexOf != -1 ? jsonPointer().substring(indexOf + 1) : "";
  }
}
