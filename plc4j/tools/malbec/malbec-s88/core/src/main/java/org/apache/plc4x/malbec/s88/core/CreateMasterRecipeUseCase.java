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
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;

/**
 * Use case for starting a new master recipe.
 * <p>
 * A recipe that has just been created is not an empty file. It carries the two steps every recipe
 * has to have, and the chart that joins them, so that the recipe is a process with a start and a
 * stop the moment it exists rather than something that only becomes one if the engineer remembers.
 * <p>
 * The chart puts a bar between the two. The model is bipartite, so a line drawn straight from one
 * box to another has nothing to follow, and a bar is how the flow says what has to be true before
 * it moves on. Here there is nothing to wait for, so the bar carries no condition and the flow
 * crosses it as soon as the step before it has finished. That is a deliberate reading of the bar
 * and not an oversight: a recipe with nothing in it says nothing about what it will wait for.
 * <p>
 * The two steps name no equipment, and neither is allowed to. They say where the process starts and
 * where it stops, and a recipe that began by naming a class of equipment would have made a claim
 * about a plant that has not been chosen yet. Every other step in the recipe names its equipment
 * the one way the recipe addresses it.
 */
public final class CreateMasterRecipeUseCase {

    /** Id of the step the flow starts at. */
    public static final String BEGIN_ID = "BEGIN";

    /** Id of the step the flow stops at. */
    public static final String END_ID = "END";

    /** Id of the bar the flow crosses on the way from the start to the stop. */
    public static final String FIRST_TRANSITION_ID = "T1";

    /** Id of the line from the start to the bar. */
    public static final String FIRST_LINK_ID = "L1";

    /** Id of the line from the bar to the stop. */
    public static final String LAST_LINK_ID = "L2";

    private CreateMasterRecipeUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Builds a recipe that has its start and its stop and nothing else.
     *
     * @param id   id of the recipe, must not be {@code null} or blank
     * @param kind how the recipe names its equipment, {@code null} meaning by class
     * @return the new recipe
     * @throws IllegalArgumentException when the id is not a name a recipe can carry
     */
    public static S88MasterRecipe execute(String id, S88RecipeKind kind) {
        RecipeNameValidator.validate(null, id);

        S88MasterRecipe recipe = new S88MasterRecipe(id, kind);
        recipe.addRecipeElement(new S88RecipeElement(BEGIN_ID, S88RecipeElementKind.BEGIN));
        recipe.addRecipeElement(new S88RecipeElement(END_ID, S88RecipeElementKind.END));
        recipe.setProcedureLogic(startToStopChart());
        return recipe;
    }

    /**
     * The chart a new recipe starts with: a box at the start, a box at the stop, and a bar between
     * them with a line to each side.
     */
    private static S88ProcedureLogic startToStopChart() {
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep(BEGIN_ID, BEGIN_ID));
        chart.addStep(new S88ProcedureStep(END_ID, END_ID));
        chart.addTransition(new S88ProcedureTransition(FIRST_TRANSITION_ID, null));

        S88ProcedureLink first = new S88ProcedureLink(FIRST_LINK_ID);
        first.addFrom(S88IdRef.step(BEGIN_ID));
        first.addTo(S88IdRef.transition(FIRST_TRANSITION_ID));
        chart.addLink(first);

        S88ProcedureLink last = new S88ProcedureLink(LAST_LINK_ID);
        last.addFrom(S88IdRef.transition(FIRST_TRANSITION_ID));
        last.addTo(S88IdRef.step(END_ID));
        chart.addLink(last);

        return chart;
    }
}
