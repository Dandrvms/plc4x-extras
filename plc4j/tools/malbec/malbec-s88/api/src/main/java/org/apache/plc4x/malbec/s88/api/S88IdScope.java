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
 * Where the name in an {@link S88IdRef} has to be looked for.
 * <p>
 * A recipe normally refers to the steps and transitions it carries itself, which is
 * {@link #INTERNAL}. When it names something that lives outside that procedure, the reference has
 * to be resolved against the recipe or the plant instead, which is {@link #EXTERNAL}.
 */
public enum S88IdScope {
    /** The name is carried by the procedure logic that holds the reference. */
    INTERNAL("Internal"),
    /** The name belongs to something outside, resolved against the recipe or the plant. */
    EXTERNAL("External"),
    /** Anything the vocabulary does not name. */
    OTHER("Other");

    /** The label this scope takes in the XML, written out rather than derived from the name. */
    private final String xmlName;

    S88IdScope(String xmlName) {
        this.xmlName = xmlName;
    }

    public String getXmlName() {
        return xmlName;
    }

    /**
     * Reads a scope from the text a recipe carries.
     *
     * @param text label to read, may be {@code null}
     * @return the matching scope, or {@code null} when the text names none
     */
    public static S88IdScope fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        for (S88IdScope scope : values()) {
            if (scope.xmlName.equalsIgnoreCase(trimmed) || scope.name().equalsIgnoreCase(trimmed)) {
                return scope;
            }
        }
        return null;
    }
}
