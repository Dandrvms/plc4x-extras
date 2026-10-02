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
 * One value of an {@link S88RecipeParameter}.
 * <p>
 * This carries everything needed to check the value later: the
 * {@link S88DataInterpretation} that says how to read the text, the {@link DataType} of the
 * variable it is aimed at, the unit, and the enumeration it is drawn from.
 */
public class S88ParameterValue {

    private final List<String> valueStrings = new ArrayList<>();
    private S88DataInterpretation dataInterpretation = S88DataInterpretation.CONSTANT;
    private DataType dataType;
    private String unitOfMeasure;
    private final List<String> enumerationSetIds = new ArrayList<>();

    /**
     * The first value string, which is the one a single valued parameter is read through. More than
     * one is allowed because a value can be a list, such as the set of labels an enumeration takes.
     *
     * @return the first value string, or {@code null} when there is none
     */
    public String getFirstValueString() {
        return valueStrings.isEmpty() ? null : valueStrings.get(0);
    }

    public List<String> getValueStrings() {
        return Collections.unmodifiableList(valueStrings);
    }

    public void addValueString(String value) {
        if (value != null) {
            valueStrings.add(value);
        }
    }

    public void setValueStrings(List<String> values) {
        valueStrings.clear();
        if (values != null) {
            for (String value : values) {
                addValueString(value);
            }
        }
    }

    public S88DataInterpretation getDataInterpretation() {
        return dataInterpretation;
    }

    public void setDataInterpretation(S88DataInterpretation dataInterpretation) {
        this.dataInterpretation = dataInterpretation != null ? dataInterpretation : S88DataInterpretation.CONSTANT;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public List<String> getEnumerationSetIds() {
        return Collections.unmodifiableList(enumerationSetIds);
    }

    public void addEnumerationSetId(String id) {
        if (id != null && !id.isBlank()) {
            enumerationSetIds.add(id);
        }
    }

    /**
     * True when this value names an enumeration, either because the type says so or because an
     * enumeration is named alongside. The two are kept consistent with each other by the writer
     * rather than assumed, because a recipe from another tool may carry only one of the two.
     *
     * @return true when the value is to be checked against a set of labels
     */
    public boolean isEnumerated() {
        return dataType == DataType.ENUMERATION || !enumerationSetIds.isEmpty();
    }

    /**
     * The value as a number.
     *
     * @return the value read as an integer, or {@code null} when it is not a whole number
     */
    public Integer asInteger() {
        String text = getFirstValueString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The value as a number.
     *
     * @return the value read as a double, or {@code null} when it is not a number at all
     */
    public Double asDouble() {
        String text = getFirstValueString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "S88ParameterValue[" + getFirstValueString() + ", " + dataType
                + (unitOfMeasure != null ? ", " + unitOfMeasure : "") + "]";
    }
}
