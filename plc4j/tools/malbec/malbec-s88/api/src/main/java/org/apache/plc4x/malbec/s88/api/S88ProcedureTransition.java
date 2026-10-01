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
 * The bar between two steps, and the report of the equipment that has to be read for the flow to
 * cross it.
 * <p>
 * This is where a recipe reads the plant. A transition names a variable the module publishes under
 * its reports, such as {@code Reports/STATE}, and the flow crosses the bar when that variable says
 * what the recipe is waiting for. So a condition is an address in the same form a parameter of a
 * class recipe uses, and {@link S88VariableAddress} is what reads it.
 * <p>
 * The name is kept as written rather than resolved here. Resolving it means finding the module the
 * step is applied to, and which module that is depends on the batch, not on the recipe: the same
 * recipe is set for a different tank on every run, and the recipe has to mean the same thing each
 * time. That is why the recipe names the variable and whoever runs the recipe decides what it reads
 * it from.
 * <p>
 * What the value is compared against is not in the recipe either. The recipe format carries a name
 * here, and the text of a comparison, when there is one, is kept as an
 * {@link S88OtherInformation#CONDITION} entry.
 */
public class S88ProcedureTransition {

    private String id;
    private String condition;
    private String conditionAnnotation;
    private final List<String> descriptions = new ArrayList<>();

    public S88ProcedureTransition() {
    }

    public S88ProcedureTransition(String id, String condition) {
        this.id = id;
        setCondition(condition);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** The name of the report this bar waits on, kept exactly as the recipe wrote it. */
    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     * The report this bar waits on, as an address.
     * <p>
     * Null when the bar names no report, which is a real thing a recipe can have: a bar that is
     * crossed because the previous step finished, rather than because something was read.
     *
     * @return the address of the report, or {@code null} when there is none to read
     */
    public S88VariableAddress conditionAddress() {
        return S88VariableAddress.parse(condition);
    }

    /**
     * A note about the condition, for the person reading the recipe rather than for anything that
     * runs it.
     */
    public String getConditionAnnotation() {
        return conditionAnnotation;
    }

    public void setConditionAnnotation(String conditionAnnotation) {
        this.conditionAnnotation = conditionAnnotation;
    }

    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    public void addDescription(String description) {
        if (description != null) {
            descriptions.add(description);
        }
    }

    /** True when the bar names a condition at all. A recipe may draw one that guards nothing. */
    public boolean isGuarded() {
        return condition != null && !condition.isBlank();
    }

    @Override
    public String toString() {
        return "S88ProcedureTransition[" + id + (isGuarded() ? " when " + condition : ", unguarded") + "]";
    }
}
