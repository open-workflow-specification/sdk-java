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
package io.serverlessworkflow.utils;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.actions.Action;
import io.serverlessworkflow.api.branches.Branch;
import io.serverlessworkflow.api.defaultdef.DefaultConditionDefinition;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.events.OnEvents;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.functions.FunctionRef;
import io.serverlessworkflow.api.interfaces.State;
import io.serverlessworkflow.api.start.Start;
import io.serverlessworkflow.api.states.*;
import io.serverlessworkflow.api.switchconditions.DataCondition;
import io.serverlessworkflow.api.switchconditions.EventCondition;
import java.util.*;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Provides common utility methods to provide most often needed answers from a workflow */
@SuppressWarnings("StreamToLoop")
public final class WorkflowUtils {
  private static final int DEFAULT_STARTING_STATE_POSITION = 0;
  private static final long DEFAULT_STATE_COUNT = 0;

  /**
   * Gets State matching Start state. If start is not present returns first state. If start is
   * present, returns the matching start State. If matching state is not present, returns null
   *
   * @param workflow workflow
   * @return {@code state} when present else returns {@code null}
   */
  public static State getStartingState(Workflow workflow) {
    if (!hasStates(workflow)) {
      return null;
    }

    Start start = workflow.getStart();
    if (start == null) {
      return workflow.getStates().get(DEFAULT_STARTING_STATE_POSITION);
    } else {
      Optional<State> startingState =
          workflow.getStates().stream()
              .filter(state -> state.getName().equals(start.getStateName()))
              .findFirst();
      return startingState.orElse(null);
    }
  }

  /**
   * Gets List of States matching stateType
   *
   * @param workflow
   * @param stateType
   * @return {@code List<State>}. Returns {@code null} when workflow is null.
   */
  public static List<State> getStates(Workflow workflow, DefaultState.Type stateType) {
    if (!hasStates(workflow)) {
      return null;
    }

    return workflow.getStates().stream()
        .filter(state -> state.getType() == stateType)
        .collect(Collectors.toList());
  }

  /**
   * @return {@code List<io.serverlessworkflow.api.events.EventDefinition>}. Returns {@code NULL}
   *     when workflow is null or when workflow does not contain events
   */
  public static List<EventDefinition> getDefinedConsumedEvents(Workflow workflow) {
    return getDefinedEvents(workflow, EventDefinition.Kind.CONSUMED);
  }

  /**
   * @return {@code List<io.serverlessworkflow.api.events.EventDefinition>}. Returns {@code NULL}
   *     when workflow is null or when workflow does not contain events
   */
  public static List<EventDefinition> getDefinedProducedEvents(Workflow workflow) {
    return getDefinedEvents(workflow, EventDefinition.Kind.PRODUCED);
  }

  /**
   * Gets list of event definition matching eventKind
   *
   * @param workflow
   * @return {@code List<io.serverlessworkflow.api.events.EventDefinition>}. Returns {@code NULL}
   *     when workflow is null or when workflow does not contain events
   */
  public static List<EventDefinition> getDefinedEvents(
      Workflow workflow, EventDefinition.Kind eventKind) {
    if (!hasEventDefs(workflow)) {
      return null;
    }

    List<EventDefinition> eventDefs = workflow.getEvents().getEventDefs();
    return eventDefs.stream()
        .filter(eventDef -> eventDef.getKind() == eventKind)
        .collect(Collectors.toList());
  }

  /**
   * @return {@code int} Returns count of defined event count matching eventKind
   */
  public static int getDefinedEventsCount(Workflow workflow, EventDefinition.Kind eventKind) {
    List<EventDefinition> definedEvents = getDefinedEvents(workflow, eventKind);
    return definedEvents == null ? 0 : definedEvents.size();
  }

  /**
   * @return {@code int} Returns count of Defined Consumed Event Count
   */
  public static int getDefinedConsumedEventsCount(Workflow workflow) {
    return getDefinedEventsCount(workflow, EventDefinition.Kind.CONSUMED);
  }

  /**
   * @return {@code int} Returns count of Defined Produced Event Count
   */
  public static int getDefinedProducedEventsCount(Workflow workflow) {
    return getDefinedEventsCount(workflow, EventDefinition.Kind.PRODUCED);
  }

  /**
   * Gets Consumed Events of parent workflow Iterates through states in parent workflow and collects
   * all the ConsumedEvents. Sub Workflows of the Workflow <strong>are not</strong> considered for
   * getting Consumed Events
   *
   * @return Returns {@code List<EventDefinition>}
   */
  public static List<EventDefinition> getWorkflowConsumedEvents(Workflow workflow) {
    return getWorkflowEventDefinitions(workflow, EventDefinition.Kind.CONSUMED);
  }

  /**
   * Gets Produced Events of parent workflow Iterates through states in parent workflow and collects
   * all the ConsumedEvents. Sub Workflows of the Workflow <strong>are not</strong> considered for
   * getting Consumed Events
   *
   * @return Returns {@code List<EventDefinition>}
   */
  public static List<EventDefinition> getWorkflowProducedEvents(Workflow workflow) {
    return getWorkflowEventDefinitions(workflow, EventDefinition.Kind.PRODUCED);
  }

  /**
   * Gets Events of parent workflow matching {@code EventDefinition.Kind} Iterates through states in
   * parent workflow and collects all the events matching {@code EventDefinition.Kind} .
   *
   * @return Returns {@code List<EventDefinition>}
   */
  public static List<EventDefinition> getWorkflowEventDefinitions(
      Workflow workflow, EventDefinition.Kind eventKind) {
    if (!hasStates(workflow)) {
      return null;
    }

    List<String> uniqueWorkflowEventsFromStates = getUniqueWorkflowEventsFromStates(workflow);
    List<EventDefinition> definedConsumedEvents = getDefinedEvents(workflow, eventKind);
    if (definedConsumedEvents == null) {
      return null;
    }
    return definedConsumedEvents.stream()
        .filter(definedEvent -> uniqueWorkflowEventsFromStates.contains(definedEvent.getName()))
        .collect(Collectors.toList());
  }

  /** Returns a list of unique event names from workflow states */
  public static List<String> getUniqueWorkflowEventsFromStates(Workflow workflow) {
    List<String> eventReferences = new ArrayList<>();

    for (State state : workflow.getStates()) {
      if (state instanceof SwitchState) {
        SwitchState switchState = (SwitchState) state;
        if (switchState.getEventConditions() != null) {
          switchState
              .getEventConditions()
              .forEach(eventCondition -> eventReferences.add(eventCondition.getEventRef()));
        }
      } else if (state instanceof CallbackState) {
        CallbackState callbackState = (CallbackState) state;
        if (callbackState.getEventRef() != null) eventReferences.add(callbackState.getEventRef());
        if (callbackState.getAction() != null && callbackState.getAction().getEventRef() != null) {
          eventReferences.addAll(getActionEvents(callbackState.getAction()));
        }
      } else if (state instanceof EventState) {
        EventState eventState = (EventState) state;
        if (eventState.getOnEvents() != null) {
          eventState
              .getOnEvents()
              .forEach(
                  onEvents -> {
                    eventReferences.addAll(onEvents.getEventRefs());
                    if (onEvents.getActions() != null) {
                      for (Action action : onEvents.getActions()) {
                        eventReferences.addAll(getActionEvents(action));
                      }
                    }
                  });
        }
      } else if (state instanceof OperationState) {
        OperationState operationState = (OperationState) state;
        if (operationState.getActions() != null) {
          for (Action action : operationState.getActions()) {
            eventReferences.addAll(getActionEvents(action));
          }
        }
      } else if (state instanceof ParallelState) {
        ParallelState parallelState = (ParallelState) state;
        if (parallelState.getBranches() != null) {
          for (Branch branch : parallelState.getBranches()) {
            if (branch.getActions() != null) {
              for (Action action : branch.getActions()) {
                eventReferences.addAll(getActionEvents(action));
              }
            }
          }
        }
      }
    }

    return eventReferences.stream().distinct().collect(Collectors.toList());
  }

  /**
   * @return Returns {@code int } Count of the workflow consumed events. <strong>Does not</strong>
   *     consider sub-workflows
   */
  public static int getWorkflowConsumedEventsCount(Workflow workflow) {
    List<EventDefinition> workflowConsumedEvents = getWorkflowConsumedEvents(workflow);
    return workflowConsumedEvents == null ? 0 : workflowConsumedEvents.size();
  }

  /**
   * @return Returns {@code int} Count of the workflow produced events. <strong>Does not</strong>
   *     consider sub-workflows in the count
   */
  public static int getWorkflowProducedEventsCount(Workflow workflow) {
    List<EventDefinition> workflowProducedEvents = getWorkflowProducedEvents(workflow);
    return workflowProducedEvents == null ? 0 : workflowProducedEvents.size();
  }

  /**
   * @return Returns function definition for actions
   */
  public static FunctionDefinition getFunctionDefinitionsForAction(
      Workflow workflow, String action) {
    if (!hasFunctionDefs(workflow)) return null;
    FunctionRef functionRef = getFunctionRefFromAction(workflow, action);
    if (functionRef == null) return null;
    final Optional<FunctionDefinition> functionDefinition =
        workflow.getFunctions().getFunctionDefs().stream()
            .filter(functionDef -> functionDef.getName().equals(functionRef.getRefName()))
            .distinct()
            .findFirst();

    return functionDefinition.isPresent() ? functionDefinition.get() : null;
  }

  /**
   * @return : Returns @{code List<Action>} which uses a function defintion
   */
  public static List<Action> getActionsForFunctionDefinition(
      Workflow workflow, String functionDefinitionName) {
    if (!hasFunctionDefs(workflow, functionDefinitionName)) return null;
    return getActionsWhichUsesFunctionDefinition(workflow, functionDefinitionName);
  }

  /**
   * Gets Num of State in the workflow does not consider child workflow
   *
   * @param workflow
   * @return
   */
  public static long getNumOfStates(Workflow workflow) {
    return hasStates(workflow) ? workflow.getStates().size() : DEFAULT_STATE_COUNT;
  }

  /**
   * Gets Num of States for State Type
   *
   * @param workflow
   * @param type
   * @return
   */
  public static long getNumOfStates(Workflow workflow, DefaultState.Type type) {
    return hasStates(workflow)
        ? workflow.getStates().stream().filter(state -> state.getType() == type).count()
        : DEFAULT_STATE_COUNT;
  }

  /**
   * Returns workflow state from provided name, or null if not found.
   *
   * @param workflow
   * @param name
   * @return
   */
  public static State getStateWithName(Workflow workflow, String name) {
    if (!hasStates(workflow)) {
      return null;
    }

    Optional<State> state =
        workflow.getStates().stream().filter(s -> s.getName().equals(name)).findFirst();

    if (state.isPresent()) {
      return state.get();
    } else {
      return null;
    }
  }

  public static long getNumOfEndStates(Workflow workflow) {
    if (hasStates(workflow)) {
      long count = workflow.getStates().stream().filter(state -> state.getEnd() != null).count();
      List<State> switchStates =
          workflow.getStates().stream()
              .filter(state -> state instanceof SwitchState)
              .collect(Collectors.toList());
      for (State state : switchStates) {
        SwitchState switchState = (SwitchState) state;
        List<EventCondition> eventConditions = switchState.getEventConditions();
        if (eventConditions != null) {
          count =
              count
                  + eventConditions.stream()
                      .filter(eventCondition -> eventCondition.getEnd() != null)
                      .count();
        }
        List<DataCondition> dataConditions = switchState.getDataConditions();
        if (dataConditions != null) {
          count =
              count
                  + dataConditions.stream()
                      .filter(dataCondition -> dataCondition.getEnd() != null)
                      .count();
        }
        DefaultConditionDefinition defaultCondition = switchState.getDefaultCondition();
        if (defaultCondition != null) {
          count = (defaultCondition.getEnd() != null) ? count + 1 : count;
        }
      }
      return count;
    } else {
      return DEFAULT_STATE_COUNT;
    }
  }

  public static List<Action> getActionsWhichUsesFunctionDefinition(
      Workflow workflow, String functionDefinitionName) {
    List<Action> actions = new ArrayList<>();
    for (State state : workflow.getStates()) {
      if (state instanceof EventState) {
        EventState eventState = (EventState) state;
        List<OnEvents> onEvents = eventState.getOnEvents();
        if (onEvents != null) {
          for (OnEvents onEvent : onEvents) {
            if (onEvent != null) {
              List<Action> onEventActions = onEvent.getActions();
              if (onEventActions != null) {
                for (Action onEventAction : onEventActions) {
                  if (checkIfActionUsesFunctionDefinition(functionDefinitionName, onEventAction))
                    actions.add(onEventAction);
                }
              }
            }
          }
        }
      } else if (state instanceof CallbackState) {
        CallbackState callbackState = (CallbackState) state;
        final Action callbackStateAction = callbackState.getAction();
        if (checkIfActionUsesFunctionDefinition(functionDefinitionName, callbackStateAction)) {
          actions.add(callbackStateAction);
        }

      } else if (state instanceof OperationState) {
        OperationState operationState = (OperationState) state;
        final List<Action> operationStateActions = operationState.getActions();
        if (operationStateActions != null) {
          for (Action operationStateAction : operationStateActions) {
            if (checkIfActionUsesFunctionDefinition(functionDefinitionName, operationStateAction)) {
              actions.add(operationStateAction);
            }
          }
        }
      } else if (state instanceof ParallelState) {
        ParallelState parallelState = (ParallelState) state;
        List<Branch> parallelStateBranches = parallelState.getBranches();
        if (parallelStateBranches != null) {
          for (Branch branch : parallelStateBranches) {
            List<Action> branchActions = branch.getActions();
            if (branchActions != null) {
              for (Action branchAction : branchActions) {
                if (checkIfActionUsesFunctionDefinition(functionDefinitionName, branchAction)) {
                  actions.add(branchAction);
                }
              }
            }
          }
        }
      } else if (state instanceof ForEachState) {
        ForEachState forEachState = (ForEachState) state;
        List<Action> forEachStateActions = forEachState.getActions();
        if (forEachStateActions != null) {
          for (Action forEachStateAction : forEachStateActions) {
            if (checkIfActionUsesFunctionDefinition(functionDefinitionName, forEachStateAction)) {
              actions.add(forEachStateAction);
            }
          }
        }
      }
    }

    return actions;
  }

  public static boolean checkIfActionUsesFunctionDefinition(
      String functionDefinitionName, Action action) {
    return action != null
        && action.getFunctionRef() != null
        && action.getFunctionRef().getRefName() != null
        && action.getFunctionRef().getRefName().equals(functionDefinitionName);
  }

  public static boolean hasFunctionDefs(Workflow workflow, String functionDefinitionName) {
    if (!hasFunctionDefs(workflow)) return false;
    List<FunctionDefinition> functionDefs = workflow.getFunctions().getFunctionDefs();
    return functionDefs.stream()
        .anyMatch(
            functionDefinition -> functionDefinition.getName().equals(functionDefinitionName));
  }

  public static FunctionRef getFunctionRefFromAction(Workflow workflow, String action) {
    if (!hasStates(workflow)) return null;

    for (State state : workflow.getStates()) {
      if (state instanceof EventState) {
        EventState eventState = (EventState) state;
        List<OnEvents> onEvents = eventState.getOnEvents();
        if (onEvents != null) {
          for (OnEvents onEvent : onEvents) {
            if (onEvent != null) {
              List<Action> onEventActions = onEvent.getActions();
              if (onEventActions != null) {
                for (Action onEventAction : onEventActions) {
                  if (onEventAction != null
                      && onEventAction.getName() != null
                      && onEventAction.getName().equals(action))
                    return onEventAction.getFunctionRef();
                }
              }
            }
          }
        }
      } else if (state instanceof CallbackState) {
        CallbackState callbackState = (CallbackState) state;
        final Action callbackStateAction = callbackState.getAction();
        if (callbackStateAction != null
            && callbackStateAction.getName() != null
            && callbackStateAction.getName().equals(action)) {
          return callbackStateAction.getFunctionRef();
        }

      } else if (state instanceof OperationState) {
        OperationState operationState = (OperationState) state;
        final List<Action> operationStateActions = operationState.getActions();
        if (operationStateActions != null) {
          for (Action operationStateAction : operationStateActions) {
            if (operationStateAction != null
                && operationStateAction.getName() != null
                && operationStateAction.getName().equals(action)) {
              return operationStateAction.getFunctionRef();
            }
          }
        }
      } else if (state instanceof ParallelState) {
        ParallelState parallelState = (ParallelState) state;
        List<Branch> parallelStateBranches = parallelState.getBranches();
        if (parallelStateBranches != null) {
          for (Branch branch : parallelStateBranches) {
            List<Action> branchActions = branch.getActions();
            if (branchActions != null) {
              for (Action branchAction : branchActions) {
                if (branchAction != null
                    && branchAction.getName() != null
                    && branchAction.getName().equals(action)) {
                  return branchAction.getFunctionRef();
                }
              }
            }
          }
        }
      } else if (state instanceof ForEachState) {
        ForEachState forEachState = (ForEachState) state;
        List<Action> forEachStateActions = forEachState.getActions();
        if (forEachStateActions != null) {
          for (Action forEachStateAction : forEachStateActions) {
            if (forEachStateAction != null
                && forEachStateAction.getName() != null
                && forEachStateAction.getName().equals(action)) {
              return forEachStateAction.getFunctionRef();
            }
          }
        }
      }
    }

    return null;
  }

  public static boolean hasFunctionDefs(Workflow workflow) {
    return workflow != null
        && workflow.getFunctions() != null
        && workflow.getFunctions().getFunctionDefs() != null
        && !workflow.getFunctions().getFunctionDefs().isEmpty();
  }

  /** Returns true if workflow has states, otherwise false */
  public static boolean hasStates(Workflow workflow) {
    return workflow != null && workflow.getStates() != null && !workflow.getStates().isEmpty();
  }

  /** Returns true if workflow has events definitions, otherwise false */
  public static boolean hasEventDefs(Workflow workflow) {
    return workflow != null
        && workflow.getEvents() != null
        && workflow.getEvents().getEventDefs() != null
        && !workflow.getEvents().getEventDefs().isEmpty();
  }

  /** Gets event refs of an action */
  public static List<String> getActionEvents(Action action) {
    List<String> actionEvents = new ArrayList<>();

    if (action != null && action.getEventRef() != null) {
      if (action.getEventRef().getTriggerEventRef() != null) {
        actionEvents.add(action.getEventRef().getTriggerEventRef());
      }
      if (action.getEventRef().getResultEventRef() != null) {
        actionEvents.add(action.getEventRef().getResultEventRef());
      }
    }

    return actionEvents;
  }

  /**
   * Merges two JsonNode
   *
   * @param mainNode
   * @param updateNode
   * @return merged JsonNode
   */
  public static JsonNode mergeNodes(JsonNode mainNode, JsonNode updateNode) {
    for (String fieldName : updateNode.propertyNames()) {
      JsonNode jsonNode = mainNode.get(fieldName);
      // if field exists and is an embedded object
      if (jsonNode != null && jsonNode.isObject()) {
        mergeNodes(jsonNode, updateNode.get(fieldName));
      } else {
        if (mainNode instanceof ObjectNode) {
          // Overwrite field
          JsonNode value = updateNode.get(fieldName);
          ((ObjectNode) mainNode).set(fieldName, value);
        }
      }
    }

    return mainNode;
  }

  /**
   * Adds node as field
   *
   * @param mainNode
   * @param toAddNode
   * @param fieldName
   * @return original, main node with field added
   */
  public static JsonNode addNode(JsonNode mainNode, JsonNode toAddNode, String fieldName) {
    ((ObjectNode) mainNode).set(fieldName, toAddNode);
    return mainNode;
  }

  /**
   * Adds array with name
   *
   * @param mainNode
   * @param toAddArray
   * @param arrayName
   * @return original, main node with array added
   */
  public static JsonNode addArray(JsonNode mainNode, ArrayNode toAddArray, String arrayName) {
    ((ObjectNode) mainNode).set(arrayName, toAddArray);
    return mainNode;
  }

  /**
   * Adds a object field
   *
   * @param mainNode
   * @param toAddValue
   * @param fieldName
   * @return original, main node with field added
   */
  public static JsonNode addFieldValue(JsonNode mainNode, Object toAddValue, String fieldName) {
    ObjectMapper mapper = new ObjectMapper();
    ((ObjectNode) mainNode).set(fieldName, mapper.valueToTree(toAddValue));
    return mainNode;
  }

  /**
   * Returns a list of function definitions that have the given type.
   *
   * @param workflow
   * @param type
   * @return list of functions defs or null
   */
  public static List<FunctionDefinition> getFunctionDefinitionsWithType(
      Workflow workflow, FunctionDefinition.Type type) {
    if (!hasFunctionDefs(workflow)) return null;
    return workflow.getFunctions().getFunctionDefs().stream()
        .filter(fd -> fd.getType().equals(type))
        .collect(Collectors.toList());
  }

  /**
   * Returns function definition with provided name
   *
   * @param workflow
   * @param name
   * @return function definition or null
   */
  public static FunctionDefinition getFunctionDefinitionWithName(Workflow workflow, String name) {
    if (!hasFunctionDefs(workflow)) return null;
    Optional<FunctionDefinition> funcDef =
        workflow.getFunctions().getFunctionDefs().stream()
            .filter(fd -> fd.getName().equals(name))
            .findFirst();
    return funcDef.orElse(null);
  }
}
