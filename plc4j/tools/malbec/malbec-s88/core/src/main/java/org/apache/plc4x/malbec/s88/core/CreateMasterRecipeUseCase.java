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
 * Starting a new master recipe. It adds a BEGIN and an END element joined by a transition bar.
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
        recipe.setProcedureLogic(CreateChartUseCase.empty());
        return recipe;
    }
}
