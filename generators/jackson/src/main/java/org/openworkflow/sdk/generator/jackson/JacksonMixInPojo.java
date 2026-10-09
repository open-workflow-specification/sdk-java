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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.Module.SetupContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import io.github.classgraph.AnnotationClassRef;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.openworkflow.sdk.annotations.AdditionalProperties;
import org.openworkflow.sdk.annotations.ExclusiveUnion;
import org.openworkflow.sdk.annotations.InclusiveUnion;
import org.openworkflow.sdk.annotations.Item;
import org.openworkflow.sdk.annotations.ItemKey;
import org.openworkflow.sdk.annotations.ItemValue;
import org.openworkflow.sdk.generator.graalvm.ReflectNativeFileHandler;

@Mojo(
    name = "generate",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class JacksonMixInPojo extends AbstractMojo {

  @Parameter(
      property = "jacksonmixinpojo.outputDirectory",
      defaultValue = "${project.build.directory}/generated-sources/jacksonmixinpojo")
  private File outputDirectory;

  @Parameter(defaultValue = "${project.groupId}", readonly = true, required = true)
  private String groupId;

  @Parameter(defaultValue = "${project.artifactId}", readonly = true, required = true)
  private String artifactId;

  @Parameter(
      property = "jacksonmixinpojo.outputResources",
      defaultValue = "${project.build.directory}/generated-resources")
  private File outputResources;

  @Parameter(property = "jacksonmixinpojo.srcPackage")
  private String srcPackage;

  @Parameter(property = "jacksonmixinpojo.targetPackage")
  private String targetPackage;

  private static final String MIXIN_METHOD = "setMixInAnnotation";
  private static final String ADD_PROPERTIES_METHOD = "getAdditionalProperties";
  private static final String SETUP_METHOD = "setupModule";
  private JCodeModel codeModel;
  private JPackage rootPackage;
  private JMethod setupMethod;
  private ReflectNativeFileHandler nativeHandler;

  @FunctionalInterface
  interface AnnotationProcessor {
    void accept(ClassInfo classInfo, JDefinedClass definedClass)
        throws JClassAlreadyExistsException;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try (ScanResult result =
        new ClassGraph()
            .enableAnnotationInfo()
            .enableMethodInfo()
            .acceptPackages(srcPackage)
            .scan()) {
      codeModel = new JCodeModel();
      nativeHandler = new ReflectNativeFileHandler(groupId, artifactId);
      rootPackage = codeModel._package(targetPackage);
      setupMethod =
          rootPackage
              ._class("JacksonMixInModule")
              ._extends(SimpleModule.class)
              .method(JMod.PUBLIC, codeModel.VOID, SETUP_METHOD);
      nativeHandler.addClasses(result.getAllClasses().stream().map(c -> c.getName()));
      processAnnotatedClasses(result, ExclusiveUnion.class, this::buildExclusiveUnionMixIn);
      processAnnotatedClasses(result, InclusiveUnion.class, this::buildInclusiveUnionMixIn);
      processAnnotatedClasses(result, AdditionalProperties.class, this::buildAdditionalPropsMixIn);
      processAnnotatedClasses(result, Item.class, this::buildItemMixIn);
      processAnnotatedClasses(result.getAllEnums(), this::buildEnumMixIn);
      setupMethod
          .body()
          .invoke(JExpr._super(), SETUP_METHOD)
          .arg(setupMethod.param(SetupContext.class, "context"));
      Files.createDirectories(outputDirectory.toPath());
      codeModel.build(outputDirectory, (PrintStream) null);

      nativeHandler.generate(outputResources.toPath());
    } catch (JClassAlreadyExistsException | IOException e) {
      getLog().error(e);
    }
  }

  private void processAnnotatedClasses(
      Iterable<ClassInfo> classesInfo, AnnotationProcessor processor)
      throws JClassAlreadyExistsException {
    for (ClassInfo classInfo : classesInfo) {
      setupMethod
          .body()
          .invoke(JExpr._super(), MIXIN_METHOD)
          .arg(JExpr.dotclass(codeModel.ref(classInfo.getName())))
          .arg(processAnnotatedClass(classInfo, processor));
    }
  }

  private void processAnnotatedClasses(
      ScanResult result, Class<? extends Annotation> annotation, AnnotationProcessor processor)
      throws JClassAlreadyExistsException {
    processAnnotatedClasses(result.getClassesWithAnnotation(annotation), processor);
  }

  private JExpression processAnnotatedClass(ClassInfo classInfo, AnnotationProcessor processor)
      throws JClassAlreadyExistsException {
    JDefinedClass result =
        rootPackage._class(JMod.ABSTRACT | JMod.PUBLIC, classInfo.getSimpleName() + "MixIn");
    nativeHandler.addClass(result);
    processor.accept(classInfo, result);
    return JExpr.dotclass(result);
  }

  private void buildAdditionalPropsMixIn(
      ClassInfo addPropsClassInfo, JDefinedClass addPropsMixClass) {
    JClass mapStringObject =
        getReturnType(addPropsClassInfo.getMethodInfo().getSingleMethod(ADD_PROPERTIES_METHOD));
    JClass objectType = mapStringObject.getTypeParameters().get(1);
    addPropsMixClass
        .method(JMod.ABSTRACT, mapStringObject, ADD_PROPERTIES_METHOD)
        .annotate(JsonAnyGetter.class);
    addPropsMixClass
        .field(JMod.NONE, mapStringObject, "additionalProperties")
        .annotate(JsonIgnore.class);
    JMethod setter =
        addPropsMixClass.method(JMod.ABSTRACT, codeModel.VOID, "setAdditionalProperty");
    setter.param(String.class, "name");
    setter.param(objectType, "value");
    setter.annotate(JsonAnySetter.class);
  }

  private void buildItemMixIn(ClassInfo classInfo, JDefinedClass mixClass)
      throws JClassAlreadyExistsException {
    JClass relClass = codeModel.ref(classInfo.getName());
    MethodInfo keyMethod =
        classInfo.getMethodInfo().filter(m -> m.hasAnnotation(ItemKey.class)).get(0);
    MethodInfo valueMethod =
        classInfo.getMethodInfo().filter(m -> m.hasAnnotation(ItemValue.class)).get(0);
    JDefinedClass serializerClass =
        GeneratorUtils.generateSerializer(
            rootPackage, relClass, keyMethod.getName(), valueMethod.getName());
    nativeHandler.addClass(serializerClass);
    mixClass.annotate(JsonSerialize.class).param("using", serializerClass);
    JDefinedClass deserializerClass =
        GeneratorUtils.generateDeserializer(rootPackage, relClass, getReturnType(valueMethod));
    nativeHandler.addClass(deserializerClass);
    mixClass.annotate(JsonDeserialize.class).param("using", deserializerClass);
  }

  private void buildExclusiveUnionMixIn(ClassInfo unionClassInfo, JDefinedClass unionMixClass)
      throws JClassAlreadyExistsException {
    JClass unionClass = codeModel.ref(unionClassInfo.getName());
    JDefinedClass serializerClass = GeneratorUtils.generateSerializer(rootPackage, unionClass);
    unionMixClass.annotate(JsonSerialize.class).param("using", serializerClass);
    nativeHandler.addClass(serializerClass);
    JDefinedClass deserializerClass =
        GeneratorUtils.generateDeserializer(
            rootPackage, unionClass, getUnionClasses(ExclusiveUnion.class, unionClassInfo));
    unionMixClass.annotate(JsonDeserialize.class).param("using", deserializerClass);
    nativeHandler.addClass(deserializerClass);
  }

  private void buildInclusiveUnionMixIn(ClassInfo unionClassInfo, JDefinedClass unionMixClass)
      throws JClassAlreadyExistsException {
    Collection<JClass> unionClasses = getUnionClasses(InclusiveUnion.class, unionClassInfo);
    for (MethodInfo methodInfo : unionClassInfo.getMethodInfo()) {
      JClass typeClass = getReturnType(methodInfo);
      if (unionClasses.contains(typeClass)) {
        JMethod method = unionMixClass.method(JMod.ABSTRACT, typeClass, methodInfo.getName());
        method.annotate(JsonUnwrapped.class);
        method.annotate(JsonIgnoreProperties.class).param("ignoreUnknown", true);
      }
    }
  }

  private void buildEnumMixIn(ClassInfo classInfo, JDefinedClass mixClass)
      throws JClassAlreadyExistsException {
    mixClass.method(JMod.ABSTRACT, String.class, "value").annotate(JsonValue.class);
    JMethod staticMethod =
        mixClass.method(JMod.STATIC, codeModel.ref(classInfo.getName()), "fromValue");
    staticMethod.param(String.class, "value");
    staticMethod.annotate(JsonCreator.class);
    staticMethod.body()._return(JExpr._null());
  }

  private Collection<JClass> getUnionClasses(
      Class<? extends Annotation> annotation, ClassInfo unionClassInfo) {
    AnnotationInfo info = unionClassInfo.getAnnotationInfoRepeatable(annotation).get(0);
    Object[] unionClasses = (Object[]) info.getParameterValues().getValue("value");
    return Stream.of(unionClasses)
        .map(AnnotationClassRef.class::cast)
        .map(ref -> codeModel.ref(ref.getName()))
        .collect(Collectors.toList());
  }

  private JClass getReturnType(MethodInfo info) {
    return getReturnType(info.getTypeSignatureOrTypeDescriptor().getResultType());
  }

  private JClass getReturnType(ClassRefTypeSignature refTypeSignature) {
    JClass result = codeModel.ref(refTypeSignature.getFullyQualifiedClassName());
    for (TypeArgument t : refTypeSignature.getTypeArguments())
      result = result.narrow(getReturnType(t.getTypeSignature()));
    return result;
  }

  private JClass getReturnType(TypeSignature t) {
    return t instanceof ClassRefTypeSignature refTypeSignature
        ? getReturnType(refTypeSignature)
        : codeModel.ref(t.toString());
  }
}
