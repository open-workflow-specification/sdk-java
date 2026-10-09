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
package org.openworkflow.sdk.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JPackage;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.util.NameHelper;

public class RefNameHelper extends NameHelper {

  public RefNameHelper(GenerationConfig generationConfig) {
    super(generationConfig);
  }

  @Override
  public String getUniqueClassName(String nodeName, JsonNode node, JPackage _package) {
    String className = getClassName(nodeName, node, _package);
    try {
      JDefinedClass _class = _package._class(className);
      _package.remove(_class);
      return className;
    } catch (JClassAlreadyExistsException ex) {
      return super.getUniqueClassName(nodeName, null, _package);
    }
  }
}
