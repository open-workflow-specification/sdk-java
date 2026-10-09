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
package org.openworkflow.sdk.impl.scripts;

import java.util.Arrays;

public enum ScriptLanguageId {
  JS("js"),
  PYTHON("python");

  private final String lang;

  ScriptLanguageId(String lang) {
    this.lang = lang;
  }

  public String getLang() {
    return lang;
  }

  public static ScriptLanguageId from(String lang) {
    for (ScriptLanguageId l : ScriptLanguageId.values()) {
      if (l.getLang().equalsIgnoreCase(lang)) {
        return l;
      }
    }
    throw new IllegalStateException(
        "Unsupported script language: "
            + lang
            + ". Supported languages are: "
            + Arrays.toString(
                Arrays.stream(ScriptLanguageId.values()).map(ScriptLanguageId::getLang).toArray()));
  }
}
