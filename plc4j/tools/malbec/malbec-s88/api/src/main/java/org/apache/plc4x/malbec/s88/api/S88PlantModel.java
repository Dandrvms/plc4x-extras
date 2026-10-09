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
package org.apache.plc4x.malbec.s88.api;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * S88 Physical Model for a plant
 */
public class S88PlantModel {

    private final S88Element root;
    private final List<S88ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, S88Element> idMap = new LinkedHashMap<>();
    private final Map<String, S88ElementClass> classes = new LinkedHashMap<>();
    private final Set<String> duplicateIds = new LinkedHashSet<>();

    public S88PlantModel(S88Element root) {
        this.root = root;
        rebuildIndex();
    }

    private void rebuildIndex() {
        idMap.clear();
        duplicateIds.clear();
        if (root != null) {
            addToIndex(root);
        }
    }

    private void addToIndex(S88Element element) {
        String id = element.getId();
        if (id != null) {
            S88Element indexed = idMap.get(id);
            if (indexed == null) {
                idMap.put(id, element);
            } else if (indexed != element) {
                duplicateIds.add(id);
            }
        }
        for (S88Element child : element.getChildren()) {
            addToIndex(child);
        }
    }

    /**
     * Attaches {@code child} under {@code parent} and indexes it, so a newly added element is
     * reachable by id without waiting for the next reload.
     * <p>
     * Indexing is safe to repeat: re-adding an element that is already indexed (for instance by a
     * subsequent {@link #fireChangeEvent(S88ChangeEvent)} of type {@code ADDED}) changes nothing.
     *
     * @param parent element the child is attached to, {@code null} to attach under the root
     * @param child  element being added, cannot be {@code null}
     * @throws IllegalArgumentException when there is no parent to attach the child to
     */
    public void addChild(S88Element parent, S88Element child) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        S88Element target = parent != null ? parent : root;
        if (target == null) {
            throw new IllegalArgumentException("There is no parent to attach the element to.");
        }
        validateChildLevel(target, child);
        target.addChild(child);
        addToIndex(child);
    }

    private void validateChildLevel(S88Element parent, S88Element child) {
        if (parent.getLevel() == null) {
            return;
        }
        S88Level expectedChildLevel = parent.getLevel().getChildLevel();
        if (expectedChildLevel == null) {
            throw new IllegalStateException("Cannot add a child to a leaf level");
        }
        if (child.getLevel() == null) {
            throw new IllegalStateException("Child level cannot be null when parent expects "
                    + expectedChildLevel);
        }
        if (child.getLevel() != expectedChildLevel) {
            throw new IllegalStateException("Child level " + child.getLevel()
                    + " does not match expected " + expectedChildLevel);
        }
    }

    /**
     * Ids shared by more than one element of the plant.
     * <p>
     * Such an id makes the second element unreachable through {@link #findById(String)}, which
     * in turn defeats the uniqueness check the use cases rely on. The clash is reported instead
     * of thrown so that a plant already stored with duplicated ids can still be opened and
     * repaired.
     *
     * @return the duplicated ids, in the order they were found, empty when the plant is sound
     */
    public Set<String> getDuplicateIds() {
        return Collections.unmodifiableSet(duplicateIds);
    }


    public S88Element getRoot() {
        return root;
    }

    public void registerClass(S88ElementClass ec) {
        String name = ec != null ? ec.getName() : null;
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Class ID cannot be empty");
        }
        if (classes.containsKey(name)) {
            throw new IllegalStateException("Class with ID '" + name + "' already exists.");
        }
        classes.put(name, ec);
    }

    /**
     * The classes an element can be, and nothing else.
     * <p>
     * <b>Enumerations are not equipment classes.</b> An enumeration is a set of values a variable
     * can take, and it lives in the same place as the equipment classes because that is where the
     * plant keeps everything it declares. It is never something an element can be, so it is left
     * out here. Reading {@link #getClasses()} instead brings the two together, and anything that
     * offers classes to choose from has to know about the {@code ENUM_} prefix to get this right.
     *
     * @return the equipment classes, keyed by name, never {@code null}
     */
    public Map<String, S88ElementClass> getEquipmentClasses() {
        Map<String, S88ElementClass> equipment = new LinkedHashMap<>();
        for (Map.Entry<String, S88ElementClass> entry : classes.entrySet()) {
            if (!isEnumerationClass(entry.getValue())) {
                equipment.put(entry.getKey(), entry.getValue());
            }
        }
        return equipment;
    }

    public S88ElementClass findClass(String name) { return classes.get(name); }

    /**
     * Every class the plant declares, enumerations included.
     * <p>
     * They share one place, and that is why this is not what an element can be. Use
     * {@link #getEquipmentClasses()} for that.
     *
     * @return the declared classes, keyed by name, never {@code null}
     */
    public Map<String, S88ElementClass> getClasses(){
        return classes;
    }

    /**
     * Classes that may be used for the children of an element at {@code childLevel}.
     * <p>
     * Element classes are global to the plant: a class defined while building one branch is offered
     * to every element that creates children of the same level, whatever branch it sits in.
     *
     * @param childLevel level the elements to create will have, {@code null} when unknown
     * @return the usable classes, never {@code null}
     */
    public List<S88ElementClass> getClassesForChildLevel(S88Level childLevel) {
        Map<String, S88ElementClass> byName = new LinkedHashMap<>();
        if (childLevel == null) {
            return List.of();
        }
        for (S88ElementClass ec : classes.values()) {
            if (usableFor(ec, childLevel)) {
                byName.putIfAbsent(ec.getName(), ec);
            }
        }
        for (S88ElementClass ec : classesAttachedInTree(childLevel)) {
            byName.putIfAbsent(ec.getName(), ec);
        }
        return List.copyOf(byName.values());
    }

    /**
     * Picks the classes already attached to the elements, in tree order, keeping only those meant
     * for {@code childLevel}.
     */
    private List<S88ElementClass> classesAttachedInTree(S88Level childLevel) {
        List<S88ElementClass> found = new ArrayList<>();
        collectAttachedClasses(root, childLevel, found);
        return found;
    }

    private void collectAttachedClasses(S88Element element, S88Level childLevel, List<S88ElementClass> found) {
        if (element == null) {
            return;
        }
        S88Level level = element.getLevel();
        if (level != null && level.getChildLevel() == childLevel && element.getElementClasses() != null) {
            for (S88ElementClass ec : element.getElementClasses()) {
                if (usableFor(ec, childLevel)) {
                    found.add(ec);
                }
            }
        }
        for (S88Element child : element.getChildren()) {
            collectAttachedClasses(child, childLevel, found);
        }
    }

    private static boolean usableFor(S88ElementClass ec, S88Level childLevel) {
        return ec != null
                && !isEnumerationClass(ec)
                && ec.getTargetLevel() == childLevel
                && ec.getName() != null;
    }

    public static final String ENUM_CLASS_PREFIX = "ENUM_";

    /** Property holding the setpoints and commands of an element. */
    public static final String PARAMETERS = "Parameters";

    /** Property holding the values read back from an element. */
    public static final String REPORTS = "Reports";

    /**
     * The properties whose value is a map of named entries, each entry being a variable.
     * <p>
     */
    public static final Set<String> CONTAINER_KEYS = Set.of(PARAMETERS, REPORTS);

    /**
     * Whether the property is one of the containers holding variables.
     *
     * @param property property name, may be {@code null}
     * @return {@code true} when the property holds variables rather than an attribute
     */
    public static boolean isContainerKey(String property) {
        return property != null && CONTAINER_KEYS.contains(property);
    }

    public static boolean isEnumerationClass(S88ElementClass ec) {
        return ec != null && ec.getName() != null && ec.getName().startsWith(ENUM_CLASS_PREFIX);
    }

    public S88Enumeration toEnumeration(S88ElementClass ec) {
        if (!isEnumerationClass(ec)) {
            return null;
        }
        S88Enumeration enumeration = new S88Enumeration(ec.getName().substring(ENUM_CLASS_PREFIX.length()));
        for (var entry : ec.getProperties().entrySet()) {
            Object raw = entry.getValue();
            if (raw instanceof Map<?, ?> nested) {
                Object idx = nested.get("index");
                Integer value = null;
                if (idx instanceof Number n) {
                    value = n.intValue();
                } else if (idx != null) {
                    try {
                        value = Integer.parseInt(String.valueOf(idx).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (value != null) {
                    enumeration.setValue(entry.getKey(), value);
                }
            }
        }
        return enumeration;
    }

    public S88ElementClass fromEnumeration(S88Enumeration enumeration) {
        S88ElementClass ec = new S88ElementClass();
        ec.setName(ENUM_CLASS_PREFIX + enumeration.getName());
        for (var entry : enumeration.getValues().entrySet()) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("index", entry.getValue());
            ec.setProperty(entry.getKey(), props);
        }
        return ec;
    }

    public void registerEnumeration(S88Enumeration enumeration) {
        if (enumeration == null || enumeration.getName() == null || enumeration.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Enumeration name cannot be empty");
        }

        String key = ENUM_CLASS_PREFIX + enumeration.getName();
        if (classes.containsKey(key)) {
            throw new IllegalStateException("Class with ID '" + key + "' already exists.");
        }
        classes.put(key, fromEnumeration(enumeration));
    }

    public void unregisterEnumeration(String name) {
        if (name == null) {
            return;
        }
        classes.remove(ENUM_CLASS_PREFIX + name);
    }

    public S88Enumeration findEnumeration(String name) {
        S88ElementClass ec = classes.get(ENUM_CLASS_PREFIX + name);
        return ec != null ? toEnumeration(ec) : null;
    }

    public List<S88Enumeration> getEnumerations() {
        List<S88Enumeration> result = new ArrayList<>();
        for (S88ElementClass ec : classes.values()) {
            if (isEnumerationClass(ec)) {
                result.add(toEnumeration(ec));
            }
        }
        return result;
    }

    public List<S88Element> findInstancesOf(String className) {
        List<S88Element> result = new ArrayList<>();
        if (root != null) {
            collectInstances(root, className, result);
        }
        return result;
    }

    private void collectInstances(S88Element element, String className, List<S88Element> result) {
        S88ElementClass ec = element.getElementClass();
        if (ec != null && className.equals(ec.getName())) {
            result.add(element);
        }
        for (S88Element child : element.getChildren()) {
            collectInstances(child, className, result);
        }
    }


    public Optional<S88Element> findById(String id) {
        return Optional.ofNullable(idMap.get(id));
    }

    /**
     * Finds an element by its stable identity.
     * <p>
     * Unlike {@link #findById(String)}, this lookup survives a rename: the uid never changes, so
     * a recipe can keep addressing the element it was bound to.
     *
     * @param uid uid of the element, may be {@code null}
     * @return the element, or {@link Optional#empty()} when no element carries that uid
     */
    public Optional<S88Element> findByUid(String uid) {
        if (uid == null || uid.isBlank()) {
            return Optional.empty();
        }
        return findByUid(root, uid);
    }

    private Optional<S88Element> findByUid(S88Element element, String uid) {
        if (element == null) {
            return Optional.empty();
        }
        if (uid.equals(element.getUid())) {
            return Optional.of(element);
        }
        for (S88Element child : element.getChildren()) {
            Optional<S88Element> found = findByUid(child, uid);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }


    public void addChangeListener(S88ChangeListener listener) {
        listeners.add(listener);
    }


    public void removeChangeListener(S88ChangeListener listener) {
        listeners.remove(listener);
    }


    public void fireChangeEvent(S88ChangeEvent event) {
        if (null != event.type())
            switch (event.type()) {
                case ADDED -> addToIndex(event.element());
                case REMOVED -> removeFromIndex(event.element());
                case RELOADED -> rebuildIndex();
                default -> {
                }
            }

        for (S88ChangeListener listener : listeners) {
            listener.onS88Change(event);
        }
    }

    private void removeFromIndex(S88Element element) {
        if (element.getId() != null && idMap.get(element.getId()) == element) {
            idMap.remove(element.getId());
        }
        for (S88Element child : element.getChildren()) {
            removeFromIndex(child);
        }
    }
}
