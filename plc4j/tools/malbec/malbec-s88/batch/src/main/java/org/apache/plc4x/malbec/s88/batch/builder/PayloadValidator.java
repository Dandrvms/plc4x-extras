/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.batch.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformVariables;
import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.api.S88TransitionCondition;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;
import org.apache.plc4x.malbec.s88.batch.model.FollowRule;
import org.apache.plc4x.malbec.s88.batch.model.PreparedCondition;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.BaseNameResolver;
import org.apache.plc4x.malbec.s88.core.ChartGraph;
import org.apache.plc4x.malbec.s88.core.ChartSoundness;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;

/** Checks a batch request and turns its recipes and plant into prepared values. */
public final class PayloadValidator {

    private static final String TYPE = "Type";
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";

    private PayloadValidator() {
        /* This utility class should not be instantiated */
    }

    /** A chart of a recipe, with the step that owns it. */
    public record ChartRef(String path, S88RecipeElement owner, S88ProcedureLogic chart) {
    }

    public static void validateRequest(BatchRequest request, List<String> errors) {
        if (request == null) {
            errors.add("There is no batch request to prepare.");
            return;
        }
        if (request.batchId() == null || request.batchId().isBlank()) {
            errors.add("The batch has no id, so there is nothing to tell its run apart from any other.");
        }
        if (request.master() == null) {
            errors.add("There is no master recipe to prepare.");
        }
        if (request.plant() == null) {
            errors.add("This recipe has no frozen plant; create the recipe set with a plant.");
        }
    }

    public static List<ChartRef> charts(S88Recipe recipe, List<String> errors) {
        List<ChartRef> refs = new ArrayList<>();
        if (recipe == null) {
            return refs;
        }
        Map<String, ChartRef> byPath = new LinkedHashMap<>();
        if (recipe.getProcedureLogic() != null) {
            ChartRef root = new ChartRef("", null, recipe.getProcedureLogic());
            refs.add(root);
            byPath.put("", root);
        } else {
            errors.add("The recipe has no chart.");
        }
        for (S88RecipeElement element : recipe.getAllElements()) {
            if (element.getProcedureLogic() == null) {
                continue;
            }
            String path = element.getId();
            if (path == null || path.isBlank() || byPath.containsKey(path)) {
                continue;
            }
            ChartRef ref = new ChartRef(path, element, element.getProcedureLogic());
            refs.add(ref);
            byPath.put(path, ref);
        }
        detectCycles(recipe, refs, errors);
        return refs;
    }

    public static void validateCharts(List<ChartRef> charts, List<String> errors) {
        for (ChartRef ref : charts) {
            if (ref.chart() == null) {
                errors.add("The chart of " + label(ref.path()) + " is missing.");
                continue;
            }
            errors.addAll(ChartSoundness.check(ref.chart()));
            ChartGraph graph = ChartGraph.of(ref.chart());
            if (graph.hasExternalFlow()) {
                errors.add("The chart of " + label(ref.path())
                        + " leaves the procedure, so it cannot be prepared.");
            }
            if (!graph.hasNode(CreateMasterRecipeUseCase.BEGIN_ID)) {
                errors.add("The chart of " + label(ref.path())
                        + " has no BEGIN, so there is nowhere to start.");
            }
            if (!graph.hasNode(CreateMasterRecipeUseCase.END_ID)) {
                errors.add("The chart of " + label(ref.path())
                        + " has no END, so there is nowhere to stop.");
            }
        }
    }

    public static Map<String, PreparedCondition> prepareConditions(
            S88MasterRecipe recipe, S88PlantSnapshot plant, List<ChartRef> charts,
            EquipmentAllocator.Allocation allocation, List<String> errors) {
        Map<String, PreparedCondition> prepared = new LinkedHashMap<>();
        if (recipe == null || plant == null || charts == null) {
            return prepared;
        }
        for (ChartRef ref : charts) {
            if (ref.chart() == null) {
                continue;
            }
            for (S88ProcedureTransition transition : ref.chart().getTransitions()) {
                if (!transition.isGuarded()) {
                    continue;
                }
                String text = transition.getCondition();
                S88TransitionCondition condition;
                try {
                    condition = S88TransitionCondition.parse(text);
                } catch (IllegalArgumentException notACondition) {
                    errors.add(uninterpretable(ref, transition, text));
                    continue;
                }
                if (condition == null) {
                    errors.add(uninterpretable(ref, transition, text));
                    continue;
                }
                PreparedCondition result = prepare(condition, recipe, plant, ref,
                        transition.getId(), allocation, errors);
                if (result != null) {
                    prepared.put(key(ref.path(), transition.getId()), result);
                }
            }
        }
        return prepared;
    }

    public static Map<String, List<PreparedSetpoint>> prepareSetpoints(
            S88MasterRecipe recipe, S88PlantSnapshot plant, List<ChartRef> charts,
            EquipmentAllocator.Allocation allocation, List<String> warnings, List<String> errors) {
        Map<String, List<PreparedSetpoint>> prepared = new LinkedHashMap<>();
        if (recipe == null || plant == null || charts == null) {
            return prepared;
        }
        for (ChartRef ref : charts) {
            if (ref.chart() == null) {
                continue;
            }
            for (S88ProcedureStep step : ref.chart().getSteps()) {
                S88RecipeElement element = recipe.findElement(step.getRecipeElementId()).orElse(null);
                if (element == null) {
                    continue;
                }
                String stepKey = key(ref.path(), step.getId());
                String elementId = allocation.moduleOfStep().get(stepKey);
                if (elementId == null) {
                    elementId = allocation.unitOfStep().get(stepKey);
                }
                if (elementId == null) {
                    continue;
                }
                S88Element target = plant.findById(elementId).orElse(null);
                if (target == null) {
                    continue;
                }
                List<PreparedSetpoint> forStep = new ArrayList<>();
                for (S88RecipeParameter parameter : element.getParameters()) {
                    PreparedSetpoint setpoint = prepareSetpoint(recipe, plant, target, elementId,
                            element, parameter, warnings, errors);
                    if (setpoint != null) {
                        forStep.add(setpoint);
                    }
                }
                if (!forStep.isEmpty()) {
                    prepared.put(stepKey, forStep);
                }
            }
        }
        return prepared;
    }

    public static void validateProfile(SimulationProfile profile, S88PlantSnapshot plant,
                                       EquipmentAllocator.Allocation allocation,
                                       List<String> errors) {
        if (profile == null || plant == null) {
            return;
        }
        List<String> modules = new ArrayList<>();
        for (String module : allocation.moduleOfStep().values()) {
            if (!modules.contains(module)) {
                modules.add(module);
            }
        }
        if (modules.isEmpty()) {
            for (S88Element module : PlatformVariables.modulesOf(plant.getRoot())) {
                modules.add(module.getId());
            }
        }
        for (FollowRule rule : profile.followRules()) {
            if (rule.moduleId() != null) {
                S88Element module = plant.findById(rule.moduleId()).orElse(null);
                if (module == null) {
                    errors.add("The follow rule names module '" + rule.moduleId()
                            + "', which the plant does not have.");
                } else if (module.getLevel() != S88Level.EQUIPMENTMODULE) {
                    errors.add("The follow rule names '" + rule.moduleId()
                            + "', which is not an equipment module.");
                } else if (!publishesBoth(module, rule.reportBase(), rule.setpointBase())) {
                    errors.add("The follow rule '" + rule.reportBase() + "' -> '"
                            + rule.setpointBase() + "' points at variables that '"
                            + rule.moduleId() + "' does not publish.");
                }
            } else if (!anyModulePublishesBoth(plant, modules, rule)) {
                errors.add("No module publishes both '" + rule.reportBase() + "' and '"
                        + rule.setpointBase() + "' for the follow rule.");
            }
        }
    }

    public static String key(String chartPath, String nodeId) {
        return (chartPath == null ? "" : chartPath) + "/" + nodeId;
    }

    private static PreparedCondition prepare(S88TransitionCondition condition, S88MasterRecipe recipe,
                                             S88PlantSnapshot plant, ChartRef ref,
                                             String transitionId,
                                             EquipmentAllocator.Allocation allocation,
                                             List<String> errors) {
        return switch (condition.getKind()) {
            case WAIT -> {
                if (condition.getSeconds() < 0) {
                    errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                            + " waits for less than no time.");
                    yield null;
                }
                yield new PreparedCondition.Wait(condition.getSeconds());
            }
            case AND, OR -> {
                List<PreparedCondition> operands = new ArrayList<>();
                for (S88TransitionCondition operand : condition.getOperands()) {
                    PreparedCondition prepared = prepare(operand, recipe, plant, ref, transitionId,
                            allocation, errors);
                    if (prepared == null) {
                        yield null;
                    }
                    operands.add(prepared);
                }
                yield condition.getKind() == S88TransitionCondition.Kind.AND
                        ? new PreparedCondition.All(operands)
                        : new PreparedCondition.Any(operands);
            }
            case COMPARISON -> prepareComparison(condition.getComparison(), recipe, plant, ref,
                    transitionId, allocation, errors);
        };
    }

    private static PreparedCondition prepareComparison(S88ConditionExpression expression,
                                                       S88MasterRecipe recipe,
                                                       S88PlantSnapshot plant, ChartRef ref,
                                                       String transitionId,
                                                       EquipmentAllocator.Allocation allocation,
                                                       List<String> errors) {
        if (expression == null) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " has no comparison.");
            return null;
        }
        S88ConditionOperator operator = expression.getOperator();
        if (operator == null || operator == S88ConditionOperator.OTHER) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " compares with an operator this builder does not understand.");
            return null;
        }
        String moduleId = expression.getEquipment();
        if (moduleId != null && !moduleId.isBlank()) {
            moduleId = moduleId.trim();
            S88Element equipment = plant.findById(moduleId).orElse(null);
            if (equipment == null) {
                errors.add("The equipment '" + moduleId + "' that transition '" + transitionId
                        + "' reads is not in the plant.");
                return null;
            }
            if (equipment.getLevel() != S88Level.EQUIPMENTMODULE) {
                errors.add("The equipment '" + moduleId + "' that transition '" + transitionId
                        + "' reads is not an equipment module.");
                return null;
            }
        } else {
            moduleId = predecessorModule(recipe, plant, ref, transitionId, allocation, errors);
            if (moduleId == null) {
                return null;
            }
        }
        S88VariableAddress address = expression.getVariable();
        if (address == null) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " does not say what it reads.");
            return null;
        }
        String container = address.hasContainer() ? address.getContainer() : S88PlantModel.REPORTS;
        if (!S88PlantModel.REPORTS.equalsIgnoreCase(container)) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " reads '" + address.toText() + "', but only Reports can be read.");
            return null;
        }
        String baseName = address.getName();
        S88Element module = plant.findById(moduleId).orElse(null);
        if (module == null) {
            errors.add("The equipment '" + moduleId + "' that transition '" + transitionId
                    + "' reads is not in the plant.");
            return null;
        }
        String variable;
        try {
            variable = BaseNameResolver.resolveVariable(module, S88PlantModel.REPORTS, baseName)
                    .orElse(null);
        } catch (IllegalStateException ambiguous) {
            errors.add(ambiguous.getMessage());
            return null;
        }
        if (variable == null) {
            errors.add("The report '" + baseName + "' that transition '" + transitionId
                    + "' reads is not published by '" + moduleId + "'.");
            return null;
        }
        Map<String, Object> definition = definitionOf(module, S88PlantModel.REPORTS, variable);
        DataType dataType = typeOf(definition);
        String enumName = text(definition, UNITS_OR_ENUM);
        List<String> enumerationValues = List.of();
        if (dataType == DataType.ENUMERATION) {
            if (enumName == null) {
                errors.add("The plant does not say which enumeration report '" + baseName
                        + "' of '" + moduleId + "' is drawn from.");
                return null;
            }
            S88Enumeration enumeration = plant.findEnumeration(enumName);
            if (enumeration == null) {
                errors.add("The plant has no enumeration '" + enumName + "' for report '"
                        + baseName + "' of '" + moduleId + "'.");
                return null;
            }
            enumerationValues = List.copyOf(enumeration.getValues().keySet());
        }
        String literal = expression.getLiteral();
        if (!literalFits(dataType, literal, enumerationValues)) {
            errors.add("The value '" + literal + "' cannot be held by report '" + baseName
                    + "' of '" + moduleId + "'.");
            return null;
        }
        String unit = dataType == DataType.ENUMERATION ? null : enumName;
        return new PreparedCondition.Comparison(moduleId, variable, dataType, unit, operator,
                literal, enumerationValues);
    }

    private static PreparedSetpoint prepareSetpoint(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                                    S88Element target, String elementId,
                                                    S88RecipeElement element,
                                                    S88RecipeParameter parameter,
                                                    List<String> warnings, List<String> errors) {
        S88VariableAddress address = recipe.addressOf(parameter);
        if (address == null) {
            warnings.add("Step '" + element.getId() + "' has a parameter with no address.");
            return null;
        }
        String container = address.hasContainer() ? address.getContainer() : S88PlantModel.PARAMETERS;
        if (!S88PlantModel.PARAMETERS.equalsIgnoreCase(container)) {
            warnings.add("Step '" + element.getId() + "' sets '" + address.toText()
                    + "', but only Parameters can be written.");
            return null;
        }
        String baseName = address.getName();
        String variable;
        try {
            variable = BaseNameResolver.resolveVariable(target, S88PlantModel.PARAMETERS, baseName)
                    .orElse(null);
        } catch (IllegalStateException ambiguous) {
            errors.add(ambiguous.getMessage());
            return null;
        }
        if (variable == null) {
            warnings.add("Step '" + element.getId() + "' sets parameter '" + baseName
                    + "', which '" + elementId + "' does not publish.");
            return null;
        }
        S88ParameterValue value = parameter.getFirstValue();
        if (value == null || value.getFirstValueString() == null) {
            warnings.add("Step '" + element.getId() + "' sets parameter '" + baseName
                    + "' to no value.");
            return null;
        }
        Map<String, Object> definition = definitionOf(target, S88PlantModel.PARAMETERS, variable);
        DataType dataType = value.getDataType() != null ? value.getDataType() : typeOf(definition);
        String enumName = value.getEnumerationSetIds().isEmpty()
                ? text(definition, UNITS_OR_ENUM)
                : value.getEnumerationSetIds().get(0);
        List<String> enumerationValues = List.of();
        if (dataType == DataType.ENUMERATION && enumName != null) {
            S88Enumeration enumeration = plant.findEnumeration(enumName);
            if (enumeration == null) {
                errors.add("The plant has no enumeration '" + enumName + "' for parameter '"
                        + baseName + "' of '" + elementId + "'.");
                return null;
            }
            enumerationValues = List.copyOf(enumeration.getValues().keySet());
        }
        String literal = value.getFirstValueString();
        if (!literalFits(dataType, literal, enumerationValues)) {
            errors.add("The value '" + literal + "' cannot be held by parameter '" + baseName
                    + "' of '" + elementId + "'.");
            return null;
        }
        String unit = value.getUnitOfMeasure() != null
                ? value.getUnitOfMeasure()
                : (dataType == DataType.ENUMERATION ? null : text(definition, UNITS_OR_ENUM));
        return new PreparedSetpoint(elementId, variable, dataType, literal, unit,
                enumerationValues);
    }

    private static String predecessorModule(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                            ChartRef ref, String transitionId,
                                            EquipmentAllocator.Allocation allocation,
                                            List<String> errors) {
        List<String> stepIds = predecessorStepIds(ref.chart(), transitionId);
        if (stepIds.isEmpty()) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " has no step before it, so there is no equipment to read.");
            return null;
        }
        Map<String, String> byModule = new LinkedHashMap<>();
        for (String stepId : stepIds) {
            String stepKey = key(ref.path(), stepId);
            String module = allocation.moduleOfStep().get(stepKey);
            if (module == null) {
                module = allocation.unitOfStep().get(stepKey);
            }
            if (module == null) {
                errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                        + " follows step '" + stepId + "', which has no equipment.");
                continue;
            }
            byModule.putIfAbsent(module, stepId);
        }
        if (byModule.isEmpty()) {
            return null;
        }
        if (byModule.size() > 1) {
            errors.add("Transition '" + transitionId + "' in chart " + label(ref.path())
                    + " can be reached from different equipment ("
                    + String.join(", ", byModule.keySet())
                    + "), so the report it reads is ambiguous.");
            return null;
        }
        return byModule.keySet().iterator().next();
    }

    private static List<String> predecessorStepIds(S88ProcedureLogic chart, String transitionId) {
        List<String> ids = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            boolean intoTransition = false;
            for (S88IdRef ref : link.getTo()) {
                if (ref.isInternal() && ref.getType() == S88IdRefType.TRANSITION
                        && transitionId.equals(ref.getValue())) {
                    intoTransition = true;
                    break;
                }
            }
            if (!intoTransition) {
                continue;
            }
            for (S88IdRef from : link.getFrom()) {
                if (from.isInternal() && from.getType() == S88IdRefType.STEP
                        && from.getValue() != null && !ids.contains(from.getValue())) {
                    ids.add(from.getValue());
                }
            }
        }
        return ids;
    }

    private static boolean publishesBoth(S88Element element, String reportBase,
                                         String setpointBase) {
        try {
            return BaseNameResolver.resolveVariable(element, S88PlantModel.REPORTS, reportBase)
                    .isPresent()
                    && BaseNameResolver.resolveVariable(element, S88PlantModel.PARAMETERS,
                    setpointBase).isPresent();
        } catch (IllegalStateException ambiguous) {
            return false;
        }
    }

    private static boolean anyModulePublishesBoth(S88PlantSnapshot plant, List<String> modules,
                                                  FollowRule rule) {
        for (String id : modules) {
            S88Element module = plant.findById(id).orElse(null);
            if (module != null && publishesBoth(module, rule.reportBase(), rule.setpointBase())) {
                return true;
            }
        }
        return false;
    }

    private static void detectCycles(S88Recipe recipe, List<ChartRef> refs, List<String> errors) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (ChartRef ref : refs) {
            Set<String> next = new LinkedHashSet<>();
            for (S88ProcedureStep step : ref.chart().getSteps()) {
                S88RecipeElement element = recipe.findElement(step.getRecipeElementId()).orElse(null);
                if (element != null && element.getProcedureLogic() != null
                        && element.getId() != null) {
                    next.add(element.getId());
                }
            }
            edges.put(ref.path(), next);
        }
        Map<String, Integer> colour = new HashMap<>();
        Set<String> reported = new LinkedHashSet<>();
        for (String path : edges.keySet()) {
            visit(path, edges, colour, reported, errors);
        }
    }

    private static void visit(String path, Map<String, Set<String>> edges,
                              Map<String, Integer> colour, Set<String> reported,
                              List<String> errors) {
        Integer state = colour.get(path);
        if (state != null && state == 1) {
            if (reported.add(path)) {
                errors.add("The chart of " + label(path)
                        + " contains itself, so it cannot be prepared.");
            }
            return;
        }
        if (state != null && state == 2) {
            return;
        }
        colour.put(path, 1);
        for (String next : edges.getOrDefault(path, Set.of())) {
            visit(next, edges, colour, reported, errors);
        }
        colour.put(path, 2);
    }

    private static String uninterpretable(ChartRef ref, S88ProcedureTransition transition,
                                          String text) {
        return "The condition of transition '" + transition.getId() + "' in chart "
                + label(ref.path()) + " cannot be interpreted: '" + text + "'.";
    }

    private static String label(String path) {
        return path == null || path.isBlank() ? "the recipe" : "'" + path + "'";
    }

    private static Map<String, Object> definitionOf(S88Element element, String container,
                                                    String variable) {
        Map<String, Object> definitions = element.getStructuredProperty(container);
        if (definitions == null) {
            return Map.of();
        }
        Object definition = definitions.get(variable);
        if (!(definition instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static DataType typeOf(Map<String, Object> definition) {
        DataType declared = DataType.fromString(text(definition, TYPE));
        return declared != null ? declared : DataType.STRING;
    }

    private static String text(Map<String, Object> definition, String key) {
        Object value = definition.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean literalFits(DataType dataType, String literal,
                                       List<String> enumerationValues) {
        if (literal == null) {
            return false;
        }
        if (dataType == null || dataType == DataType.STRING) {
            return true;
        }
        return switch (dataType) {
            case INTEGER -> {
                try {
                    Long.parseLong(literal.trim());
                    yield true;
                } catch (NumberFormatException notANumber) {
                    yield false;
                }
            }
            case REAL -> {
                try {
                    Double.parseDouble(literal.trim());
                    yield true;
                } catch (NumberFormatException notANumber) {
                    yield false;
                }
            }
            case ENUMERATION -> enumerationValues.stream()
                    .anyMatch(value -> value.equalsIgnoreCase(literal.trim()));
            default -> false;
        };
    }
}
