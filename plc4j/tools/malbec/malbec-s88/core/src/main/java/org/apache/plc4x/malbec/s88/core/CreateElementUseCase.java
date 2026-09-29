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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Use Case for creating a new S88 Element.
 * <p>
 * A brand-new element is always an instance of an equipment type. When no class is chosen for it,
 * one is derived from the element id: an element {@code OLLA_2} is created under the class
 * {@code OLLA}, reusing it when a class of that name already serves the child level and creating it
 * otherwise. The variables of the class are published re-suffixed with the element id
 * ({@code TEMPERATURA_SP_OLLA_2} from {@code Parameters/TEMPERATURA_SP}) and tied to their base
 * name, exactly as a duplicated copy publishes them, so the two roads into an instance stay
 * consistent.
 */
public class CreateElementUseCase {
    private CreateElementUseCase() {
        /* This utility class should not be instantiated */
    }

    private static final Set<String> CONTAINER_KEYS = Set.of(
            VariableKeySupport.PARAMETERS, VariableKeySupport.REPORTS);

    public static void execute(S88PlantModel model, S88Element parent, String id, S88ElementClass s88ElementClass) {
        NameValidator.validate(id, "Element ID");

        if (model != null && model.findById(id).isPresent()) {
            throw new IllegalStateException("Element with ID '" + id + "' already exists.");
        }

        S88Element targetParent = parent != null ? parent
                : (model != null ? model.getRoot() : null);
        S88Level childLevel = targetParent != null && targetParent.getLevel() != null
                ? targetParent.getLevel().getChildLevel() : null;
        if (childLevel == null) {
            throw new IllegalStateException("Cannot create an element under '"
                    + (targetParent != null && targetParent.getLevel() != null
                    ? targetParent.getLevel() : "unknown")
                    + "' (leaf level).");
        }

        S88ElementClass elementClass = resolveClass(model, targetParent, id, childLevel, s88ElementClass);

        S88Element child =
                new S88Element()
                .setId(id)
                .setLevel(childLevel)
                .setClass(elementClass);

        // A class holds the base names of its variables; the element publishes them re-suffixed
        // with its own id and remembers the base name each was derived from, mirroring how the
        // copies of a duplicated type publish their variables. Everything else is copied as it is.
        if (elementClass != null) {
            for (Map.Entry<String, Object> entry : elementClass.getProperties().entrySet()) {
                String name = entry.getKey();
                if (CONTAINER_KEYS.contains(name) && entry.getValue() instanceof Map<?, ?> container) {
                    Map<String, Object> published = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> variable : container.entrySet()) {
                        String base = String.valueOf(variable.getKey());
                        String publishedName = BaseNameSupport.rePrefixed(base, null, id);
                        published.put(publishedName, PropertyValues.deepCopy(variable.getValue()));
                        child.setBaseName(name, publishedName, name + "/" + base);
                    }
                    child.setProperty(name, published);
                } else {
                    child.setProperty(name, PropertyValues.deepCopy(entry.getValue()));
                }
            }
        }

        if (model != null) {
            model.addChild(targetParent, child);
        } else {
            targetParent.addChild(child);
        }
        if (model != null) {
            model.fireChangeEvent(new S88ChangeEvent(S88ChangeEvent.Type.ADDED, child));
        }
    }

    /**
     * The class the element is created under: the one the user chose when it can serve the child
     * level, otherwise the class derived from the element id, reused when it already exists.
     */
    private static S88ElementClass resolveClass(S88PlantModel model, S88Element targetParent,
                                                String id, S88Level childLevel, S88ElementClass chosen) {
        if (chosen != null) {
            if (!canServe(chosen, childLevel)) {
                throw new IllegalStateException("Class '" + chosen.getName()
                        + "' is for level " + chosen.getTargetLevel() + " and cannot serve a "
                        + childLevel + ".");
            }
            return chosen;
        }

        String name = BaseNameSupport.baseIdOf(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cannot derive an equipment type from id '" + id + "'.");
        }
        if (name.startsWith(S88PlantModel.ENUM_CLASS_PREFIX)) {
            throw new IllegalArgumentException("'" + S88PlantModel.ENUM_CLASS_PREFIX
                    + "' is a reserved prefix for global enumerations.");
        }
        NameValidator.validate(name, "Equipment type");
        S88ElementClass found = model != null ? model.findClass(name) : null;
        if (found != null) {
            if (!canServe(found, childLevel)) {
                throw new IllegalStateException("Class '" + name + "' already exists for level "
                        + found.getTargetLevel() + " and cannot serve a " + childLevel + ".");
            }
            return found;
        }

        S88ElementClass created = new S88ElementClass();
        created.setName(name);
        created.setTargetLevel(childLevel);
        if (targetParent != null) {
            targetParent.addElementClass(created);
        }
        if (model != null) {
            model.registerClass(created);
        }
        return created;
    }

    private static boolean canServe(S88ElementClass elementClass, S88Level childLevel) {
        if (elementClass == null || elementClass.getName() == null) {
            return false;
        }
        if (S88PlantModel.isEnumerationClass(elementClass)) {
            return false;
        }
        return elementClass.getTargetLevel() == null || elementClass.getTargetLevel() == childLevel;
    }
}
