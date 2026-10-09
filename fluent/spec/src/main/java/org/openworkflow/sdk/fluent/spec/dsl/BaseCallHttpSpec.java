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
package org.openworkflow.sdk.fluent.spec.dsl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.openworkflow.sdk.fluent.spec.configurers.AuthenticationConfigurer;
import org.openworkflow.sdk.fluent.spec.spi.CallHttpTaskFluent;

public interface BaseCallHttpSpec<SELF extends BaseCallHttpSpec<SELF>> {

  /** Implementors must return the concrete SELF instance (usually {@code return this;}). */
  SELF self();

  /**
   * Internal list of configuration steps that will be replayed against the underlying {@link
   * CallHttpTaskFluent}.
   */
  List<Consumer<CallHttpTaskFluent<?>>> steps();

  default SELF get() {
    steps().add(c -> c.method("GET"));
    return self();
  }

  /**
   * @deprecated Use {@link #get()} instead.
   */
  @Deprecated(since = "7.25.0", forRemoval = true)
  default SELF GET() {
    return get();
  }

  default SELF post() {
    steps().add(c -> c.method("POST"));
    return self();
  }

  /**
   * @deprecated Use {@link #post()} instead.
   */
  @Deprecated(since = "7.25.0", forRemoval = true)
  default SELF POST() {
    return post();
  }

  default SELF acceptJSON() {
    return header("Accept", "application/json");
  }

  default SELF endpoint(String urlExpr) {
    steps().add(b -> b.endpoint(urlExpr));
    return self();
  }

  default SELF endpoint(String urlExpr, AuthenticationConfigurer auth) {
    steps().add(b -> b.endpoint(urlExpr, auth));
    return self();
  }

  default SELF uri(String url) {
    steps().add(b -> b.endpoint(URI.create(url)));
    return self();
  }

  default SELF uri(String url, AuthenticationConfigurer auth) {
    steps().add(b -> b.endpoint(URI.create(url), auth));
    return self();
  }

  default SELF uri(URI uri) {
    steps().add(b -> b.endpoint(uri));
    return self();
  }

  default SELF uri(URI uri, AuthenticationConfigurer auth) {
    steps().add(b -> b.endpoint(uri, auth));
    return self();
  }

  default SELF body(String bodyExpr) {
    steps().add(c -> c.body(bodyExpr));
    return self();
  }

  default SELF body(Map<String, Object> body) {
    steps().add(c -> c.body(body));
    return self();
  }

  default SELF body(Object bodyExpr) {
    steps().add(c -> c.body(bodyExpr));
    return self();
  }

  default SELF method(String method) {
    steps().add(b -> b.method(method));
    return self();
  }

  default SELF header(String name, String value) {
    steps().add(c -> c.headers(h -> h.header(name, value)));
    return self();
  }

  default SELF headers(Map<String, String> headers) {
    steps().add(b -> b.headers(headers));
    return self();
  }

  default SELF query(String queryExpr) {
    steps().add(c -> c.query(queryExpr));
    return self();
  }

  default SELF query(Map<String, String> query) {
    steps().add(c -> c.query(query));
    return self();
  }

  default SELF query(String name, String value) {
    steps().add(c -> c.query(q -> q.query(name, value)));
    return self();
  }

  default SELF put() {
    steps().add(c -> c.method("PUT"));
    return self();
  }

  default SELF delete() {
    steps().add(c -> c.method("DELETE"));
    return self();
  }

  default SELF patch() {
    steps().add(c -> c.method("PATCH"));
    return self();
  }

  default SELF head() {
    steps().add(c -> c.method("HEAD"));
    return self();
  }

  default SELF options() {
    steps().add(c -> c.method("OPTIONS"));
    return self();
  }

  default SELF acceptXML() {
    return header("Accept", "application/xml");
  }

  default SELF acceptForm() {
    return header("Accept", "application/x-www-form-urlencoded");
  }

  default SELF acceptText() {
    return header("Accept", "text/plain");
  }

  default SELF contentTypeJSON() {
    return header("Content-Type", "application/json");
  }

  default SELF contentTypeXML() {
    return header("Content-Type", "application/xml");
  }

  default SELF contentTypeForm() {
    return header("Content-Type", "application/x-www-form-urlencoded");
  }

  default SELF contentTypeText() {
    return header("Content-Type", "text/plain");
  }

  default SELF redirect(boolean redirect) {
    steps().add(c -> c.redirect(redirect));
    return self();
  }

  default void accept(CallHttpTaskFluent<?> b) {
    for (var s : steps()) {
      s.accept(b);
    }
  }
}
