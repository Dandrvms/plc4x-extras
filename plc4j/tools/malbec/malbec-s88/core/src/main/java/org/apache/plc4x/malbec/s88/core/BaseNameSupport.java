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
import java.util.List;
import java.util.Map;

/**
 * The "base name" of a variable: the identifier a recipe uses within an equipment type, as opposed
 * to the concrete name the variable is published under.
 * <p>
 * A copy of an equipment names its variables after the instance that owns them
 * ({@code TEMPERATURA_SP_OLLA_2}) and remembers that they came from {@code Parameters/TEMPERATURA_SP}.
 * The batch engine later looks the recipe variable up by base name inside the chosen instance, so the
 * user is free to rename the concrete variable without breaking any recipe. Variables created by hand
 * carry no base name and therefore use their own name as the base name.
 */
public final class BaseNameSupport {

    private BaseNameSupport() {
        /* This utility class should not be instantiated */
    }

    /**
     * The id without its trailing sequence number: {@code OLLA_1} and {@code OLLA_2} both yield
     * {@code OLLA}. The id is returned unchanged when it does not end with one.
     *
     * @param id element id, may be {@code null}
     * @return the base id, or {@code null} when there is no id
     */
    public static String baseIdOf(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.replaceFirst("_\\d+$", "");
    }

    /**
     * The base name a variable was derived from before being re-suffixed with its element id.
     * <p>
     * When the variable name ends with {@code _}{@code elementId}, that suffix is the instance
     * discriminator the copy added, so the base name is what remains (e.g. {@code TEMPERATURA_SP}
     * from {@code TEMPERATURA_SP_OLLA_2}); otherwise the whole name is the base name, exactly as
     * the user typed it.
     *
     * @param variableName the concrete variable name, may be {@code null}
     * @param elementId    the id of the element that owns the variable, may be {@code null}
     * @return the base name, or {@code null} when there is no variable name
     */
    public static String suggestBaseName(String variableName, String elementId) {
        if (variableName == null) {
            return null;
        }
        String name = variableName.trim();
        if (elementId == null || elementId.isBlank()) {
            return name;
        }
        String normalized = elementId.trim().toUpperCase(java.util.Locale.ROOT);
        if (endsWithDiscriminator(name, normalized)) {
            return name.substring(0, name.length() - normalized.length() - 1);
        }
        return name;
    }

    /**
     * The name a copy of a variable should be given, re-suffixed with the copy's element id.
     * <ul>
     *   <li>a name already ending with {@code _}{@code newId} is kept as is;</li>
     *   <li>a name ending with {@code _}{@code oldId} has that trailing discriminator replaced by
     *       {@code _}{@code newId} (e.g. {@code TEMPERATURA_SP_OLLA_1} becomes
     *       {@code TEMPERATURA_SP_OLLA_2});</li>
     *   <li>any other name gets {@code _}{@code newId} appended, so every copy is identifiable.</li>
     * </ul>
     *
     * @param name  the concrete name as the user wrote it, may be {@code null}
     * @param oldId the id of the element the name belonged to, may be {@code null}
     * @param newId the id the copy will have, may be {@code null}
     * @return the re-suffixed name, or {@code null} when there is no name
     */
    public static String rePrefixed(String name, String oldId, String newId) {
        if (name == null) {
            return null;
        }
        if (newId == null || newId.isBlank()) {
            return name;
        }
        String normalizedNew = newId.trim().toUpperCase(java.util.Locale.ROOT);
        if (endsWithDiscriminator(name.trim(), normalizedNew)) {
            return name;
        }
        if (oldId != null && endsWithDiscriminator(name.trim(), oldId.trim().toUpperCase(java.util.Locale.ROOT))) {
            String trimmed = name.trim();
            return trimmed.substring(0, trimmed.length() - oldId.trim().length() - 1) + "_" + normalizedNew;
        }
        return name.trim() + "_" + normalizedNew;
    }

    /**
     * The base name the batch engine should use for a variable of an element: the recorded base name
     * when the copy stored one, the variable's own name otherwise.
     *
     * @param element       element owning the variable, may be {@code null}
     * @param containerKey  container holding the variable, may be {@code null}
     * @param variableName  the concrete variable name, may be {@code null}
     * @return the base name, or {@code null} when there is no variable name
     */
    public static String resolveBaseName(S88Element element, String containerKey, String variableName) {
        if (variableName == null) {
            return null;
        }
        if (element != null) {
            String recorded = element.getBaseName(containerKey, variableName);
            if (recorded != null) {
                return baseNameOf(recorded);
            }
        }
        return variableName;
    }

    /**
     * The base name a pointer names, with the container segment dropped. A pointer written before
     * unit attributes were pointed at by their bare name still carries a container in front of it,
     * and the class declares the attribute at the top level of its schema either way.
     *
     * @param pointer the recorded pointer, may be {@code null}
     * @return the base name, or {@code null} when there is no pointer
     */
    public static String baseNameOf(String pointer) {
        if (pointer == null) {
            return null;
        }
        int slash = pointer.lastIndexOf('/');
        return slash >= 0 ? pointer.substring(slash + 1) : pointer;
    }

    /**
     * Whether a unit attribute holds a value the unit is given, as opposed to one it reads back:
     * a static value is stored under {@code StaticValue}, a value read back under {@code Reference}.
     * <p>
     * This only tells the two kinds of attribute apart for the user. It is deliberately not part of
     * the base name pointer: an attribute is published at the top level of the element, and the class
     * declares it at the top level of its schema, so the pointer that ties the two together is the
     * bare base name. A container segment there would name a container the class does not have.
     *
     * @param element       element owning the attribute, may be {@code null}
     * @param attributeName the attribute as published, may be {@code null}
     * @return {@code true} when the attribute holds a value given to the unit
     */
    public static boolean holdsStaticValue(S88Element element, String attributeName) {
        if (element == null || attributeName == null) {
            return false;
        }
        return element.getProperty(attributeName) instanceof Map<?, ?> attribute
                && attribute.containsKey("StaticValue");
    }

    /**
     * The base name pointer of a unit attribute: the bare base name the class declares it under,
     * {@code PRESION} for the attribute {@code PRESION_TANQUE_1}.
     * <p>
     * Unlike a variable, an attribute belongs to no container, so the pointer carries no container
     * segment and resolves against the top level of the class schema exactly as the schema stores
     * it. A pointer written before this convention existed still resolves, because
     * {@link #resolveBaseName} reads everything past the last separator.
     *
     * @param element       element owning the attribute, may be {@code null}
     * @param attributeName the attribute as published, may be {@code null}
     * @return the pointer, or {@code null} when there is no attribute name
     */
    public static String attributePointer(S88Element element, String attributeName) {
        if (element == null || attributeName == null || attributeName.isBlank()) {
            return null;
        }
        return suggestBaseName(attributeName, element.getId());
    }

    /**
     * The element ids the copies of {@code sourceId} should be given, each unique in the plant and
     * none equal to the source id itself.
     *
     * @param model    plant holding the elements, may be {@code null}
     * @param sourceId the id being replicated, may be {@code null}
     * @param count    how many ids to generate
     * @return the generated ids, never {@code null}
     */
    public static List<String> nextIds(S88PlantModel model, String sourceId, int count) {
        return nextIds(model, null, sourceId, count);
    }

    /**
     * The element ids the copies of {@code source} should be given, each unique among the ids the
     * plant knows about and among the siblings of the source, and none equal to the source id itself.
     *
     * @param model  plant holding the elements, may be {@code null}
     * @param source element being replicated, may be {@code null}
     * @param count  how many ids to generate
     * @return the generated ids, never {@code null}
     */
    public static List<String> nextIds(S88PlantModel model, S88Element source, int count) {
        return nextIds(model, source, source == null ? null : source.getId(), count);
    }

    private static List<String> nextIds(S88PlantModel model, S88Element source, String sourceId, int count) {
        List<String> result = new ArrayList<>();
        if (sourceId == null || count <= 0) {
            return result;
        }
        String base = baseIdOf(sourceId);
        int n = 2;
        while (result.size() < count) {
            String candidate = base + "_" + n;
            n++;
            if (candidate.equalsIgnoreCase(sourceId)) {
                continue;
            }
            if (model != null && model.findById(candidate).isPresent()) {
                continue;
            }
            if (source != null && source.getParent() != null && siblingTakes(source, candidate)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    private static boolean siblingTakes(S88Element source, String candidate) {
        for (S88Element sibling : source.getParent().getChildren()) {
            if (sibling != null && sibling != source && sibling.getId() != null
                    && sibling.getId().equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithDiscriminator(String name, String discriminator) {
        return name.toUpperCase(java.util.Locale.ROOT).endsWith("_" + discriminator);
    }
}