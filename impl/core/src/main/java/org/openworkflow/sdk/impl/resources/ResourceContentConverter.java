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
package org.openworkflow.sdk.impl.resources;

import java.util.function.BiFunction;

/**
 * A converter that converts the content of a URI to a specific type. The converter takes the name
 * of the file name associated to the resource plus the byte[] contained in the resource, and
 * returns an object of type T.
 *
 * @param <T>
 */
@FunctionalInterface
public interface ResourceContentConverter<T> extends BiFunction<String, byte[], T> {}
