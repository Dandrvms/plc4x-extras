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
package org.apache.plc4x.malbec.s88.core;

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Use Case for renaming an S88 Element.
 * <p>
 * Renaming an element re-suffixes every variable of the subtree that is named after it: the
 * copies of a type carry the id of the instance that owns them, so {@code OLLA_1} going to
 * {@code OLLA_2} moves {@code NIVEL_OLLA_1} and {@code TEMPERATURA_SP_CALENTAMIENTO_OLLA_1} to
 * their {@code _OLLA_2} counterparts, in the unit itself and in every element below it. The
 * descendants whose own id carries the parent's id are renamed the same way
 * ({@code CALENTAMIENTO_OLLA_1} becoming {@code CALENTAMIENTO_OLLA_2}), chaining one id into the
 * next, so a conforming EquipmentModule stays a conforming EquipmentModule across the rename.
 * Variables that do not follow the convention are the user's own names and never move.
 */
public class RenameElementUseCase {

    private static final String NAME_DISPLAY = "Element ID";
    private static final Set<String> CONTAINER_KEYS = Set.of(
            VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS);

    private RenameElementUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Reports what a rename would do to the variables of the subtree, without performing it.
     * <p>
     * Elements that belong to a type name their variables after the instance's id
     * ({@code TEMPERATURA_SP_OLLA_1}). Renaming the element re-suffixes exactly those variables,
     * whether the element publishes them itself or an element below it does. The caller is
     * expected to show this to the user and let them confirm. See
     * {@link NamingAdvisor#describeRenameImpact}.
     *
     * @param element element about to be renamed
     * @return the advice, {@code null} when the rename would not touch any variable
     */
    public static NamingAdvisor.Advice impactOf(S88Element element) {
        return NamingAdvisor.describeRenameImpact(element);
    }

    public static void execute(S88PlantModel model, S88Element element, String newId) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        NameValidator.validate(newId, NAME_DISPLAY);

        String oldId = element.getId();
        if (oldId != null && oldId.equals(newId)) {
            return;
        }
        if (model != null && model.findById(newId).isPresent()) {
            throw new IllegalStateException("Element with the id '" + newId + "' already exists.");
        }

        if (oldId != null) {
            String oldDiscriminator = "_" + oldId.trim().toUpperCase(Locale.ROOT);
            String newDiscriminator = "_" + newId.trim().toUpperCase(Locale.ROOT);

            // The whole rename is planned and checked before a single value moves, so a rename
            // refused halfway through leaves the plant exactly as it was, ids and variables alike.
            List<ChildRename> childRenames = planChildRenames(element, oldId, newId);
            validateNoIdCollisions(model, element, newId, childRenames);

            List<VariableRename> variableRenames = new ArrayList<>();
            for (S88Element current : subtree(element)) {
                variableRenames.add(planVariableRename(current, oldDiscriminator, newDiscriminator));
            }

            for (VariableRename rename : variableRenames) {
                rename.apply();
            }
            for (ChildRename rename : childRenames) {
                rename.element().setId(rename.newId());
            }
        }

        element.setId(newId);

        if (model != null) {
            model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.RELOADED, element));
        }
    }

    /**
     * Works out how one element's variables would be re-suffixed, both in the
     * {@code Parameters}/{@code Reports} containers and among the top level properties, without
     * touching the element. Two variables or properties that would end up under the same published
     * name make the rename ambiguous, so the rename is refused wherever that would happen. The
     * returned plan also carries the base name pointer of every variable it moves, so the recipes
     * that addressed the base name keep working once the move is applied.
     */
    private static VariableRename planVariableRename(S88Element element, String oldDiscriminator,
                                                     String newDiscriminator) {
        Map<String, Object> properties = element.getProperties();
        Map<String, Map<String, Object>> newContainers = new LinkedHashMap<>();
        List<MovedVariable> movedVariables = new ArrayList<>();
        for (String container : CONTAINER_KEYS) {
            Object raw = properties.get(container);
            if (!(raw instanceof Map<?, ?> containerMap) || containerMap.isEmpty()) {
                continue;
            }
            Map<String, Object> reworked = new LinkedHashMap<>();
            boolean touched = false;
            for (Map.Entry<?, ?> entry : containerMap.entrySet()) {
                String variable = String.valueOf(entry.getKey());
                if (variable.endsWith(oldDiscriminator)) {
                    String base = variable.substring(0, variable.length() - oldDiscriminator.length());
                    String renamed = base + newDiscriminator;
                    if (reworked.containsKey(renamed)) {
                        throw new IllegalStateException("Renaming '" + element.getId()
                                + "' would publish the variable '" + renamed + "' twice: it comes from '"
                                + variable + "', and another variable of '" + element.getId()
                                + "' already publishes it either as typed or re-suffixed. Rename one "
                                + "of them first.");
                    }
                    reworked.put(renamed, entry.getValue());
                    movedVariables.add(new MovedVariable(container, variable, renamed));
                    touched = true;
                } else {
                    if (reworked.containsKey(variable)) {
                        throw new IllegalStateException("Renaming '" + element.getId()
                                + "' would publish the variable '" + variable + "' twice: it is typed "
                                + "that way, and another variable of '" + element.getId()
                                + "' re-suffixes to the very same name. Rename one of them first.");
                    }
                    reworked.put(variable, entry.getValue());
                }
            }
            if (touched) {
                newContainers.put(container, reworked);
            }
        }


        Set<String> publishedTopLevel = new LinkedHashSet<>();
        for (String name : properties.keySet()) {
            if (!CONTAINER_KEYS.contains(name) && !NameValidator.isReservedProperty(name)) {
                publishedTopLevel.add(name);
            }
        }
        Map<String, Object> topLevelAdditions = new LinkedHashMap<>();
        List<String> topLevelRemovals = new ArrayList<>();
        boolean topLevelTouched = false;
        for (String name : new ArrayList<>(properties.keySet())) {
            if (CONTAINER_KEYS.contains(name) || NameValidator.isReservedProperty(name)) {
                continue;
            }
            if (name.endsWith(oldDiscriminator)) {
                String base = name.substring(0, name.length() - oldDiscriminator.length());
                String renamed = base + newDiscriminator;
                publishedTopLevel.remove(name);
                if (!publishedTopLevel.add(renamed)) {
                    throw new IllegalStateException("Renaming '" + element.getId()
                            + "' would publish the top level property '" + renamed + "' twice: it is "
                            + "typed that way, and another top level attribute of '" + element.getId()
                            + "' re-suffixes to the very same name. Rename one of them first.");
                }
                topLevelAdditions.put(renamed, properties.get(name));
                topLevelRemovals.add(name);
                topLevelTouched = true;
            }
        }

        return new VariableRename(element, newContainers, movedVariables,
                topLevelTouched ? topLevelAdditions : Map.of(), topLevelRemovals,
                oldDiscriminator, newDiscriminator);
    }

    /**
     * The plan for one element, applied only once the whole rename has been found sound: the
     * reworked containers, the variables whose base name pointer travels with them, and the top
     * level properties that gain a name and lose the old one.
     */
    private record VariableRename(S88Element element,
                                  Map<String, Map<String, Object>> newContainers,
                                  List<MovedVariable> movedVariables,
                                  Map<String, Object> topLevelAdditions,
                                  List<String> topLevelRemovals,
                                  String oldDiscriminator,
                                  String newDiscriminator) {

        void apply() {
            for (Map.Entry<String, Map<String, Object>> container : newContainers.entrySet()) {
                element.setProperty(container.getKey(), container.getValue());
            }
            for (MovedVariable moved : movedVariables) {
                String pointer = element.getBaseName(moved.container(), moved.variable());
                element.setBaseName(moved.container(), moved.renamed(),
                        pointer != null ? pointer : moved.container() + "/"
                                + BaseNameSupport.suggestBaseName(moved.variable(), element.getId()));
                element.setBaseName(moved.container(), moved.variable(), null);
            }
            for (Map.Entry<String, Object> addition : topLevelAdditions.entrySet()) {
                element.setProperty(addition.getKey(), addition.getValue());
            }
            for (String removal : topLevelRemovals) {
                element.setProperty(removal, null);

                String pointer = element.getBaseName(null, removal);
                if (pointer != null) {
                    String base = removal.substring(0, removal.length() - oldDiscriminator.length());
                    element.setBaseName(null, base + newDiscriminator, pointer);
                    element.setBaseName(null, removal, null);
                }
            }
        }
    }

    private record MovedVariable(String container, String variable, String renamed) {
    }

    /**
     * Plans the ids of the descendants that carry the id of the ancestor being renamed, one id
     * chaining into the next: a child conforming to its parent is re-suffixed ({@code
     * CALENTAMIENTO_OLLA_1} to {@code CALENTAMIENTO_OLLA_2}), and its own children are then
     * measured against the child's old and new ids; a child that does not conform keeps its id and
     * passes its parent's pair down unchanged.
     */
    private static List<ChildRename> planChildRenames(S88Element element, String oldId, String newId) {
        List<ChildRename> planned = new ArrayList<>();
        planDescendants(element, oldId, newId, planned);
        return planned;
    }

    private static void planDescendants(S88Element element, String oldId, String newId,
                                        List<ChildRename> planned) {
        for (S88Element child : element.getChildren()) {
            if (child == null || child.getId() == null) {
                continue;
            }
            String childId = child.getId();
            if (childId.toUpperCase(Locale.ROOT).endsWith("_" + oldId.toUpperCase(Locale.ROOT))) {
                String renamed = BaseNameSupport.rePrefixed(childId, oldId, newId);
                planned.add(new ChildRename(child, renamed));
                planDescendants(child, childId, renamed, planned);
            } else {
                planDescendants(child, oldId, newId, planned);
            }
        }
    }

    /**
     * Simulates the rename against the ids that already exist, so a re-suffixed descendant never
     * lands on an id another element of the plant is using.
     */
    private static void validateNoIdCollisions(S88PlantModel model, S88Element element,
                                               String newId, List<ChildRename> planned) {
        Set<String> occupied = new HashSet<>();
        if (model != null && model.getRoot() != null) {
            collectIds(model.getRoot(), occupied);
        } else {
            for (S88Element current = element; current != null; current = current.getParent()) {
                collectIds(current, occupied);
            }
        }
        occupied.remove(element.getId());
        for (ChildRename rename : planned) {
            occupied.remove(rename.element().getId());
        }
        if (!occupied.add(newId)) {
            throw new IllegalStateException("Element with the id '" + newId + "' already exists.");
        }
        for (ChildRename rename : planned) {
            if (!occupied.add(rename.newId())) {
                throw new IllegalStateException("Renaming '" + rename.element().getId() + "' to '"
                        + rename.newId() + "' would clash with an element that already has that id.");
            }
        }
    }

    private static void collectIds(S88Element element, Set<String> out) {
        if (element == null || element.getId() == null) {
            return;
        }
        out.add(element.getId());
        for (S88Element child : element.getChildren()) {
            collectIds(child, out);
        }
    }

    private record ChildRename(S88Element element, String newId) {
    }

    private static List<S88Element> subtree(S88Element element) {
        List<S88Element> nodes = new ArrayList<>();
        collect(element, nodes);
        return nodes;
    }

    private static void collect(S88Element element, List<S88Element> out) {
        if (element == null) {
            return;
        }
        out.add(element);
        for (S88Element child : element.getChildren()) {
            collect(child, out);
        }
    }
}
