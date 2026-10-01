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

import java.util.Objects;

/**
 * The address of one variable of the plant, as a container and a name inside it.
 * <p>
 * A recipe of the {@link S88RecipeKind#CLASS} kind names a variable the same way on every module
 * of the plant, which takes two things to say: which of the two containers it lives in, and the
 * name it has there. The recipe format carries a parameter's address as one string, so the two are
 * joined here with {@link #SEPARATOR} and taken apart again when the recipe is read.
 * <p>
 * This is about writing down an address, and nothing else. Turning a base name into the name a
 * particular module publishes it under is the work of the plant, and stays there.
 */
public final class S88VariableAddress {

    /** What stands between the container and the name in the text form of an address. */
    public static final String SEPARATOR = "/";

    private final String container;
    private final String name;

    private S88VariableAddress(String container, String name) {
        this.container = container;
        this.name = name;
    }

    /**
     * An address given as one string, such as {@code Reports/STATE}.
     *
     * @param text the address as the recipe carries it
     * @return the address, or {@code null} when there is no text to read
     */
    public static S88VariableAddress parse(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int split = trimmed.indexOf(SEPARATOR);
        if (split < 0) {
            // No container written down, so the name stands on its own. Which container it is in is
            // then a question for the plant, which is the only thing that knows.
            return new S88VariableAddress(null, trimmed);
        }
        String container = trimmed.substring(0, split).trim();
        String name = trimmed.substring(split + SEPARATOR.length()).trim();
        if (name.isEmpty()) {
            return null;
        }
        return new S88VariableAddress(container.isEmpty() ? null : container, name);
    }

    /**
     * An address given as its two parts.
     *
     * @param container container the variable lives in, may be {@code null} when it is not said
     * @param name      name of the variable inside the container
     * @return the address, or {@code null} when there is no name
     */
    public static S88VariableAddress of(String container, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String trimmedName = name.trim();
        if (container == null || container.trim().isEmpty()) {
            return new S88VariableAddress(null, trimmedName);
        }
        return new S88VariableAddress(container.trim(), trimmedName);
    }

    /**
     * The container the variable lives in.
     *
     * @return the container, or {@code null} when the address did not name one
     */
    public String getContainer() {
        return container;
    }

    /** The name of the variable inside its container. Never {@code null}. */
    public String getName() {
        return name;
    }

    /** True when the address says which container the variable is in. */
    public boolean hasContainer() {
        return container != null;
    }

    /**
     * The address as one string, which is the form the recipe format stores.
     *
     * @return the address in text form, with the container left out when there is none
     */
    public String toText() {
        return hasContainer() ? container + SEPARATOR + name : name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof S88VariableAddress that)) {
            return false;
        }
        return Objects.equals(container, that.container) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, name);
    }

    @Override
    public String toString() {
        return toText();
    }
}
