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
package org.openworkflow.sdk.impl.auth;

import static org.openworkflow.sdk.impl.WorkflowUtils.checkSecret;
import static org.openworkflow.sdk.impl.WorkflowUtils.secretProp;
import static org.openworkflow.sdk.impl.auth.AuthUtils.PASSWORD;
import static org.openworkflow.sdk.impl.auth.AuthUtils.USER;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openworkflow.sdk.api.types.DigestAuthenticationPolicy;
import org.openworkflow.sdk.api.types.DigestAuthenticationProperties;
import org.openworkflow.sdk.api.types.Workflow;
import org.openworkflow.sdk.impl.TaskContext;
import org.openworkflow.sdk.impl.WorkflowApplication;
import org.openworkflow.sdk.impl.WorkflowContext;
import org.openworkflow.sdk.impl.WorkflowModel;
import org.openworkflow.sdk.impl.WorkflowUtils;
import org.openworkflow.sdk.impl.WorkflowValueResolver;
import org.openworkflow.sdk.impl.utils.RandomFactory;

class DigestAuthProvider implements AuthProvider {

  private static final String NONCE = "nonce";
  private static final String REALM = "realm";
  private static final String QOP_KEY = "qop";
  private static final String OPAQUE = "opaque";
  private static final String DIGEST = "Digest";
  private static final AtomicInteger nc = new AtomicInteger(1);

  private static class DigestServerInfo {

    private Algorithm algorithm = Algorithm.MD5;
    private String nonce;
    private String opaque;
    private String realm;
    private Optional<QOP> qop = Optional.empty();

    private static final Pattern pattern = Pattern.compile("([A-Za-z]+)=\\\"([^\\\"]*)\\\",?");

    public static DigestServerInfo from(String header) {
      if (header == null || !header.startsWith(DIGEST)) {
        throw new IllegalArgumentException("Invalid WWW-Authenticate header content: " + header);
      }
      DigestServerInfo serverInfo = new DigestServerInfo();
      Matcher m = pattern.matcher(header.substring(DIGEST.length()).trim());
      while (m.find()) {
        String key = m.group(1);
        String value = m.group(2).trim();
        switch (key) {
          case "algorithm":
            serverInfo.algorithm = Algorithm.valueOf(value.toUpperCase());
            break;
          case NONCE:
            serverInfo.nonce = value;
            break;
          case OPAQUE:
            serverInfo.opaque = value;
            break;
          case REALM:
            serverInfo.realm = value;
            break;
          case QOP_KEY:
            StringTokenizer qopTokenizer = new StringTokenizer(value, ",");
            while (qopTokenizer.hasMoreElements()) {
              try {
                serverInfo.qop = Optional.of(QOP.valueOf(qopTokenizer.nextToken().toUpperCase()));
                break;
              } catch (IllegalArgumentException ex) {
                // search for next valid protocol
              }
            }
            break;
        }
      }

      return serverInfo;
    }
  }

  private static enum Algorithm {
    MD5,
    MD5SESSS
  };

  private static enum QOP {
    AUTH,
    AUTH_INT,
  };

  private static final String HEX = "%08x";
  private final WorkflowValueResolver<String> userFilter;
  private final WorkflowValueResolver<String> passwordFilter;
  private final String method;
  private final Random random = RandomFactory.getRandom();

  public DigestAuthProvider(
      WorkflowApplication app,
      Workflow workflow,
      DigestAuthenticationPolicy authPolicy,
      String method) {
    DigestAuthenticationProperties properties =
        authPolicy.getDigest().getDigestAuthenticationProperties();
    if (properties != null) {
      userFilter = WorkflowUtils.buildStringFilter(app, properties.getUsername());
      passwordFilter = WorkflowUtils.buildStringFilter(app, properties.getPassword());
    } else if (authPolicy.getDigest().getDigestAuthenticationPolicySecret() != null) {
      String secretName =
          checkSecret(workflow, authPolicy.getDigest().getDigestAuthenticationPolicySecret());
      userFilter = (w, t, m) -> secretProp(w, secretName, USER);
      passwordFilter = (w, t, m) -> secretProp(w, secretName, PASSWORD);
    } else {
      throw new IllegalStateException(
          "Both secret and properties are null for digest authorization");
    }
    this.method = method;
  }

  @Override
  public String scheme() {
    return DIGEST;
  }

  @Override
  public CompletableFuture<String> content(
      WorkflowContext workflow, TaskContext task, WorkflowModel model, URI uri) {
    try {
      HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setRequestMethod(method);
      int responseCode = connection.getResponseCode();
      if (responseCode == 401) {
        DigestServerInfo serverInfo =
            DigestServerInfo.from(connection.getHeaderField("WWW-Authenticate"));
        String userName = userFilter.apply(workflow, task, model);
        String path = uri.getPath();
        String nonceCount;
        String clientNonce;
        if (serverInfo.qop.isPresent() || serverInfo.algorithm == Algorithm.MD5SESSS) {
          nonceCount = String.format(HEX, nc.getAndIncrement());
          clientNonce = String.format(HEX, random.nextInt());
        } else {
          nonceCount = null;
          clientNonce = null;
        }
        final String hash1 =
            calculateHash(userName, serverInfo.realm, passwordFilter.apply(workflow, task, model));
        final String ha1 =
            serverInfo.algorithm == Algorithm.MD5SESSS
                ? calculateHash(hash1, serverInfo.nonce, clientNonce)
                : hash1;
        final String ha2 = calculateHash(method, path);
        String response =
            serverInfo
                .qop
                .map(
                    qop ->
                        calculateHash(
                            ha1,
                            serverInfo.nonce,
                            nonceCount,
                            clientNonce,
                            qop.toString().toLowerCase(),
                            ha2))
                .orElseGet(() -> calculateHash(ha1, serverInfo.nonce, ha2));

        return CompletableFuture.completedFuture(
            buildResponseInfo(serverInfo, userName, path, clientNonce, nonceCount, response));
      } else {
        return CompletableFuture.failedFuture(
            new IllegalStateException(
                "URI "
                    + uri
                    + " is not digest protected, it returned code "
                    + responseCode
                    + " when invoked without authentication header, but it should have returned 401 as per RFC 2617"));
      }
    } catch (IOException io) {
      return CompletableFuture.failedFuture(io);
    }
  }

  private String buildResponseInfo(
      DigestServerInfo digestInfo,
      String userName,
      String uri,
      String clientNonce,
      String nonceCount,
      String response) {
    StringBuilder sb = new StringBuilder("username=\"" + userName + "\"");
    addHeader(sb, REALM, digestInfo.realm);
    addHeader(sb, NONCE, digestInfo.nonce);
    addHeader(sb, "uri", uri);
    digestInfo.qop.ifPresent(qop -> addUnquotedHeader(sb, QOP_KEY, qop.toString().toLowerCase()));
    if (clientNonce != null) {
      addUnquotedHeader(sb, "nc", nonceCount);
      addHeader(sb, "cnonce", clientNonce);
    }
    addHeader(sb, "response", response);
    if (digestInfo.opaque != null) {
      addHeader(sb, OPAQUE, digestInfo.opaque);
    }
    return sb.toString();
  }

  private StringBuilder addHeader(StringBuilder sb, String key, String value) {
    return sb.append(',').append(key).append('=').append('"').append(value).append('"');
  }

  private StringBuilder addUnquotedHeader(StringBuilder sb, String key, String value) {
    return sb.append(',').append(key).append('=').append(value);
  }

  private String calculateHash(String firstOne, String... strs) {
    try {
      StringBuilder sb = new StringBuilder(firstOne);
      for (String str : strs) {
        sb.append(':').append(str);
      }
      return printHexBinary(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes()));
    } catch (NoSuchAlgorithmException ex) {
      throw new UnsupportedOperationException("System is not supporting MD5!!!!", ex);
    }
  }

  private static final char[] hexCode = "0123456789abcdef".toCharArray();

  private static String printHexBinary(byte[] data) {
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (byte b : data) {
      sb.append(hexCode[(b >> 4) & 0xF]);
      sb.append(hexCode[(b & 0xF)]);
    }
    return sb.toString();
  }
}
