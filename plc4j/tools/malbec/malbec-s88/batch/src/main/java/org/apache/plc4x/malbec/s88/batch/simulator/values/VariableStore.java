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
package org.apache.plc4x.malbec.s88.batch.simulator.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformEnumerations;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.PlatformVariables;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;

/** The values the simulated PLC publishes and reads. */
public final class VariableStore {

    private static final String TYPE = "Type";
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";
    private static final String DEFAULT = "Default";

    private final Map<String, ProcessVariable> variables = new LinkedHashMap<>();
    private final Map<String, Object> defaults = new LinkedHashMap<>();

    public static VariableStore of(S88PlantSnapshot plant, Collection<String> moduleIds,
                                   List<String> warnings) {
        VariableStore store = new VariableStore();
        if (plant == null) {
            return store;
        }
        List<String> ids = new ArrayList<>();
        if (moduleIds != null) {
            ids.addAll(moduleIds);
        }
        if (ids.isEmpty()) {
            for (S88Element module : PlatformVariables.modulesOf(plant.getRoot())) {
                ids.add(module.getId());
            }
        }
        for (String id : ids) {
            S88Element module = id == null ? null : plant.findById(id).orElse(null);
            if (module == null) {
                if (warnings != null) {
                    warnings.add("The plant has no module '" + id + "' to simulate.");
                }
                continue;
            }
            store.addDefinitions(plant, module, S88PlantModel.REPORTS);
            store.addDefinitions(plant, module, S88PlantModel.PARAMETERS);
            store.addPlatformVariables(module, warnings);
        }
        return store;
    }

    public void define(String name, DataType type, String unit, List<String> enumerationValues,
                       Object defaultValue, double simTime) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A variable needs a name.");
        }
        DataType actualType = type == null ? DataType.STRING : type;
        List<String> values = enumerationValues == null ? List.of() : List.copyOf(enumerationValues);
        Object initial = coerce(defaultValue, actualType, values);
        variables.put(name, new ProcessVariable(name, initial, actualType, unit, simTime, values));
        defaults.put(name, initial);
    }

    public boolean contains(String name) {
        return variables.containsKey(name);
    }

    public ProcessVariable get(String name) {
        return variables.get(name);
    }

    public Object value(String name) {
        ProcessVariable variable = variables.get(name);
        return variable == null ? null : variable.value();
    }

    public Double numeric(String name) {
        ProcessVariable variable = variables.get(name);
        if (variable == null || !(variable.value() instanceof Number number)) {
            return null;
        }
        return number.doubleValue();
    }

    public String text(String name) {
        ProcessVariable variable = variables.get(name);
        if (variable == null || variable.value() == null) {
            return null;
        }
        return variable.value() instanceof Enum<?> value ? value.name() : variable.value().toString();
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(variables.keySet());
    }

    public Map<String, ProcessVariable> variables() {
        return Collections.unmodifiableMap(variables);
    }

    public void set(String name, Object value, double simTime) {
        ProcessVariable variable = variables.get(name);
        if (variable == null) {
            throw new IllegalArgumentException("The plant does not publish '" + name + "'.");
        }
        Object coerced = coerce(value, variable.type(), variable.enumerationValues());
        if (Objects.equals(variable.value(), coerced)) {
            return;
        }
        variables.put(name, variable.withValue(coerced, simTime));
    }

    public void setIfPresent(String name, Object value, double simTime) {
        if (variables.containsKey(name)) {
            set(name, value, simTime);
        }
    }

    public void reset(double simTime) {
        defaults.forEach((name, value) -> {
            ProcessVariable variable = variables.get(name);
            if (variable != null) {
                variables.put(name, variable.withValue(value, simTime));
            }
        });
    }

    private void addDefinitions(S88PlantSnapshot plant, S88Element element, String container) {
        Map<String, Object> definitions = element.getStructuredProperty(container);
        if (definitions == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> definition = copy(raw);
            DataType type = DataType.fromString(text(definition, TYPE));
            if (type == null) {
                type = DataType.STRING;
            }
            String enumName = text(definition, UNITS_OR_ENUM);
            List<String> enumerationValues = enumerationValues(plant, enumName);
            String unit = type == DataType.ENUMERATION ? null : enumName;
            Object defaultValue = defaultValue(text(definition, DEFAULT), type, enumerationValues);
            define(entry.getKey(), type, unit, enumerationValues, defaultValue, 0.0);
        }
    }

    private void addPlatformVariables(S88Element module, List<String> warnings) {
        for (PlatformVariable variable : PlatformVariable.values()) {
            String name = variable.publishedName(module.getId());
            if (variables.containsKey(name)) {
                continue;
            }
            if (warnings != null) {
                warnings.add("The module '" + module.getId() + "' does not publish '" + name
                        + "'; the simulator adds it.");
            }
            List<String> values = enumerationValues(null, variable.enumeration());
            define(name, variable.dataType(), null, values, variable.defaultValue(), 0.0);
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
        return enumeration == null ? List.of() : List.copyOf(enumeration.getValues().keySet());
    }

    private static Object defaultValue(String text, DataType type, List<String> enumerationValues) {
        if (text == null || text.isBlank()) {
            return zero(type, enumerationValues);
        }
        return switch (type) {
            case INTEGER, REAL -> parseNumber(text);
            case ENUMERATION -> chooseEnumeration(text, enumerationValues);
            case STRING -> text;
        };
    }

    private static Object coerce(Object value, DataType type, List<String> enumerationValues) {
        if (value == null) {
            return zero(type, enumerationValues);
        }
        return switch (type) {
            case INTEGER, REAL -> {
                if (value instanceof Number number) {
                    yield number.doubleValue();
                }
                if (value instanceof String text) {
                    yield parseNumber(text);
                }
                throw new IllegalArgumentException("A numeric variable cannot hold '"
                        + value.getClass().getSimpleName() + "'.");
            }
            case ENUMERATION -> {
                if (value instanceof Number) {
                    throw new IllegalArgumentException("An enumeration cannot hold a number.");
                }
                yield chooseEnumeration(value.toString(), enumerationValues);
            }
            case STRING -> {
                if (value instanceof Number) {
                    throw new IllegalArgumentException("A text variable cannot hold a number.");
                }
                yield value.toString();
            }
        };
    }

    private static Object zero(DataType type, List<String> enumerationValues) {
        return switch (type) {
            case INTEGER, REAL -> 0.0;
            case ENUMERATION -> enumerationValues.isEmpty() ? "" : enumerationValues.get(0);
            case STRING -> "";
        };
    }

    private static double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + text + "' is not a number.");
        }
    }

    private static String chooseEnumeration(String text, List<String> values) {
        String wanted = text.trim();
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return value;
            }
        }
        if (values.isEmpty()) {
            return wanted;
        }
        throw new IllegalArgumentException("'" + text + "' is not one of " + values + ".");
    }

    private static Map<String, Object> copy(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String text(Map<String, Object> definition, String key) {
        Object value = definition.get(key);
        return value == null ? null : value.toString();
    }
}
