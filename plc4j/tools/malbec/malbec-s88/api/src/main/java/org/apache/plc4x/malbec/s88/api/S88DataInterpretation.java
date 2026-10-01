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
 * How to read the value carried by an {@link S88ParameterValue}.
 * <p>
 * A value in a recipe is not always a number somebody typed. It can be a constant, a name that
 * points somewhere else, or an expression that has to be worked out. This says which, so a reader
 * does not try to add up the name of a variable.
 */
public enum S88DataInterpretation {
    /** The text is the value. */
    CONSTANT("Constant"),
    /** The text names something whose value is used instead. */
    REFERENCE("Reference"),
    /** The text is an expression to be evaluated. */
    EQUATION("Equation"),
    /** The value is held outside the recipe and is fetched from there. */
    EXTERNAL("External"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /** The label this interpretation takes in the XML, written out rather than derived from the name. */
    private final String xmlName;

    S88DataInterpretation(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /** True when the text has to be worked out rather than used as it stands. */
    public boolean isComputed() {
        return this == EQUATION || this == REFERENCE;
    }

    /**
     * Reads an interpretation from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching interpretation, or {@code null} when the text names none
     */
    public static S88DataInterpretation fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88DataInterpretation interpretation : values()) {
            if (interpretation.xmlName.equalsIgnoreCase(trimmed)
                    || interpretation.name().equalsIgnoreCase(trimmed)) {
                return interpretation;
            }
        }
        return null;
    }
}
