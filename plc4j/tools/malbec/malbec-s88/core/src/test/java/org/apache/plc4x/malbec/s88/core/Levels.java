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
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Steps of the level the hierarchy calls for, for a test that needs a tree and not just a step.
 *
 * <p>The chart of the recipe is a procedure and holds unit procedures. A unit procedure holds
 * phases. An operation is the older level that used to sit between the two and is still read.
 */
final class Levels {

    private Levels() {
        /* This utility class should not be instantiated */
    }

    /**
     * Adds a unit procedure to the chart of the recipe, with its own empty chart.
     *
     * @param recipe recipe to add it to
     * @param name   name of the step
     * @param unit   unit or class of units it works on, {@code null} for none
     * @return the step that was added
     */
    static S88RecipeElement unitProcedure(S88Recipe recipe, String name, String unit) {
        S88RecipeElement step = CreateRecipeElementUseCase.execute(recipe, null, name,
                S88RecipeElementKind.UNIT_PROCEDURE);
        CreateChartUseCase.forStep(recipe, step);
        if (unit != null) {
            step.setEquipmentClassId(unit);
        }
        return step;
    }

    /**
     * Adds an operation of the older kind under the given step, with its own empty chart.
     *
     * @param recipe  recipe to add it to
     * @param parent  name of the step that owns the chart it goes in
     * @param name    name of the step
     * @return the step that was added
     */
    static S88RecipeElement operation(S88Recipe recipe, String parent, String name) {
        S88RecipeElement step = operationBare(recipe, parent, name);
        CreateChartUseCase.forStep(recipe, step);
        return step;
    }

    /**
     * Adds an operation under the given step, with no chart of its own.
     *
     * <p>For a test that draws the chart itself, because a chart born empty already holds a line
     * with the name the test would give its own.
     *
     * @param recipe  recipe to add it to
     * @param parent  name of the step that owns the chart it goes in
     * @param name    name of the step
     * @return the step that was added
     */
    static S88RecipeElement operationBare(S88Recipe recipe, String parent, String name) {
        S88RecipeElement step = new S88RecipeElement(name, S88RecipeElementKind.OPERATION);
        recipe.attachUnder(owner(recipe, parent), step);
        return step;
    }

    /**
     * Adds a phase under the given step, bound to the module it works on.
     *
     * @param recipe recipe to add it to
     * @param parent name of the step that owns the chart it goes in
     * @param name   name of the step
     * @param module module or class of modules it works on, {@code null} for none
     * @return the step that was added
     */
    static S88RecipeElement phase(S88Recipe recipe, String parent, String name, String module) {
        S88RecipeElement step = CreateRecipeElementUseCase.execute(recipe, owner(recipe, parent),
                name, S88RecipeElementKind.PHASE);
        if (module != null) {
            step.setEquipmentClassId(module);
        }
        return step;
    }

    /**
     * The chart of a step of this recipe.
     *
     * @param recipe recipe the step belongs to
     * @param name   name of the step, {@code null} for the chart of the recipe
     * @return the chart, or {@code null} when the step has none
     */
    static S88ProcedureLogic chartOf(S88Recipe recipe, String name) {
        if (name == null) {
            return recipe.getProcedureLogic();
        }
        return owner(recipe, name).getProcedureLogic();
    }

    /**
     * The step of this recipe of the given name.
     *
     * @param recipe recipe to look in
     * @param name   name of the step
     * @return the step
     */
    static S88RecipeElement owner(S88Recipe recipe, String name) {
        return recipe.findElement(name).orElseThrow(() -> new IllegalArgumentException(
                "This recipe has no step called '" + name + "'."));
    }
}
