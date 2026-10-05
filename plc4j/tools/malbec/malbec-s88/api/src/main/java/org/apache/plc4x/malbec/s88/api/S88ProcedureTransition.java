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
 * The bar between two steps, and what has to be true for the flow to cross it.
 * <p>
 * What the bar waits on is written as a comparison, and the recipe format stores it as a string in
 * {@link #getCondition()}. {@link #expression()} reads it as an {@link S88ConditionExpression}, so
 * that whoever draws the bar can show what it waits on, and {@link #setExpression} writes one back.
 * The comparison is <b>not</b> worked out here: reading the value and deciding whether the process
 * may continue is the business of whatever runs the recipe.
 * <p>
 * The address is kept as written. Resolving it means finding the module the
 * step is applied to, and which module that is depends on the batch, not on the recipe: the same
 * recipe is set for a different tank on every run and has to mean the same thing each time.
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
        this.condition = condition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** The comparison this bar waits on, exactly as the recipe wrote it. */
    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     * The comparison this bar waits on, read as one.
     *
     * @return the comparison, or {@code null} when the bar waits on nothing, or when the text was
     *         written in a way this does not read
     */
    public S88ConditionExpression expression() {
        return S88ConditionExpression.parse(condition);
    }

    /**
     * Writes a comparison onto this bar, in the text the recipe carries.
     *
     * @param expression comparison to wait on, {@code null} for a bar that waits on nothing
     */
    public void setExpression(S88ConditionExpression expression) {
        this.condition = expression != null ? expression.toText() : null;
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

    /** True when the bar has to wait for something before the flow may cross it. */
    public boolean isGuarded() {
        return condition != null && !condition.isBlank();
    }

    /**
     * True when the flow crosses this bar as soon as the step before it has finished.
     *
     * @return true when nothing has to be true before this bar is crossed
     */
    public boolean crossesAlways() {
        return !isGuarded();
    }

    @Override
    public String toString() {
        return "S88ProcedureTransition[" + id + (isGuarded() ? " when " + condition : ", always") + "]";
    }
}
