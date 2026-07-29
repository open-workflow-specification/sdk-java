/*
 * Copyright 2020-Present The Serverless Workflow Specification Authors
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
package io.serverlessworkflow.validation.test;

import static io.serverlessworkflow.api.states.DefaultState.Type.OPERATION;
import static io.serverlessworkflow.api.states.DefaultState.Type.SLEEP;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.actions.Action;
import io.serverlessworkflow.api.end.End;
import io.serverlessworkflow.api.error.ErrorDefinition;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.events.EventRef;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.functions.FunctionDefinition.Type;
import io.serverlessworkflow.api.functions.FunctionRef;
import io.serverlessworkflow.api.interfaces.WorkflowValidator;
import io.serverlessworkflow.api.retry.RetryDefinition;
import io.serverlessworkflow.api.start.Start;
import io.serverlessworkflow.api.states.ForEachState;
import io.serverlessworkflow.api.states.InjectState;
import io.serverlessworkflow.api.states.OperationState;
import io.serverlessworkflow.api.states.SleepState;
import io.serverlessworkflow.api.validation.ValidationError;
import io.serverlessworkflow.api.workflow.Errors;
import io.serverlessworkflow.api.workflow.Events;
import io.serverlessworkflow.api.workflow.Functions;
import io.serverlessworkflow.api.workflow.Retries;
import io.serverlessworkflow.validation.WorkflowValidatorImpl;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

public class WorkflowValidationTest {

  @Test
  public void testIncompleteJsonWithSchemaValidation() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator.setSource("{\n" + "  \"id\": \"abc\" \n" + "}").validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(3, validationErrors.size());
  }

  @Test
  public void testIncompleteYamlWithSchemaValidation() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator.setSource("---\n" + "key: abc\n").validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(4, validationErrors.size());
  }

  @Test
  public void testFromIncompleteWorkflow() {
    Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withVersion("1.0")
            .withStart(new Start())
            .withStates(
                Arrays.asList(
                    new SleepState()
                        .withName("sleepState")
                        .withType(SLEEP)
                        .withEnd(new End())
                        .withDuration("PT1M")));

    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors = workflowValidator.setWorkflow(workflow).validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(1, validationErrors.size());
    Assertions.assertEquals(
        "No state name found that matches the workflow start definition",
        validationErrors.get(0).getMessage());
  }

  @Test
  public void testWorkflowMissingStates() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "\t\"id\": \"testwf\",\n"
                    + "\t\"name\": \"test workflow\",\n"
                    + "  \"version\": \"1.0\",\n"
                    + "  \"start\": \"SomeState\",\n"
                    + "  \"states\": []\n"
                    + "}")
            .validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(1, validationErrors.size());

    Assertions.assertEquals("No states found", validationErrors.get(0).getMessage());
  }

  @Test
  public void testWorkflowMissingStatesIdAndKey() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "\t\"name\": \"test workflow\",\n"
                    + "  \"version\": \"1.0\",\n"
                    + "  \"start\": \"SomeState\",\n"
                    + "  \"states\": []\n"
                    + "}")
            .validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(1, validationErrors.size());

    Assertions.assertEquals(
        "required property 'id' not found", validationErrors.get(0).getMessage());
  }

  @Test
  public void testOperationStateNoFunctionRef() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "\"id\": \"checkInbox\",\n"
                    + "\"name\": \"Check Inbox Workflow\",\n"
                    + "\"description\": \"Periodically Check Inbox\",\n"
                    + "\"version\": \"1.0\",\n"
                    + "\"start\": \"CheckInbox\",\n"
                    + "\"functions\": [\n"
                    + "\n"
                    + "],\n"
                    + "\"states\": [\n"
                    + "    {\n"
                    + "        \"name\": \"CheckInbox\",\n"
                    + "        \"type\": \"operation\",\n"
                    + "        \"actionMode\": \"sequential\",\n"
                    + "        \"actions\": [\n"
                    + "            {\n"
                    + "                \"functionRef\": {\n"
                    + "                    \"refName\": \"checkInboxFunction\"\n"
                    + "                }\n"
                    + "            }\n"
                    + "        ],\n"
                    + "        \"transition\": {\n"
                    + "            \"nextState\": \"SendTextForHighPrioriry\"\n"
                    + "        }\n"
                    + "    },\n"
                    + "    {\n"
                    + "        \"name\": \"SendTextForHighPrioriry\",\n"
                    + "        \"type\": \"foreach\",\n"
                    + "        \"inputCollection\": \"${ .message }\",\n"
                    + "        \"iterationParam\": \"${ .singlemessage }\",\n"
                    + "        \"end\": {\n"
                    + "            \"kind\": \"default\"\n"
                    + "        }\n"
                    + "    }\n"
                    + "]\n"
                    + "}")
            .validate();

    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(1, validationErrors.size());

    Assertions.assertEquals(
        "State action 'null' functionRef does not reference an existing workflow function definition",
        validationErrors.get(0).getMessage());
  }

  @Test
  public void testValidateWorkflowForOptionalStartStateAndWorkflowName() {
    Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withVersion("1.0")
            .withStates(
                Arrays.asList(
                    new SleepState()
                        .withName("sleepState")
                        .withType(SLEEP)
                        .withEnd(new End())
                        .withDuration("PT1M")));

    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors = workflowValidator.setWorkflow(workflow).validate();
    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(0, validationErrors.size());
  }

  @Test
  public void testValidateWorkflowForOptionalIterationParam() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "\"id\": \"checkInbox\",\n"
                    + "  \"name\": \"Check Inbox Workflow\",\n"
                    + "\"description\": \"Periodically Check Inbox\",\n"
                    + "\"version\": \"1.0\",\n"
                    + "\"start\": \"CheckInbox\",\n"
                    + "\"functions\": [\n"
                    + "\n"
                    + "],\n"
                    + "\"states\": [\n"
                    + "    {\n"
                    + "        \"name\": \"CheckInbox\",\n"
                    + "        \"type\": \"operation\",\n"
                    + "        \"actionMode\": \"sequential\",\n"
                    + "        \"actions\": [\n"
                    + "            {\n"
                    + "                \"functionRef\": {\n"
                    + "                    \"refName\": \"checkInboxFunction\"\n"
                    + "                }\n"
                    + "            }\n"
                    + "        ],\n"
                    + "        \"transition\": {\n"
                    + "            \"nextState\": \"SendTextForHighPrioriry\"\n"
                    + "        }\n"
                    + "    },\n"
                    + "    {\n"
                    + "        \"name\": \"SendTextForHighPrioriry\",\n"
                    + "        \"type\": \"foreach\",\n"
                    + "        \"inputCollection\": \"${ .message }\",\n"
                    + "        \"end\": {\n"
                    + "            \"kind\": \"default\"\n"
                    + "        }\n"
                    + "    }\n"
                    + "]\n"
                    + "}")
            .validate();

    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(
        1,
        validationErrors.size()); // validation error raised for functionref not for iterationParam
  }

  @Test
  public void testMissingFunctionRefForCallbackState() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "  \"id\": \"callbackstatemissingfuncref\",\n"
                    + "  \"version\": \"1.0\",\n"
                    + "  \"specVersion\": \"0.8\",\n"
                    + "  \"name\": \"Callback State Test\",\n"
                    + "  \"start\": \"CheckCredit\",\n"
                    + "  \"states\": [\n"
                    + "    {\n"
                    + "      \"name\": \"CheckCredit\",\n"
                    + "      \"type\": \"callback\",\n"
                    + "      \"action\": {\n"
                    + "        \"functionRef\": {\n"
                    + "          \"refName\": \"callCreditCheckMicroservice\",\n"
                    + "          \"arguments\": {\n"
                    + "            \"customer\": \"${ .customer }\"\n"
                    + "          }\n"
                    + "        }\n"
                    + "      },\n"
                    + "      \"eventRef\": \"CreditCheckCompletedEvent\",\n"
                    + "      \"timeouts\": {\n"
                    + "        \"stateExecTimeout\": \"PT15M\"\n"
                    + "      },\n"
                    + "      \"end\": true\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}")
            .validate();

    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(2, validationErrors.size());
    Assertions.assertEquals(
        "CallbackState event ref does not reference a defined workflow event definition",
        validationErrors.get(0).getMessage());
    Assertions.assertEquals(
        "CallbackState action function ref does not reference a defined workflow function definition",
        validationErrors.get(1).getMessage());
  }

  @Test
  void testFunctionCall() {
    Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withVersion("1.0")
            .withStart(new Start().withStateName("start"))
            .withFunctions(
                new Functions(
                    Arrays.asList(new FunctionDefinition("expression").withType(Type.EXPRESSION))))
            .withStates(
                Arrays.asList(
                    new OperationState()
                        .withName("start")
                        .withType(OPERATION)
                        .withActions(
                            Arrays.asList(
                                new Action().withFunctionRef(new FunctionRef("expression"))))
                        .withEnd(new End())));
    Assertions.assertTrue(new WorkflowValidatorImpl().setWorkflow(workflow).validate().isEmpty());
  }

  @Test
  void testEventCall() {
    Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withVersion("1.0")
            .withStart(new Start().withStateName("start"))
            .withEvents(new Events(Arrays.asList(new EventDefinition().withName("event"))))
            .withRetries(new Retries(Arrays.asList(new RetryDefinition("start", "PT1S"))))
            .withStates(
                Arrays.asList(
                    new OperationState()
                        .withName("start")
                        .withType(OPERATION)
                        .withActions(
                            Arrays.asList(
                                new Action()
                                    .withEventRef(new EventRef().withTriggerEventRef("event"))))
                        .withEnd(new End())));
    Assertions.assertTrue(new WorkflowValidatorImpl().setWorkflow(workflow).validate().isEmpty());
  }

  /**
   * @see <a href="https://github.com/serverlessworkflow/sdk-java/issues/212">Validation missing out
   *     on refname in foreach>actions</a>
   */
  @Test
  void testActionDefForEach() {
    Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withVersion("1.0")
            .withStart(new Start().withStateName("TestingForEach"))
            .withFunctions(new Functions(Arrays.asList(new FunctionDefinition("Test"))))
            .withStates(
                Arrays.asList(
                    new ForEachState()
                        .withName("TestingForEach")
                        .withInputCollection("${ .archives }")
                        .withIterationParam("archive")
                        .withOutputCollection("${ .output}")
                        .withActions(
                            Arrays.asList(
                                new Action()
                                    .withName("callFn")
                                    .withFunctionRef(new FunctionRef("DoesNotExist"))))
                        .withEnd(new End())));
    final List<ValidationError> validationErrors =
        new WorkflowValidatorImpl().setWorkflow(workflow).validate();
    Assertions.assertEquals(1, validationErrors.size());
    Assertions.assertEquals(
        "State action 'callFn' functionRef does not reference an existing workflow function definition",
        validationErrors.get(0).getMessage());
  }

  /**
   * @see <a href="https://github.com/serverlessworkflow/sdk-java/issues/213">Retry definition
   *     validation doesn't work</a>
   */
  @Test
  public void testValidateRetry() {
    WorkflowValidator workflowValidator = new WorkflowValidatorImpl();
    List<ValidationError> validationErrors =
        workflowValidator
            .setSource(
                "{\n"
                    + "  \"id\": \"workflow_1\",\n"
                    + "  \"name\": \"workflow_1\",\n"
                    + "  \"description\": \"workflow_1\",\n"
                    + "  \"version\": \"1.0\",\n"
                    + "  \"specVersion\": \"0.8\",\n"
                    + "  \"start\": \"Task1\",\n"
                    + "  \"functions\": [\n"
                    + "    {\n"
                    + "      \"name\": \"increment\",\n"
                    + "      \"type\": \"custom\",\n"
                    + "      \"operation\": \"worker\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"retries\": [\n"
                    + "    {\n"
                    + "      \"maxAttempts\": 3\n"
                    + "    },\n"
                    + "    {\n"
                    + "      \"name\": \"testRetry\" \n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"states\": [\n"
                    + "    {\n"
                    + "      \"name\": \"Task1\",\n"
                    + "      \"type\": \"operation\",\n"
                    + "      \"actionMode\": \"sequential\",\n"
                    + "      \"actions\": [\n"
                    + "        {\n"
                    + "          \"functionRef\": {\n"
                    + "            \"refName\": \"increment\",\n"
                    + "            \"arguments\": {\n"
                    + "              \"input\": \"some text\"\n"
                    + "            }\n"
                    + "          },\n"
                    + "          \"retryRef\": \"const\",\n"
                    + "          \"actionDataFilter\": {\n"
                    + "            \"toStateData\": \"${ .result }\"\n"
                    + "          }\n"
                    + "        }\n"
                    + "      ],\n"
                    + "      \"end\": true\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}")
            .validate();

    Assertions.assertNotNull(validationErrors);
    Assertions.assertEquals(2, validationErrors.size());
    Assertions.assertEquals("Retry name should not be empty", validationErrors.get(0).getMessage());
    Assertions.assertEquals(
        "Operation State action 'null' retryRef does not reference an existing workflow retry definition",
        validationErrors.get(1).getMessage());
  }

  /**
   * @see <a href="https://github.com/serverlessworkflow/sdk-java/issues/232">WorkflowValidator
   *     validate Wrokflow.tojson(workflow) failed</a>
   */
  @Test
  void testErrorsArrayParsing() {
    final Workflow workflow =
        new Workflow()
            .withId("test-workflow")
            .withName("test-workflow")
            .withVersion("1.0")
            .withStart(new Start().withStateName("testingErrors"))
            .withErrors(new Errors(Arrays.asList(new ErrorDefinition())))
            .withStates(
                Arrays.asList(
                    new InjectState()
                        .withName("testingErrors")
                        .withData(new ObjectMapper().createObjectNode().put("name", "Skywalker"))
                        .withEnd(new End())));
    Assertions.assertTrue(
        new WorkflowValidatorImpl().setSource(Workflow.toJson(workflow)).isValid());
  }

  /**
   * @see <a href="https://github.com/serverlessworkflow/sdk-java/issues/357">Error parsing Oauth
   *     properties in cncf spec using java sdk</a>
   */
  @Test
  void testOAuthPropertiesDefinition() {
    final Workflow workflow =
        Workflow.fromSource(
            "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"id\": \"greeting-workflow\", \n"
                + "  \"specVersion\": \"0.8\",\n"
                + "  \"name\": \"greeting-workflow\",\n"
                + "  \"description\": \"Greet Someone\",\n"
                + "  \"start\": \"greet\",\n"
                + "  \"auth\": [\n"
                + "    {\n"
                + "      \"name\": \"serviceCloud\",\n"
                + "      \"scheme\": \"oauth2\",\n"
                + "      \"properties\": {\n"
                + "        \"scopes\": [\"$$$$XXXMMMMM\"],\n"
                + "        \"audiences\": [\"%%%XXXXXXX\"],\n"
                + "        \"clientId\": \"whatever\",\n"
                + "        \"grantType\": \"password\"\n"
                + "      }\n"
                + "    }\n"
                + "  ],\n"
                + "  \"functions\": [\n"
                + "    {\n"
                + "      \"name\": \"greeting-function\",\n"
                + "      \"type\": \"rest\",\n"
                + "      \"operation\": \"file://myapis/greetingapis.json#greeting\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"states\": [\n"
                + "    {\n"
                + "      \"name\": \"greet\",\n"
                + "      \"type\": \"operation\",\n"
                + "      \"actions\": [\n"
                + "        {\n"
                + "          \"name\": \"greet-action\",\n"
                + "          \"functionRef\": {\n"
                + "            \"refName\": \"greeting-function\",\n"
                + "            \"arguments\": {\n"
                + "              \"name\": \"${ .person.name }\"\n"
                + "            }\n"
                + "          },\n"
                + "          \"actionDataFilter\": {\n"
                + "            \"results\": \"${ {greeting: .greeting} }\"\n"
                + "          }\n"
                + "        }\n"
                + "      ],\n"
                + "      \"end\": true\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");
    final List<ValidationError> validationErrors =
        new WorkflowValidatorImpl().setWorkflow(workflow).validate();

    Assertions.assertTrue(validationErrors.isEmpty());
  }

  @Test
  public void testKuflowCallbackSwitchWorkflowSchemaValidation() {
    String source =
        """
        {
          "id" : "process-definition-id",
          "name" : "process-definition-name",
          "version" : "1.0",
          "specVersion" : "0.8",
          "start" : "dm-node-8ad9f542-4e61-42f8-af61-ff88f6b9ef96",
          "states" : [ {
            "id" : "dm-node-8ad9f542-4e61-42f8-af61-ff88f6b9ef96",
            "name" : "dm-node-8ad9f542-4e61-42f8-af61-ff88f6b9ef96",
            "type" : "inject",
            "data" : {
              "__kuflowDummy" : true
            },
            "transition" : "dm-node-0e7d2afd-1ecc-40a4-ba98-45c291e91177"
          }, {
            "id" : "dm-node-e0972733-0b72-4f0d-a6e5-372b90063fe2",
            "name" : "dm-node-e0972733-0b72-4f0d-a6e5-372b90063fe2",
            "type" : "inject",
            "data" : {
              "__kuflowDummy" : true
            },
            "end" : true
          }, {
            "id" : "dm-node-0e7d2afd-1ecc-40a4-ba98-45c291e91177",
            "name" : "dm-node-0e7d2afd-1ecc-40a4-ba98-45c291e91177",
            "type" : "callback",
            "action" : {
              "functionRef" : {
                "refName" : "KuFlowCreateTask",
                "arguments" : {
                  "taskCode" : "TASK_001",
                  "owner" : "${ \\"jrodped@kuflow.com\\" }",
                  "elementValues" : { }
                }
              }
            },
            "eventRef" : "KuFlowCreateTaskResponseEvent",
            "transition" : "dm-node-c4381d5e-69ff-462e-b77d-a98080918a0d"
          }, {
            "id" : "dm-node-c4381d5e-69ff-462e-b77d-a98080918a0d",
            "name" : "dm-node-c4381d5e-69ff-462e-b77d-a98080918a0d",
            "type" : "switch",
            "dataConditions" : [ {
              "condition" : "${ .tasks.TASK_001.data.ELEMENT_003 >= (now | todate) }",
              "transition" : "dm-node-e0972733-0b72-4f0d-a6e5-372b90063fe2"
            }, {
              "condition" : "${ .tasks.TASK_001.data.ELEMENT_003 < (now | todate) }",
              "transition" : "dm-node-4fe0124e-7614-45ac-a5fa-4ce39f6912bf"
            } ],
            "defaultCondition" : {
              "transition" : "dm-node-e0972733-0b72-4f0d-a6e5-372b90063fe2"
            }
          }, {
            "id" : "dm-node-4fe0124e-7614-45ac-a5fa-4ce39f6912bf",
            "name" : "dm-node-4fe0124e-7614-45ac-a5fa-4ce39f6912bf",
            "type" : "callback",
            "action" : {
              "functionRef" : {
                "refName" : "KuFlowCreateTask",
                "arguments" : {
                  "taskCode" : "TASK_2",
                  "elementValues" : { }
                }
              }
            },
            "eventRef" : "KuFlowCreateTaskResponseEvent",
            "transition" : "dm-node-0e7d2afd-1ecc-40a4-ba98-45c291e91177"
          } ],
          "functions" : [ {
            "name" : "KuFlowCreateTask",
            "operation" : "https://api.kuflow.com/openapi/specs/api.kuflow.com/v1/openapi.yaml#operation/createTask"
          } ],
          "events" : [ {
            "kind" : "consumed",
            "name" : "KuFlowCreateTaskResponseEvent",
            "type" : "com.kuflow.task",
            "source" : "KuFlowSystem"
          } ],
          "metadata" : {
            "diagram" : "irrelevant",
            "diagramValid" : "true"
          }
        }
        """;

    List<ValidationError> validationErrors = new WorkflowValidatorImpl().setSource(source).validate();

    Assertions.assertTrue(
        validationErrors.isEmpty(),
        () ->
            "Expected no validation errors but got: "
                + validationErrors.stream().map(ValidationError::toString).toList());
  }
}
