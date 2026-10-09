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
package org.openworkflow.sdk.generator.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.openworkflow.sdk.serialization.DeserializeHelper;
import org.openworkflow.sdk.serialization.SerializeHelper;

public class GeneratorUtils {

  @FunctionalInterface
  public interface SerializerFiller {
    void accept(JMethod method, JVar valueParam, JVar genParam);
  }

  @FunctionalInterface
  public interface DeserializerFiller {
    void accept(JMethod method, JVar parserParam);
  }

  public static JDefinedClass serializerClass(JPackage jPackage, JClass relatedClass)
      throws JClassAlreadyExistsException {
    return createClass(jPackage, relatedClass, JsonSerializer.class, "Serializer");
  }

  public static JDefinedClass deserializerClass(JPackage jPackage, JClass relatedClass)
      throws JClassAlreadyExistsException {
    return createClass(jPackage, relatedClass, JsonDeserializer.class, "Deserializer");
  }

  public static void fillSerializer(
      JDefinedClass definedClass, JClass relatedClass, SerializerFiller filler) {
    JMethod method = definedClass.method(JMod.PUBLIC, void.class, "serialize");
    method.annotate(Override.class);
    method._throws(IOException.class);
    JVar valueParam = method.param(relatedClass, "value");
    JVar genParam = method.param(JsonGenerator.class, "gen");
    method.param(SerializerProvider.class, "serializers");
    filler.accept(method, valueParam, genParam);
  }

  public static void fillDeserializer(
      JDefinedClass definedClass, JClass relatedClass, DeserializerFiller filler) {
    JMethod method = definedClass.method(JMod.PUBLIC, relatedClass, "deserialize");
    method.annotate(Override.class);
    method._throws(IOException.class);
    JVar parserParam = method.param(JsonParser.class, "parser");
    method.param(DeserializationContext.class, "dctx");
    filler.accept(method, parserParam);
  }

  private static JDefinedClass createClass(
      JPackage jPackage, JClass relatedClass, Class<?> serializerClass, String suffix)
      throws JClassAlreadyExistsException {
    JDefinedClass definedClass = jPackage._class(JMod.PUBLIC, relatedClass.name() + suffix);
    definedClass._extends(definedClass.owner().ref(serializerClass).narrow(relatedClass));
    return definedClass;
  }

  public static JDefinedClass generateSerializer(JPackage jPackage, JClass relatedClass)
      throws JClassAlreadyExistsException {
    JDefinedClass definedClass = GeneratorUtils.serializerClass(jPackage, relatedClass);
    GeneratorUtils.fillSerializer(
        definedClass,
        relatedClass,
        (method, valueParam, genParam) ->
            method
                .body()
                .staticInvoke(definedClass.owner().ref(SerializeHelper.class), "serializeOneOf")
                .arg(genParam)
                .arg(valueParam));
    return definedClass;
  }

  public static JDefinedClass generateDeserializer(
      JPackage jPackage, JClass relatedClass, Collection<JClass> oneOfTypes)
      throws JClassAlreadyExistsException {
    JDefinedClass definedClass = GeneratorUtils.deserializerClass(jPackage, relatedClass);
    GeneratorUtils.fillDeserializer(
        definedClass,
        relatedClass,
        (method, parserParam) -> {
          JBlock body = method.body();

          body._return(
              definedClass
                  .owner()
                  .ref(DeserializeHelper.class)
                  .staticInvoke("deserializeOneOf")
                  .arg(parserParam)
                  .arg(relatedClass.dotclass())
                  .arg(list(definedClass, oneOfTypes)));
        });
    return definedClass;
  }

  public static JDefinedClass generateDeserializer(
      JPackage jPackage, JClass relatedClass, JType propertyType)
      throws JClassAlreadyExistsException {
    JDefinedClass definedClass = GeneratorUtils.deserializerClass(jPackage, relatedClass);
    GeneratorUtils.fillDeserializer(
        definedClass,
        relatedClass,
        (method, parserParam) ->
            method
                .body()
                ._return(
                    definedClass
                        .owner()
                        .ref(DeserializeHelper.class)
                        .staticInvoke("deserializeItem")
                        .arg(parserParam)
                        .arg(relatedClass.dotclass())
                        .arg(((JClass) propertyType).dotclass())));
    return definedClass;
  }

  public static JDefinedClass generateSerializer(
      JPackage jPackage, JClass relatedClass, String keyMethod, String valueMethod)
      throws JClassAlreadyExistsException {
    JDefinedClass definedClass = GeneratorUtils.serializerClass(jPackage, relatedClass);
    GeneratorUtils.fillSerializer(
        definedClass,
        relatedClass,
        (method, valueParam, genParam) -> {
          JBlock body = method.body();
          body.invoke(genParam, "writeStartObject");
          body.invoke(genParam, "writeObjectField")
              .arg(valueParam.invoke(keyMethod))
              .arg(valueParam.invoke(valueMethod));
          body.invoke(genParam, "writeEndObject");
        });
    return definedClass;
  }

  private static JInvocation list(JDefinedClass definedClass, Collection<JClass> list) {
    JInvocation result = definedClass.owner().ref(List.class).staticInvoke("of");
    list.forEach(c -> result.arg(c.dotclass()));
    return result;
  }

  private GeneratorUtils() {}
}
