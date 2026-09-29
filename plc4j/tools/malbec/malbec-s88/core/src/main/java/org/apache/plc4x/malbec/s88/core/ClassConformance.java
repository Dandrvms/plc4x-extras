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
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far an element has drifted away from the class it belongs to.
 * <p>
 * The class is the contract the batch engine works against, and an element is expected to publish
 * exactly what its class declares. Editing an element freely breaks that in two directions, and
 * neither is an error: an element may publish something the class has never heard of (excess), and
 * the class may declare something the element does not publish (deficit). A class is shared by
 * every instance of the type, so a change to it reaches the whole fleet at once, which is why the
 * two are reconciled deliberately and never as a side effect of an edit.
 * <p>
 * The state is derived, never stored: it is read off the element and its class every time it is
 * asked for, so it cannot go stale, and a plant that has never been reconciled needs no migration.
 * An element with no class, or whose class is an enumeration, has nothing to conform to and reports
 * an empty divergence.
 */
public final class ClassConformance {

    private static final Set<String> CONTAINER_KEYS = VariableKeySupport.containerKeys();

    private final List<String> excess = new ArrayList<>();
    private final List<String> deficit = new ArrayList<>();

    private ClassConformance(List<String> excess, List<String> deficit) {
        this.excess.addAll(excess);
        this.deficit.addAll(deficit);
    }

    /**
     * What the element publishes under a name the class does not declare, as
     * {@code Parameters/X} for a variable and the bare base name for a top level attribute.
     */
    public List<String> excess() {
        return Collections.unmodifiableList(excess);
    }

    /**
     * What the class declares and the element does not publish, in the same form as the excess.
     */
    public List<String> deficit() {
        return Collections.unmodifiableList(deficit);
    }

    public boolean isConforming() {
        return excess.isEmpty() && deficit.isEmpty();
    }

    /**
     * The divergence of {@code element} against the class it carries. The pointers the element
     * already recorded are honoured, so a variable whose base name was set by hand is compared
     * under that name rather than the one its concrete name suggests.
     */
    public static ClassConformance of(S88Element element) {
        return against(element, element != null ? element.getElementClass() : null);
    }

    /**
     * The divergence of {@code element} against {@code elementClass}, for an element whose class is
     * not the one it currently carries.
     */
    public static ClassConformance against(S88Element element, S88ElementClass elementClass) {
        if (element == null || elementClass == null) {
            return new ClassConformance(List.of(), List.of());
        }
        Map<String, Object> published = DuplicateElementUseCase.deriveSchema(
                element, element.getId(), Map.of());
        Set<String> publishedNames = addresses(published);
        Set<String> declaredNames = addresses(elementClass.getProperties());
        List<String> excess = new ArrayList<>();
        for (String name : publishedNames) {
            if (!declaredNames.contains(name)) {
                excess.add(name);
            }
        }
        List<String> deficit = new ArrayList<>();
        for (String name : declaredNames) {
            if (!publishedNames.contains(name)) {
                deficit.add(name);
            }
        }
        return new ClassConformance(excess, deficit);
    }

    /**
     * Every base name the element publishes, in the form a pointer uses: the container and the name
     * for a variable, the bare name for a top level attribute, which belongs to no container.
     * <p>
     * The pointers the element records are honoured, so a variable whose base name was set by hand
     * appears under that name rather than the one its concrete name suggests.
     *
     * @param element element to inspect, may be {@code null}
     * @return the addresses it publishes, empty when there is nothing to inspect
     */
    public static Set<String> publishedAddresses(S88Element element) {
        if (element == null) {
            return Set.of();
        }
        return addresses(DuplicateElementUseCase.deriveSchema(element, element.getId(), Map.of()));
    }

    /**
     * Whether the element already publishes every one of the given addresses. The question a caller
     * has to ask before telling the user how many other elements a change to the class would leave
     * behind: an element that already publishes what is about to be added is not left short of it.
     *
     * @param element   element to inspect, may be {@code null}
     * @param addresses the addresses, in the form of a pointer, may be {@code null}
     * @return {@code true} when the element publishes all of them
     */
    public static boolean publishesAll(S88Element element, List<String> addresses) {
        if (element == null || addresses == null || addresses.isEmpty()) {
            return true;
        }
        return publishedAddresses(element).containsAll(addresses);
    }

    /**
     * Every base name a schema addresses, in the form a pointer uses: the container and the name for
     * a variable, the bare name for a top level attribute, which belongs to no container.
     */
    private static Set<String> addresses(Map<String, Object> schema) {
        Set<String> addresses = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();
            if (CONTAINER_KEYS.contains(key)) {
                if (entry.getValue() instanceof Map<?, ?> container) {
                    for (Object childKey : container.keySet()) {
                        addresses.add(key + "/" + String.valueOf(childKey));
                    }
                }
            } else if (!NameValidator.isReservedProperty(key)) {
                addresses.add(key);
            }
        }
        return addresses;
    }

    /**
     * The base names a variable of {@code element} is published under, with the ones the element
     * recorded itself taking precedence over the name its concrete name would suggest.
     */
    public static Map<String, String> publishedBaseNames(S88Element element) {
        Map<String, String> pointers = new LinkedHashMap<>();
        if (element == null) {
            return pointers;
        }
        for (String container : CONTAINER_KEYS) {
            Object raw = element.getProperties().get(container);
            if (!(raw instanceof Map<?, ?> containerMap)) {
                continue;
            }
            for (Object childKey : containerMap.keySet()) {
                String variable = String.valueOf(childKey);
                String base = BaseNameSupport.baseNameOf(element.getBaseName(container, variable));
                if (base == null) {
                    base = BaseNameSupport.suggestBaseName(variable, element.getId());
                }
                pointers.put(container + "/" + variable, base);
            }
        }
        for (Map.Entry<String, Object> property : element.getProperties().entrySet()) {
            String name = property.getKey();
            if (CONTAINER_KEYS.contains(name) || NameValidator.isReservedProperty(name)) {
                continue;
            }
            String base = element.getBaseName(null, name);
            if (base == null) {
                base = BaseNameSupport.suggestBaseName(name, element.getId());
            }
            pointers.put(name, base);
        }
        return pointers;
    }

    /**
     * Adds to the class every base name the element publishes that the class does not declare yet,
     * creating a container when the class has none. The class is the contract, so this changes what
     * every other instance of the type is expected to publish: the siblings keep what they have and
     * are left in deficit, which the caller is expected to tell the user about rather than repair
     * behind their back.
     *
     * @return the number of base names the class did not hold and now does
     */
    public static int addToClass(S88Element element, S88ElementClass elementClass) {
        if (element == null || elementClass == null) {
            throw new IllegalArgumentException("An element and a class are required.");
        }
        int before = addresses(elementClass.getProperties()).size();
        DuplicateElementUseCase.mergeMissingBases(elementClass, element,
                DuplicateElementUseCase.deriveSchema(element, element.getId(), Map.of()));
        return addresses(elementClass.getProperties()).size() - before;
    }

    /**
     * Publishes on the element every base name the class declares and the element does not, under
     * the name the element's own variables are named by, seeded with the class's definition of the
     * variable. The counterpart of {@link #addToClass}: it makes the element match the contract
     * without touching the contract, so no sibling is affected.
     *
     * @return the base names that were published
     */
    public static List<String> alignWithClass(S88Element element, S88ElementClass elementClass) {
        if (element == null || elementClass == null) {
            throw new IllegalArgumentException("An element and a class are required.");
        }
        Set<String> known = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : publishedBaseNames(element).entrySet()) {
            int slash = entry.getKey().indexOf('/');
            String base = BaseNameSupport.baseNameOf(entry.getValue());
            known.add(slash < 0 ? base : entry.getKey().substring(0, slash) + "/" + base);
        }
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Object> entry : elementClass.getProperties().entrySet()) {
            String key = entry.getKey();
            if (CONTAINER_KEYS.contains(key)) {
                if (!(entry.getValue() instanceof Map<?, ?> container)) {
                    continue;
                }
                // A variable lives inside its container, so the container map is rebuilt with the
                // new entry rather than the entry being published at the top level of the element.
                Map<String, Object> published = new LinkedHashMap<>();
                if (element.getProperties().get(key) instanceof Map<?, ?> existing) {
                    for (Map.Entry<?, ?> held : existing.entrySet()) {
                        published.put(String.valueOf(held.getKey()), held.getValue());
                    }
                }
                int addedHere = 0;
                for (Map.Entry<?, ?> variable : container.entrySet()) {
                    String base = String.valueOf(variable.getKey());
                    if (known.contains(key + "/" + base)) {
                        continue;
                    }
                    String name = BaseNameSupport.rePrefixed(base, null, element.getId());
                    published.put(name, PropertyValues.deepCopy(variable.getValue()));
                    element.setBaseName(key, name, key + "/" + base);
                    added.add(key + "/" + base);
                    addedHere++;
                }
                if (addedHere > 0) {
                    element.setProperty(key, published);
                }
            } else if (!NameValidator.isReservedProperty(key)) {
                if (known.contains(key)) {
                    continue;
                }
                // The concrete name of an attribute follows the element it belongs to, the way the
                // attributes the element already carries are named; the base name is what the
                // class declares.
                String name = BaseNameSupport.rePrefixed(key, null, element.getId());
                element.setProperty(name, PropertyValues.deepCopy(entry.getValue()));
                element.setBaseName(null, name, key);
                added.add(key);
            }
        }
        return added;
    }
}
