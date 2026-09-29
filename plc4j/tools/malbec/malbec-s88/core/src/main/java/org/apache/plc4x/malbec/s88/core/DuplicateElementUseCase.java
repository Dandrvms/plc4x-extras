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

import org.apache.plc4x.malbec.s88.api.S88ChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Use Case to turn an element into an equipment type by giving it a class and producing copies of it.
 * <p>
 * Copying is the only way into a type: for the copies to be interchangeable the model needs a named
 * contract to attach them to, so the source element is either attached to an existing class or a new
 * class is derived from its variables under the type name, and every copy joins that class too.
 * Reusing a class never discards what the source already publishes either: base names present in the
 * instance but missing from the class schema are merged into it, so a hand-grown instance that joins
 * a known type keeps its variables addressable by the batch engine.
 * <p>
 * A copy names each variable after the instance that owns it, {@code TEMPERATURA_SP_OLLA_2} rather
 * than {@code TEMPERATURA_SP}, and records the base name the variable was derived from (the class
 * schema, e.g. {@code Parameters/TEMPERATURA_SP}). The base name is what a recipe addresses, so the
 * user stays free to rename the concrete variables without breaking the batches that consume them.
 * Nested elements are re-identified the same way: a child whose id ends with its parent's id has
 * that discriminator replaced, {@code CALENTAMIENTO_OLLA_1} becoming {@code CALENTAMIENTO_OLLA_2}.
 * The child equipment modules are given types of their own, derived from their id in the same way
 * ({@code CALENTAMIENTO_OLLA} in the example), so the copies of a unit share the modules that make
 * it up instead of carrying free-shaped ones.
 * <p>
 * Nothing is propagated afterwards: mutating a variable of one instance changes only that instance,
 * the class is a contract for the batch engine, not a source of updates.
 */
public final class DuplicateElementUseCase {

    private static final Set<String> CONTAINER_KEYS = Set.of(
            VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS);

    private DuplicateElementUseCase() {
        /* This utility class should not be instantiated */
    }

    public static List<S88Element> execute(S88PlantModel model, S88Element source, int copies, String typeName) {
        return execute(model, source, copies, typeName, Map.of());
    }

    /**
     * Duplicates the element {@code copies} times.
     *
     * @param model             plant holding the element, may be {@code null}
     * @param source            element being turned into a type, cannot be {@code null}
     * @param copies            how many copies to create, {@code 0} still registers the class and
     *                          the base names of the source itself
     * @param typeName          name of the type, resolved from the element id when blank; ignored
     *                          when the element already carries a class
     * @param baseNameOverrides base name to use for a given variable of the source, keyed by
     *                          {@code container/variable} (e.g. {@code Parameters/TEMPERATURA_SP}),
     *                          overriding the name derived by stripping the element id
     * @return the copies created, never {@code null}
     */
    public static List<S88Element> execute(S88PlantModel model, S88Element source, int copies,
                                           String typeName, Map<String, String> baseNameOverrides) {
        if (source == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        if (copies < 0) {
            throw new IllegalArgumentException("Number of copies cannot be negative.");
        }
        guardDuplicableLevel(source);

        S88ElementClass type = ensureClass(model, source, typeName, baseNameOverrides, source.getId());
        ensureChildClasses(model, source, baseNameOverrides);
        attachBaseNames(source, baseNameOverrides);

        List<S88Element> created = new ArrayList<>();
        if (copies == 0) {
            return created;
        }

        List<String> ids = BaseNameSupport.nextIds(model, source, copies);
        S88Element parent = source.getParent();
        int insertAt = parent.getChildren().indexOf(source) + 1;
        for (int i = 0; i < ids.size(); i++) {
            S88Element copy = cloneSubtree(source, ids.get(i), source.getId(), ids.get(i), baseNameOverrides);
            parent.addChild(insertAt + i, copy);
            if (model != null) {
                model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, copy));
            }
            created.add(copy);
        }
        // The base names of the source and the class membership changed too, so the tree listeners
        // get a single sync after the batch instead of one per variable edited.
        if (model != null) {
            model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.RELOADED, source));
        }
        return created;
    }

    /**
     * Guards the levels that form the container of the plant rather than the equipment the copies
     * stand for: the whole plant, an area or a process cell is never duplicated, only the units and
     * the modules fitted into them get copies.
     */
    private static void guardDuplicableLevel(S88Element source) {
        if (source.getParent() == null) {
            throw new IllegalArgumentException("The root of a plant cannot be turned into an equipment type.");
        }
        if (source.getLevel() == S88Level.AREA || source.getLevel() == S88Level.PROCESSCELL) {
            throw new IllegalArgumentException("Only a UNIT or an EQUIPMENT MODULE can be duplicated, not a "
                    + (source.getLevel().getDisplayName() != null
                    ? source.getLevel().getDisplayName() : source.getLevel()) + ".");
        }
    }

    /**
     * Returns the class every instance of the type will share, reusing the one the element already
     * carries or creating a new one derived from the element's variables.
     */
    private static S88ElementClass ensureClass(S88PlantModel model, S88Element source,
                                               String typeName, Map<String, String> baseNameOverrides,
                                               String elementId) {
        S88ElementClass existing = source.getElementClass();
        if (existing != null && canServe(existing, source.getLevel())) {
            mergeMissingBases(existing, source, elementId, baseNameOverrides);
            return existing;
        }
        if (existing != null) {
            throw new IllegalStateException("The element already belongs to class '" + existing.getName()
                    + "' of level " + existing.getTargetLevel() + ", which cannot serve a "
                    + source.getLevel() + ".");
        }

        String name = resolveTypeName(model, source, typeName);
        S88ElementClass found = model != null ? model.findClass(name) : null;
        if (found != null) {
            if (!canServe(found, source.getLevel())) {
                throw new IllegalStateException("Class '" + name + "' already exists for level "
                        + found.getTargetLevel() + " and cannot serve a " + source.getLevel() + ".");
            }
            mergeMissingBases(found, source, elementId, baseNameOverrides);
            source.setClass(found);
            return found;
        }

        S88ElementClass created = new S88ElementClass();
        created.setName(name);
        created.setTargetLevel(source.getLevel());
        Map<String, Object> schema = deriveSchema(source, elementId, baseNameOverrides);
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            created.setProperty(entry.getKey(), entry.getValue());
        }
        if (source.getParent() != null) {
            source.getParent().addElementClass(created);
        }
        if (model != null) {
            model.registerClass(created);
        }
        source.setClass(created);
        return created;
    }

    /**
     * Gives every equipment module under the source a type of its own, so the copies of the source
     * share the kinds of modules they are made of. Each module reuses (by the base id of its name)
     * or creates the class for the next copy, recursively; the variables are tied to the base names
     * they carry already, derived by stripping the id of the module that owns them.
     */
    private static void ensureChildClasses(S88PlantModel model, S88Element element,
                                           Map<String, String> baseNameOverrides) {
        for (S88Element child : element.getChildren()) {
            if (child == null) {
                continue;
            }
            ensureClass(model, child, null, baseNameOverrides, child.getId());
            ensureChildClasses(model, child, baseNameOverrides);
        }
    }

    /**
     * Merges into {@code elementClass} the base names the source publishes that the class does not
     * know yet, leaving everything the class already holds untouched.
     */
    private static void mergeMissingBases(S88ElementClass elementClass, S88Element source,
                                          String elementId, Map<String, String> baseNameOverrides) {
        mergeMissingBases(elementClass, source,
                deriveSchema(source, elementId, baseNameOverrides));
    }

    /**
     * Adds to the class every base name the derived schema holds that the class does not, leaving
     * everything the class already declares untouched.
     */
    static void mergeMissingBases(S88ElementClass elementClass, S88Element source,
                                  Map<String, Object> schema) {
        if (elementClass == null || source == null) {
            return;
        }
        for (Map.Entry<String, Object> schemaEntry : schema.entrySet()) {
            String container = schemaEntry.getKey();
            if (!(schemaEntry.getValue() instanceof Map<?, ?> bases)) {
                continue;
            }
            if (!CONTAINER_KEYS.contains(container)) {
                // A unit attribute joins the contract as a whole: the class gets the attribute the
                // unit publishes unless the class already declares one by that base name.
                if (!(elementClass.getProperty(container) instanceof Map)) {
                    elementClass.setProperty(container, bases);
                }
                continue;
            }
            Object existingRaw = elementClass.getProperty(container);
            Map<String, Object> merged = new LinkedHashMap<>();
            if (existingRaw instanceof Map<?, ?> existing) {
                for (Map.Entry<?, ?> entry : existing.entrySet()) {
                    merged.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            boolean added = false;
            for (Map.Entry<?, ?> baseEntry : bases.entrySet()) {
                String base = String.valueOf(baseEntry.getKey());
                if (!merged.containsKey(base)) {
                    merged.put(base, baseEntry.getValue());
                    added = true;
                }
            }
            if (added) {
                elementClass.setProperty(container, merged);
            }
        }
    }

    private static boolean canServe(S88ElementClass elementClass, S88Level level) {
        if (elementClass == null || elementClass.getName() == null) {
            return false;
        }
        if (S88PlantModel.isEnumerationClass(elementClass)) {
            return false;
        }
        return elementClass.getTargetLevel() == null || elementClass.getTargetLevel() == level;
    }

    private static String resolveTypeName(S88PlantModel model, S88Element source, String typeName) {
        String name = typeName == null || typeName.isBlank()
                ? BaseNameSupport.baseIdOf(source.getId())
                : typeName.trim().toUpperCase(Locale.ROOT);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A type name is required to copy an element "
                    + "whose id carries no base ('" + source.getId() + "').");
        }
        if (name.startsWith(S88PlantModel.ENUM_CLASS_PREFIX)) {
            throw new IllegalArgumentException("'" + S88PlantModel.ENUM_CLASS_PREFIX
                    + "' is a reserved prefix for global enumerations.");
        }
        NameValidator.validate(name, "Type name");
        return name;
    }

    /**
     * The base schema of the element: every variable and every attribute it publishes, keyed by its
     * base name. The variables of a container stay grouped under it, while a unit attribute is part
     * of the contract at the top level, held under its base name, because that is where a recipe
     * addresses it.
     */
    static Map<String, Object> deriveSchema(S88Element source, String elementId,
                                            Map<String, String> baseNameOverrides) {
        Map<String, Object> schema = new LinkedHashMap<>();
        for (String container : CONTAINER_KEYS) {
            Object raw = source.getProperties().get(container);
            if (!(raw instanceof Map<?, ?> containerMap)) {
                continue;
            }
            Map<String, Object> baseEntries = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : containerMap.entrySet()) {
                String variable = String.valueOf(entry.getKey());
                String base = baseOf(source.getBaseName(container, variable),
                        () -> baseName(container, source, variable, elementId, baseNameOverrides));
                baseEntries.putIfAbsent(base, PropertyValues.deepCopy(entry.getValue()));
            }
            schema.put(container, baseEntries);
        }
        for (Map.Entry<String, Object> property : topLevelAttributes(source)) {
            String name = property.getKey();
            String base = baseOf(source.getBaseName(null, name),
                    () -> attributeBaseName(source, name, elementId, baseNameOverrides));
            schema.put(base, PropertyValues.deepCopy(property.getValue()));
        }
        return schema;
    }

    /**
     * The base name a recorded pointer names, with the container segment dropped. A pointer is
     * stored as it is addressed, so a plant written before unit attributes were pointed at by their
     * bare name still carries a container in front of it; the schema keys on the name the class
     * declares either way.
     */
    private static String baseOf(String recorded, java.util.function.Supplier<String> derived) {
        String base = BaseNameSupport.baseNameOf(recorded);
        return base == null || base.isBlank() ? derived.get() : base;
    }

    /**
     * The attributes an element publishes at its top level: everything that is not a container of
     * variables and not one of the properties the model reserves for itself.
     */
    private static List<Map.Entry<String, Object>> topLevelAttributes(S88Element element) {
        List<Map.Entry<String, Object>> attributes = new ArrayList<>();
        for (Map.Entry<String, Object> property : element.getProperties().entrySet()) {
            if (!CONTAINER_KEYS.contains(property.getKey())
                    && !NameValidator.isReservedProperty(property.getKey())) {
                attributes.add(property);
            }
        }
        return attributes;
    }

    /**
     * The base name a unit attribute is derived from: the override the user chose for this very
     * attribute when one exists, otherwise the name with the id of the owning element stripped.
     * <p>
     * An attribute is addressed by its own name in the overrides, without the container prefix the
     * variables carry, so the two kinds of override can never be read as one another.
     */
    private static String attributeBaseName(S88Element element, String attribute, String elementId,
                                            Map<String, String> baseNameOverrides) {
        if (baseNameOverrides != null) {
            String override = baseNameOverrides.get(attribute);
            if (override != null && !override.isBlank()) {
                NameValidator.validate(override, "Base name");
                return override.trim().toUpperCase(Locale.ROOT);
            }
        }
        return BaseNameSupport.suggestBaseName(attribute,
                elementId != null ? elementId : element.getId());
    }

    /**
     * Registers the base names of the source element subtree, without overwriting pointers the
     * elements may already carry. Every variable is derived from the id of the element that owns
     * it: an element {@code CALENTAMIENTO_OLLA_1} strips its own {@code _CALENTAMIENTO_OLLA_1} (and
     * therefore the {@code _OLLA_1} it contains) off {@code TEMPERATURA_SP_CALENTAMIENTO_OLLA_1},
     * so the base name a recipe addresses always names the type schema, just like a class
     * derivation does.
     */
    private static void attachBaseNames(S88Element element,
                                        Map<String, String> baseNameOverrides) {
        for (String container : CONTAINER_KEYS) {
            Object raw = element.getProperties().get(container);
            if (!(raw instanceof Map<?, ?> containerMap)) {
                continue;
            }
            for (Object childKey : containerMap.keySet()) {
                String variable = String.valueOf(childKey);
                if (element.getBaseName(container, variable) != null) {
                    continue;
                }
                String base = baseName(container, element, variable, element.getId(), baseNameOverrides);
                String pointer = container + "/" + base;
                element.setBaseName(container, variable, pointer);
            }
        }
        for (Map.Entry<String, Object> attribute : topLevelAttributes(element)) {
            String name = attribute.getKey();
            if (element.getBaseName(null, name) != null) {
                continue;
            }
            // An attribute has no container of its own to key it by, and the class declares it at
            // the top level of its schema, so the pointer is the bare base name: a container segment
            // here would name a container the class does not have.
            String base = attributeBaseName(element, name, element.getId(), baseNameOverrides);
            element.setBaseName(null, name, base);
        }
        for (S88Element child : element.getChildren()) {
            if (child != null) {
                attachBaseNames(child, baseNameOverrides);
            }
        }
    }

    /**
     * Clones the subtree of {@code source}: the copy gets {@code copyId}, every descendant is
     * re-identified by replacing its parent's id, and every variable is re-suffixed with the id of
     * the cloned root and tied to its base name (derived from the node that owns it). Two variables
     * that would end up under the same published name make the copy ambiguous, so the copy is
     * refused before that happens.
     */
    private static S88Element cloneSubtree(S88Element source, String copyId, String rootOldId,
                                           String rootNewId, Map<String, String> baseNameOverrides) {
        S88Element copy = new S88Element();
        copy.setId(copyId);
        copy.setLevel(source.getLevel());
        if (source.getElementClass() != null) {
            copy.setClass(source.getElementClass());
        }

        for (Map.Entry<String, Object> property : source.getProperties().entrySet()) {
            String name = property.getKey();
            if (CONTAINER_KEYS.contains(name) && property.getValue() instanceof Map<?, ?> container) {
                Map<String, Object> copiedContainer = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : container.entrySet()) {
                    String variable = String.valueOf(entry.getKey());
                    String renamed = BaseNameSupport.rePrefixed(variable, rootOldId, rootNewId);
                    if (copiedContainer.containsKey(renamed)) {
                        throw new IllegalStateException("Copying '" + rootOldId + "' to '" + rootNewId
                                + "' would publish the variable '" + renamed + "' twice: it is derived from '"
                                + variable + "', whose name another variable of '" + source.getId()
                                + "' already re-suffixes to. Rename one of them first.");
                    }
                    String base = baseName(name, source, variable, source.getId(), baseNameOverrides);
                    copiedContainer.put(renamed, PropertyValues.deepCopy(entry.getValue()));
                    copy.setBaseName(name, renamed, name + "/" + base);
                }
                copy.setProperty(name, copiedContainer);
            } else {
                // The attributes the unit publishes at its top level are named after the unit just
                // like the variables of a type, so the copies re-suffix them with the copy's id
                // instead of inheriting the source's instance name.
                String renamed = NameValidator.isReservedProperty(name)
                        ? name
                        : BaseNameSupport.rePrefixed(name, rootOldId, rootNewId);
                if (copy.getProperties().containsKey(renamed)) {
                    throw new IllegalStateException("Copying '" + rootOldId + "' to '" + rootNewId
                            + "' would publish the top level property '" + renamed + "' twice: another "
                            + "top level attribute of '" + source.getId() + "' re-suffixes to it. Rename "
                            + "one of them first.");
                }
                copy.setProperty(renamed, PropertyValues.deepCopy(property.getValue()));
                // The copy is addressed by the very same schema entry as the source, so the pointer
                // travels with the attribute: the recipe keeps reaching it by its base name.
                String pointer = source.getBaseName(null, name);
                if (pointer == null && !NameValidator.isReservedProperty(name)) {
                    pointer = BaseNameSupport.attributePointer(source, name);
                }
                if (pointer != null) {
                    copy.setBaseName(null, renamed, pointer);
                }
            }
        }

        for (S88Element child : source.getChildren()) {
            if (child == null) {
                continue;
            }
            String childId = BaseNameSupport.rePrefixed(child.getId(), source.getId(), copyId);
            copy.addChild(cloneSubtree(child, childId, rootOldId, rootNewId, baseNameOverrides));
        }
        return copy;
    }

    /**
     * The base name a variable of {@code source} is derived from: the override the user chose for
     * this very container and variable when one exists, otherwise the suggested base name.
     */
    private static String baseName(String container, S88Element source, String variable, String elementId,
                                   Map<String, String> baseNameOverrides) {
        if (baseNameOverrides != null) {
            String override = baseNameOverrides.get(container + "/" + variable);
            if (override != null && !override.isBlank()) {
                String normalized = override.trim().toUpperCase(Locale.ROOT);
                NameValidator.validate(override, "Base name");
                return normalized;
            }
        }
        return BaseNameSupport.suggestBaseName(variable, elementId);
    }
}