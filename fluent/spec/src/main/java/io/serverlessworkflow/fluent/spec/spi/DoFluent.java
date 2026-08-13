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
package io.serverlessworkflow.fluent.spec.spi;

import io.serverlessworkflow.fluent.spec.CallAsyncAPITaskBuilder;
import io.serverlessworkflow.fluent.spec.CallGrpcTaskBuilder;
import io.serverlessworkflow.fluent.spec.CallHttpTaskBuilder;
import io.serverlessworkflow.fluent.spec.CallOpenAPITaskBuilder;
import io.serverlessworkflow.fluent.spec.EmitTaskBuilder;
import io.serverlessworkflow.fluent.spec.ForEachTaskBuilder;
import io.serverlessworkflow.fluent.spec.ForkTaskBuilder;
import io.serverlessworkflow.fluent.spec.ListenTaskBuilder;
import io.serverlessworkflow.fluent.spec.RaiseTaskBuilder;
import io.serverlessworkflow.fluent.spec.SetTaskBuilder;
import io.serverlessworkflow.fluent.spec.SwitchTaskBuilder;
import io.serverlessworkflow.fluent.spec.TaskItemListBuilder;
import io.serverlessworkflow.fluent.spec.TryTaskBuilder;
import io.serverlessworkflow.fluent.spec.WaitTaskBuilder;
import io.serverlessworkflow.fluent.spec.WorkflowTaskBuilder;

/**
 * Documents the exposed fluent `do` DSL.
 *
 * @see <a
 *     href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#do">CNCF
 *     DSL Reference - Do</a>
 */
public interface DoFluent<T>
    extends SetFluent<SetTaskBuilder, T>,
        SwitchFluent<SwitchTaskBuilder, T>,
        TryCatchFluent<TryTaskBuilder<TaskItemListBuilder>, T>,
        CallHttpFluent<CallHttpTaskBuilder, T>,
        EmitFluent<EmitTaskBuilder, T>,
        ForEachFluent<ForEachTaskBuilder<TaskItemListBuilder>, T>,
        ForkFluent<ForkTaskBuilder, T>,
        ListenFluent<ListenTaskBuilder, T>,
        WaitFluent<WaitTaskBuilder, T>,
        RaiseFluent<RaiseTaskBuilder, T>,
        CallOpenAPIFluent<CallOpenAPITaskBuilder, T>,
        CallAsyncAPIFluent<CallAsyncAPITaskBuilder, T>,
        CallGrpcFluent<CallGrpcTaskBuilder, T>,
        WorkflowFluent<WorkflowTaskBuilder, T> {}
