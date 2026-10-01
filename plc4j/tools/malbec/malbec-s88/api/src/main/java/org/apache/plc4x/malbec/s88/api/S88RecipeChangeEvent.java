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

/**
 * Event representing a change in a recipe.
 * <p>
 * The element may be {@code null} for a change that belongs to no step in particular, which is the
 * case {@link Type#CHART_UPDATED} exists for: a line moved or a bar retargeted is a change to the
 * chart, and there is no step that owns it. Whoever is drawing the chart redraws the chart.
 */
public record S88RecipeChangeEvent(Type type, S88RecipeElement element, String propertyName) {

    public enum Type {
        /** A step was attached to the recipe or to another step. */
        ADDED,
        /** A step was taken off the recipe, or a step of the chart. */
        REMOVED,
        /** A value, a name or a condition changed. */
        UPDATED,
        /** A step or a line was put somewhere else. */
        MOVED,
        /** The chart changed in a way no single step accounts for. */
        CHART_UPDATED,
        /** The recipe was read back from its file. */
        RELOADED
    }

    public S88RecipeChangeEvent(Type type, S88RecipeElement element) {
        this(type, element, null);
    }

    /**
     * A change to the chart of the recipe, which no one step owns.
     *
     * @return the event, carrying no step
     */
    public static S88RecipeChangeEvent chart() {
        return new S88RecipeChangeEvent(Type.CHART_UPDATED, null, null);
    }
}
