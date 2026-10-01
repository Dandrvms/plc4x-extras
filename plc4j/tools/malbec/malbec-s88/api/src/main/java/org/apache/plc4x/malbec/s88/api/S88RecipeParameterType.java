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
 * Whether an {@link S88RecipeParameter} is something that goes in, comes out, or is merely a
 * setting of the process.
 * <p>
 * The distinction decides the direction a value travels, so a reader of the recipe does not push a
 * result back into the equipment it just read.
 */
public enum S88RecipeParameterType {
    /** Material or energy entering the step. */
    PROCESS_INPUT("ProcessInput"),
    /** Material or energy leaving the step. */
    PROCESS_OUTPUT("ProcessOutput"),
    /** A setting of the step that is neither in nor out. */
    PROCESS_PARAMETER("ProcessParameter"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /** The label this type takes in the XML, written out rather than derived from the name. */
    private final String xmlName;

    S88RecipeParameterType(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /**
     * Reads a parameter type from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching type, or {@code null} when the text names none
     */
    public static S88RecipeParameterType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88RecipeParameterType type : values()) {
            if (type.xmlName.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return null;
    }
}
