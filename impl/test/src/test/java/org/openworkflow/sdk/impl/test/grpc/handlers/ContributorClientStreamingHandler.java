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
package org.openworkflow.sdk.impl.test.grpc.handlers;

import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.openworkflow.sdk.impl.executors.grpc.contributors.ClientStreamingGrpc.ClientStreamingImplBase;
import org.openworkflow.sdk.impl.executors.grpc.contributors.Contributors;

public class ContributorClientStreamingHandler extends ClientStreamingImplBase {

  private static final Map<String, LongAdder> GITHUBS = new ConcurrentHashMap<>();

  @Override
  public StreamObserver<Contributors.AddContributionRequest> createContributor(
      StreamObserver<Contributors.AddContributionResponse> responseObserver) {

    return new StreamObserver<>() {
      @Override
      public void onNext(Contributors.AddContributionRequest value) {
        String github = value.getGithub();
        GITHUBS.compute(
            github,
            (key, counter) -> {
              if (counter == null) {
                LongAdder longAdder = new LongAdder();
                longAdder.increment();
                return longAdder;
              }
              counter.increment();
              return counter;
            });
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {
        StringBuilder stringBuilder = new StringBuilder();
        Set<Map.Entry<String, LongAdder>> entries = GITHUBS.entrySet();
        for (Map.Entry<String, LongAdder> entry : entries) {
          stringBuilder
              .append(entry.getKey())
              .append(" has ")
              .append(entry.getValue())
              .append(" contributions");
        }
        responseObserver.onNext(
            Contributors.AddContributionResponse.newBuilder()
                .setMessage(stringBuilder.toString())
                .build());
        responseObserver.onCompleted();
      }
    };
  }
}
