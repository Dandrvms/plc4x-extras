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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The variables every equipment module publishes, whatever the recipe asks of it.
 * <p>
 * A module runs the phase by itself, from beginning to end, and reports how it is going while the
 * batch server is free to come and go. These variables are the vocabulary of that conversation:
 * {@code COMMAND} goes down to the module, and the rest comes back up.
 * <p>
 * The names are published re-suffixed with the id of the module, the same way every other variable
 * of the plant is: the base name {@code STATE} of module {@code CALENTAMIENTO_TANQUE_1} is
 * published as {@code STATE_CALENTAMIENTO_TANQUE_1}, so a class recipe addressing the base name
 * still reaches one instance, and a recipe bound to an instance reaches it by its own name.
 * <p>
 * A unit declares none of them: a unit does not execute anything, it owns the modules that do, and
 * the state of a unit is the state of the phase running in it.
 */
public enum PlatformVariable {

    /** What the module is doing now, read back from the module. The value is the state. */
    STATE("STATE", S88PlantModel.REPORTS, DataType.ENUMERATION, PlatformEnumerations.STATE, "IDLE"),

    /** Set when the module gives up because something in it failed, so the batch is told at once. */
    FAILURE("FAILURE", S88PlantModel.REPORTS, DataType.INTEGER, null, "0"),

    /** A request from the module asking the operator for something while the phase is running. */
    REQUEST("REQUEST", S88PlantModel.REPORTS, DataType.INTEGER, null, "0"),

    /** The step the module is on inside its phase. Only the recipe cares, and only while it runs. */
    STEP_INDEX("STEP_INDEX", S88PlantModel.REPORTS, DataType.INTEGER, null, "0"),

    /** How many steps the phase has. Together with {@code STEP_INDEX} it names the current step. */
    STEP_COUNT("STEP_COUNT", S88PlantModel.REPORTS, DataType.INTEGER, null, "0"),

    /**
     * The order the batch gives the module. {@code NONE} is the value a module reads when no order
     * has arrived, which is what it sees whenever the batch server is not there: it carries on with
     * its own routine.
     */
    COMMAND("COMMAND", S88PlantModel.PARAMETERS, DataType.ENUMERATION, PlatformEnumerations.COMMAND, "NONE");

    private final String baseName;
    private final String container;
    private final DataType dataType;
    private final String enumeration;
    private final String defaultValue;

    PlatformVariable(String baseName, String container, DataType dataType,
                      String enumeration, String defaultValue) {
        this.baseName = baseName;
        this.container = container;
        this.dataType = dataType;
        this.enumeration = enumeration;
        this.defaultValue = defaultValue;
    }

    /**
     * The name a recipe addresses, the same on every module of the plant.
     *
     * @return the base name, never {@code null}
     */
    public String baseName() {
        return baseName;
    }

    /**
     * Where the variable is published: a value the module reports, or an order it receives.
     *
     * @return the property name of the container, never {@code null}
     */
    public String container() {
        return container;
    }

    /**
     * The data type the editor shows and the exporters write.
     *
     * @return the data type, never {@code null}
     */
    public DataType dataType() {
        return dataType;
    }

    /**
     * The enumeration that gives this variable its values.
     *
     * @return the enumeration name, or {@code null} when the variable is not an enumeration
     */
    public String enumeration() {
        return enumeration;
    }

    /**
     * The value the variable holds before anything is sent to the module.
     *
     * @return the default value, as it is stored, never {@code null}
     */
    public String defaultValue() {
        return defaultValue;
    }

    /**
     * Whether the variable is input or output.
     *
     * @return {@code true} when the batch reads this variable
     */
    public boolean isReport() {
        return S88PlantModel.REPORTS.equals(container);
    }

    /**
     * The name this variable is published.
     *
     * @param elementId id of the module, may be {@code null} or blank
     * @return the published name, e.g. {@code STATE_CALENTAMIENTO_TANQUE_1}, or the bare base name
     *         when the module has no id to qualify it
     */
    public String publishedName(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            return baseName;
        }
        return baseName + "_" + elementId.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * The definition of the variable, as it is stored in the plant: the data type, the
     * enumeration that gives it its values and the value it starts from.
     *
     * @return a fresh map holding the definition, never {@code null}
     */
    public Map<String, Object> definition() {
        Map<String, Object> bag = new LinkedHashMap<>();
        bag.put("Type", dataType.name());
        if (enumeration != null) {
            bag.put("Eng_Units/Enum", enumeration);
        }
        bag.put("Default", defaultValue);
        return bag;
    }

    /**
     * The base name a recipe addresses, looked up by name.
     *
     * @param baseName candidate name, may be {@code null}
     * @return the variable, or {@link Optional#empty()} when no platform variable goes by that name
     */
    public static Optional<PlatformVariable> find(String baseName) {
        if (baseName == null) {
            return Optional.empty();
        }
        for (PlatformVariable variable : values()) {
            if (variable.baseName.equals(baseName)) {
                return Optional.of(variable);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a name belongs to the platform, so a user is not allowed to define a variable that
     * would collide with one every module already publishes.
     *
     * @param baseName candidate name, may be {@code null}
     * @return {@code true} when the name is reserved by the platform
     */
    public static boolean isPlatformName(String baseName) {
        return find(baseName).isPresent();
    }

    /**
     * The base name a published name answers to, read off the variable it is qualified with.
     * <p>
     * Only the names this platform publishes are recognized, so a name that merely looks like a
     * qualified one is left alone.
     *
     * @param publishedName name as published on a module, may be {@code null}
     * @return the variable it belongs to, or {@link Optional#empty()} when the name is not one
     */
    public static Optional<PlatformVariable> findByPublishedName(String publishedName) {
        if (publishedName == null || publishedName.isBlank()) {
            return Optional.empty();
        }
        for (PlatformVariable variable : values()) {
            if (publishedName.equals(variable.baseName) || publishedName.startsWith(variable.baseName + "_")) {
                return Optional.of(variable);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the batch writes this variable while the recipe runs, rather than the author writing it
     * in the editor.
     * <p>
     * Only a variable that is a parameter can be written at all, and of those the platform declares
     * one: the order. The rest are read back from the module and no author ever sets them.
     *
     * @param publishedName name as published on a module, may be {@code null}
     * @return {@code true} when the value belongs to the run and not to the recipe being written
     */
    public static boolean isWrittenByTheBatch(String publishedName) {
        return findByPublishedName(publishedName)
                .filter(variable -> !variable.isReport())
                .isPresent();
    }
}
