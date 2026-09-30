/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88Element;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation rules shared by every user supplied name of the plant model: element ids,
 * property names and class names.
 * <p>
 * Names are restricted to uppercase ASCII letters, digits and the underscore so that they
 * can be reused verbatim as identifiers by the downstream consumers (the batch engine and
 * others) without any further escaping or
 * transliteration.
 * <p>
 * The properties listed in {@link #RESERVED_PROPERTY_NAMES} describe the model itself and
 * are therefore exempt: they are never published as variables and keep their original
 * spelling for backward compatibility with the stored plant files.
 */
public final class NameValidator {

    /**
     * Maximum number of characters accepted for a user supplied name, and also the maximum
     * accepted for a derived variable key.
     */
    public static final int MAX_LENGTH = 60;

    /** Property names owned by the model rather than by the user. */
    public static final Set<String> RESERVED_PROPERTY_NAMES =
            Set.of("xPos", "yPos", "Check", "icon", "description", "targetLevel", S88Element.UID_PROPERTY);

    private static final Pattern ALLOWED = Pattern.compile("[A-Z0-9_]+");

    private NameValidator() {
        /* This utility class should not be instantiated */
    }

    /**
     * Tells whether the given name is one of the properties owned by the model, which are
     * exempt from the naming rules.
     *
     * @param name candidate name, may be {@code null}
     * @return {@code true} when the name is reserved by the model
     */
    public static boolean isReservedProperty(String name) {
        return name != null && RESERVED_PROPERTY_NAMES.contains(name);
    }

    /**
     * Tells whether the given name complies with every naming rule.
     *
     * @param name candidate name, may be {@code null}
     * @return {@code true} when the name is valid
     */
    public static boolean isValidName(String name) {
        return check(name, "Name").isEmpty();
    }

    /**
     * Inspects the given name without throwing, so that callers able to surface a live
     * warning (an editor preview, for instance) can reuse the very same rules that the use
     * cases enforce.
     *
     * @param name  candidate name, may be {@code null}
     * @param label human-readable name of what is being validated, used to build the message
     * @return the reason why the name is rejected, or {@link Optional#empty()} when it is valid
     */
    public static Optional<String> check(String name, String label) {
        if (name == null || name.isBlank()) {
            return Optional.of(label + " cannot be empty.");
        }
        if (name.length() > MAX_LENGTH) {
            return Optional.of(label + " '" + name + "' is too long: " + name.length()
                    + " characters, the maximum is " + MAX_LENGTH
                    + ". Please use a shorter name.");
        }
        if (!ALLOWED.matcher(name).matches()) {
            return Optional.of(label + " '" + name + "' contains invalid characters. "
                    + "Only uppercase letters (A-Z), digits (0-9) and underscore (_) are allowed.");
        }
        return Optional.empty();
    }

    /**
     * Enforces every naming rule, failing fast on the first violation.
     *
     * @param name  candidate name
     * @param label human-readable name of what is being validated
     * @throws IllegalArgumentException when the name is empty, too long or holds invalid characters
     */
    public static void validate(String name, String label) {
        check(name, label).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    /**
     * Enforces the naming rules on a property of an element, letting through the properties
     * reserved by the model.
     *
     * @param name candidate property name
     * @throws IllegalArgumentException when the name breaks a rule and is not reserved
     */
    public static void validateProperty(String name) {
        if (isReservedProperty(name)) {
            return;
        }
        validate(name, "Property name");
    }

    /**
     * Enforces the naming rules on an entry written into a property container.
     * <p>
     * A {@code containerKey} of {@code null} addresses a top level property, where the names
     * reserved by the model may appear; inside a container such as {@code Parameters} every
     * entry is a variable and therefore has to comply with the rules.
     *
     * @param containerKey container holding the entry, {@code null} for a top level property
     * @param entryKey     candidate entry name
     * @throws IllegalArgumentException when the name breaks a rule and is not reserved
     */
    public static void validateEntry(String containerKey, String entryKey) {
        if (containerKey == null && isReservedProperty(entryKey)) {
            return;
        }
        validate(entryKey, "Entry name");
    }
}
