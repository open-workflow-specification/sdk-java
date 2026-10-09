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
package org.openworkflow.sdk.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openworkflow.sdk.api.types.Workflow;

/** Utility class for reading and parsing Open Workflow definitions from various sources. */
public class WorkflowReader {

  /**
   * Reads a workflow from an {@link InputStream} using the specified format.
   *
   * @param input the input stream containing the workflow definition
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(InputStream input, WorkflowFormat format) throws IOException {
    return defaultReader().read(input, format);
  }

  /**
   * Reads a workflow from a {@link Reader} using the specified format.
   *
   * @param input the reader containing the workflow definition
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(Reader input, WorkflowFormat format) throws IOException {
    return defaultReader().read(input, format);
  }

  /**
   * Reads a workflow from a byte array using the specified format.
   *
   * @param input the byte array containing the workflow definition
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(byte[] input, WorkflowFormat format) throws IOException {
    return defaultReader().read(input, format);
  }

  /**
   * Reads a workflow from a file path, inferring the format from the file extension.
   *
   * @param path the path to the workflow file
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(Path path) throws IOException {
    return readWorkflow(path, WorkflowFormat.fromPath(path), defaultReader());
  }

  /**
   * Reads a workflow from a file path using the specified format.
   *
   * @param path the path to the workflow file
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(Path path, WorkflowFormat format) throws IOException {
    return readWorkflow(path, format, defaultReader());
  }

  /**
   * Reads a workflow from a string using the specified format.
   *
   * @param input the string containing the workflow definition
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflowFromString(String input, WorkflowFormat format)
      throws IOException {
    return defaultReader().read(input, format);
  }

  /**
   * Reads a workflow from the classpath, inferring the format from the file name.
   *
   * @param classpath the classpath location of the workflow file
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflowFromClasspath(String classpath) throws IOException {
    return readWorkflowFromClasspath(classpath, defaultReader());
  }

  /**
   * Reads a workflow from the classpath using the specified class loader and format.
   *
   * @param classpath the classpath location of the workflow file
   * @param cl the class loader to use
   * @param format the workflow format
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflowFromClasspath(
      String classpath, ClassLoader cl, WorkflowFormat format) throws IOException {
    return readWorkflowFromClasspath(classpath, defaultReader());
  }

  /**
   * Reads a workflow from a file path using a custom reader.
   *
   * @param path the path to the workflow file
   * @param reader the custom {@link WorkflowReaderOperations}
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(Path path, WorkflowReaderOperations reader)
      throws IOException {
    return readWorkflow(path, WorkflowFormat.fromPath(path), reader);
  }

  /**
   * Reads a workflow from a file path using the specified format and custom reader.
   *
   * @param path the path to the workflow file
   * @param format the workflow format
   * @param reader the custom {@link WorkflowReaderOperations}
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflow(
      Path path, WorkflowFormat format, WorkflowReaderOperations reader) throws IOException {
    return reader.read(Files.readAllBytes(path), format);
  }

  /**
   * Reads a workflow from the classpath using a custom reader.
   *
   * @param classpath the classpath location of the workflow file
   * @param reader the custom {@link WorkflowReaderOperations}
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs
   */
  public static Workflow readWorkflowFromClasspath(
      String classpath, WorkflowReaderOperations reader) throws IOException {
    return readWorkflowFromClasspath(
        classpath,
        Thread.currentThread().getContextClassLoader(),
        WorkflowFormat.fromFileName(classpath),
        reader);
  }

  /**
   * Reads a workflow from the classpath using the specified class loader, format, and custom
   * reader.
   *
   * @param classpath the classpath location of the workflow file
   * @param cl the class loader to use
   * @param format the workflow format
   * @param reader the custom {@link WorkflowReaderOperations}
   * @return the parsed {@link Workflow}
   * @throws IOException if an I/O error occurs or the resource is not found
   */
  public static Workflow readWorkflowFromClasspath(
      String classpath, ClassLoader cl, WorkflowFormat format, WorkflowReaderOperations reader)
      throws IOException {
    try (InputStream in = cl.getResourceAsStream(classpath)) {
      if (in == null) {
        throw new FileNotFoundException(classpath);
      }
      return reader.read(in, format);
    }
  }

  /**
   * Returns a {@link WorkflowReaderOperations} instance that performs no validation.
   *
   * @return a no-validation reader
   */
  public static WorkflowReaderOperations noValidation() {
    return NoValidationHolder.instance;
  }

  /**
   * Returns a {@link WorkflowReaderOperations} instance that performs validation.
   *
   * @return a validation reader
   */
  public static WorkflowReaderOperations validation() {
    return ValidationHolder.instance;
  }

  private static class NoValidationHolder {
    private static final WorkflowReaderOperations instance = new DirectReader();
  }

  private static class ValidationHolder {
    private static final WorkflowReaderOperations instance = new ValidationReader();
  }

  /**
   * Returns the default {@link WorkflowReaderOperations} instance
   *
   * @return the default reader
   */
  private static WorkflowReaderOperations defaultReader() {
    return ValidationHolder.instance;
  }

  private WorkflowReader() {}
}
