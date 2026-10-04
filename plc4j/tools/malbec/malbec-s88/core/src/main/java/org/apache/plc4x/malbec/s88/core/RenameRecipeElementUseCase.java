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

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Renames a step of a recipe, and everything that points at it.
 * <p>
 * A step is named after an element of the plant, so changing its name is changing which element of
 * the plant the recipe works on. That name is written down wherever a box works on the step, which
 * may be on any chart in the recipe and not only the one the step itself has.
 */
public class RenameRecipeElementUseCase {

    private RenameRecipeElementUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Renames a step and every reference to it.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step to rename, may be {@code null}
     * @param newId  name it is to have
     * @throws IllegalArgumentException when the new name is not one the recipe can use
     * @throws IllegalStateException    when the new name is already taken
     */
    public static void execute(S88Recipe recipe, S88RecipeElement step, String newId) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        if (step == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        String oldId = step.getId();
        if (oldId != null && oldId.equals(newId)) {
            return;
        }

        RecipeNameValidator.validate(recipe, newId);

        if (!recipe.getDuplicateIds().isEmpty()) {
            throw new IllegalStateException("This recipe already has two steps of the same name ("
                    + String.join(", ", recipe.getDuplicateIds()) + "), so it cannot be renamed"
                    + " without making that harder to find. Fix the names it already has first.");
        }

        List<StepRename> renames = plan(recipe, step, oldId, newId);
        for (StepRename rename : renames) {
            rename.apply();
        }
        recipe.fireChangeEvent(new S88RecipeChangeEvent(S88RecipeChangeEvent.Type.UPDATED, step));
    }

    /**
     * Works out what a rename has to touch, without touching any of it.
     */
    private static List<StepRename> plan(S88Recipe recipe, S88RecipeElement step,
                                         String oldId, String newId) {
        List<StepRename> renames = new ArrayList<>();
        for (S88RecipeElement element : recipe.getAllElements()) {
            if (element == step) {
                renames.add(() -> element.setId(newId));
            }
            S88ProcedureLogic chart = element.getProcedureLogic();
            if (chart == null) {
                continue;
            }
            for (S88ProcedureStep box : chart.getSteps()) {
                if (oldId != null && oldId.equals(box.getRecipeElementId())) {
                    renames.add(() -> box.setRecipeElementId(newId));
                }
            }
        }
        return renames;
    }

    private static List<S88IdRef> endsOf(S88ProcedureLogic chart) {
        List<S88IdRef> ends = new ArrayList<>();
        chart.getLinks().forEach(link -> {
            ends.addAll(link.getFrom());
            ends.addAll(link.getTo());
        });
        return ends;
    }

    /**
     * Whether a rename would leave a chart pointing at a step that is not there. Used to check a
     * rename before doing it, and by {@code RecipeConformance} to see whether a chart is whole.
     *
     * @param chart chart to look at, may be {@code null}
     * @return true when every internal end of every line names a step this chart carries
     */
    public static boolean isChartWhole(S88ProcedureLogic chart) {
        if (chart == null) {
            return true;
        }
        for (S88IdRef ref : endsOf(chart)) {
            if (ref.getType() == S88IdRefType.STEP && ref.isInternal()
                    && chart.findStep(ref.getValue()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** One name that has to change, held until everything has been checked. */
    @FunctionalInterface
    private interface StepRename {
        void apply();
    }
}
