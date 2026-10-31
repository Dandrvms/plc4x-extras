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
package org.apache.plc4x.malbec.s88.batch;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.PlatformVariables;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.batch.builder.BatchRequest;
import org.apache.plc4x.malbec.s88.batch.model.SimulationProfile;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;
import org.apache.plc4x.malbec.s88.core.UpdateRecipeParameterUseCase;

/** The plant and recipe used by the batch builder tests. */
public final class DemoFixtures {

    public static final String RECIPE_ID = "DEMO_CALENTAMIENTO";
    public static final String BATCH_ID = "BATCH-DEMO";

    private DemoFixtures() {
        /* This utility class should not be instantiated */
    }

    public static BatchRequest request(String batchId) {
        S88PlantSnapshot plant = plant();
        return new BatchRequest(demoRecipe(plant), plant, batchId, Map.of(), null);
    }

    public static BatchRequest request(S88MasterRecipe recipe, S88PlantSnapshot plant,
                                       String batchId, Map<String, String> overrides,
                                       SimulationProfile profile) {
        return new BatchRequest(recipe, plant, batchId, overrides, profile);
    }

    public static S88PlantSnapshot plant() {
        S88Element area = element("AREA_1", "uid-area", S88Level.AREA);
        S88Element cell = element("PC_1", "uid-cell", S88Level.PROCESSCELL);
        S88Element reactor = element("REACTOR_1", "uid-reactor-1", S88Level.UNIT);
        S88Element pump = element("BOMBA_1", "uid-bomba-1", S88Level.EQUIPMENTMODULE);
        S88Element heater1 = element("CALENTADOR_1", "uid-cal-1", S88Level.EQUIPMENTMODULE);
        S88Element heater2 = element("CALENTADOR_2", "uid-cal-2", S88Level.EQUIPMENTMODULE);
        area.addChild(cell);
        cell.addChild(reactor);
        reactor.addChild(pump);
        reactor.addChild(heater1);
        reactor.addChild(heater2);

        S88ElementClass reactorType = equipmentClass("REACTOR_TIPO", S88Level.UNIT);
        S88ElementClass pumpType = equipmentClass("BOMBA_TIPO", S88Level.EQUIPMENTMODULE);
        S88ElementClass heaterType = equipmentClass("CALENTADOR_TIPO", S88Level.EQUIPMENTMODULE);
        reactor.setClass(reactorType);
        reactor.addElementClass(reactorType);
        pump.setClass(pumpType);
        pump.addElementClass(pumpType);
        heater1.setClass(heaterType);
        heater1.addElementClass(heaterType);
        heater2.setClass(heaterType);
        heater2.addElementClass(heaterType);

        addClassVariable(heaterType, S88PlantModel.REPORTS, "TEMPERATURA_ACTUAL",
                "REAL", "20", null);
        addClassVariable(heaterType, S88PlantModel.PARAMETERS, "TEMPERATURA_SP",
                "REAL", "0", null);
        addClassVariable(heaterType, S88PlantModel.PARAMETERS, "DURACION",
                "INTEGER", "0", null);
        addElementVariable(heater1, S88PlantModel.REPORTS, "TEMPERATURA_ACTUAL",
                "REAL", "20", null);
        addElementVariable(heater1, S88PlantModel.PARAMETERS, "TEMPERATURA_SP",
                "REAL", "0", null);
        addElementVariable(heater1, S88PlantModel.PARAMETERS, "DURACION",
                "INTEGER", "0", null);
        addElementVariable(heater2, S88PlantModel.REPORTS, "TEMPERATURA_ACTUAL",
                "REAL", "20", null);
        addElementVariable(heater2, S88PlantModel.PARAMETERS, "TEMPERATURA_SP",
                "REAL", "0", null);
        addElementVariable(heater2, S88PlantModel.PARAMETERS, "DURACION",
                "INTEGER", "0", null);

        S88PlantModel model = new S88PlantModel(area);
        model.registerClass(reactorType);
        model.registerClass(pumpType);
        model.registerClass(heaterType);
        PlatformVariables.injectInto(model);
        return PlantSnapshotUseCase.of(model);
    }

    public static S88MasterRecipe demoRecipe(S88PlantSnapshot plant) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute(RECIPE_ID, S88RecipeKind.CLASS);
        recipe.setVersion("1.0");
        S88RecipeElement unit = EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BEGIN",
                "UP_REACTOR", "UP_REACTOR", "T_UP", "REACTOR_TIPO");
        S88RecipeElement load = EditProcedureLogicUseCase.insertStepAfter(recipe, unit, "BEGIN",
                "CARGAR", "CARGAR", "T_CARGAR", "BOMBA_TIPO");
        S88RecipeElement heat = EditProcedureLogicUseCase.insertStepAfter(recipe, unit, "CARGAR",
                "CALENTAR", "CALENTAR", "T_CALENTAR", "CALENTADOR_TIPO");
        S88RecipeElement hold = EditProcedureLogicUseCase.insertStepAfter(recipe, unit, "CALENTAR",
                "MANTENER", "MANTENER", "T_MANTENER", "CALENTADOR_TIPO");
        SetBarConditionUseCase.waitUntil(recipe, unit, "T_CALENTAR", plant, null,
                "STATE", "=", "COMPLETE");
        SetBarConditionUseCase.waitUntil(recipe, unit, "T_MANTENER", plant, null,
                "TEMPERATURA_ACTUAL", ">=", "72");
        UpdateRecipeParameterUseCase.execute(recipe, heat, "Parameters/TEMPERATURA_SP",
                "72", DataType.REAL, null);
        UpdateRecipeParameterUseCase.execute(recipe, hold, "Parameters/DURACION",
                "30", DataType.INTEGER, null);
        return recipe;
    }

    public static S88MasterRecipe alternativeRecipe(S88PlantSnapshot plant) {
        S88MasterRecipe recipe = branchableRecipe("DEMO_ALTERNATIVA");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "UP_REACTOR")
                .setEquipmentClassId("REACTOR_TIPO");
        return recipe;
    }

    public static S88MasterRecipe parallelRecipe(S88PlantSnapshot plant) {
        S88MasterRecipe recipe = branchableRecipe("DEMO_PARALELA");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "UP_REACTOR")
                .setEquipmentClassId("REACTOR_TIPO");
        return recipe;
    }

    public static S88MasterRecipe loopRecipe(S88PlantSnapshot plant) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("DEMO_BUCLE",
                S88RecipeKind.CLASS);
        recipe.setVersion("1.0");
        EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BEGIN", "U1", "U1", "T_U1",
                "REACTOR_TIPO");
        EditProcedureLogicUseCase.insertStepAfter(recipe, null, "U1", "U2", "U2", "T_U2",
                "REACTOR_TIPO");
        S88ProcedureLink toEnd = null;
        for (S88ProcedureLink link : recipe.getProcedureLogic().getLinks()) {
            if (names(link.getFrom(), "T1") && names(link.getTo(), "END")) {
                toEnd = link;
                break;
            }
        }
        if (toEnd == null) {
            throw new IllegalStateException("The loop fixture did not get a line to END");
        }
        recipe.getProcedureLogic().removeLink(toEnd);
        S88ProcedureLink loop = new S88ProcedureLink("L_LOOP");
        loop.addFrom(S88IdRef.transition("T1"));
        loop.addTo(S88IdRef.transition("T_U1"));
        recipe.getProcedureLogic().addLink(loop);
        recipe.getProcedureLogic().addLink(toEnd);
        return recipe;
    }

    private static S88MasterRecipe branchableRecipe(String id) {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute(id, S88RecipeKind.CLASS);
        recipe.setVersion("1.0");
        EditProcedureLogicUseCase.insertStepAfter(recipe, null, "BEGIN", "UP_REACTOR",
                "UP_REACTOR", "T_UP", "REACTOR_TIPO");
        return recipe;
    }

    private static boolean names(java.util.List<S88IdRef> refs, String id) {
        return refs.stream().anyMatch(ref -> id.equals(ref.getValue()));
    }

    private static S88Element element(String id, String uid, S88Level level) {
        return new S88Element(uid).setId(id).setLevel(level);
    }

    private static S88ElementClass equipmentClass(String name, S88Level level) {
        S88ElementClass elementClass = new S88ElementClass();
        elementClass.setName(name);
        elementClass.setTargetLevel(level);
        return elementClass;
    }

    private static void addClassVariable(S88ElementClass elementClass, String container,
                                         String name, String type, String defaultValue,
                                         String enumeration) {
        Map<String, Object> definitions = copy(elementClass.getProperties().get(container));
        definitions.put(name, definition(type, defaultValue, enumeration));
        elementClass.setProperty(container, definitions);
    }

    private static void addElementVariable(S88Element element, String container, String name,
                                           String type, String defaultValue, String enumeration) {
        Map<String, Object> definitions = copy(element.getProperties().get(container));
        definitions.put(name, definition(type, defaultValue, enumeration));
        element.setProperty(container, definitions);
    }

    private static Map<String, Object> copy(Object source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        }
        return result;
    }

    private static Map<String, Object> definition(String type, String defaultValue,
                                                  String enumeration) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("Type", type);
        if (enumeration != null) {
            definition.put("Eng_Units/Enum", enumeration);
        }
        definition.put("Default", defaultValue);
        return definition;
    }
}
