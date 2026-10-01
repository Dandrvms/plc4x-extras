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
 * may be on any chart in the recipe and not only the one the step itself has. Miss one and that box
 * is left working on a step that is no longer there, which the reader finds out as a chart full of
 * boxes pointing at nothing rather than as a rename.
 * <p>
 * So the whole rename is worked out and checked before anything moves, the same way
 * {@code RenameElementUseCase} does it for the plant. A rename that is going to be refused is refused
 * with the recipe exactly as it was, rather than half applied and left for someone to find.
 * <p>
 * What is deliberately not touched: the names of the boxes on the chart. A line names the box it runs
 * between, which is the chart's own naming and not the recipe's, so a box called {@code BOX_HEAT}
 * keeps that name when the step it works on is renamed. Changing what a box works on is what this
 * does; changing what the box is called is a different thing and belongs to whoever drew it.
 * <p>
 * Nor is anything checked here about the plant. A step that works on equipment, a class recipe, and a
 * step that is itself named for a module: those names come from the plant, and whether the plant
 * still has an element of that name is a question {@code ResolveRecipeUseCase} asks.
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
        // Checked before the name, because the name the recipe is already going to be called is the
        // one name the recipe has. Validating it first would refuse a rename to the name it already
        // has, which is the most harmless thing anybody can ask for.
        RecipeNameValidator.validate(recipe, newId);
        // The index keeps only the first of a pair, so a recipe that already has a clash is not one
        // this can be trusted to keep coherent. Refusing to rename into it is better than renaming
        // and making the clash harder to see.
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
     * <p>
     * Only the boxes of the chart. A line names the box it runs between, not the step the box works
     * on, so the two are separate names in separate places: a box called {@code BOX_HEAT} working on
     * a step called {@code HEAT} keeps its own name when {@code HEAT} is renamed, and a line into
     * {@code BOX_HEAT} is not touched either. The chart's own naming belongs to whoever is drawing
     * it and is changed by renaming a box, which is a different thing from renaming what the box
     * does.
     * <p>
     * Every chart in the recipe is looked through, not only the one of the step being renamed,
     * because a chart drawn at a level above works on a step of its own that may be one of these.
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
