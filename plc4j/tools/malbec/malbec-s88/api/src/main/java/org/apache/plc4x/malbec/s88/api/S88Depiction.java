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
 * How an {@link S88ProcedureLink} should be drawn.
 * <p>
 * Purely presentational. It is kept apart from {@link S88LinkType} on purpose, because what a link
 * means and how it is drawn are decided separately: a reader of the recipe file should be able to
 * change one without touching the other.
 */
public enum S88Depiction {
    /** Nothing is drawn for this link. */
    NONE("None"),
    /** A plain line. */
    LINE("Line"),
    /** The id of the link. */
    ID("ID"),
    /** A line and the id. */
    LINE_AND_ID("LineAndID"),
    /** A line with an arrowhead and the id. */
    LINE_AND_ARROW("LineAndArrow"),
    /** A line with an arrowhead and the id. */
    LINE_ARROW_AND_ID("LineArrowAndID"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /**
     * The label this depiction takes in the XML. Written out because the recipe format keeps the
     * initials in capitals, as in {@code ID} and {@code LineAndID}, which is not what turning the
     * name of the constant into words would produce.
     */
    private final String xmlName;

    S88Depiction(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /**
     * Reads a depiction from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching depiction, or {@code null} when the text names none
     */
    public static S88Depiction fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88Depiction depiction : values()) {
            if (depiction.xmlName.equalsIgnoreCase(trimmed) || depiction.name().equalsIgnoreCase(trimmed)) {
                return depiction;
            }
        }
        return null;
    }
}
