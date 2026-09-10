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
package io.serverlessworkflow.fluent.spec;

import io.serverlessworkflow.fluent.spec.spi.DoFluent;
import java.util.function.Consumer;

public class DoTaskBuilder extends BaseDoTaskBuilder<DoTaskBuilder, TaskItemListBuilder>
    implements DoFluent<DoTaskBuilder> {

  DoTaskBuilder(int listSizeOffset) {
    super(new TaskItemListBuilder(listSizeOffset));
  }

  @Override
  protected DoTaskBuilder self() {
    return this;
  }

  @Override
  public DoTaskBuilder http(String name, Consumer<CallHttpTaskBuilder> itemsConfigurer) {
    this.listBuilder().http(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder emit(String name, Consumer<EmitTaskBuilder> itemsConfigurer) {
    this.listBuilder().emit(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder forEach(
      String name, Consumer<ForEachTaskBuilder<TaskItemListBuilder>> itemsConfigurer) {
    this.listBuilder().forEach(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder fork(String name, Consumer<ForkTaskBuilder> itemsConfigurer) {
    this.listBuilder().fork(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder listen(String name, Consumer<ListenTaskBuilder> itemsConfigurer) {
    this.listBuilder().listen(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder raise(String name, Consumer<RaiseTaskBuilder> itemsConfigurer) {
    this.listBuilder().raise(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder set(String name, Consumer<SetTaskBuilder> itemsConfigurer) {
    this.listBuilder().set(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder set(String name, String expr) {
    this.listBuilder().set(name, expr);
    return this;
  }

  @Override
  public DoTaskBuilder wait(String name, Consumer<WaitTaskBuilder> itemsConfigurer) {
    this.listBuilder().wait(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder switchCase(String name, Consumer<SwitchTaskBuilder> itemsConfigurer) {
    this.listBuilder().switchCase(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder tryCatch(
      String name, Consumer<TryTaskBuilder<TaskItemListBuilder>> itemsConfigurer) {
    this.listBuilder().tryCatch(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder openapi(String name, Consumer<CallOpenAPITaskBuilder> itemsConfigurer) {
    this.listBuilder().openapi(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder asyncapi(String name, Consumer<CallAsyncAPITaskBuilder> itemsConfigurer) {
    this.listBuilder().asyncapi(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder grpc(String name, Consumer<CallGrpcTaskBuilder> itemsConfigurer) {
    this.listBuilder().grpc(name, itemsConfigurer);
    return this;
  }

  @Override
  public DoTaskBuilder workflow(String name, Consumer<WorkflowTaskBuilder> itemsConfigurer) {
    this.listBuilder().workflow(name, itemsConfigurer);
    return this;
  }
}
