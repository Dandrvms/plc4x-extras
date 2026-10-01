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
 * What a {@link S88RecipeElement} is.
 * <p>
 * The kind tells how deep the element goes into the process, which is what a reader needs in
 * order to know whether a step it sees is the whole recipe or one phase of a unit procedure.
 * <p>
 * {@link #BEGIN} and {@link #END} are here rather than inferred from the graph. An element tagged
 * as one of them is where the flow starts and stops, which is not something that can be worked out
 * from the links alone once a recipe is allowed to have several segments.
 */
public enum S88RecipeElementKind {
    /** The whole recipe, the outermost element. */
    PROCEDURE("Procedure"),
    /** The whole recipe, the outermost element. */
    UNIT_RECIPE("UnitRecipe"),
    /** An ordered set of operations performed by one unit. */
    UNIT_PROCEDURE("UnitProcedure"),
    /** A single transformation, the usual target of a step. */
    OPERATION("Operation"),
    /** A subdivision of an operation, the level the equipment module executes. */
    PHASE("Phase"),
    /** A reservation of equipment that another part of the recipe then uses. */
    ALLOCATION("Allocation"),
    /** Where the flow starts. */
    BEGIN("Begin"),
    /** Where the flow stops. */
    END("End"),
    /** A separately compiled portion of a recipe. */
    RECIPE_SEGMENT("RecipeSegment"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /**
     * The label this kind takes in the XML. Written out rather than derived from the name of the
     * constant, because the two are not the same and the label is the one the recipe format fixes:
     * {@code UNIT_PROCEDURE} is written {@code UnitProcedure}, which no reading of the name of the
     * constant would produce.
     */
    private final String xmlName;

    S88RecipeElementKind(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /**
     * Reads a kind from the text a recipe carries.
     * <p>
     * Both the XML label and the name of the constant are accepted, so a value written by hand as
     * {@code UNIT_RECIPE} is understood as well as one written as {@code UnitRecipe}.
     *
     * @param text label to read, may be {@code null}
     * @return the matching kind, or {@code null} when the text names none
     */
    public static S88RecipeElementKind fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88RecipeElementKind kind : values()) {
            if (kind.xmlName.equalsIgnoreCase(trimmed) || kind.name().equalsIgnoreCase(trimmed)) {
                return kind;
            }
        }
        return null;
    }

    /** True when the kind is one of the two that bound the flow. */
    public boolean isTerminal() {
        return this == BEGIN || this == END;
    }
}
