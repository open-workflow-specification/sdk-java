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

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import org.openworkflow.sdk.impl.scheduler.CronResolver;
import org.openworkflow.sdk.impl.scheduler.CronResolverFactory;

public class CronUtilsResolverFactory implements CronResolverFactory {

  private final CronParser cronParser;

  public CronUtilsResolverFactory() {
    this(CronType.UNIX);
  }

  public CronUtilsResolverFactory(CronType type) {
    this.cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(type));
  }

  @Override
  public CronResolver parseCron(String cron) {
    return new CronUtilsResolver(cronParser.parse(cron));
  }
}
