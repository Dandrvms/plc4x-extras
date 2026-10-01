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
 * How an {@link S88MasterRecipe} names the equipment and the variables it works with.
 * <p>
 * The two ways of addressing are the whole difference between a recipe written once and used on
 * whatever equipment is free, and a recipe written for one particular piece of it. It is a
 * property of the recipe, not of the step: the same recipe addresses every one of its steps the
 * same way.
 */
public enum S88RecipeKind {
    /**
     * The recipe names the class of the equipment and the base name of the variables, so it can
     * be bound to any module of that class. This is the recipe you write when you do not yet know
     * which tank is going to run.
     */
    CLASS,
    /**
     * The recipe names the equipment and the variables of one particular module. This is the
     * recipe you write when you do know, and it needs nothing but the recipe file to be run.
     */
    INSTANCE;

    /**
     * Reads a kind from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching kind, or {@code null} when the text names none
     */
    public static S88RecipeKind fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88RecipeKind kind : values()) {
            if (kind.name().equalsIgnoreCase(trimmed)) {
                return kind;
            }
        }
        return null;
    }
}
