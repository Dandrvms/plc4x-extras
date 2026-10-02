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
 * A named piece of information a recipe carries outside the vocabulary of the standard.
 * <p>
 * The recipe format has no place for a handful of things this tool needs: the class of the
 * equipment a step of a {@link S88RecipeKind#CLASS} recipe applies to, where a box sits on the
 * chart, and the text of a condition whose evaluation is not written down in the recipe. They are
 * kept here under a name, so a recipe written by hand and a recipe written by this tool can be
 * read back the same way and nothing is silently dropped.
 */
public class S88OtherInformation {

    /**
     * The class of the equipment a recipe element of a {@link S88RecipeKind#CLASS} recipe applies
     * to, held as the value of an entry with this id. It is what the recipe is bound to when the
     * plant is loaded, and a recipe of this kind is not runnable without it.
     */
    public static final String EQUIPMENT_CLASS_ID = "EquipmentClassID";

    /**
     * Whether the recipe addresses its equipment by class or by instance, held as the value of an
     * entry with this id.
     * <p>
     */
    public static final String RECIPE_KIND = "RecipeKind";

    /**
     * Where the elements of a chart are drawn, held as the value of an entry with this id. Purely
     * presentational: a recipe that carries no layout still describes the same process and can be
     * run.
     */
    public static final String LAYOUT = "Layout";

    /**
     * The text of a condition that the recipe names but does not express, held as the value of an
     * entry with this id.
     */
    public static final String CONDITION = "Condition";

    private String id;
    private final List<S88ParameterValue> values = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();

    public S88OtherInformation() {
    }

    public S88OtherInformation(String id) {
        this.id = id;
    }

    /** An entry carrying a single value, the shape most of the names above use. */
    public static S88OtherInformation of(String id, String value) {
        S88OtherInformation info = new S88OtherInformation(id);
        S88ParameterValue parameterValue = new S88ParameterValue();
        parameterValue.addValueString(value);
        parameterValue.setDataInterpretation(S88DataInterpretation.CONSTANT);
        info.addValue(parameterValue);
        return info;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<S88ParameterValue> getValues() {
        return Collections.unmodifiableList(values);
    }

    public void addValue(S88ParameterValue value) {
        if (value != null) {
            values.add(value);
        }
    }

    public void removeValue(S88ParameterValue value) {
        values.remove(value);
    }

    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    public void addDescription(String description) {
        if (description != null) {
            descriptions.add(description);
        }
    }

    public void removeDescription(String description) {
        descriptions.remove(description);
    }

    /**
     * The first value string carried by this entry, which is what a single valued name such as
     * {@link #EQUIPMENT_CLASS_ID} is read through.
     *
     * @return the first value, or {@code null} when the entry carries none
     */
    public String getFirstValue() {
        for (S88ParameterValue value : values) {
            String first = value.getFirstValueString();
            if (first != null) {
                return first;
            }
        }
        return null;
    }

    /**
     * Finds the first entry of {@code name} carried by {@code entries}.
     *
     * @param entries entries to search, may be {@code null}
     * @param name     id to look for, matched without regard to case
     * @return the entry, or {@code null} when there is none by that name
     */
    public static S88OtherInformation find(List<S88OtherInformation> entries, String name) {
        if (entries == null || name == null) {
            return null;
        }
        for (S88OtherInformation entry : entries) {
            if (entry != null && name.equalsIgnoreCase(entry.getId())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "S88OtherInformation[" + id + "=" + getFirstValue() + "]";
    }
}
