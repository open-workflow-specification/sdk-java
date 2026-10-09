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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openworkflow.sdk.api.types.Workflow;

/**
 * Utility class for writing Open Workflow definitions to various outputs and formats.
 *
 * <p>This class provides static methods to serialize {@link Workflow} objects to files, streams,
 * writers, byte arrays, or strings in either JSON or YAML format. The format is determined by the
 * {@link WorkflowFormat} parameter or inferred from file extensions.
 */
public class WorkflowWriter {

  /**
   * Writes a {@link Workflow} to the given {@link OutputStream} in the specified format.
   *
   * @param output the output stream to write the workflow to
   * @param workflow the workflow object to serialize
   * @param format the format to use (JSON or YAML)
   * @throws IOException if an I/O error occurs during writing
   */
  public static void writeWorkflow(OutputStream output, Workflow workflow, WorkflowFormat format)
      throws IOException {
    format.mapper().writeValue(output, workflow);
  }

  /**
   * Writes a {@link Workflow} to the given {@link Writer} in the specified format.
   *
   * @param output the writer to write the workflow to
   * @param workflow the workflow object to serialize
   * @param format the format to use (JSON or YAML)
   * @throws IOException if an I/O error occurs during writing
   */
  public static void writeWorkflow(Writer output, Workflow workflow, WorkflowFormat format)
      throws IOException {
    format.mapper().writeValue(output, workflow);
  }

  /**
   * Writes a {@link Workflow} to the specified file path, inferring the format from the file
   * extension.
   *
   * @param output the file path to write the workflow to
   * @param workflow the workflow object to serialize
   * @throws IOException if an I/O error occurs during writing
   */
  public static void writeWorkflow(Path output, Workflow workflow) throws IOException {
    writeWorkflow(output, workflow, WorkflowFormat.fromPath(output));
  }

  /**
   * Writes a {@link Workflow} to the specified file path in the given format.
   *
   * @param output the file path to write the workflow to
   * @param workflow the workflow object to serialize
   * @param format the format to use (JSON or YAML)
   * @throws IOException if an I/O error occurs during writing
   */
  public static void writeWorkflow(Path output, Workflow workflow, WorkflowFormat format)
      throws IOException {
    try (OutputStream out = Files.newOutputStream(output)) {
      writeWorkflow(out, workflow, format);
    }
  }

  /**
   * Serializes a {@link Workflow} to a string in the specified format.
   *
   * @param workflow the workflow object to serialize
   * @param format the format to use (JSON or YAML)
   * @return the serialized workflow as a string
   * @throws IOException if an error occurs during serialization
   */
  public static String workflowAsString(Workflow workflow, WorkflowFormat format)
      throws IOException {
    return format.mapper().writeValueAsString(workflow);
  }

  /**
   * Serializes a {@link Workflow} to a byte array in the specified format.
   *
   * @param workflow the workflow object to serialize
   * @param format the format to use (JSON or YAML)
   * @return the serialized workflow as a byte array
   * @throws IOException if an error occurs during serialization
   */
  public static byte[] workflowAsBytes(Workflow workflow, WorkflowFormat format)
      throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      writeWorkflow(out, workflow, format);
      return out.toByteArray();
    }
  }

  // Private constructor to prevent instantiation
  private WorkflowWriter() {}
}
