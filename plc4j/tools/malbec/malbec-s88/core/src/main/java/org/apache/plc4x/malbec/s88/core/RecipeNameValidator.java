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
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;

import java.util.Optional;

/**
 * Whether a recipe is willing to call a step something.
 * <p>
 * A name is used exactly once. A step is named by a module, and two steps both called {@code HEAT}
 * is two claims on one module that nothing can tell apart. The chart points at steps by name, so
 * the ambiguity would reach there too.
 */
public class RecipeNameValidator {

    private static final String STEP_DISPLAY = "Step name";

    private RecipeNameValidator() {
        /* This utility class should not be instantiated */
    }

    /**
     * Whether a step could be called this within this recipe.
     *
     * @param recipe recipe the name would live in, may be {@code null} to check only the form
     * @param name   name to check
     * @return what is wrong with it, or empty when it is acceptable
     */
    public static Optional<String> check(S88Recipe recipe, String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("A step needs a name.");
        }
        if (name.length() > MAX_LENGTH) {
            return Optional.of("The name is longer than " + MAX_LENGTH + " characters, and a recipe"
                    + " is read by tools that will not take it.");
        }
        if (name.contains(S88_VARIABLE_SEPARATOR)) {
            return Optional.of("The name '" + name + "' cannot contain '" + S88_VARIABLE_SEPARATOR
                    + "', which separates the container from the variable in a parameter.");
        }
        if (recipe != null && recipe.findElement(name).isPresent()) {
            return Optional.of("There is already a step called '" + name + "' in this recipe.");
        }
        return Optional.empty();
    }

    /**
     * Refuses a name the recipe cannot use.
     *
     * @param recipe recipe the name would live in, may be {@code null}
     * @param name   name to check
     * @throws IllegalArgumentException when the name is not acceptable
     */
    public static void validate(S88Recipe recipe, String name) {
        check(recipe, name).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    /**
     * Whether a name is free within a recipe and of a form the recipe format can hold.
     *
     * @param recipe recipe the name would live in, may be {@code null}
     * @param name   name to check
     * @return true when the recipe would accept it
     */
    public static boolean isValidName(S88Recipe recipe, String name) {
        return check(recipe, name).isEmpty();
    }

    /**
     * Whether a name is already taken inside a chart.
     * <p>
     * Kept apart from the recipe's own names because the chart has its own: two steps of the chart
     * may both be called {@code HEAT} as long as they are in different charts of different parts of
     * the recipe, and the ends of the lines say which one they mean.
     *
     * @param chart chart to look in, may be {@code null}
     * @param name  name to check
     * @return true when this chart already has a step of that name
     */
    public static boolean isStepTaken(S88ProcedureLogic chart, String name) {
        return chart != null && chart.findStep(name).isPresent();
    }

    /**
     * Whether a name is already taken inside a chart for a line or a bar.
     *
     * @param chart chart to look in, may be {@code null}
     * @param name  name to check
     * @return true when this chart already has a link or a transition of that name
     */
    public static boolean isChartNameTaken(S88ProcedureLogic chart, String name) {
        if (chart == null || name == null) {
            return false;
        }
        return chart.findLink(name).isPresent() || chart.findTransition(name).isPresent();
    }

    /**
     * How long a name may be.
     */
    public static final int MAX_LENGTH = 60;

    /**
     * What stands between a container and a variable inside a parameter, and so cannot appear in the
     * name of a step.
     */
    public static final String S88_VARIABLE_SEPARATOR = S88VariableAddress.SEPARATOR;
}
