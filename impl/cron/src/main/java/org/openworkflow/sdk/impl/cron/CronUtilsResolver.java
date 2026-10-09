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
package org.openworkflow.sdk.impl.cron;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.openworkflow.sdk.impl.scheduler.CronResolver;

class CronUtilsResolver implements CronResolver {

  private final ExecutionTime executionTime;

  public CronUtilsResolver(Cron cron) {
    this.executionTime = ExecutionTime.forCron(cron);
  }

  @Override
  public Optional<Duration> nextExecution() {
    return executionTime.timeToNextExecution(ZonedDateTime.now());
  }
}
