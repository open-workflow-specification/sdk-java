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
package cloud.openworkflow.sdk.common.mcp;

import java.nio.file.Path;

/** Starts {@link StdioPeer} as a child and, like many launchers, does not forward termination. */
public class StdioLauncher {
  public static void main(String[] args) throws Exception {
    var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    new ProcessBuilder(
            java, "-cp", System.getProperty("java.class.path"), StdioPeer.class.getName(), args[0])
        .inheritIO()
        .start()
        .waitFor();
  }
}
