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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Enumeration;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;

/**
 * What a bar between two steps waits on.
 * <p>
 * A bar waits on a value read off a piece of equipment, and the two kinds of recipe name that
 * equipment in different words. A recipe written by class names a base name, which something will be
 * resolved against equipment later, so there is no equipment to name and none is written. A recipe
 * written for particular equipment names the equipment it reads off, because two of them can publish
 * a report of the same name and reading the one off the other is a recipe that does the wrong thing.
 * <p>
 * <b>Nothing is offered that is not there.</b> The equipment offered is the equipment that publishes
 * something to wait on, the reports offered are the ones that equipment publishes, and a report it
 * does not publish is refused instead of written down. A bar on a report that is not there is a bar
 * that never opens, which is a recipe that stops halfway with nothing to say why.
 * <p>
 * <b>The value is checked against the type the plant declares.</b> A temperature that is a word is a
 * temperature no equipment will ever hold, and writing it down compares against a value that was
 * never going to be there.
 */
public final class SetBarConditionUseCase {

    /** Where a piece of equipment publishes what it reports. */
    private static final String REPORTS = "Reports";

    /** The type the plant declares for a report. */
    private static final String TYPE = "Type";

    /**
     * The engineering unit of a report, or the name of its enumeration when the type says it is
     * one. Which of the two depends on {@link #TYPE}, and the same property holds both.
     */
    private static final String UNITS_OR_ENUM = "Eng_Units/Enum";

    /** What the plant calls an enumeration once it is stored, which is not how a report names it. */
    private static final String ENUM_PREFIX = "ENUM_";

    private SetBarConditionUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Makes a bar wait until a value read off equipment is as the operator says.
     *
     * @param recipe      recipe being edited, may be {@code null} when no change event is wanted
     * @param step        step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param barId       bar of the chart that waits on the value
     * @param plant       the plant the recipe is written against
     * @param equipmentId equipment the report is read off, {@code null} for a recipe by class
     * @param reportName  report to read, which is a base name in a recipe by class
     * @param operator    how the value is compared, such as {@code >}
     * @param literal     the value it is compared against
     * @throws IllegalArgumentException when there is no bar, no plant, no such report, or a value
     *                                  the report can never hold
     */
    public static void waitUntil(S88MasterRecipe recipe, S88RecipeElement step, String barId,
                                 S88PlantSnapshot plant, String equipmentId, String reportName,
                                 String operator, String literal) {
        S88ProcedureLogic chart = chartOf(recipe, step, plant);
        S88ProcedureTransition bar = chart.findTransition(barId)
                .orElseThrow(() -> new IllegalArgumentException("This chart has no bar called '"
                        + barId + "'."));
        S88ConditionOperator how = S88ConditionOperator.fromSymbol(operator);
        if (how == null) {
            throw new IllegalArgumentException("'" + operator + "' is not something a value can be"
                    + " compared with.");
        }

        boolean byClass = recipe.addressesByClass();
        DeclaredReport report = byClass
                ? reportOfAnyClassUsed(recipe, plant, reportName)
                : reportOf(plant, equipmentId, reportName);
        checkLiteral(plant, report, literal);

        S88VariableAddress address = byClass
                ? S88VariableAddress.parse(REPORTS + "/" + reportName)
                : S88VariableAddress.of(null, reportName);
        EditProcedureLogicUseCase.setCondition(recipe, step, barId,
                S88ConditionExpression.on(byClass ? null : equipmentId, address, how, literal)
                        .toText());
    }

    /**
     * The values a bar waiting on this report has to be given, or nothing when the report is not an
     * enumeration and takes whatever a person writes.
 *
 * <p>Offered rather than only checked, because a value the author has to remember is a value they
 * will get wrong, and the plant already says which ones are right.
 *
 * @param plant       the plant the recipe is written against
 * @param recipe      recipe being edited, to find the class a report is read from
 * @param equipmentId equipment the report is read off, {@code null} for a recipe by class
 * @param reportName  report the bar waits on
 * @return the values it allows, empty when there are none to offer
 */
    public static List<String> allowedValues(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                             String equipmentId, String reportName) {
        DeclaredReport report;
        try {
            report = recipe != null && recipe.addressesByClass()
                    ? reportOfAnyClassUsed(recipe, plant, reportName)
                    : reportOf(plant, equipmentId, reportName);
        } catch (IllegalArgumentException notThere) {
            return List.of();
        }
        if (typeOf(report.type()) != DataType.ENUMERATION) {
            return List.of();
        }
        return membersOf(plant, report.unitsOrEnum());
    }

    /**
     * The equipment a bar of a recipe for particular equipment can wait on, in the order it sits in
     * the plant.
     * <p>
     * Only the equipment that publishes something, because equipment with no report cannot be waited
     * on and offering it makes the operator choose something that does not work.
     *
     * @param plant the plant the recipe is written against
     * @return the names of the equipment that publish reports
     */
    public static List<String> equipmentWithReports(S88PlantSnapshot plant) {
        if (plant == null || plant.getRoot() == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        collectEquipment(plant.getRoot(), found);
        return List.copyOf(found);
    }

    private static void collectEquipment(S88Element element, List<String> found) {
        if (element.getLevel() == S88Level.EQUIPMENTMODULE
                && !reports(element.getProperties()).isEmpty()) {
            found.add(element.getId());
        }
        for (S88Element child : element.getChildren()) {
            collectEquipment(child, found);
        }
    }

    /**
     * The reports a class of equipment publishes, which are the base names a recipe by class waits on.
     *
     * @param plant     the plant the recipe is written against
     * @param className name of the class of equipment
     * @return the names it publishes, in the order the class declares them
     * @throws IllegalArgumentException when the plant has no class of that name
     */
    public static List<String> reportsOfTheClass(S88PlantSnapshot plant, String className) {
        return List.copyOf(classOf(plant, className).reports().keySet());
    }

    /**
     * The reports one piece of equipment publishes.
     *
     * @param plant       the plant the recipe is written against
     * @param equipmentId name the equipment has in the plant
     * @return the names it publishes, in the order it declares them
     * @throws IllegalArgumentException when the plant has no such equipment
     */
    public static List<String> reportsOf(S88PlantSnapshot plant, String equipmentId) {
        return List.copyOf(equipmentOf(plant, equipmentId).reports().keySet());
    }

    /**
     * The base names a recipe by class can wait on, gathered from every class of equipment its steps
     * stand for.
     * <p>
     * They are gathered rather than asked which class, because the bar waits on a value and the class
     * is a detail of where that value comes from. A base name two classes publish appears once.
     *
     * @param recipe recipe being edited
     * @param plant  the plant the recipe is written against
     * @return the base names, in the order the classes declare them
     */
    public static List<String> reportsToWaitOn(S88MasterRecipe recipe, S88PlantSnapshot plant) {
        Map<String, DeclaredReport> byName = new LinkedHashMap<>();
        for (String className : classesUsedBy(recipe)) {
            byName.putAll(classOf(plant, className).reports());
        }
        return List.copyOf(byName.keySet());
    }

    private static DeclaredReport reportOfAnyClassUsed(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                                       String reportName) {
        for (String className : classesUsedBy(recipe)) {
            DeclaredReport report = classOf(plant, className).reports().get(reportName);
            if (report != null) {
                return report;
            }
        }
        throw new IllegalArgumentException(reportName + " is not a report published by any class of"
                + " equipment this recipe uses, so there is nothing to wait on called that.");
    }

    private static DeclaredReport reportOf(S88PlantSnapshot plant, String equipmentId,
                                           String reportName) {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw new IllegalArgumentException("A recipe written for particular equipment has to say"
                    + " which equipment the bar reads from, because two of them can publish a report"
                    + " of the same name.");
        }
        DeclaredReport report = equipmentOf(plant, equipmentId).reports().get(reportName);
        if (report == null) {
            throw new IllegalArgumentException(equipmentId + " does not publish a report called '"
                    + reportName + "'.");
        }
        return report;
    }

    private static EquipmentOf equipmentOf(S88PlantSnapshot plant, String equipmentId) {
        S88Element element = plant == null ? null : plant.findById(equipmentId).orElse(null);
        if (element == null) {
            throw new IllegalArgumentException("The plant has no equipment called '"
                    + equipmentId + "'.");
        }
        return new EquipmentOf(reports(element.getProperties()));
    }

    private static EquipmentOf classOf(S88PlantSnapshot plant, String className) {
        S88ElementClass elementClass = plant == null ? null : plant.findClass(className);
        if (elementClass == null) {
            throw new IllegalArgumentException("The plant has no class of equipment called '"
                    + className + "'.");
        }
        return new EquipmentOf(reports(elementClass.getProperties()));
    }

    /** What one piece of equipment, or one class of equipment, declares it publishes. */
    private record EquipmentOf(Map<String, DeclaredReport> reports) {
    }

    /** What the plant says about one report: its type, and its unit or the name of its enumeration. */
    private record DeclaredReport(String type, String unitsOrEnum) {
    }

    /**
     * The classes of equipment the steps of a recipe stand for, in the order they first appear.
     */
    private static Set<String> classesUsedBy(S88MasterRecipe recipe) {
        Set<String> names = new LinkedHashSet<>();
        if (recipe == null) {
            return names;
        }
        for (S88RecipeElement element : recipe.getAllElements()) {
            if (element.getEquipmentClassId() != null) {
                names.add(element.getEquipmentClassId());
            }
        }
        return names;
    }

    /**
     * Whether the value written is one the report can ever hold.
     * <p>
     * An enumeration report names its enumeration, and the values it takes are the members of that
     * enumeration rather than its name. Reading the name as the list would refuse every value the
     * equipment can actually hold and accept the one string it cannot.
     */
    private static void checkLiteral(S88PlantSnapshot plant, DeclaredReport report, String literal) {
        if (literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("A bar has to say what the value has to be.");
        }
        switch (typeOf(report.type())) {
            case INTEGER -> number(literal, Long::parseLong);
            case REAL -> number(literal, Double::parseDouble);
            case ENUMERATION -> {
                List<String> members = membersOf(plant, report.unitsOrEnum());
                if (!members.isEmpty() && !members.contains(literal.trim())) {
                    throw new IllegalArgumentException("The value has to be one of "
                            + String.join(", ", members) + ", and '" + literal
                            + "' is not one of them.");
                }
            }
            default -> {
                /* A string can hold what a person writes. */
            }
        }
    }

    /**
     * The values the named enumeration allows, in the order it declares them.
     *
     * <p>A report names its enumeration the way the plant file spells it, while the plant model
     * keeps enumerations under a prefix, so both spellings are tried before giving up.
     *
     * @param plant    the plant the recipe is written against
     * @param enumName name of the enumeration, as the report spells it
     * @return the values it allows, or nothing when the plant has no enumeration of that name
     */
    public static List<String> membersOf(S88PlantSnapshot plant, String enumName) {
        if (plant == null || enumName == null || enumName.isBlank()) {
            return List.of();
        }
        for (String name : new String[]{enumName, withoutPrefix(enumName), ENUM_PREFIX + enumName}) {
            S88Enumeration enumeration = plant.findEnumeration(name);
            if (enumeration != null) {
                return List.copyOf(enumeration.getValues().keySet());
            }
        }
        return List.of();
    }

    private static String withoutPrefix(String name) {
        return name.startsWith(ENUM_PREFIX) ? name.substring(ENUM_PREFIX.length()) : name;
    }

    private static DataType typeOf(String declared) {
        DataType type = declared == null ? null : DataType.fromString(declared);
        return type == null ? DataType.STRING : type;
    }

    private static void number(String literal, java.util.function.Function<String, Number> read) {
        try {
            read.apply(literal.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("The value has to be a number, and '" + literal
                    + "' is not one.");
        }
    }

/**
     * The reports a piece of equipment, or one class of equipment, declares.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, DeclaredReport> reports(Map<String, Object> properties) {
        Map<String, DeclaredReport> byName = new LinkedHashMap<>();
        Object declared = properties == null ? null : properties.get(REPORTS);
        if (!(declared instanceof Map<?, ?> container)) {
            return byName;
        }
        for (Map.Entry<String, Object> report : ((Map<String, Object>) container).entrySet()) {
            if (!(report.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> definition = (Map<String, Object>) raw;
            Object type = definition.get(TYPE);
            Object unitsOrEnum = definition.get(UNITS_OR_ENUM);
            byName.put(report.getKey(), new DeclaredReport(
                    type == null ? null : type.toString(),
                    unitsOrEnum == null ? null : unitsOrEnum.toString()));
        }
        return byName;
    }

    private static S88ProcedureLogic chartOf(S88MasterRecipe recipe, S88RecipeElement step,
                                             S88PlantSnapshot plant) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to put a bar on.");
        }
        if (plant == null) {
            throw new IllegalArgumentException("There is no plant to read a report from. What a bar"
                    + " waits on is something the plant declares.");
        }
        return chart;
    }
}