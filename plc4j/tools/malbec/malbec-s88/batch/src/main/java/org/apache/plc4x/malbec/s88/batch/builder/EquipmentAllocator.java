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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;

/** Chooses the plant element each unit procedure and phase works on. */
public final class EquipmentAllocator {

    private EquipmentAllocator() {
        /* This utility class should not be instantiated */
    }

    /** The steps of the recipe mapped to the units and modules that run them. */
    public record Allocation(Map<String, String> moduleOfStep, Map<String, String> unitOfStep) {

        public Allocation {
            moduleOfStep = Collections.unmodifiableMap(new LinkedHashMap<>(moduleOfStep));
            unitOfStep = Collections.unmodifiableMap(new LinkedHashMap<>(unitOfStep));
        }
    }

    public static Allocation allocate(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                      Map<String, String> overrides,
                                      List<PayloadValidator.ChartRef> charts, List<String> errors) {
        Map<String, String> moduleOfStep = new LinkedHashMap<>();
        Map<String, String> unitOfStep = new LinkedHashMap<>();
        if (recipe == null || plant == null || charts == null) {
            return new Allocation(moduleOfStep, unitOfStep);
        }
        Map<String, String> chosen = overrides == null ? Map.of() : overrides;
        for (PayloadValidator.ChartRef chartRef : charts) {
            if (chartRef.chart() == null) {
                continue;
            }
            for (S88ProcedureStep step : chartRef.chart().getSteps()) {
                S88RecipeElement element = recipe.findElement(step.getRecipeElementId()).orElse(null);
                if (element == null) {
                    continue;
                }
                S88RecipeElementKind kind = element.getKind();
                S88Level expected = RecipeLevels.boundTo(kind);
                if (expected == null) {
                    continue;
                }
                String selected = select(recipe, plant, chosen, element, expected, errors);
                if (selected == null) {
                    continue;
                }
                String key = PayloadValidator.key(chartRef.path(), step.getId());
                if (kind == S88RecipeElementKind.UNIT_PROCEDURE) {
                    unitOfStep.put(key, selected);
                } else if (kind == S88RecipeElementKind.PHASE) {
                    moduleOfStep.put(key, selected);
                }
            }
        }
        return new Allocation(moduleOfStep, unitOfStep);
    }

    private static String select(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                 Map<String, String> overrides, S88RecipeElement element,
                                 S88Level expected, List<String> errors) {
        if (recipe.addressesByClass()) {
            return selectForClass(plant, overrides, element, expected, errors);
        }
        return selectForInstance(plant, element, expected, errors);
    }

    private static String selectForClass(S88PlantSnapshot plant, Map<String, String> overrides,
                                         S88RecipeElement element, S88Level expected,
                                         List<String> errors) {
        String classId = element.getEquipmentClassId();
        if (classId == null || classId.isBlank()) {
            errors.add("Step '" + element.getId() + "' names no class of equipment, so there is"
                    + " nothing to allocate.");
            return null;
        }
        String wanted = overrides.get(element.getId());
        if (wanted == null) {
            wanted = overrides.get(classId);
        }
        if (wanted != null) {
            S88Element found = plant.findById(wanted).orElse(null);
            if (found == null) {
                errors.add("The equipment '" + wanted + "' given for step '" + element.getId()
                        + "' is not in the plant.");
                return null;
            }
            if (!isOfClass(found, classId)) {
                errors.add("The equipment '" + wanted + "' given for step '" + element.getId()
                        + "' is not of class '" + classId + "'.");
                return null;
            }
            return checkLevel(found, element, expected, errors) ? wanted : null;
        }
        List<S88Element> candidates = new ArrayList<>();
        for (S88Element candidate : instancesOf(plant.getRoot(), classId)) {
            if (candidate.getLevel() == expected) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            errors.add("There is no equipment of class '" + classId + "' for step '"
                    + element.getId() + "'.");
            return null;
        }
        return candidates.get(0).getId();
    }

    private static String selectForInstance(S88PlantSnapshot plant, S88RecipeElement element,
                                            S88Level expected, List<String> errors) {
        List<String> ids = element.getActualEquipmentIds();
        if (ids.isEmpty()) {
            errors.add("Step '" + element.getId() + "' names no equipment, so there is nothing to"
                    + " allocate.");
            return null;
        }
        String id = ids.get(0);
        S88Element found = plant.findById(id).orElse(null);
        if (found == null) {
            errors.add("The equipment '" + id + "' of step '" + element.getId()
                    + "' is not in the plant.");
            return null;
        }
        return checkLevel(found, element, expected, errors) ? id : null;
    }

    private static boolean checkLevel(S88Element found, S88RecipeElement element,
                                      S88Level expected, List<String> errors) {
        if (found.getLevel() == expected) {
            return true;
        }
        errors.add("Step '" + element.getId() + "' has to work on a " + expected
                + ", but '" + found.getId() + "' is a " + found.getLevel() + ".");
        return false;
    }

    private static List<S88Element> instancesOf(S88Element root, String classId) {
        List<S88Element> found = new ArrayList<>();
        collectInstances(root, classId, found);
        return found;
    }

    private static void collectInstances(S88Element element, String classId,
                                         List<S88Element> found) {
        if (element == null) {
            return;
        }
        if (isOfClass(element, classId)) {
            found.add(element);
        }
        for (S88Element child : element.getChildren()) {
            collectInstances(child, classId, found);
        }
    }

    private static boolean isOfClass(S88Element element, String classId) {
        S88ElementClass direct = element.getElementClass();
        if (direct != null && classId.equals(direct.getName())) {
            return true;
        }
        for (S88ElementClass candidate : element.getElementClasses()) {
            if (candidate != null && classId.equals(candidate.getName())) {
                return true;
            }
        }
        return false;
    }
}
