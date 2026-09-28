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
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The name a variable is published under, which is the name written in the plant file.
 * <p>
 * The tool offers a naming convention as a suggestion the user can
 * apply, and warns when a name is already taken or too long, but it never rewrites a name on its
 * own.
 */
public final class VariableKeySupport {

    /** Property holding the setpoints and commands of an element. */
    public static final String PARAMETERS = "Parameters";

    /** Property holding the values read back from an element. */
    public static final String REPORTS = "Reports";

    /** Properties whose value is a map of named entries, each entry being a variable. */
    private static final Set<String> CONTAINER_KEYS = Set.of(PARAMETERS, REPORTS);

    private VariableKeySupport() {
        /* This utility class should not be instantiated */
    }

    /**
     * A single addressable variable: its name and the path of the property that owns it.
     *
     * @param key   the name the variable is published under
     * @param owner readable path of the property, used to report conflicts
     */
    public record VariableKey(String key, String owner) {
    }

    /**
     * Two or more properties published under the same name.
     *
     * @param key    the colliding variable name
     * @param owners paths of every property published under it
     */
    public record VariableKeyConflict(String key, List<String> owners) {
    }

    /**
     * A variable as it is found in the model, before it is reduced to what a caller needs. The
     * element and property are kept so a lookup can tell "somebody else already uses this name" from
     * "this is the very property being edited".
     *
     * @param element  element owning the property
     * @param property name of the variable as stored
     * @param key      the name the variable is published under
     * @param owner    readable path of the property, used to report conflicts
     */
    private record Entry(S88Element element, String property, String key, String owner) {

        private VariableKey toVariableKey() {
            return new VariableKey(key, owner);
        }
    }

    /**
     * This normalizes case and surrounding blanks only, so that a lookup finds a name
     * whichever way it was typed.
     *
     * @param element  element owning the property, used to tell a variable from a blank name
     * @param property name of the property as stored
     * @return the published name, or {@code null} when there is none
     */
    public static String resolve(S88Element element, String property) {
        if (element == null || property == null || property.isBlank()) {
            return null;
        }
        return property.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Lists every variable of the plant in a deterministic order, walking the tree depth
     * first and preserving the declaration order of the properties.
     *
     * @param model plant to inspect, may be {@code null}
     * @return the variables, empty when the model is empty
     */
    public static List<VariableKey> list(S88PlantModel model) {
        List<VariableKey> result = new ArrayList<>();
        for (Entry entry : entries(model)) {
            result.add(entry.toVariableKey());
        }
        return result;
    }

    private static List<Entry> entries(S88PlantModel model) {
        List<Entry> result = new ArrayList<>();
        if (model != null) {
            collect(model.getRoot(), result);
        }
        return result;
    }

    /**
     * Reports the names published by more than one property. A collision means two properties are
     * published under one name, so the downstream mapping cannot tell them apart.
     *
     * @param model plant to inspect, may be {@code null}
     * @return the collisions, ordered by name
     */
    public static List<VariableKeyConflict> findConflicts(S88PlantModel model) {
        Map<String, List<String>> ownersByKey = new LinkedHashMap<>();
        for (Entry entry : entries(model)) {
            ownersByKey.computeIfAbsent(entry.key(), k -> new ArrayList<>()).add(entry.owner());
        }

        Map<String, List<String>> sorted = new TreeMap<>(ownersByKey);
        List<VariableKeyConflict> conflicts = new ArrayList<>();
        for (var entry : sorted.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(new VariableKeyConflict(entry.getKey(), List.copyOf(entry.getValue())));
            }
        }
        return conflicts;
    }

    /**
     * Reports the variable names that exceed the maximum accepted length.
     *
     * @param model plant to inspect, may be {@code null}
     * @return the offending variables, ordered by name
     */
    public static List<VariableKey> findOverlongKeys(S88PlantModel model) {
        List<VariableKey> result = new ArrayList<>();
        for (Entry entry : entries(model)) {
            if (entry.key().length() > NameValidator.MAX_LENGTH) {
                result.add(entry.toVariableKey());
            }
        }
        result.sort((a, b) -> a.key().compareTo(b.key()));
        return result;
    }

    /**
     * Fails when the plant publishes colliding or oversized variable keys, describing every
     * problem found so the user can fix them in one pass.
     *
     * @param model plant to inspect
     * @throws IllegalStateException when at least one conflict or oversized key exists
     */
    public static void validate(S88PlantModel model) {
        List<VariableKeyConflict> conflicts = findConflicts(model);
        List<VariableKey> overlong = findOverlongKeys(model);
        if (conflicts.isEmpty() && overlong.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("The variable names of this plant need attention:");
        for (VariableKeyConflict conflict : conflicts) {
            message.append(System.lineSeparator())
                    .append("  - '").append(conflict.key()).append("' is used by ")
                    .append(conflict.owners().size()).append(" properties:")
                    .append(System.lineSeparator())
                    .append("      ").append(String.join(System.lineSeparator() + "      ", conflict.owners()));
        }
        for (VariableKey variable : overlong) {
            message.append(System.lineSeparator())
                    .append("  - '").append(variable.key()).append("' is too long (")
                    .append(variable.key().length()).append(" characters, the maximum is ")
                    .append(NameValidator.MAX_LENGTH).append("), owned by ").append(variable.owner())
                    .append(". Please use a shorter name.");
        }
        throw new IllegalStateException(message.toString());
    }

    /**
     * Reports every property of the plant already publishing the given name.
     * @param model plant to inspect, may be {@code null}
     * @param key   name to look for, compared case insensitively as names are upper case
     * @return the owners of the name, empty when the name is still free
     */
    public static List<String> findOwnersOf(S88PlantModel model, String key) {
        return findOwnersOf(model, key, null, null);
    }

    /**
     * Reports the properties already publishing the given name, ignoring one of them.
     * @param model           plant to inspect, may be {@code null}
     * @param key             name to look for, compared case insensitively as names are upper case
     * @param editingElement  element whose property is being edited, {@code null} to ignore nothing
     * @param editingProperty name of the property being edited, {@code null} to ignore nothing
     * @return the other owners of the name, empty when the name is free
     */
    public static List<String> findOwnersOf(S88PlantModel model, String key,
                                            S88Element editingElement, String editingProperty) {
        if (model == null || !isFilled(key)) {
            return List.of();
        }
        String wanted = key.trim().toUpperCase(Locale.ROOT);
        String editingName = editingProperty != null ? editingProperty.trim().toUpperCase(Locale.ROOT) : null;
        List<String> owners = new ArrayList<>();
        for (Entry entry : entries(model)) {
            if (!entry.key().equals(wanted)) {
                continue;
            }
            if (editingElement != null && editingName != null
                    && entry.element() == editingElement
                    && entry.property().toUpperCase(Locale.ROOT).equals(editingName)) {
                continue;
            }
            owners.add(entry.owner());
        }
        return owners;
    }

    /**
     * Lists the variables published by an element and by everything below it.
     *
     * @param root element to inspect, may be {@code null}
     * @return the variables of the subtree, in tree order
     */
    public static List<VariableKey> listSubtree(S88Element root) {
        List<Entry> found = new ArrayList<>();
        collect(root, found);
        List<VariableKey> result = new ArrayList<>();
        for (Entry entry : found) {
            result.add(entry.toVariableKey());
        }
        return result;
    }

    private static void collect(S88Element element, List<Entry> out) {
        if (element == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : element.getProperties().entrySet()) {
            String name = entry.getKey();
            if (NameValidator.isReservedProperty(name)) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> container && CONTAINER_KEYS.contains(name)) {
                for (Object childKey : container.keySet()) {
                    addVariable(element, String.valueOf(childKey), name + "." + childKey, out);
                }
            } else {
                addVariable(element, name, name, out);
            }
        }
        for (S88Element child : element.getChildren()) {
            collect(child, out);
        }
    }

    private static void addVariable(S88Element element, String property, String suffix, List<Entry> out) {
        String key = resolve(element, property);
        if (key != null) {
            out.add(new Entry(element, property, key, path(element) + "." + suffix));
        }
    }

    private static String path(S88Element element) {
        List<String> ids = new ArrayList<>();
        for (S88Element candidate = element; candidate != null; candidate = candidate.getParent()) {
            if (isFilled(candidate.getId())) {
                ids.add(0, candidate.getId());
            }
        }
        return String.join("/", ids);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
