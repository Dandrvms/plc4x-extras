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
package org.apache.plc4x.malbec.s88.batch.link;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayload;
import org.apache.plc4x.malbec.s88.batch.model.PreparedSetpoint;

/**
 * The variables a batch publishes or receives, with the type each one uses on the wire.
 */
public final class VariableCatalog {

    private static final String TYPE = "Type";
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";

    /**
     * One catalog entry.
     *
     * @param name   the published name
     * @param type   the data type
     * @param unit   the engineering unit, {@code null} for enumerations and text
     * @param values the enumeration values, empty when the variable is not an enumeration
     */
    public record Definition(String name, DataType type, String unit, List<String> values) {

        public Definition {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    private VariableCatalog() {
    }

    /** @return the variables of the plant the payload uses, in plant order */
    public static Map<String, Definition> of(BatchPayload payload) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        if (payload == null || payload.plant() == null) {
            return definitions;
        }
        S88PlantSnapshot plant = payload.plant();
        Set<String> moduleIds = new LinkedHashSet<>(payload.moduleOfStep().values());
        for (String unitId : payload.unitOfStep().values()) {
            moduleIds.add(unitId);
        }
        for (String moduleId : moduleIds) {
            S88Element element = moduleId == null ? null : plant.findById(moduleId).orElse(null);
            if (element == null) {
                continue;
            }
            addDefinitions(plant, element, S88PlantModel.REPORTS, definitions);
            addDefinitions(plant, element, S88PlantModel.PARAMETERS, definitions);
            if (element.getLevel() == S88Level.EQUIPMENTMODULE) {
                addPlatformVariables(element, definitions);
            }
        }
        for (List<PreparedSetpoint> setpoints : payload.setpoints().values()) {
            for (PreparedSetpoint setpoint : setpoints) {
                definitions.putIfAbsent(setpoint.variable(), new Definition(setpoint.variable(),
                        setpoint.dataType(), setpoint.unit(), setpoint.enumerationValues()));
            }
        }
        return definitions;
    }

    /** @return the variables the plant reports for the modules of the payload */
    public static Map<String, Definition> reportsOf(BatchPayload payload) {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        if (payload == null || payload.plant() == null) {
            return definitions;
        }
        S88PlantSnapshot plant = payload.plant();
        Set<String> moduleIds = new LinkedHashSet<>(payload.moduleOfStep().values());
        for (String unitId : payload.unitOfStep().values()) {
            moduleIds.add(unitId);
        }
        for (String moduleId : moduleIds) {
            S88Element element = moduleId == null ? null : plant.findById(moduleId).orElse(null);
            if (element == null) {
                continue;
            }
            addDefinitions(plant, element, S88PlantModel.REPORTS, definitions);
            if (element.getLevel() == S88Level.EQUIPMENTMODULE) {
                for (PlatformVariable variable : PlatformVariable.values()) {
                    if (variable.isReport()) {
                        String name = variable.publishedName(moduleId);
                        definitions.putIfAbsent(name, new Definition(name, variable.dataType(),
                                null, enumerationValues(null, variable.enumeration())));
                    }
                }
            }
        }
        return definitions;
    }

    /** @return the writable variables a recipe sends to the plant */
    public static Map<String, Definition> parametersOf(BatchPayload payload) {
        Map<String, Definition> parameters = new LinkedHashMap<>();
        if (payload == null) {
            return parameters;
        }
        Map<String, Definition> all = of(payload);
        for (List<PreparedSetpoint> setpoints : payload.setpoints().values()) {
            for (PreparedSetpoint setpoint : setpoints) {
                Definition definition = all.get(setpoint.variable());
                parameters.put(setpoint.variable(), definition == null
                        ? new Definition(setpoint.variable(), setpoint.dataType(), setpoint.unit(),
                        setpoint.enumerationValues())
                        : definition);
            }
        }
        return parameters;
    }

    private static void addDefinitions(S88PlantSnapshot plant, S88Element element, String container,
                                       Map<String, Definition> definitions) {
        Map<String, Object> source = element.getStructuredProperty(container);
        if (source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            String name = entry.getKey();
            if (name == null || name.isBlank() || definitions.containsKey(name)) {
                continue;
            }
            String typeText = text(raw, TYPE);
            DataType type = DataType.fromString(typeText);
            if (type == null) {
                type = DataType.STRING;
            }
            String enumName = text(raw, UNITS_OR_ENUM);
            List<String> values = enumerationValues(plant, enumName);
            String unit = type == DataType.ENUMERATION ? null : enumName;
            definitions.put(name, new Definition(name, type, unit, values));
        }
    }

    private static void addPlatformVariables(S88Element module,
                                             Map<String, Definition> definitions) {
        for (PlatformVariable variable : PlatformVariable.values()) {
            String name = variable.publishedName(module.getId());
            definitions.putIfAbsent(name, new Definition(name, variable.dataType(), null,
                    enumerationValues(null, variable.enumeration())));
        }
    }

    private static List<String> enumerationValues(S88PlantSnapshot plant, String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return List.of();
        }
        if (PlatformEnumerations.STATE.equalsIgnoreCase(enumName)) {
            return List.copyOf(PlatformEnumerations.valuesOf(PlatformEnumerations.STATE));
        }
        if (PlatformEnumerations.COMMAND.equalsIgnoreCase(enumName)) {
            return List.copyOf(PlatformEnumerations.valuesOf(PlatformEnumerations.COMMAND));
        }
        if (plant == null) {
            return List.of();
        }
        S88Enumeration enumeration = plant.findEnumeration(enumName);
        if (enumeration == null) {
            return List.of();
        }
        return new ArrayList<>(enumeration.getValues().keySet());
    }

    private static String text(Map<?, ?> definition, String key) {
        Object value = definition.get(key);
        return value == null ? null : value.toString();
    }
}
