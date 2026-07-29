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
package io.serverlessworkflow.api.test;

import static org.junit.jupiter.api.Assertions.*;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.actions.Action;
import io.serverlessworkflow.api.auth.AuthDefinition;
import io.serverlessworkflow.api.branches.Branch;
import io.serverlessworkflow.api.defaultdef.DefaultConditionDefinition;
import io.serverlessworkflow.api.end.End;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.events.EventRef;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.functions.FunctionRef;
import io.serverlessworkflow.api.functions.SubFlowRef;
import io.serverlessworkflow.api.interfaces.State;
import io.serverlessworkflow.api.retry.RetryDefinition;
import io.serverlessworkflow.api.states.CallbackState;
import io.serverlessworkflow.api.states.EventState;
import io.serverlessworkflow.api.states.InjectState;
import io.serverlessworkflow.api.states.OperationState;
import io.serverlessworkflow.api.states.ParallelState;
import io.serverlessworkflow.api.states.SwitchState;
import io.serverlessworkflow.api.switchconditions.DataCondition;
import io.serverlessworkflow.api.test.utils.WorkflowTestUtils;
import io.serverlessworkflow.api.timeouts.WorkflowExecTimeout;
import io.serverlessworkflow.api.workflow.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

public class MarkupToWorkflowTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/examples/applicantrequest.json",
        "/examples/applicantrequest.yml",
        "/examples/carauctionbids.json",
        "/examples/carauctionbids.yml",
        "/examples/creditcheck.json",
        "/examples/creditcheck.yml",
        "/examples/eventbasedgreeting.json",
        "/examples/eventbasedgreeting.yml",
        "/examples/finalizecollegeapplication.json",
        "/examples/finalizecollegeapplication.yml",
        "/examples/greeting.json",
        "/examples/greeting.yml",
        "/examples/helloworld.json",
        "/examples/helloworld.yml",
        "/examples/jobmonitoring.json",
        "/examples/jobmonitoring.yml",
        "/examples/monitorpatient.json",
        "/examples/monitorpatient.yml",
        "/examples/parallel.json",
        "/examples/parallel.yml",
        "/examples/provisionorder.json",
        "/examples/provisionorder.yml",
        "/examples/sendcloudevent.json",
        "/examples/sendcloudevent.yml",
        "/examples/solvemathproblems.json",
        "/examples/solvemathproblems.yml",
        "/examples/foreachstatewithactions.json",
        "/examples/foreachstatewithactions.yml",
        "/examples/periodicinboxcheck.json",
        "/examples/periodicinboxcheck.yml",
        "/examples/vetappointmentservice.json",
        "/examples/vetappointmentservice.yml",
        "/examples/eventbasedtransition.json",
        "/examples/eventbasedtransition.yml",
        "/examples/roomreadings.json",
        "/examples/roomreadings.yml",
        "/examples/checkcarvitals.json",
        "/examples/checkcarvitals.yml",
        "/examples/booklending.json",
        "/examples/booklending.yml"
      })
  public void testSpecExamplesParsing(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.getStates().size() > 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/applicantrequest.json", "/features/applicantrequest.yml"})
  public void testSpecFeatureFunctionRef(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNull(workflow.getKey());
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.getStates().size() > 0);

    assertNotNull(workflow.getFunctions());
    assertEquals(1, workflow.getFunctions().getFunctionDefs().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/features/applicantrequest-with-key.json",
        "/features/applicantrequest-with-key.yml"
      })
  public void testSpecFeatureFunctionRefWithKey(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertEquals("applicant-key-request", workflow.getKey());
    assertNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.getStates().size() > 0);

    assertNotNull(workflow.getFunctions());
    assertEquals(1, workflow.getFunctions().getFunctionDefs().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/features/applicantrequest-with-id-and-key.json",
        "/features/applicantrequest-with-id-and-key.yml"
      })
  public void testSpecFeatureFunctionRefWithIdAndKey(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertEquals("applicant-key-request", workflow.getKey());
    assertEquals("applicant-with-key-and-id", workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.getStates().size() > 0);

    assertNotNull(workflow.getFunctions());
    assertEquals(1, workflow.getFunctions().getFunctionDefs().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/vetappointment.json", "/features/vetappointment.yml"})
  public void testSpecFreatureEventRef(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.getStates().size() > 0);

    assertNotNull(workflow.getEvents());
    assertEquals(2, workflow.getEvents().getEventDefs().size());

    assertNotNull(workflow.getRetries());
    assertEquals(1, workflow.getRetries().getRetryDefs().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/features/compensationworkflow.json", "/features/compensationworkflow.yml"})
  public void testSpecFreatureCompensation(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(2, workflow.getStates().size());

    State firstState = workflow.getStates().get(0);
    assertTrue(firstState instanceof EventState);
    assertNotNull(firstState.getCompensatedBy());
    assertEquals("CancelPurchase", firstState.getCompensatedBy());

    State secondState = workflow.getStates().get(1);
    assertTrue(secondState instanceof OperationState);
    OperationState operationState = (OperationState) secondState;

    assertTrue(operationState.isUsedForCompensation());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/functiontypes.json", "/features/functiontypes.yml"})
  public void testFunctionTypes(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    State state = workflow.getStates().get(0);
    assertTrue(state instanceof OperationState);

    List<FunctionDefinition> functionDefs = workflow.getFunctions().getFunctionDefs();
    assertNotNull(functionDefs);
    assertEquals(2, functionDefs.size());

    FunctionDefinition restFunc = functionDefs.get(0);
    assertEquals(restFunc.getType(), FunctionDefinition.Type.REST);

    FunctionDefinition restFunc2 = functionDefs.get(1);
    assertEquals(restFunc2.getType(), FunctionDefinition.Type.EXPRESSION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/transitions.json", "/features/transitions.yml"})
  public void testTransitions(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    State state = workflow.getStates().get(0);
    assertTrue(state instanceof SwitchState);

    SwitchState switchState = (SwitchState) workflow.getStates().get(0);
    assertNotNull(switchState.getDataConditions());
    List<DataCondition> dataConditions = switchState.getDataConditions();

    assertEquals(2, dataConditions.size());

    DataCondition cond1 = switchState.getDataConditions().get(0);
    assertNotNull(cond1.getTransition());
    assertEquals("StartApplication", cond1.getTransition().getNextState());
    assertNotNull(cond1.getTransition().getProduceEvents());
    assertTrue(cond1.getTransition().getProduceEvents().isEmpty());
    assertFalse(cond1.getTransition().isCompensate());

    DataCondition cond2 = switchState.getDataConditions().get(1);
    assertNotNull(cond2.getTransition());
    assertEquals("RejectApplication", cond2.getTransition().getNextState());
    assertNotNull(cond2.getTransition().getProduceEvents());
    assertEquals(1, cond2.getTransition().getProduceEvents().size());
    assertNotNull(cond2.getTransition().getProduceEvents().get(0).getContextAttributes());
    Map<String, String> contextAttributes =
        cond2.getTransition().getProduceEvents().get(0).getContextAttributes();
    assertEquals(2, contextAttributes.size());
    assertEquals("IN", contextAttributes.get("order_location"));
    assertEquals("online", contextAttributes.get("order_type"));
    assertFalse(cond2.getTransition().isCompensate());

    assertNotNull(switchState.getDefaultCondition());
    DefaultConditionDefinition defaultDefinition = switchState.getDefaultCondition();
    assertNotNull(defaultDefinition.getTransition());
    assertEquals("RejectApplication", defaultDefinition.getTransition().getNextState());
    assertNull(defaultDefinition.getTransition().getProduceEvents());
    assertTrue(defaultDefinition.getTransition().isCompensate());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/functionrefs.json", "/features/functionrefs.yml"})
  public void testFunctionRefs(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    State state = workflow.getStates().get(0);
    assertTrue(state instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getActions());
    assertEquals(2, operationState.getActions().size());

    Action action1 = operationState.getActions().get(0);
    assertNotNull(action1);
    assertNotNull(action1.getFunctionRef());
    FunctionRef functionRef1 = action1.getFunctionRef();
    assertEquals("creditCheckFunction", functionRef1.getRefName());
    assertNull(functionRef1.getArguments());

    Action action2 = operationState.getActions().get(1);
    assertNotNull(action2);
    assertNotNull(action2.getFunctionRef());
    FunctionRef functionRef2 = action2.getFunctionRef();
    assertEquals("sendRejectionEmailFunction", functionRef2.getRefName());
    assertEquals(1, functionRef2.getArguments().size());
    assertEquals("${ .customer }", functionRef2.getArguments().get("applicant").asString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/features/keepactiveexectimeout.json", "/features/keepactiveexectimeout.yml"})
  public void testKeepActiveExecTimeout(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertTrue(workflow.isKeepActive());
    assertNotNull(workflow.getTimeouts());
    assertNotNull(workflow.getTimeouts().getWorkflowExecTimeout());

    WorkflowExecTimeout execTimeout = workflow.getTimeouts().getWorkflowExecTimeout();
    assertEquals("PT1H", execTimeout.getDuration());
    assertEquals("GenerateReport", execTimeout.getRunBefore());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/features/functionrefjsonparams.json", "/features/functionrefjsonparams.yml"})
  public void testFunctionRefJsonParams(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());
    assertTrue(workflow.getStates().get(0) instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getActions());
    assertEquals(1, operationState.getActions().size());
    List<Action> actions = operationState.getActions();
    assertNotNull(actions.get(0).getFunctionRef());
    assertEquals("addPet", actions.get(0).getFunctionRef().getRefName());

    JsonNode params = actions.get(0).getFunctionRef().getArguments();
    assertNotNull(params);
    assertEquals(4, params.size());
    assertEquals(123, params.get("id").intValue());
    assertEquals("My Address, 123 MyCity, MyCountry", params.get("address").asString());
    assertEquals("${ .owner.name }", params.get("owner").asString());
    assertEquals("Pluto", params.get("body").get("name").asString());
    assertEquals("${ .pet.tagnumber }", params.get("body").get("tag").asString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/features/functionrefnoparams.json", "/features/functionrefnoparams.yml"})
  public void testFunctionRefNoParams(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());
    assertTrue(workflow.getStates().get(0) instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getActions());
    assertEquals(2, operationState.getActions().size());
    List<Action> actions = operationState.getActions();
    assertNotNull(actions.get(0).getFunctionRef());
    assertNotNull(actions.get(1).getFunctionRef());
    assertEquals("addPet", actions.get(0).getFunctionRef().getRefName());
    assertEquals("addPet", actions.get(1).getFunctionRef().getRefName());

    JsonNode params = actions.get(0).getFunctionRef().getArguments();
    assertNull(params);
    JsonNode params2 = actions.get(1).getFunctionRef().getArguments();
    assertNull(params2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/simpleschedule.json", "/features/simpleschedule.yml"})
  public void testSimplifiedSchedule(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);

    assertNotNull(workflow.getStart());
    assertNotNull(workflow.getStart().getSchedule());

    assertEquals(
        "2020-03-20T09:00:00Z/2020-03-20T15:00:00Z",
        workflow.getStart().getSchedule().getInterval());

    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/simplecron.json", "/features/simplecron.yml"})
  public void testSimplifiedCron(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);

    assertNotNull(workflow.getStart());
    assertNotNull(workflow.getStart().getSchedule());

    assertEquals("0 0/15 * * * ?", workflow.getStart().getSchedule().getCron().getExpression());

    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(2, workflow.getStates().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/expressionlang.json", "/features/expressionlang.yml"})
  public void testExpressionLang(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getExpressionLang());
    assertEquals("abc", workflow.getExpressionLang());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/shortstart.json", "/features/shortstart.yml"})
  public void testShortStart(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStart());
    assertEquals("TestFunctionRefs", workflow.getStart().getStateName());
    assertNull(workflow.getStart().getSchedule());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/longstart.json", "/features/longstart.yml"})
  public void testLongStart(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStart());
    assertEquals("TestFunctionRefs", workflow.getStart().getStateName());
    assertNotNull(workflow.getStart().getSchedule());
    assertNotNull(workflow.getStart().getSchedule().getCron());
    assertEquals("0 0/15 * * * ?", workflow.getStart().getSchedule().getCron().getExpression());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/retriesprops.json", "/features/retriesprops.yml"})
  public void testRetriesProps(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getRetries());
    assertNotNull(workflow.getStates());

    Retries retries = workflow.getRetries();
    assertNotNull(retries.getRetryDefs());
    assertEquals(1, retries.getRetryDefs().size());

    RetryDefinition retryDefinition = retries.getRetryDefs().get(0);
    assertEquals("Test Retries", retryDefinition.getName());
    assertEquals("PT1M", retryDefinition.getDelay());
    assertEquals("PT2M", retryDefinition.getMaxDelay());
    assertEquals("PT2S", retryDefinition.getIncrement());
    assertEquals("1.2", retryDefinition.getMultiplier());
    assertEquals("20", retryDefinition.getMaxAttempts());
    assertEquals("0.4", retryDefinition.getJitter());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/features/datainputschemastring.json",
        "/features/datainputschemastring.yml",
        "/features/datainputschemaobjstring.json",
        "/features/datainputschemaobjstring.yml"
      })
  public void testDataInputSchemaFromString(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    DataInputSchema dataInputSchema = workflow.getDataInputSchema();
    assertNotNull(dataInputSchema);
    assertEquals("features/somejsonschema.json", dataInputSchema.getRefValue());
    assertTrue(dataInputSchema.isFailOnValidationErrors());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/datainputschemawithnullschema.json"})
  public void testDataInputSchemaWithNullSchema(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    DataInputSchema dataInputSchema = workflow.getDataInputSchema();
    assertNotNull(dataInputSchema);
    assertEquals("null", dataInputSchema.getRefValue());
    assertTrue(dataInputSchema.isFailOnValidationErrors());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/datainputschemaobj.json", "/features/datainputschemaobj.yml"})
  public void testDataInputSchemaFromObject(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getDataInputSchema());
    DataInputSchema dataInputSchema = workflow.getDataInputSchema();
    assertNotNull(dataInputSchema.getSchemaDef());

    JsonNode schemaObj = dataInputSchema.getSchemaDef();
    assertNotNull(schemaObj.get("properties"));
    JsonNode properties = schemaObj.get("properties");
    assertNotNull(properties.get("firstName"));
    JsonNode typeNode = properties.get("firstName");
    JsonNode stringNode = typeNode.get("type");
    assertEquals("string", stringNode.asString());
    assertFalse(dataInputSchema.isFailOnValidationErrors());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/subflowref.json", "/features/subflowref.yml"})
  public void testSubFlowRef(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    assertTrue(workflow.getStates().get(0) instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);

    List<Action> actions = operationState.getActions();
    assertNotNull(actions);
    assertEquals(2, actions.size());

    Action firstAction = operationState.getActions().get(0);
    assertNotNull(firstAction.getSubFlowRef());
    SubFlowRef firstSubflowRef = firstAction.getSubFlowRef();
    assertEquals("subflowRefReference", firstSubflowRef.getWorkflowId());

    Action secondAction = operationState.getActions().get(1);
    assertNotNull(secondAction.getSubFlowRef());
    SubFlowRef secondSubflowRef = secondAction.getSubFlowRef();
    assertEquals("subflowrefworkflowid", secondSubflowRef.getWorkflowId());
    assertEquals("1.0", secondSubflowRef.getVersion());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/secrets.json", "/features/secrets.yml"})
  public void testSecrets(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getSecrets());
    Secrets secrets = workflow.getSecrets();
    assertNotNull(secrets.getSecretDefs());
    assertEquals(3, secrets.getSecretDefs().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/constants.json", "/features/constants.yml"})
  public void testConstants(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getConstants());
    Constants constants = workflow.getConstants();
    assertNotNull(constants.getConstantsDef());

    JsonNode constantObj = constants.getConstantsDef();
    assertNotNull(constantObj.get("Translations"));
    JsonNode translationNode = constantObj.get("Translations");
    assertNotNull(translationNode.get("Dog"));
    JsonNode translationDogNode = translationNode.get("Dog");
    JsonNode serbianTranslationNode = translationDogNode.get("Serbian");
    assertEquals("pas", serbianTranslationNode.asString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/constantsRef.json", "/features/constantsRef.yml"})
  public void testConstantsRef(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getConstants());
    Constants constants = workflow.getConstants();
    assertEquals("constantValues.json", constants.getRefValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/timeouts.json", "/features/timeouts.yml"})
  public void testTimeouts(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getTimeouts());
    assertNotNull(workflow.getTimeouts().getWorkflowExecTimeout());

    WorkflowExecTimeout execTimeout = workflow.getTimeouts().getWorkflowExecTimeout();
    assertEquals("PT1H", execTimeout.getDuration());
    assertEquals("GenerateReport", execTimeout.getRunBefore());

    assertNotNull(workflow.getStates());
    assertEquals(2, workflow.getStates().size());
    assertTrue(workflow.getStates().get(0) instanceof EventState);

    EventState firstState = (EventState) workflow.getStates().get(0);
    assertNotNull(firstState.getTimeouts());
    assertNotNull(firstState.getTimeouts().getStateExecTimeout());
    assertNotNull(firstState.getTimeouts().getEventTimeout());
    assertEquals("PT5M", firstState.getTimeouts().getStateExecTimeout().getTotal());
    assertEquals("PT2M", firstState.getTimeouts().getEventTimeout());

    assertTrue(workflow.getStates().get(1) instanceof ParallelState);
    ParallelState secondState = (ParallelState) workflow.getStates().get(1);
    assertNotNull(secondState.getTimeouts());
    assertNotNull(secondState.getTimeouts().getStateExecTimeout());
    assertEquals("PT5M", secondState.getTimeouts().getStateExecTimeout().getTotal());

    assertNotNull(secondState.getBranches());
    assertEquals(2, secondState.getBranches().size());
    List<Branch> branches = secondState.getBranches();

    assertNotNull(branches.get(0).getTimeouts());
    assertNotNull(branches.get(0).getTimeouts().getBranchExecTimeout());
    assertEquals("PT3S", branches.get(0).getTimeouts().getBranchExecTimeout());

    assertNotNull(branches.get(1).getTimeouts());
    assertNotNull(branches.get(1).getTimeouts().getBranchExecTimeout());
    assertEquals("PT4S", branches.get(1).getTimeouts().getBranchExecTimeout());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/authbasic.json", "/features/authbasic.yml"})
  public void testAuthBasic(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());

    assertNotNull(workflow.getAuth());
    AuthDefinition auth = workflow.getAuth().getAuthDefs().get(0);
    assertNotNull(auth.getName());
    assertEquals("authname", auth.getName());
    assertNotNull(auth.getScheme());
    assertEquals("basic", auth.getScheme().value());
    assertNotNull(auth.getBasicauth());
    assertEquals("testuser", auth.getBasicauth().getUsername());
    assertEquals("testpassword", auth.getBasicauth().getPassword());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/authbearer.json", "/features/authbearer.yml"})
  public void testAuthBearer(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());

    assertNotNull(workflow.getAuth());
    AuthDefinition auth = workflow.getAuth().getAuthDefs().get(0);
    assertNotNull(auth.getName());
    assertEquals("authname", auth.getName());
    assertNotNull(auth.getScheme());
    assertEquals("bearer", auth.getScheme().value());
    assertNotNull(auth.getBearerauth());
    assertEquals("testtoken", auth.getBearerauth().getToken());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/authoauth.json", "/features/authoauth.yml"})
  public void testAuthOAuth(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());

    assertNotNull(workflow.getAuth());
    AuthDefinition auth = workflow.getAuth().getAuthDefs().get(0);
    assertNotNull(auth.getName());
    assertEquals("authname", auth.getName());
    assertNotNull(auth.getScheme());
    assertEquals("oauth2", auth.getScheme().value());
    assertNotNull(auth.getOauth());
    assertEquals("testauthority", auth.getOauth().getAuthority());
    assertEquals("clientCredentials", auth.getOauth().getGrantType().value());
    assertEquals("${ $SECRETS.clientid }", auth.getOauth().getClientId());
    assertEquals("${ $SECRETS.clientsecret }", auth.getOauth().getClientSecret());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/actionssleep.json", "/features/actionssleep.yml"})
  public void testActionsSleep(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    State state = workflow.getStates().get(0);
    assertTrue(state instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getActions());
    assertEquals(2, operationState.getActions().size());

    Action action1 = operationState.getActions().get(0);
    assertNotNull(action1);
    assertNotNull(action1.getFunctionRef());
    assertNotNull(action1.getSleep());
    assertEquals("PT5S", action1.getSleep().getBefore());
    assertEquals("PT10S", action1.getSleep().getAfter());
    FunctionRef functionRef1 = action1.getFunctionRef();
    assertEquals("creditCheckFunction", functionRef1.getRefName());
    assertNull(functionRef1.getArguments());

    Action action2 = operationState.getActions().get(1);
    assertNotNull(action2);
    assertNotNull(action2.getFunctionRef());
    assertNotNull(action2.getSleep());
    assertEquals("PT5S", action2.getSleep().getBefore());
    assertEquals("PT10S", action2.getSleep().getAfter());
    FunctionRef functionRef2 = action2.getFunctionRef();
    assertEquals("sendRejectionEmailFunction", functionRef2.getRefName());
    assertEquals(1, functionRef2.getArguments().size());
    assertEquals("${ .customer }", functionRef2.getArguments().get("applicant").asString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/errors.json", "/features/errors.yml"})
  public void testErrorsParams(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());
    assertTrue(workflow.isAutoRetries());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    assertNotNull(workflow.getErrors());
    assertEquals(2, workflow.getErrors().getErrorDefs().size());

    assertTrue(workflow.getStates().get(0) instanceof OperationState);

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getActions());
    assertEquals(1, operationState.getActions().size());
    List<Action> actions = operationState.getActions();
    assertNotNull(actions.get(0).getFunctionRef());
    assertEquals("addPet", actions.get(0).getFunctionRef().getRefName());
    assertNotNull(actions.get(0).getRetryRef());
    assertEquals("testRetry", actions.get(0).getRetryRef());
    assertNotNull(actions.get(0).getNonRetryableErrors());
    assertEquals(2, actions.get(0).getNonRetryableErrors().size());
    assertNotNull(actions.get(0).getCondition());
    assertEquals("${ .data }", actions.get(0).getCondition());

    assertNotNull(operationState.getOnErrors());
    assertEquals(1, operationState.getOnErrors().size());
    assertNotNull(operationState.getOnErrors().get(0).getErrorRefs());
    assertEquals(2, operationState.getOnErrors().get(0).getErrorRefs().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/continueasstring.json", "/features/continueasstring.yml"})
  public void testContinueAsString(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getEnd());
    End end = operationState.getEnd();
    assertNotNull(end.getContinueAs());
    assertNotNull(end.getContinueAs().getWorkflowId());
    assertEquals("myworkflowid", end.getContinueAs().getWorkflowId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/continueasobject.json", "/features/continueasobject.yml"})
  public void testContinueAsObject(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getEnd());
    End end = operationState.getEnd();
    assertNotNull(end.getContinueAs());
    assertNotNull(end.getContinueAs().getWorkflowId());
    assertEquals("myworkflowid", end.getContinueAs().getWorkflowId());
    assertEquals("1.0", end.getContinueAs().getVersion());
    assertEquals("${ .data }", end.getContinueAs().getData());
    assertNotNull(end.getContinueAs().getWorkflowExecTimeout());
    assertEquals("PT1M", end.getContinueAs().getWorkflowExecTimeout().getDuration());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/invoke.json", "/features/invoke.yml"})
  public void testFunctionInvoke(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());
    assertNotNull(workflow.getStates());

    assertNotNull(workflow.getStates());
    assertEquals(1, workflow.getStates().size());

    OperationState operationState = (OperationState) workflow.getStates().get(0);
    assertNotNull(operationState.getEnd());
    assertNotNull(operationState.getActions());
    assertEquals(3, operationState.getActions().size());

    Action action1 = operationState.getActions().get(0);
    assertNotNull(action1.getFunctionRef());
    assertNotNull(action1.getFunctionRef().getInvoke());
    assertEquals(FunctionRef.Invoke.ASYNC, action1.getFunctionRef().getInvoke());

    Action action2 = operationState.getActions().get(1);
    assertNotNull(action2.getSubFlowRef());
    assertNotNull(action2.getSubFlowRef().getOnParentComplete());
    assertEquals(
        SubFlowRef.OnParentComplete.CONTINUE, action2.getSubFlowRef().getOnParentComplete());
    assertNotNull(action2.getSubFlowRef().getInvoke());
    assertEquals(SubFlowRef.Invoke.ASYNC, action2.getSubFlowRef().getInvoke());

    Action action3 = operationState.getActions().get(2);
    assertNotNull(action3.getEventRef());
    assertNotNull(action3.getEventRef().getInvoke());
    assertEquals(EventRef.Invoke.ASYNC, action3.getEventRef().getInvoke());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/annotations.json", "/features/annotations.yml"})
  public void testAnnotations(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());

    assertNotNull(workflow.getAnnotations());
    List<String> annotations = workflow.getAnnotations();
    assertEquals(4, annotations.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/features/eventdefdataonly.json", "/features/eventdefdataonly.yml"})
  public void testEventDefDataOnly(String workflowLocation) {
    Workflow workflow = Workflow.fromSource(WorkflowTestUtils.readWorkflowFile(workflowLocation));

    assertNotNull(workflow);
    assertNotNull(workflow.getId());
    assertNotNull(workflow.getName());

    assertNotNull(workflow.getEvents());
    Events events = workflow.getEvents();
    assertNotNull(workflow.getEvents().getEventDefs());
    assertEquals(2, events.getEventDefs().size());
    EventDefinition eventDefOne = events.getEventDefs().get(0);
    EventDefinition eventDefTwo = events.getEventDefs().get(1);
    assertEquals("visaApprovedEvent", eventDefOne.getName());
    assertFalse(eventDefOne.isDataOnly());
    assertEquals("visaRejectedEvent", eventDefTwo.getName());
    assertTrue(eventDefTwo.isDataOnly());
  }
}
