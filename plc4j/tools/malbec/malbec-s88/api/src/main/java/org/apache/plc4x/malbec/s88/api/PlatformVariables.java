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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the ISA-88 variables on the equipment modules of a plant.
 * <p>
 * They are added wherever a module comes into existence, so the plant, the equipment type and any
 * copy of the module all publish the same ones and none of them can go missing: a module created
 * from a type that already declares them receives them with the rest of the type, and one created
 * from a type that does not yet declare them is given them and pushes them onto the type, so the
 * next module of that type starts from the same contract.
 * <p>
 * Adding them twice changes nothing, which is what lets the same call sit on the load, create and
 * duplicate paths without any of them having to know which one ran first.
 */
public final class PlatformVariables {

    private PlatformVariables() {
        /* This utility class should not be instantiated */
    }

    /**
     * Whether the variables belong on an element.
     * <p>
     * Only a module executes a phase, so only a module reports a state or receives an order. A unit
     * owns the modules that do, and the state of a unit is the state of the module running in it,
     * which is a question the runtime answers rather than the plant.
     *
     * @param element element to inspect, may be {@code null}
     * @return {@code true} when the element is an equipment module
     */
    public static boolean appliesTo(S88Element element) {
        return element != null && element.getLevel() == S88Level.EQUIPMENTMODULE;
    }

    /**
     * Publishes the variables on a module, under the names the module's own id qualifies, and
     * records the base name a recipe addresses each of them by.
     *
     * @param element module receiving the variables, ignored when it is not one
     */
    public static void injectIntoElement(S88Element element) {
        if (!appliesTo(element)) {
            return;
        }
        for (PlatformVariable variable : PlatformVariable.values()) {
            String published = variable.publishedName(element.getId());
            Map<String, Object> container = containerOf(element, variable.container());
            if (container.containsKey(published)) {
                continue;
            }
            container.put(published, variable.definition());
            element.setProperty(variable.container(), container);
            element.setBaseName(variable.container(), published,
                    variable.container() + "/" + variable.baseName());
        }
    }

    /**
     * Declares the variables on an equipment type, keyed by the base name a recipe addresses,
     * which is what the type is: what every instance of it is expected to publish.
     *
     * @param elementClass type receiving the variables, ignored when it does not target modules
     */
    public static void injectIntoClass(S88ElementClass elementClass) {
        if (elementClass == null || elementClass.getTargetLevel() != S88Level.EQUIPMENTMODULE) {
            return;
        }
        for (PlatformVariable variable : PlatformVariable.values()) {
            Map<String, Object> container = containerOf(elementClass, variable.container());
            if (container.containsKey(variable.baseName())) {
                continue;
            }
            container.put(variable.baseName(), variable.definition());
            elementClass.setProperty(variable.container(), container);
        }
    }

    /**
     * Gives the whole plant its platform variables, together with the enumerations that give them
     * their values.
     * <p>
     * This is the path a plant file takes: a module written before these variables existed has
     * none, and the same goes for the type that describes it, so both are filled in on the way in
     * rather than by the user having to add them.
     *
     * @param model plant to complete, ignored when {@code null}
     */
    public static void injectInto(S88PlantModel model) {
        if (model == null) {
            return;
        }
        ensureEnumerations(model);
        injectIntoSubtree(model.getRoot());
    }

    /**
     * Gives the platform variables to every module below an element, and to the type of each.
     * <p>
     * The whole subtree is walked rather than the element alone, because the modules nested under
     * a unit are modules in their own right and each carries its own type. This runs before any
     * schema is derived, so a type built from these modules declares the variables and the copies
     * made afterwards inherit them with the rest of the contract.
     *
     * @param root element to walk down from, ignored when {@code null}
     */
    public static void injectIntoSubtree(S88Element root) {
        for (S88Element element : modulesOf(root)) {
            injectIntoElement(element);
            injectIntoClass(element.getElementClass());
        }
    }

    /**
     * Registers the two enumerations the platform variables are drawn from, unless the plant
     * already holds them.
     *
     * @param model plant to complete, ignored when {@code null}
     */
    public static void ensureEnumerations(S88PlantModel model) {
        if (model == null) {
            return;
        }
        if (model.findEnumeration(PlatformEnumerations.STATE) == null) {
            model.registerEnumeration(PlatformEnumerations.state());
        }
        if (model.findEnumeration(PlatformEnumerations.COMMAND) == null) {
            model.registerEnumeration(PlatformEnumerations.command());
        }
    }

    /**
     * The modules of a plant, in tree order.
     *
     * @param root element to walk, may be {@code null}
     * @return the equipment modules found, never {@code null}
     */
    public static List<S88Element> modulesOf(S88Element root) {
        List<S88Element> modules = new ArrayList<>();
        collectModules(root, modules);
        return modules;
    }

    private static void collectModules(S88Element element, List<S88Element> found) {
        if (element == null) {
            return;
        }
        if (appliesTo(element)) {
            found.add(element);
        }
        for (S88Element child : element.getChildren()) {
            collectModules(child, found);
        }
    }

    /**
     * A copy of the variables a container holds, or an empty one when it holds none.
     * <p>
     * The copy is what lets the caller add a variable and hand the whole container back: the map
     * the element was holding is never changed under it, the same rule the rest of the model
     * follows when a structured property is written.
     */
    private static Map<String, Object> containerOf(S88Element element, String containerKey) {
        Map<String, Object> container = new LinkedHashMap<>();
        if (element.getProperties().get(containerKey) instanceof Map<?, ?> held) {
            for (Map.Entry<?, ?> entry : held.entrySet()) {
                container.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return container;
    }

    private static Map<String, Object> containerOf(S88ElementClass elementClass, String containerKey) {
        Map<String, Object> container = new LinkedHashMap<>();
        if (elementClass.getProperties().get(containerKey) instanceof Map<?, ?> held) {
            for (Map.Entry<?, ?> entry : held.entrySet()) {
                container.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return container;
    }
}
