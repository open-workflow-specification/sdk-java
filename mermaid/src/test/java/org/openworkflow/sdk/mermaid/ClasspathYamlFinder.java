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
package org.openworkflow.sdk.mermaid;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ClasspathYamlFinder {
  private ClasspathYamlFinder() {}

  public static List<String> listYamlResources(String base) throws IOException {
    String prefix = normalizeBase(base);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    // Try the requested base (as a directory) first; if not found, fall back to root
    List<URL> bases = Collections.list(cl.getResources(prefix.isEmpty() ? "" : prefix + "/"));
    if (bases.isEmpty() && !prefix.isEmpty()) {
      bases = Collections.list(cl.getResources("")); // fallback
    }

    Set<String> results = new LinkedHashSet<>();
    for (URL url : bases) {
      switch (url.getProtocol()) {
        case "file" -> results.addAll(scanFileUrl(url, prefix));
        case "jar" -> results.addAll(scanJarUrl(url));
        default -> {
          /* ignore */
        }
      }
    }
    return results.stream().sorted().collect(Collectors.toList());
  }

  private static String normalizeBase(String base) {
    if (base == null) return "";
    String b = base.replace('\\', '/');
    if (b.startsWith("/")) b = b.substring(1);
    while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
    return b;
  }

  private static Collection<String> scanFileUrl(URL url, String prefix) throws IOException {
    Path root = Paths.get(URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8));
    if (!Files.exists(root)) return List.of();

    // If we resolved exactly "<classpathRoot>/<prefix>/", we should prepend "prefix/"
    // to the relativized filenames to mirror the JAR behaviour.
    String rootStr = root.normalize().toString().replace('\\', '/');
    boolean rootIsPrefixDir = !prefix.isEmpty() && rootStr.endsWith("/" + prefix);

    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(Files::isRegularFile)
          .filter(
              p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".yaml") || name.endsWith(".yml");
              })
          .map(
              p -> {
                String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                // When scanning the specific prefix directory, add "prefix/" so callers get
                // paths relative to the classpath root, e.g. "workflows-samples/foo.yaml".
                return rootIsPrefixDir ? (prefix + "/" + rel) : rel;
              })
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private static Collection<String> scanJarUrl(URL url) throws IOException {
    JarURLConnection conn = (JarURLConnection) url.openConnection();
    try (JarFile jar = conn.getJarFile()) {
      String dir = ensureDirPrefix(conn.getEntryName());
      List<String> out = new ArrayList<>();
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry je = entries.nextElement();
        if (je.isDirectory()) continue;
        String name = je.getName();
        if (!name.startsWith(dir)) continue;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
          out.add(name);
        }
      }
      return out;
    }
  }

  private static String ensureDirPrefix(String entryName) {
    if (entryName == null) return "";
    String e = entryName;
    if (!e.isEmpty() && !e.endsWith("/")) e = e + "/";
    return e;
  }
}
