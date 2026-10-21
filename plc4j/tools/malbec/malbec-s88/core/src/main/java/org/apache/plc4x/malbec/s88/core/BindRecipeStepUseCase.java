/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.plc4x.malbec.s88.api.*;

/**
 * What a step of a recipe is: one piece of equipment with values given to its parameters.
 * <p>
 * A step is created empty and named here. A class recipe names a class of equipment and leaves the choice of
 * equipment to the batch, so the variables it carries are the base names the class publishes and
 * something else resolves them later. An equipment recipe names the equipment itself, so its variables
 * are the names that equipment publishes and there is nothing left to resolve.
 *
 * @see #bindToClass(S88Recipe, S88RecipeElement, String, String, S88PlantSnapshot)
 * @see #bindToEquipment(S88Recipe, S88RecipeElement, String, String, S88PlantSnapshot)
 */
public final class BindRecipeStepUseCase {

    /** Where a step takes its parameters from. */
    private static final String PARAMETERS = "Parameters";

    /** The engineering unit of a variable, or the name of its enumeration when it is one. */
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";

    private BindRecipeStepUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * What a step of this kind of recipe can be bound to, in the order it should be offered.
     *
     * @param plant the plant the recipe is written against
     * @param byClass whether the recipe names classes of equipment or the equipment itself
     * @return the names an operator can choose from, which are class names or equipment ids
     */
    public static List<String> choices(S88PlantSnapshot plant, boolean byClass,
                                       S88RecipeElementKind kind) {
        if (plant == null || RecipeLevels.boundTo(kind) == null) {
            return List.of();
        }
        return byClass
                ? classesFor(plant, RecipeLevels.boundTo(kind))
                : equipmentFor(plant, RecipeLevels.boundTo(kind));
    }

    /**
     * The classes of the given level a step can stand for.
     */
    private static List<String> classesFor(S88PlantSnapshot plant, S88Level level) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (S88ElementClass elementClass : plant.getClassesForChildLevel(level)) {
            byName.putIfAbsent(elementClass.getName(), elementClass.getName());
        }
        return List.copyOf(byName.values());
    }

    /**
     * The equipment of the given level a step can stand for, in the order they sit in the plant.
     */
    private static List<String> equipmentFor(S88PlantSnapshot plant, S88Level level) {
        return plant.getRoot() == null
                ? List.of()
                : equipmentUnder(plant.getRoot(), new java.util.ArrayList<>(), level);
    }

    private static List<String> equipmentUnder(S88Element element, List<String> found,
                                               S88Level level) {
        if (element.getLevel() == level) {
            found.add(element.getId());
        }
        for (S88Element child : element.getChildren()) {
            equipmentUnder(child, found, level);
        }
        return found;
    }

    /**
     * Binds a step to a class of equipment.
     *
     * @param recipe    recipe being edited, may be {@code null} when no change event is wanted
     * @param step      step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId     box of the chart the step is on
     * @param className name of the class of equipment the step stands for
     * @param plant     the plant the recipe is written against
     * @return the step that was bound
     * @throws IllegalArgumentException when there is no recipe, no chart, no plant, or no such class
     */
    public static S88RecipeElement bindToClass(S88Recipe recipe, S88RecipeElement step,
                                               String boxId, String className,
                                               S88PlantSnapshot plant) {
        S88ProcedureLogic chart = chartOf(recipe, step, plant);
        S88ElementClass equipmentClass = plant.findClass(className);
        if (equipmentClass == null) {
            throw new IllegalArgumentException("The plant has no class of equipment called '"
                    + className + "'.");
        }
        S88RecipeElement element = elementOn(recipe, chart, boxId);
        requireNotStopped(element);
        requireTheRightLevel(element, equipmentClass.getTargetLevel(), className);

        element.setEquipmentUid(null);
        removeEveryEquipment(element);
        element.setEquipmentClassId(className);

        giveParametersFrom(element, equipmentClass.getProperties());
        nameAfter(recipe, step, chart, boxId, className);
        return element;
    }

    /**
     * Binds a step to one piece of equipment.
     *
     * @param recipe   recipe being edited, may be {@code null} when no change event is wanted
     * @param step     step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId    box of the chart the step is on
     * @param equipmentId name the equipment has in the plant, which is also how the step is named
     * @param plant    the plant the recipe is written against
     * @return the step that was bound
     * @throws IllegalArgumentException when there is no recipe, no chart, no plant, or no such equipment
     */
    public static S88RecipeElement bindToEquipment(S88Recipe recipe, S88RecipeElement step,
                                                   String boxId, String equipmentId,
                                                   S88PlantSnapshot plant) {
        S88ProcedureLogic chart = chartOf(recipe, step, plant);
        S88Element equipment = plant.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("The plant has no equipment called '"
                        + equipmentId + "'."));
        S88RecipeElement element = elementOn(recipe, chart, boxId);
        requireNotStopped(element);
        requireTheRightLevel(element, equipment.getLevel(), equipmentId);
        element.setEquipmentClassId(null);
        removeEveryEquipment(element);
        element.addActualEquipmentId(equipmentId);

        element.setEquipmentUid(equipment.getUid());

        giveParametersFrom(element, equipment.getProperties());
        nameAfter(recipe, step, chart, boxId, equipmentId);
        return element;
    }

    /**
     * Takes every piece of equipment off a step, so that binding it to a class leaves nothing of what
     * it had before.
     */
    private static void removeEveryEquipment(S88RecipeElement element) {
        for (String boundId : List.copyOf(element.getActualEquipmentIds())) {
            element.removeActualEquipmentId(boundId);
        }
    }

    /**
     * The step a box of the chart works on.
     *
     * @param recipe the recipe the chart belongs to
     * @param chart  the chart the box is on
     * @param boxId  name of the box
     * @return the step behind the box
     * @throws IllegalArgumentException when there is no such box or it works on no step
     */
    private static S88RecipeElement elementOn(S88Recipe recipe, S88ProcedureLogic chart,
                                              String boxId) {
        S88ProcedureStep box = chart.findStep(boxId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This chart has no step called '" + boxId + "'."));
        String elementId = box.getRecipeElementId();
        return recipe.findElement(elementId)
                .orElseThrow(() -> new IllegalArgumentException("Step '" + boxId + "' works on '"
                        + elementId + "', which this recipe does not carry."));
    }

    private static S88ProcedureLogic chartOf(S88Recipe recipe, S88RecipeElement step,
                                             S88PlantSnapshot plant) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to bind a step on.");
        }
        if (plant == null) {
            throw new IllegalArgumentException("There is no plant to bind a step to. A step is a piece"
                    + " of equipment, and without the plant there is nothing to name.");
        }
        return chart;
    }

    /**
     * Names the step and its box after the equipment.
     * <p>
     * Two steps of one piece of equipment in one recipe cannot both be called the same thing, so the second one
     * carries a number, the same way the plant numbers two pieces of equipment of one class.
     */
    private static void nameAfter(S88Recipe recipe, S88RecipeElement step, S88ProcedureLogic chart,
                                  String boxId, String wanted) {
        S88RecipeElement element = elementOn(recipe, chart, boxId);
        String free = freeName(recipe, chart, wanted, element);
        renameElement(recipe, chart, boxId, free);
        EditProcedureLogicUseCase.renameStep(recipe, step, boxId, free);
    }

    /**
 * Puts an empty step on the chart after a box.
 *
 * @param recipe     recipe being edited, may be {@code null} when no change event is wanted
 * @param step       step whose chart is being drawn on, {@code null} for the chart of the recipe
 * @param afterBoxId box of the chart the empty step goes after
 * @return the empty step that was added
 * @throws IllegalArgumentException when there is no recipe, no chart, or no such box
 * @throws IllegalStateException    when the step to go after already goes to more than one place
 */
    public static S88RecipeElement addEmptyStepAfter(S88Recipe recipe, S88RecipeElement step,
                                                     String afterBoxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to add a step to.");
        }
        String elementId = reserveNames(recipe, chart, "STEP");
        return EditProcedureLogicUseCase.insertStepAfter(recipe, step, afterBoxId, elementId,
                elementId, "T_" + elementId, null);
    }

    /**
     * Puts an empty step in front of another one.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param beforeBoxId box of the chart the empty step goes before
     * @return the empty step that was added
     * @throws IllegalArgumentException when there is no recipe, no chart, or no such box
     * @throws IllegalStateException    when the box is where the flow starts, or more than one
     *                                  line reaches it
     */
    public static S88RecipeElement addEmptyStepBefore(S88Recipe recipe, S88RecipeElement step,
                                                      String beforeBoxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to add a step to.");
        }
        String elementId = reserveNames(recipe, chart, "STEP");
        return EditProcedureLogicUseCase.insertStepBefore(recipe, step, beforeBoxId, elementId,
                elementId, "T_" + elementId, null);
    }

    /**
     * Puts an empty step at the start of one branch of a block.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param afterBoxId box of the step that splits the block
     * @param branchId transition that opens the branch
     * @return the empty step that was added
     * @throws IllegalArgumentException when there is no recipe, no chart, or no such box or branch
     * @throws IllegalStateException    when the chosen place is not a branch point
     */
    public static S88RecipeElement addEmptyStepInBranch(S88Recipe recipe, S88RecipeElement step,
                                                        String afterBoxId, String branchId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to add a step to.");
        }
        String elementId = reserveNames(recipe, chart, "STEP");
        return EditProcedureLogicUseCase.insertStepAfter(recipe, step, afterBoxId, elementId,
                elementId, "T_" + elementId, null, InsertMode.IN_BRANCH, branchId);
    }

    private static String reserveNames(S88Recipe recipe, S88ProcedureLogic chart, String stem) {
        String candidate = stem;
        int more = 1;
        while (!isFree(recipe, chart, candidate)) {
            candidate = stem + "_" + more++;
        }
        return candidate;
    }

    private static boolean isFree(S88Recipe recipe, S88ProcedureLogic chart, String stem) {
        return !isTaken(recipe, chart, stem)
                && !isTaken(recipe, chart, "T_" + stem);
    }

    private static boolean isTaken(S88Recipe recipe, S88ProcedureLogic chart, String candidate) {
        return chart.findStep(candidate).isPresent()
                || chart.findTransition(candidate).isPresent()
                || recipe.findElement(candidate).isPresent();
    }

    /**
     * A name of that shape that nothing else in the chart or in the recipe is using.
     *
     * <p>The step being bound is not a clash with itself: binding it to the equipment it already
     * works on asks for the name it already has, and that is the name it keeps. A second step of
     * the same equipment still gets a number, because that one is a different step.
     *
     * @param element the step being bound, whose own name and box are not clashes
     */
    private static String freeName(S88Recipe recipe, S88ProcedureLogic chart, String wanted,
                                   S88RecipeElement element) {
        String candidate = wanted;
        int more = 1;
        while (isTakenByAnother(recipe, chart, candidate, element)) {
            candidate = wanted + "_" + more++;
        }
        return candidate;
    }

    /** Whether the name is used by something other than the step being bound. */
    private static boolean isTakenByAnother(S88Recipe recipe, S88ProcedureLogic chart,
                                            String candidate, S88RecipeElement element) {
        return !belongsToThisStep(recipe, chart, candidate, element)
                && isTaken(recipe, chart, candidate);
    }

    /** Whether the name is the one this step already answers to, in the recipe or on the chart. */
    private static boolean belongsToThisStep(S88Recipe recipe, S88ProcedureLogic chart,
                                             String candidate, S88RecipeElement element) {
        if (element != null && candidate.equals(element.getId())) {
            return true;
        }
        return chart.findStep(candidate)
                .map(box -> element != null
                        && element.getId().equals(box.getRecipeElementId()))
                .orElse(false);
    }

    /**
     * Renames the step and takes every box that works on it along.
     */
    private static void renameElement(S88Recipe recipe, S88ProcedureLogic chart, String boxId,
                                      String newId) {
        S88RecipeElement element = elementOn(recipe, chart, boxId);
        String oldId = element.getId();
        if (!oldId.equals(newId)) {
            for (S88ProcedureStep box : chart.getSteps()) {
                if (oldId.equals(box.getRecipeElementId())) {
                    box.setRecipeElementId(newId);
                }
            }
            rewriteBoxes(recipe.getProcedureLogic(), oldId, newId);
            recipe.renameRecipeElement(element, newId);
        }
    }

    private static void rewriteBoxes(S88ProcedureLogic chart, String oldId, String newId) {
        if (chart == null) {
            return;
        }
        for (S88ProcedureStep box : chart.getSteps()) {
            if (oldId.equals(box.getRecipeElementId())) {
                box.setRecipeElementId(newId);
            }
        }
    }

    /**
     * A step works on the plant level its own level calls for, and nothing else.
     *
     * @param element step being bound
     * @param level   level of the plant it is being bound to
     * @param name    name it is being bound to
     * @throws IllegalStateException when the levels do not agree
     */
    private static void requireTheRightLevel(S88RecipeElement element, S88Level level, String name) {
        if (RecipeLevels.bindsTo(element.getKind(), level)) {
            return;
        }
        throw new IllegalStateException("Step '" + element.getId() + "' is an "
                + element.getKind() + ", which works on "
                + RecipeLevels.boundTo(element.getKind()) + ", and '" + name + "' is a " + level
                + ". Bind it to the level its own calls for.");
    }

    /**
     * A step that has finished the process has no equipment to attach.
     *
     * @throws IllegalStateException when the step is the end of the process
     */
    private static void requireNotStopped(S88RecipeElement element) {
        if (element.getKind() == S88RecipeElementKind.END) {
            throw new IllegalStateException("Step '" + element.getId() + "' is where the flow stops,"
                    + " so there is nothing to attach to it.");
        }
    }

    /**
     * Writes down a value for every parameter the equipment declares.
     * <p>
     * The value written is the one the plant says the parameter starts at, which is what the equipment
     * would be holding if nobody had touched it. A user then changes the ones the recipe is
     * about, and the ones they did not change say what the equipment already does.
     */
    @SuppressWarnings("unchecked")
    private static void giveParametersFrom(S88RecipeElement element, Map<String, Object> properties) {
        Object declared = properties.get(PARAMETERS);
        if (!(declared instanceof Map<?, ?> container)) {
            dropParametersNotDeclaredBy(element, Set.of());
            return;
        }
        Map<String, Object> byName = (Map<String, Object>) container;
        dropParametersNotDeclaredBy(element, byName.keySet());
        for (Map.Entry<String, Object> declaredParameter : byName.entrySet()) {
            if (element.findParameter(declaredParameter.getKey()).isPresent()
                    || !(declaredParameter.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> definition = (Map<String, Object>) raw;
            DataType dataType = dataTypeOf(text(definition, "Type"));
            String unitsOrEnum = text(definition, UNITS_OR_ENUM);

            // An enumeration has no engineering units. The plant keeps the name of the one in the same place it
            // keeps a unit. Writing it as an engineering unit would export a parameter saying its value is
            // measured in "COMMAND", which is a name and not a measure.
            S88RecipeParameter added = S88RecipeParameter.of(
                    declaredParameter.getKey(),
                    text(definition, "Default"),
                    dataType,
                    dataType == DataType.ENUMERATION ? null : unitsOrEnum);
            if (dataType == DataType.ENUMERATION && unitsOrEnum != null
                    && added.getFirstValue() != null) {
                added.getFirstValue().addEnumerationSetId(unitsOrEnum);
            }
            element.addParameter(added);
        }
    }

    private static String text(Map<String, Object> definition, String key) {
        Object value = definition.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Takes off every parameter the equipment being bound to does not declare, so a step that
     * changes which equipment it works on carries nothing of the one it worked on before.
     *
     * @param element  step being bound
     * @param declared names of the parameters the equipment declares
     */
    private static void dropParametersNotDeclaredBy(S88RecipeElement element, Set<String> declared) {
        List<S88RecipeParameter> ofAnotherEquipment = element.getParameters().stream()
                .filter(parameter -> !declared.contains(parameter.getId()))
                .toList();
        ofAnotherEquipment.forEach(element::removeParameter);
    }

    /**
     * The type the plant declared, in the words the plant uses.
     */
    private static DataType dataTypeOf(String declared) {
        if (declared == null) {
            return DataType.STRING;
        }
        return switch (declared.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "INTEGER" -> DataType.INTEGER;
            case "REAL" -> DataType.REAL;
            case "ENUMERATION" -> DataType.ENUMERATION;
            default -> DataType.STRING;
        };
    }
}