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

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88DataInterpretation;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;

import java.util.List;

/**
 * Gives a value to one of a step's parameters, or takes it away.
 * <p>
 * A parameter of a recipe is the address of a variable of the element the step works on, and this is
 * what puts a value against it. Which address it is depends on how the recipe addresses its
 * equipment. A recipe written by class says  {@code Parameters/TARGET_TEMPERATURE}, which is the same
 * on every module of that class, and one written for particular equipment says the name that module
 * published. Either way the name written is the name kept, and {@code ResolveRecipeUseCase} is what
 * reads it against the plant.
 * <p>
 * The value is stored as text, exactly as given, with the type and the unit alongside it. Deciding
 * whether 75 is acceptable for a variable that takes a number, or whether {@code COMPLETE} is one of
 * the labels a state may take, needs the plant. Guessing at it here would mean a recipe that was
 * checked against the wrong thing, and a recipe is the one thing that has to be right.
 */
public class UpdateRecipeParameterUseCase {

    private UpdateRecipeParameterUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Sets what a parameter of a step is to, adding the parameter if the step has not got it yet.
     *
     * @param recipe   recipe the step belongs to, may be {@code null} when there is nothing to tell
     * @param step     step the parameter belongs to, may not be {@code null}
     * @param address  address of the variable, which is what the parameter is called
     * @param value    value to give it, may be {@code null} to set it empty
     * @param dataType type of the variable, may be {@code null} when it is not being said
     * @param unit     unit of the value, may be {@code null}
     * @return the parameter
     * @throws IllegalArgumentException when there is no step, or the parameter names no variable
     */
    public static S88RecipeParameter execute(S88Recipe recipe, S88RecipeElement step, String address,
                                             String value, DataType dataType, String unit) {
        if (step == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("A parameter has to name the variable it is for.");
        }

        S88RecipeParameter parameter = step.findParameter(address).orElseGet(() -> {
            S88RecipeParameter created = new S88RecipeParameter(address);
            step.addParameter(created);
            return created;
        });

        S88ParameterValue parameterValue = new S88ParameterValue();
        if (value != null) {
            parameterValue.addValueString(value);
        }
        parameterValue.setDataInterpretation(S88DataInterpretation.CONSTANT);
        parameterValue.setDataType(dataType);
        parameterValue.setUnitOfMeasure(unit);
        parameter.setValues(List.of(parameterValue));

        if (recipe != null) {
            recipe.fireChangeEvent(new S88RecipeChangeEvent(
                    S88RecipeChangeEvent.Type.UPDATED, step, address));
        }
        return parameter;
    }

    /**
     * Sets a value against a variable named the way a recipe written by class names it, so the caller
     * gives the container and the base name separately and gets back the address they were filed
     * under.
     *
     * @param recipe    recipe the step belongs to, may be {@code null}
     * @param step      step the parameter belongs to
     * @param container container the variable lives in, such as {@code Parameters} or {@code Reports}
     * @param baseName  base name of the variable, such as {@code TARGET_TEMPERATURE}
     * @param value     value to give it
     * @return the address the parameter was filed under
     */
    public static String byBaseName(S88Recipe recipe, S88RecipeElement step, String container,
                                    String baseName, String value) {
        String address = container + org.apache.plc4x.malbec.s88.api.S88VariableAddress.SEPARATOR
                + baseName;
        execute(recipe, step, address, value, null, null);
        return address;
    }

    /**
     * Takes a parameter off a step, so the recipe no longer says anything about that variable.
     *
     * @param recipe  recipe the step belongs to, may be {@code null}
     * @param step    step the parameter belongs to
     * @param address address of the variable the parameter is for
     * @return true when there was such a parameter and it is gone
     */
    public static boolean remove(S88Recipe recipe, S88RecipeElement step, String address) {
        if (step == null || address == null) {
            return false;
        }
        S88RecipeParameter parameter = step.findParameter(address).orElse(null);
        if (parameter == null) {
            return false;
        }
        step.removeParameter(parameter);

        for (S88RecipeParameter outer : step.getParameters()) {
            if (outer.removeParameter(parameter)) {
                break;
            }
        }
        if (recipe != null) {
            recipe.fireChangeEvent(new S88RecipeChangeEvent(
                    S88RecipeChangeEvent.Type.UPDATED, step, address));
        }
        return true;
    }
}
