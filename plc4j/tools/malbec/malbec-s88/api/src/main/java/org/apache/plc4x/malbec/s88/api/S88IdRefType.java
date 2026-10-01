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
 * What an {@link S88IdRef} points at inside the procedure logic.
 * <p>
 * A link may join steps, but it may also join transitions, which are the bars between steps in a
 * sequential function chart. Naming the kind is what tells a reader of the recipe whether an
 * endpoint is a box or a bar.
 */
public enum S88IdRefType {
    /** An endpoint is a {@link S88ProcedureStep}. */
    STEP("Step"),
    /** An endpoint is a {@link S88ProcedureTransition}. */
    TRANSITION("Transition"),
    /** An endpoint is another {@link S88ProcedureLink}. */
    LINK("Link"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /** The label this kind takes in the XML, written out rather than derived from the name. */
    private final String xmlName;

    S88IdRefType(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /**
     * Reads a reference kind from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching kind, or {@code null} when the text names none
     */
    public static S88IdRefType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88IdRefType type : values()) {
            if (type.xmlName.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return null;
    }
}
