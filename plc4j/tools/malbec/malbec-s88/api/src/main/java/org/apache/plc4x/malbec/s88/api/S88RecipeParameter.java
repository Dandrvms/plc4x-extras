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
package org.apache.plc4x.malbec.s88.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A value a step of a recipe sets, reads, or both.
 * <p>
 * The {@link #getId() id} is the address of the variable this parameter applies to, and what it
 * looks like depends on how the recipe addresses its equipment. A
 * {@link S88RecipeKind#CLASS} recipe writes the base name of the variable prefixed with its
 * container, such as {@code Reports/STATE}, which is the same on every module of the plant. A
 * {@link S88RecipeKind#INSTANCE} recipe writes the name the variable was published under, such as
 * {@code STATE_CALENTAMIENTO_TANQUE_1}, which names one module. The id is kept exactly as written
 * here, and {@link S88MasterRecipe} is what knows how to read it.
 */
public class S88RecipeParameter {

    private String id;
    private String description;
    private S88RecipeParameterType parameterType = S88RecipeParameterType.PROCESS_PARAMETER;
    private final List<String> parameterSubTypes = new ArrayList<>();
    private final List<S88ParameterValue> values = new ArrayList<>();
    private Boolean scaled;
    private String scaleReference;
    private final List<S88RecipeParameter> parameters = new ArrayList<>();

    public S88RecipeParameter() {
    }

    public S88RecipeParameter(String id) {
        this.id = id;
    }

    /**
     * A parameter carrying one constant, which is the shape almost every parameter has.
     *
     * @param id           address of the variable
     * @param value        the value to apply
     * @param dataType     type of the variable, may be {@code null} to leave it unsaid
     * @param unit         unit of the value, may be {@code null}
     * @return the parameter
     */
    public static S88RecipeParameter of(String id, String value, DataType dataType, String unit) {
        S88RecipeParameter parameter = new S88RecipeParameter(id);
        S88ParameterValue parameterValue = new S88ParameterValue();
        parameterValue.addValueString(value);
        parameterValue.setDataInterpretation(S88DataInterpretation.CONSTANT);
        parameterValue.setDataType(dataType);
        parameterValue.setUnitOfMeasure(unit);
        parameter.addValue(parameterValue);
        return parameter;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public S88RecipeParameterType getParameterType() {
        return parameterType;
    }

    public void setParameterType(S88RecipeParameterType parameterType) {
        this.parameterType = parameterType != null ? parameterType : S88RecipeParameterType.PROCESS_PARAMETER;
    }

    public List<String> getParameterSubTypes() {
        return Collections.unmodifiableList(parameterSubTypes);
    }

    public void addParameterSubType(String subType) {
        if (subType != null && !subType.isBlank()) {
            parameterSubTypes.add(subType);
        }
    }

    public List<S88ParameterValue> getValues() {
        return Collections.unmodifiableList(values);
    }

    public void addValue(S88ParameterValue value) {
        if (value != null) {
            values.add(value);
        }
    }

    /**
     * Throws the values this parameter holds away and puts these in their place.
     * <p>
     * The one way to do this, rather than a {@code clear} beside the getter, because the value of a
     * parameter is one thing: a recipe that says a step is set to 75 and also says it is set to 80
     * is a recipe that says two things, and which of them the plant would honour is a question
     * nobody should have to guess at.
     *
     * @param newValues values to hold, ignored when {@code null}
     */
    public void setValues(List<S88ParameterValue> newValues) {
        values.clear();
        if (newValues != null) {
            for (S88ParameterValue value : newValues) {
                addValue(value);
            }
        }
    }

    /** The first value of this parameter, which is the one applied when there is only one. */
    public S88ParameterValue getFirstValue() {
        return values.isEmpty() ? null : values.get(0);
    }

    public Boolean getScaled() {
        return scaled;
    }

    public void setScaled(Boolean scaled) {
        this.scaled = scaled;
    }

    public String getScaleReference() {
        return scaleReference;
    }

    public void setScaleReference(String scaleReference) {
        this.scaleReference = scaleReference;
    }

    /**
     * Parameters carried by this one, used for a value that is itself made of named parts. The
     * nesting follows the recipe format and is kept as written rather than flattened.
     */
    public List<S88RecipeParameter> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public void addParameter(S88RecipeParameter parameter) {
        if (parameter != null) {
            parameters.add(parameter);
        }
    }

    /**
     * Takes a nested parameter off this one.
     *
     * @param parameter parameter to take off, ignored when {@code null}
     * @return true when this parameter was holding it and no longer does
     */
    public boolean removeParameter(S88RecipeParameter parameter) {
        return parameter != null && parameters.remove(parameter);
    }

    @Override
    public String toString() {
        return "S88RecipeParameter[" + id + "=" + (getFirstValue() != null
                ? getFirstValue().getFirstValueString() : null) + "]";
    }
}
