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
package org.openworkflow.sdk.impl.executors.grpc;

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ProtocolStringList;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openworkflow.sdk.impl.resources.ExternalResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FileDescriptorReader {

  private static final Logger logger = LoggerFactory.getLogger(FileDescriptorReader.class);

  static FileDescriptor readDescriptor(ExternalResourceHandler externalResourceHandler) {
    Path grpcDir = null;
    try (InputStream inputStream = externalResourceHandler.open()) {
      grpcDir = Files.createTempDirectory("serverless-workflow-");
      Files.createDirectories(grpcDir);
      String name = Path.of(externalResourceHandler.name()).getFileName().toString();
      Path protoFile = grpcDir.resolve(name);
      Files.copy(inputStream, protoFile);
      Path descriptorOutput = grpcDir.resolve("descriptor.protobin");
      generateFileDescriptor(grpcDir, protoFile, descriptorOutput);
      return toFileDescriptor(
          DescriptorProtos.FileDescriptorSet.newBuilder()
              .mergeFrom(Files.readAllBytes(descriptorOutput))
              .build(),
          name);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to process gRPC proto file associated with resource: "
              + externalResourceHandler.name(),
          e);
    } finally {
      if (grpcDir != null) {
        deleteTempFiles(grpcDir);
      }
    }
  }

  private static FileDescriptor toFileDescriptor(FileDescriptorSet set, String name) {
    List<FileDescriptorProto> remainingProtos = new ArrayList<>(set.getFileList());
    Map<String, FileDescriptor> builtDescriptors = new HashMap<>();
    while (!remainingProtos.isEmpty()) {
      Iterator<FileDescriptorProto> iterator = remainingProtos.iterator();
      boolean modified = false;
      while (iterator.hasNext()) {
        FileDescriptorProto proto = iterator.next();
        ProtocolStringList requiredDependencies = proto.getDependencyList();
        List<FileDescriptor> currentDependencies =
            requiredDependencies.stream()
                .map(builtDescriptors::get)
                .filter(Objects::nonNull)
                .toList();
        if (requiredDependencies.size() == currentDependencies.size()) {
          FileDescriptor fileDescriptor = buildFileDescriptor(proto, currentDependencies);
          String protoName = proto.getName();
          if (protoName.equals(name)) {
            return fileDescriptor;
          }
          builtDescriptors.put(protoName, fileDescriptor);
          iterator.remove();
          modified = true;
        }
      }
      if (!modified) {
        throw new IllegalStateException(
            "Unable to build valid gRPC descriptor from proto file associated with resource: "
                + name
                + ". There are missing or circular dependencies");
      }
    }
    throw new IllegalStateException("Cannot build FileDescriptor for name: " + name);
  }

  private static FileDescriptor buildFileDescriptor(
      FileDescriptorProto proto, List<FileDescriptor> currentDependencies) {
    try {
      return FileDescriptor.buildFrom(
          proto, currentDependencies.toArray(new FileDescriptor[currentDependencies.size()]));
    } catch (DescriptorValidationException e) {
      throw new IllegalStateException(
          "Unable to build valid gRPC descriptor from proto file associated with resource: "
              + proto.getName(),
          e);
    }
  }

  private static void deleteTempFiles(Path grpcDir) {
    try {
      Files.walkFileTree(
          grpcDir,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              if (exc != null) {
                throw exc;
              }
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      logger.warn("Error deleting GRPC temp dir " + grpcDir, e);
    }
  }

  /*
   * Calls protoc binary with <code>--descriptor_set_out=</code> option set. First attempts to use
   * the embedded protoc from protoc-jar library. If that fails with FileNotFoundException
   * (unsupported architecture), falls back to using the system's installed protoc via
   * ProcessBuilder.
   */
  private static void generateFileDescriptor(Path grpcDir, Path protoFile, Path descriptorOutput)
      throws IOException {
    String[] protocArgs =
        new String[] {
          "--include_imports",
          "--descriptor_set_out=" + descriptorOutput.toAbsolutePath(),
          "-I",
          grpcDir.toAbsolutePath().toString(),
          protoFile.toAbsolutePath().toString()
        };

    try {
      // First attempt: use protoc-jar library
      int status = Protoc.runProtoc(protocArgs);
      if (status != 0) {
        throw new IOException(
            "Unable to generate file descriptor, 'protoc' execution failed with status " + status);
      }
    } catch (FileNotFoundException e) {
      // Fallback: try using system's installed protoc
      logger.warn(
          "Protoc binary not available for this architecture via protoc-jar. "
              + "Attempting to use system-installed protoc...");
      generateFileDescriptorWithProcessBuilder(protocArgs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Thread interrupted while generating proto descriptor", e);
    }
  }

  /*
   * Fallback method to generate file descriptor using system's installed protoc via ProcessBuilder.
   */
  private static void generateFileDescriptorWithProcessBuilder(String[] protocArgs)
      throws IOException {
    try {
      String[] command = new String[protocArgs.length + 1];
      command[0] = "protoc";
      System.arraycopy(protocArgs, 0, command, 1, protocArgs.length);
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(
            "Unable to generate file descriptor using system protoc. Exit code: " + exitCode);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Protoc execution was interrupted", e);
    }
  }

  private FileDescriptorReader() {}
}
