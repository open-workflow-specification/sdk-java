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

import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import java.lang.annotation.Annotation;
import java.util.Collection;
import org.jsonschema2pojo.util.NameHelper;

public class GeneratorUtils {

  public static JMethod implementInterface(JDefinedClass definedClass, JFieldVar valueField) {
    JMethod method = definedClass.method(JMod.PUBLIC, valueField.type(), "get");
    method.annotate(Override.class);
    method.body()._return(valueField);
    return method;
  }

  public static JMethod getterMethod(
      JDefinedClass definedClass, JFieldVar instanceField, NameHelper nameHelper, String name) {
    JMethod method =
        definedClass.method(
            JMod.PUBLIC,
            instanceField.type(),
            nameHelper.getGetterName(name, instanceField.type(), null));
    method.body()._return(instanceField);
    return method;
  }

  public static void annotateCollection(
      JDefinedClass definedClass,
      Class<? extends Annotation> annotation,
      Collection<JTypeWrapper> types) {
    JAnnotationArrayMember unionAnnotation = definedClass.annotate(annotation).paramArray("value");
    types.forEach(t -> unionAnnotation.param(t.getType()));
  }

  private GeneratorUtils() {}
}
