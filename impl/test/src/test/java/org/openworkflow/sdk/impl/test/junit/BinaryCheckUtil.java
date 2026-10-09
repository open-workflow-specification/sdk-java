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
package org.openworkflow.sdk.impl.test.junit;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public final class BinaryCheckUtil {

  private static final ConcurrentMap<String, Boolean> BINARY_CHECKS = new ConcurrentHashMap<>();

  private BinaryCheckUtil() {}

  public static boolean isBinaryAvailable(String... command) {
    return BINARY_CHECKS.computeIfAbsent(
        String.join(" ", command),
        __ -> {
          Process process;
          try {
            process =
                new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
          } catch (IOException e) {
            return false;
          }
          try {
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
          } finally {
            process.destroyForcibly();
          }
        });
  }
}
