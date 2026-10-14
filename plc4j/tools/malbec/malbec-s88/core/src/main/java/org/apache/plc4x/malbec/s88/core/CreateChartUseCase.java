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
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * The chart a step of a level that holds one is given.
 *
 * <p>Every chart is born the same way: the start joined to the stop by one line through a bar.
 * The author then puts steps in between.
 */
public final class CreateChartUseCase {

    /** Id of the bar a new chart carries. */
    public static final String FIRST_TRANSITION_ID = "T1";

    /** Id of the line from the start to the bar. */
    public static final String FIRST_LINK_ID = "L1";

    /** Id of the line from the bar to the stop. */
    public static final String LAST_LINK_ID = "L2";

    private CreateChartUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Builds the chart a new recipe or a new step starts with.
     *
     * @return the empty chart
     */
    public static S88ProcedureLogic empty() {
        S88ProcedureLogic chart = new S88ProcedureLogic();
        chart.addStep(new S88ProcedureStep(CreateMasterRecipeUseCase.BEGIN_ID,
                CreateMasterRecipeUseCase.BEGIN_ID));
        chart.addStep(new S88ProcedureStep(CreateMasterRecipeUseCase.END_ID,
                CreateMasterRecipeUseCase.END_ID));
        chart.addTransition(new S88ProcedureTransition(FIRST_TRANSITION_ID, null));

        S88ProcedureLink first = new S88ProcedureLink(FIRST_LINK_ID);
        first.addFrom(S88IdRef.step(CreateMasterRecipeUseCase.BEGIN_ID));
        first.addTo(S88IdRef.transition(FIRST_TRANSITION_ID));
        chart.addLink(first);

        S88ProcedureLink last = new S88ProcedureLink(LAST_LINK_ID);
        last.addFrom(S88IdRef.transition(FIRST_TRANSITION_ID));
        last.addTo(S88IdRef.step(CreateMasterRecipeUseCase.END_ID));
        chart.addLink(last);

        return chart;
    }

    /**
     * Gives a step the chart its level calls for, when it does not have one already.
     *
     * @param recipe recipe the step belongs to, {@code null} for no change event
     * @param step   step to give a chart to, ignored when {@code null}
     * @return the chart the step now holds, or {@code null} when its level has none
     */
    public static S88ProcedureLogic forStep(S88Recipe recipe, S88RecipeElement step) {
        if (step == null || !RecipeLevels.allowsSubChart(step.getKind())) {
            return step == null ? null : step.getProcedureLogic();
        }
        if (step.getProcedureLogic() == null) {
            step.setProcedureLogic(empty());
        }
        if (recipe != null) {
            recipe.fireChangeEvent(new org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent(
                    org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent.Type.UPDATED, step));
        }
        return step.getProcedureLogic();
    }

    /**
     * Whether a step of the given kind is the last level and holds no chart.
     *
     * @param kind kind of the step
     * @return {@code true} when the step is the last level
     */
    public static boolean isLastLevel(S88RecipeElementKind kind) {
        return kind == S88RecipeElementKind.PHASE;
    }
}
