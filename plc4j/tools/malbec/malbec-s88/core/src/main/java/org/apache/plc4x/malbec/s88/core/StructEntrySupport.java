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

import org.apache.plc4x.malbec.s88.api.S88Element;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal helpers to read and write entries of a structured property.
 * <p>
 * A {@code containerKey} of {@code null} addresses the element root (a top level property),
 * while a non-null {@code containerKey} addresses a nested map stored under that key
 * (e.g. "Parameters" or "Reports").
 * <p>
 * Writes are copy-on-write: the container is cloned before being modified so the maps
 * held by the element are never mutated in place.
 */
final class StructEntrySupport {

    private StructEntrySupport() {
        /* This utility class should not be instantiated */
    }

    static Map<String, Object> copyContainer(S88Element element, String containerKey) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (element == null || containerKey == null) {
            return copy;
        }
        Object raw = element.getProperties().get(containerKey);
        if (raw instanceof Map<?, ?> nested) {
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), PropertyValues.deepCopy(entry.getValue()));
            }
        }
        return copy;
    }

    static boolean containsEntry(S88Element element, String containerKey, String entryKey) {
        if (element == null || entryKey == null) {
            return false;
        }
        if (containerKey == null) {
            return element.getProperties().containsKey(entryKey);
        }
        Object raw = element.getProperties().get(containerKey);
        return raw instanceof Map<?, ?> nested && nested.containsKey(entryKey);
    }

    static Object getEntry(S88Element element, String containerKey, String entryKey) {
        if (element == null || entryKey == null) {
            return null;
        }
        if (containerKey == null) {
            return element.getProperties().get(entryKey);
        }
        Object raw = element.getProperties().get(containerKey);
        if (raw instanceof Map<?, ?> nested) {
            return nested.get(entryKey);
        }
        return null;
    }

    static void writeEntry(S88Element element, String containerKey, String entryKey, Object entryValue) {
        if (containerKey == null) {
            element.setProperty(entryKey, PropertyValues.deepCopy(entryValue));
            return;
        }
        Map<String, Object> container = copyContainer(element, containerKey);
        container.put(entryKey, PropertyValues.deepCopy(entryValue));
        element.setProperty(containerKey, container);
        if (element.getBaseName(containerKey, entryKey) == null) {
            String base = conformingBase(element, entryKey);
            if (base != null) {
                element.setBaseName(containerKey, entryKey, containerKey + "/" + base);
            }
        }
    }

    /**
     * Renames an existing entry, moving the base name pointer along: the base name a recipe
     * addresses does not change when only the concrete name does, and a conforming name derives a
     * fresh pointer from the element id when there was none to move.
     */
    static void renameEntry(S88Element element, String containerKey, String oldKey,
                            String newKey, Object entryValue) {
        if (containerKey == null) {
            element.setProperty(oldKey, null);
            element.setProperty(newKey, PropertyValues.deepCopy(entryValue));
            return;
        }
        Map<String, Object> container = copyContainer(element, containerKey);
        container.remove(oldKey);
        container.put(newKey, PropertyValues.deepCopy(entryValue));
        element.setProperty(containerKey, container);

        String pointer = element.getBaseName(containerKey, oldKey);
        element.setBaseName(containerKey, oldKey, null);
        if (pointer != null) {
            element.setBaseName(containerKey, newKey, pointer);
        } else {
            String base = conformingBase(element, newKey);
            if (base != null) {
                element.setBaseName(containerKey, newKey, containerKey + "/" + base);
            }
        }
    }

    static void removeEntry(S88Element element, String containerKey, String entryKey) {
        if (containerKey == null) {
            element.setProperty(entryKey, null);
            return;
        }
        Map<String, Object> container = copyContainer(element, containerKey);
        container.remove(entryKey);
        element.setProperty(containerKey, container);
        element.setBaseName(containerKey, entryKey, null);
    }

    /**
     * The base name a variable derives from its element id: a variable called
     * {@code NIVEL_OLLA_1} of element {@code OLLA_1} has base name {@code NIVEL}. Names that do
     * not follow the convention carry no base name, exactly as if they had been typed by hand.
     */
    private static String conformingBase(S88Element element, String entryKey) {
        if (element == null || entryKey == null || element.getId() == null || element.getId().isBlank()) {
            return null;
        }
        String discriminator = "_" + element.getId().trim().toUpperCase(java.util.Locale.ROOT);
        if (entryKey.endsWith(discriminator)) {
            return entryKey.substring(0, entryKey.length() - discriminator.length());
        }
        return null;
    }
}
