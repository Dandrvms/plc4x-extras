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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the base names of the variables an element publishes.
 * <p>
 * The plant model records one direction only: a concrete variable points at the base name it was
 * derived from. A recipe speaks the other way round. A master recipe names the type of the
 * equipment it needs and addresses its variables by base name; binding that recipe to an instance
 * means asking which variable of that instance answers to a given base name. {@code TEMPERATURA_SP}
 * becomes {@code TEMPERATURA_SP_OLLA_1} on one instance and {@code TEMPERATURA_SP_OLLA_2} on the
 * next, and both are answered here without the recipe knowing any concrete name.
 * <p>
 * A variable written by hand carries no recorded base name, so its own name is the base name. The
 * resolution is therefore total: every variable of a container has exactly one base name. It is
 * not necessarily injective, though. Two variables can answer to the same base name, and
 * {@link #resolveVariable(S88Element, String, String)} refuses to pick one of them rather than
 * choose silently.
 */
public final class BaseNameResolver {

    private BaseNameResolver() {
        /* This utility class should not be instantiated */
    }

    /**
     * The base name a variable of an element is published under: the recorded one, or the
     * variable's own name when it was created by hand.
     *
     * @param element      element owning the variable, may be {@code null}
     * @param containerKey container holding the variable, may be {@code null}
     * @param variableName concrete name of the variable, may be {@code null}
     * @return the effective base name, or {@code null} when there is no variable name
     */
    public static String baseNameOf(S88Element element, String containerKey, String variableName) {
        if (variableName == null) {
            return null;
        }
        String recorded = element != null ? element.getBaseName(containerKey, variableName) : null;
        if (recorded != null) {
            String base = BaseNameSupport.baseNameOf(recorded);
            if (base != null && !base.isBlank()) {
                return base;
            }
        }
        return BaseNameSupport.suggestBaseName(variableName, element != null ? element.getId() : null);
    }

    /**
     * The variable of an element that answers to a base name.
     * <p>
     * The comparison ignores case and surrounding blanks, because a recipe may be typed with
     * either while a variable name is normalized on the way in.
     *
     * @param element      element owning the variable, may be {@code null}
     * @param containerKey container holding the variable, may be {@code null}
     * @param baseName     base name a recipe addresses, may be {@code null}
     * @return the concrete variable name, or {@link Optional#empty()} when no variable has that
     *         base name
     * @throws IllegalStateException when more than one variable answers to the base name, which
     *         means the element cannot be bound unambiguously
     */
    public static Optional<String> resolveVariable(S88Element element, String containerKey, String baseName) {
        if (element == null || baseName == null || baseName.isBlank()) {
            return Optional.empty();
        }
        String wanted = normalize(baseName);
        List<String> matches = new ArrayList<>();
        for (String variable : variablesOf(element, containerKey)) {
            if (wanted.equals(normalize(baseNameOf(element, containerKey, variable)))) {
                matches.add(variable);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Base name '" + baseName.trim().toUpperCase(Locale.ROOT)
                    + "' is answered by " + matches.size() + " variables of '"
                    + (element.getId() != null ? element.getId() : "the element") + "': " + matches
                    + ". Only one may publish it, or the recipe cannot be bound to this element.");
        }
        return matches.stream().findFirst();
    }

    /**
     * The base names of a container mapped to the variable answering each one.
     * <p>
     * Ambiguous base names are left out. A recipe that binds to
     * this element is told about the ambiguity by {@link #validate(S88Element)}.
     *
     * @param element      element owning the variables, may be {@code null}
     * @param containerKey container holding the variables, may be {@code null} for the top level
     * @return base name to variable name, in declaration order, empty when the container holds no
     *         variable
     */
    public static Map<String, String> resolveAll(S88Element element, String containerKey) {
        Map<String, String> result = new LinkedHashMap<>();
        if (element == null) {
            return result;
        }
        for (String variable : variablesOf(element, containerKey)) {
            String base = baseNameOf(element, containerKey, variable);
            if (base == null || result.containsKey(base)) {
                continue;
            }
            result.put(base, variable);
        }
        return result;
    }

    /**
     * The base names published by more than one variable of an element.
     * <p>
     * Such a base name is the one thing that makes a class recipe ambiguous: the recipe addresses
     * the base name and cannot say which variable it meant, so a reference to it cannot be resolved
     * for this instance. Duplicated ids are reported separately by
     * {@link org.apache.plc4x.malbec.s88.api.S88PlantModel#getDuplicateIds()}: one duplicated id
     * is usually what puts two variables onto the same base name.
     *
     * @param element element to inspect, may be {@code null}
     * @return the conflicts, in container and declaration order, empty when the element is sound
     */
    public static List<Conflict> validate(S88Element element) {
        if (element == null) {
            return List.of();
        }
        List<Conflict> conflicts = new ArrayList<>();
        for (String container : containersOf(element)) {
            Map<String, List<String>> variablesByBase = new LinkedHashMap<>();
            for (String variable : variablesOf(element, container)) {
                String base = baseNameOf(element, container, variable);
                if (base != null) {
                    variablesByBase.computeIfAbsent(base, k -> new ArrayList<>()).add(variable);
                }
            }
            for (Map.Entry<String, List<String>> entry : variablesByBase.entrySet()) {
                if (entry.getValue().size() > 1) {
                    conflicts.add(new Conflict(container, entry.getKey(), List.copyOf(entry.getValue())));
                }
            }
        }
        return conflicts;
    }

    /**
     * Every variable of an element answering to a base name, across all its containers.
     * <p>
     * This is what a class recipe asks for: the base name {@code TEMPERATURA_SP} and the plant
     * hands back every temperature variable of every instance of that class, so the caller can
     * choose the one it was assigned.
     *
     * @param element  element to inspect, may be {@code null}
     * @param baseName base name a recipe addresses, may be {@code null}
     * @return the matching variable names, in container and declaration order
     */
    public static List<VariableRef> resolveAcrossContainers(S88Element element, String baseName) {
        if (element == null || baseName == null || baseName.isBlank()) {
            return List.of();
        }
        String wanted = normalize(baseName);
        List<VariableRef> matches = new ArrayList<>();
        for (String container : containersOf(element)) {
            for (String variable : variablesOf(element, container)) {
                if (wanted.equals(normalize(baseNameOf(element, container, variable)))) {
                    matches.add(new VariableRef(container, variable,
                            element.getUid() != null ? element.getUid() : element.getId()));
                }
            }
        }
        return matches;
    }

    /**
     * The containers an element publishes variables in: the top level plus every variable container
     * it actually holds.
     */
    private static List<String> containersOf(S88Element element) {
        List<String> containers = new ArrayList<>();
        containers.add(null);
        for (String container : List.of(VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS)) {
            if (element.getProperties().get(container) instanceof Map<?, ?>) {
                containers.add(container);
            }
        }
        return containers;
    }

    private static List<String> variablesOf(S88Element element, String containerKey) {
        if (element == null) {
            return List.of();
        }
        List<String> variables = new ArrayList<>();
        Object raw = element.getProperties().get(containerKey);
        if (raw instanceof Map<?, ?> container) {
            for (Object key : container.keySet()) {
                variables.add(String.valueOf(key));
            }
            return variables;
        }
        for (Map.Entry<String, Object> property : element.getProperties().entrySet()) {
            String name = property.getKey();
            if (containerKey != null || VariableKeySupport.PARAMETERS.equals(name)
                    || VariableKeySupport.REPORTS.equals(name) || NameValidator.isReservedProperty(name)) {
                continue;
            }
            variables.add(name);
        }
        return variables;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * A base name answered by more than one variable of the same element.
     *
     * @param containerKey container holding the variables, {@code null} for the top level
     * @param baseName     base name shared by the variables
     * @param variables    concrete names of the variables answering it, in declaration order
     */
    public record Conflict(String containerKey, String baseName, List<String> variables) {

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Conflict that)) {
                return false;
            }
            return Objects.equals(containerKey, that.containerKey)
                    && Objects.equals(baseName, that.baseName)
                    && Objects.equals(variables, that.variables);
        }

        @Override
        public int hashCode() {
            return Objects.hash(containerKey, baseName, variables);
        }

        @Override
        public String toString() {
            return "base name '" + baseName + "' is answered by " + variables
                    + (containerKey != null ? " in " + containerKey : " at the top level");
        }
    }

    /**
     * A variable of an element, addressed the way a recipe does.
     *
     * @param containerKey container holding the variable, {@code null} for the top level
     * @param variableName concrete name of the variable
     * @param ownerId      uid of the element owning the variable, or its id when it has none
     */
    public record VariableRef(String containerKey, String variableName, String ownerId) {
    }
}
