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
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;

/**
 * What a step of a recipe is: one piece of equipment with values given to its parameters.
 *
 * <p>
 * <b>A step is created empty and named here.</b> A box with nothing behind it is a place the flow
 * goes through and nothing else, which is what a step is until it is given something to do. The name
 * is not asked for either: it is the name of the equipment, because a name the operator types is a
 * name that can be typed wrong, and a recipe full of equipment that is not there is a recipe
 * nobody can run.
 *
 * <p>
 * <b>The two kinds of recipe are bound differently, and mixing them up is the mistake this class is
 * written to be hard about.</b> A class recipe names a class of equipment and leaves the choice of
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
    public static List<String> choices(S88PlantSnapshot plant, boolean byClass) {
        if (plant == null) {
            return List.of();
        }
        return byClass ? classesFor(plant) : equipmentFor(plant);
    }

    /**
     * The classes a step of a class recipe can stand for.
     * <p>
     * Units as well as equipment modules, because a unit is written as the steps of the modules it
     * is made of, and a recipe that names a unit says the same thing as one that names the modules
     * of that unit.
     */
    private static List<String> classesFor(S88PlantSnapshot plant) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (S88ElementClass elementClass : plant.getClassesForChildLevel(S88Level.EQUIPMENTMODULE)) {
            byName.putIfAbsent(elementClass.getName(), elementClass.getName());
        }
        for (S88ElementClass elementClass : plant.getClassesForChildLevel(S88Level.UNIT)) {
            byName.putIfAbsent(elementClass.getName(), elementClass.getName());
        }
        return List.copyOf(byName.values());
    }

    /**
     * The equipment a step of one piece of equipment recipe can stand for, in the order they sit in the plant.
     */
    private static List<String> equipmentFor(S88PlantSnapshot plant) {
        return plant.getRoot() == null
                ? List.of()
                : equipmentUnder(plant.getRoot(), new java.util.ArrayList<>());
    }

    private static List<String> equipmentUnder(S88Element element, List<String> found) {
        if (element.getLevel() == S88Level.EQUIPMENTMODULE) {
            found.add(element.getId());
        }
        for (S88Element child : element.getChildren()) {
            equipmentUnder(child, found);
        }
        return found;
    }

    /**
     * Binds a step to a class of equipment, which is what a
     * {@link org.apache.plc4x.malbec.s88.api.S88RecipeKind#CLASS} recipe does.
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
        // A class recipe names no equipment, so nothing is written to the equipment fields and the
        // binding is the class alone.
        element.setEquipmentUid(null);
        removeEveryEquipment(element);
        element.setEquipmentClassId(className);
        // The class publishes base names. They are written down as they are and are resolved to a
        // concrete equipment later, which is a question for the batch and not for this recipe.
        giveParametersFrom(element, equipmentClass.getProperties());
        nameAfter(recipe, step, chart, boxId, className);
        return element;
    }

    /**
     * Binds a step to one piece of equipment, which is what an
     * {@link org.apache.plc4x.malbec.s88.api.S88RecipeKind#INSTANCE} recipe does.
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
        element.setEquipmentClassId(null);
        removeEveryEquipment(element);
        element.addActualEquipmentId(equipmentId);
        // The uid rather than the name, so that renaming the equipment in the plant leaves this
        // recipe saying the same equipment.
        element.setEquipmentUid(equipment.getUid());
        // A piece of equipment publishes names of its own and there is nothing left to resolve, so what it
        // publishes is what the recipe writes down.
        giveParametersFrom(element, equipment.getProperties());
        nameAfter(recipe, step, chart, boxId, equipmentId);
        return element;
    }

/**
     * Takes every piece of equipment off a step, so that binding it to a class leaves nothing of what
     * it had before.
     * <p>
     * A step bound to a class and a step bound to particular equipment are two different things, and
     * a step carrying both says it stands for one piece of equipment and for any of a class at the same time.
     */
    private static void removeEveryEquipment(S88RecipeElement element) {
        for (String boundId : element.getActualEquipmentIds()) {
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
                        "This chart has no box called '" + boxId + "'."));
        String elementId = box.getRecipeElementId();
        return recipe.findElement(elementId)
                .orElseThrow(() -> new IllegalArgumentException("Box '" + boxId + "' works on '"
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
        String free = freeName(recipe, chart, wanted);
        renameElement(recipe, chart, boxId, free);
        EditProcedureLogicUseCase.renameStep(recipe, step, boxId, free);
    }

    /**
 * Puts an empty step on the chart after a box, and asks nothing.
 *
 * <p>
 * <b>A step is made empty and named afterwards.</b> A box that the flow goes through and that has
 * nothing behind it is a step that has not been given anything to do yet, which is a thing an author
 * makes on purpose when drawing the shape of a process before knowing what will do the work. Asking
 * for a name at this point would mean asking for it twice, and the name that counts is the one the
 * equipment gives.
 *
 * <p>
 * The names are made here and are never shown: the step is called something, and it stops being
 * called that the moment one piece of equipment is bound to it.
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
                "BOX_" + elementId, "T_" + elementId, null);
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
                && !isTaken(recipe, chart, "BOX_" + stem)
                && !isTaken(recipe, chart, "T_" + stem);
    }

    private static boolean isTaken(S88Recipe recipe, S88ProcedureLogic chart, String candidate) {
        return chart.findStep(candidate).isPresent()
                || chart.findTransition(candidate).isPresent()
                || recipe.findElement(candidate).isPresent();
    }

    /**
     * A name of that shape that nothing in the chart or in the recipe is using.
     */
    private static String freeName(S88Recipe recipe, S88ProcedureLogic chart, String wanted) {
        String candidate = wanted;
        int more = 1;
        while (isTaken(recipe, chart, candidate)) {
            candidate = wanted + "_" + more++;
        }
        return candidate;
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
            // Through the recipe, so that its index of steps by name keeps up. A step whose name
            // changed while the index kept the old one cannot be found by anything that looks it up.
            recipe.renameRecipeElement(element, newId);
        }
    }

    /**
     * A step that has finished the process has no equipment to attach.
     *
     * @throws IllegalStateException when the step is the end of the process
     */
    private static void requireNotStopped(S88RecipeElement element) {
        if (element.getKind() == org.apache.plc4x.malbec.s88.api.S88RecipeElementKind.END) {
            throw new IllegalStateException("Step '" + element.getId() + "' is where the flow stops,"
                    + " so there is nothing to attach to it.");
        }
    }

    /**
     * Writes down a value for every parameter the equipment declares.
     * <p>
     * The value written is the one the plant says the parameter starts at, which is what the equipment
     * would be holding if nobody had touched it. An operator then changes the ones the recipe is
     * about, and the ones they did not change say what the equipment already does.
     */
    @SuppressWarnings("unchecked")
    private static void giveParametersFrom(S88RecipeElement element, Map<String, Object> properties) {
        Object declared = properties.get(PARAMETERS);
        if (!(declared instanceof Map<?, ?> container)) {
            return;
        }
        Map<String, Object> byName = (Map<String, Object>) container;
        for (Map.Entry<String, Object> declaredParameter : byName.entrySet()) {
            if (element.findParameter(declaredParameter.getKey()).isPresent()
                    || !(declaredParameter.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> definition = (Map<String, Object>) raw;
            DataType dataType = dataTypeOf(text(definition, "Type"));
            String unitsOrEnum = text(definition, UNITS_OR_ENUM);
            // An enumeration has no unit. The plant keeps the name of the one in the same place it
            // keeps a unit, so writing it as a unit would export a parameter saying its value is
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