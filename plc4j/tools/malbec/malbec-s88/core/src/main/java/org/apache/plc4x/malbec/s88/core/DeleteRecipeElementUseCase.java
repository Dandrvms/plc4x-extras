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

import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Use case for taking a step out of a recipe.
 * <p>
 * This refuses when something still points at the step, and says what. That is the one place where
 * a recipe behaves differently from the plant, where deleting an element is simply a matter of
 * detaching it: a step of a recipe is named in a chart as often as it is anything else, and a box on
 * the chart and a step of the recipe are not the same thing.
 * <p>
 * So the box is removed by {@code RemoveProcedureStepUseCase} and the step by this one, and each
 * refuses while the other still stands. Tearing both down in one go would mean guessing which of the
 * lines touching the box were there to be kept, and a line with one end missing is not something a
 * reader can be expected to put right on their own.
 * <p>
 * Deleting a step takes everything nested under it with it, which is the only sensible reading: a
 * phase with no operations is not a phase. The refusal happens first, so a step that is still
 * pointed at leaves the whole subtree alone.
 */
public class DeleteRecipeElementUseCase {

    private DeleteRecipeElementUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Takes a step, and everything under it, out of a recipe.
     *
     * @param recipe recipe it belongs to, may be {@code null}
     * @param step   step to remove, may be {@code null}
     * @throws IllegalArgumentException when there is no recipe, no step, or the step is not in it
     * @throws IllegalStateException    when a chart still points at the step or at anything under it
     */
    public static void execute(S88Recipe recipe, S88RecipeElement step) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe cannot be null");
        }
        if (step == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (recipe.findElement(step.getId()).map(found -> found != step).orElse(true)) {
            throw new IllegalArgumentException("The step '" + step.getId() + "' is not part of this"
                    + " recipe.");
        }
        if (step.getKind() == S88RecipeElementKind.BEGIN || step.getKind() == S88RecipeElementKind.END) {
            throw new IllegalStateException("Step '" + step.getId() + "' is the "
                    + (step.getKind() == S88RecipeElementKind.BEGIN ? "start" : "stop")
                    + " of the process, so it cannot be removed. Every recipe has one of each.");
        }

        List<String> pointing = pointAt(recipe, step);
        if (!pointing.isEmpty()) {
            throw new IllegalStateException("Step '" + step.getId() + "' is still used by "
                    + String.join(" and ", pointing) + ". Take that off the chart first, or the"
                    + " chart would be left pointing at something that is not there.");
        }

        if (step.isTopLevel()) {
            recipe.removeRecipeElement(step);
        } else {
            recipe.detachFrom(step.getParent(), step);
        }
        recipe.fireChangeEvent(new S88RecipeChangeEvent(S88RecipeChangeEvent.Type.REMOVED, step));
    }

    /**
     * The things still naming this step, which means the boxes on every chart in the recipe that work
     * on it, and the lines those boxes are joined by, since taking a box off a chart that still has
     * lines running into it is refused in its own right.
     * <p>
     * The lines are not looked at for a name of their own. A line names the box it runs between and
     * not the step the box works on, so a line named after a step would be a coincidence of naming
     * rather than a reference, and reading one as the other would refuse deletions for no reason.
     *
     * @param recipe recipe to look through
     * @param step   step being asked about
     * @return one line per thing pointing at it, in the order they were found
     */
    private static List<String> pointAt(S88Recipe recipe, S88RecipeElement step) {
        List<String> pointing = new ArrayList<>();
        String id = step.getId();

        collectBoxes(pointing, id, recipe.getProcedureLogic(), null);

        for (S88RecipeElement element : recipe.getAllElements()) {
            if (id != null && id.equals(element.getId()) && element != step) {
                pointing.add("the step '" + id + "' that has the same name");
            }
            collectBoxes(pointing, id, element.getProcedureLogic(), element.getId());
        }
        return pointing;
    }

    /**
     * The boxes of one chart that work on the step, named after whatever holds that chart.
     *
     * @param pointing list to add to
     * @param id       id of the step being asked about, may be {@code null}
     * @param chart    chart to walk, may be {@code null}
     * @param owner    id of the step or recipe that holds the chart, {@code null} for the recipe
     */
    private static void collectBoxes(List<String> pointing, String id,
                                     S88ProcedureLogic chart, String owner) {
        if (chart == null || id == null) {
            return;
        }
        for (S88ProcedureStep box : chart.getSteps()) {
            if (id.equals(box.getRecipeElementId())) {
                pointing.add("box '" + box.getId() + "' of the chart of "
                        + (owner != null ? "'" + owner + "'" : "the recipe")
                        + ", which works on it");
            }
        }
    }
}
