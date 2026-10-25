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

import java.util.EnumMap;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Which kind of step goes in which chart.
 *
 * <p>The chart of the recipe is a procedure and holds unit procedures. The chart of a unit
 * procedure holds phases. A phase is the last level and has no chart of its own.
 *
 * <p>An operation is still read where an older recipe carries one, and its chart still holds
 * phases, but it is no longer the level a unit procedure holds.
 */
public final class RecipeLevels {

    /** The chart of the recipe, which holds unit procedures. */
    private static final S88RecipeElementKind PROCEDURE = S88RecipeElementKind.PROCEDURE;

    /** The kind a step of each kind holds in its chart. */
    private static final Map<S88RecipeElementKind, S88RecipeElementKind> HOLDS =
            new EnumMap<>(S88RecipeElementKind.class);

    /** The plant level a step of each kind works on. */
    private static final Map<S88RecipeElementKind, S88Level> BOUND_TO =
            new EnumMap<>(S88RecipeElementKind.class);

    static {
        HOLDS.put(S88RecipeElementKind.PROCEDURE, S88RecipeElementKind.UNIT_PROCEDURE);
        HOLDS.put(S88RecipeElementKind.UNIT_PROCEDURE, S88RecipeElementKind.PHASE);
        HOLDS.put(S88RecipeElementKind.OPERATION, S88RecipeElementKind.PHASE);
        BOUND_TO.put(S88RecipeElementKind.UNIT_PROCEDURE, S88Level.UNIT);
        BOUND_TO.put(S88RecipeElementKind.PHASE, S88Level.EQUIPMENTMODULE);
    }

    private RecipeLevels() {
        /* This utility class should not be instantiated */
    }

    /**
     * The kind of step the chart of a step of the given kind holds.
     *
     * @param kind kind of the step that owns the chart
     * @return the kind its chart holds, or {@code null} when its chart holds nothing
     */
    public static S88RecipeElementKind holds(S88RecipeElementKind kind) {
        return kind == null ? HOLDS.get(PROCEDURE) : HOLDS.get(kind);
    }

    /**
     * Whether a step of the given kind can be given a chart of its own.
     *
     * @param kind kind of the step
     * @return {@code true} when a step of that kind holds a chart
     */
    public static boolean allowsSubChart(S88RecipeElementKind kind) {
        return kind != null && HOLDS.containsKey(kind);
    }

    /**
     * The plant level a step of the given kind works on.
     *
     * @param kind kind of the step
     * @return the level, or {@code null} when a step of that kind groups others and works on
     *         nothing itself
     */
    public static S88Level boundTo(S88RecipeElementKind kind) {
        return kind == null ? null : BOUND_TO.get(kind);
    }

    /**
     * The kind of step the chart of the given owner holds, which is the level of that chart.
     *
     * @param owner step that owns the chart, {@code null} for the chart of the recipe
     * @return the kind its chart holds
     */
    public static S88RecipeElementKind levelOf(S88RecipeElement owner) {
        return holds(owner == null ? PROCEDURE : owner.getKind());
    }

    /**
     * The plant level the chart of the given owner works on, which is what a step of that chart
     * is bound to.
     *
     * @param owner step that owns the chart, {@code null} for the chart of the recipe
     * @return the level, or {@code null} when the steps of that chart group others
     */
    public static S88Level boundTo(S88RecipeElement owner) {
        return boundTo(levelOf(owner));
    }

    /**
     * Whether a step of the given kind belongs in the chart of the given owner.
     *
     * @param owner step that owns the chart, {@code null} for the chart of the recipe
     * @param kind  kind of the step being put in it
     * @return {@code true} when a step of that kind belongs there
     */
    public static boolean belongsIn(S88RecipeElement owner, S88RecipeElementKind kind) {
        return kind != null && kind == levelOf(owner);
    }

    /**
     * Whether the plant element is of the level a step of the given kind is bound to.
     *
     * @param kind    kind of the step
     * @param element element of the plant it is being bound to
     * @return {@code true} when the levels agree
     */
    public static boolean bindsTo(S88RecipeElementKind kind, S88Element element) {
        S88Level wanted = boundTo(kind);
        if (wanted == null) {
            return false;
        }
        return element != null && element.getLevel() == wanted;
    }

    /**
     * Whether a step of the given kind is bound to the given plant level.
     *
     * @param kind  kind of the step
     * @param level level of the plant it is being bound to
     * @return {@code true} when the levels agree
     */
    public static boolean bindsTo(S88RecipeElementKind kind, S88Level level) {
        return boundTo(kind) == level;
    }

    /**
     * Whether a class of equipment is of the level a step of the given kind is bound to.
     *
     * @param kind    kind of the step
     * @param elementClass class of the plant it is being bound to
     * @return {@code true} when the levels agree
     */
    public static boolean bindsTo(S88RecipeElementKind kind, S88ElementClass elementClass) {
        return bindsTo(kind, elementClass == null ? null : elementClass.getTargetLevel());
    }
}
