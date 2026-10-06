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

import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.PlatformVariables;
import org.apache.plc4x.malbec.s88.api.S88Element;
import org.apache.plc4x.malbec.s88.api.S88ElementClass;
import org.apache.plc4x.malbec.s88.api.S88Level;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformVariablesConformanceTest {

    /** A plant model that keeps hold of the types it was given, the way a loaded plant does. */
    private static final class S88PlantModelFixture {
        private final S88PlantModel model;

        S88PlantModelFixture(S88Element root) {
            this.model = new S88PlantModel(root);
        }

        void register(S88ElementClass type) {
            model.registerClass(type);
        }

        S88PlantModel model() {
            return model;
        }
    }

    private static S88Element module(String id) {
        return new S88Element().setId(id).setLevel(S88Level.EQUIPMENTMODULE);
    }

    @Test
    void aModuleIsNotShortOfWhatItsTypeDeclares() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);
        S88Element em = module("CALENTAMIENTO_TANQUE_1").setClass(type);

        PlatformVariables.injectIntoClass(type);
        PlatformVariables.injectIntoElement(em);

        ClassConformance conformance = ClassConformance.of(em);
        assertTrue(conformance.isConforming(),
                "the platform variables are declared on the type as well, so the module publishes"
                        + " nothing its type does not know: " + conformance.excess() + " / " + conformance.deficit());
    }

    @Test
    void aClassOnlyKeepsWhatTheUserHasNotRemoved() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);
        type.setProperty("Parameters", Map.of("NIVEL", Map.of("Type", "REAL")));
        S88Element em = module("CALENTAMIENTO_TANQUE_1").setClass(type);

        PlatformVariables.injectIntoClass(type);

        Map<String, Object> schema = (Map<String, Object>) type.getProperty("Parameters");
        assertTrue(schema.containsKey("NIVEL"), "what the type already declared is left alone");
        assertTrue(schema.containsKey("COMMAND"), "and the platform variable is added next to it");
    }

    @Test
    void aRecipeResolvesTheStateOfAModuleByItsBaseName() {
        S88Element em = module("CALENTAMIENTO_TANQUE_1");

        PlatformVariables.injectIntoElement(em);

        assertEquals("STATE_CALENTAMIENTO_TANQUE_1",
                BaseNameResolver.resolveVariable(em, "Reports", "STATE").orElseThrow(),
                "a recipe addressing the base name reaches the concrete variable of this module");
        assertEquals("COMMAND_CALENTAMIENTO_TANQUE_1",
                BaseNameResolver.resolveAll(em, "Parameters").get("COMMAND"),
                "and the order it is given is reachable the same way");
    }

    @Test
    void aModuleCarriesNoVariableAnsweringATwiceAsTheSameBaseName() {
        S88Element em = module("CALENTAMIENTO_TANQUE_1");

        PlatformVariables.injectIntoElement(em);

        assertTrue(BaseNameResolver.validate(em).isEmpty(),
                "every module publishes a distinct name for every one of its variables, so a recipe"
                        + " bound to it can tell them apart");
    }

    @Test
    void theStateOfAModuleIsNotConfusedWithTheStateOfAnother() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);
        S88Element first = module("CALENTAMIENTO_TANQUE_1").setClass(type);
        S88Element second = module("CALENTAMIENTO_TANQUE_2").setClass(type);

        PlatformVariables.injectIntoClass(type);
        PlatformVariables.injectIntoElement(first);
        PlatformVariables.injectIntoElement(second);

        assertEquals("STATE_CALENTAMIENTO_TANQUE_1",
                BaseNameResolver.resolveVariable(first, "Reports", "STATE").orElseThrow());
        assertEquals("STATE_CALENTAMIENTO_TANQUE_2",
                BaseNameResolver.resolveVariable(second, "Reports", "STATE").orElseThrow());
    }

    @Test
    void aModuleCreatedFromATypeThatDeclaresThemPublishesThem() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);
        PlatformVariables.injectIntoClass(type);
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        root.addChild(unit);
        S88PlantModelFixture model = new S88PlantModelFixture(root);
        model.register(type);

        CreateElementUseCase.execute(model.model(), unit, "CALENTAMIENTO_TANQUE_1", type);

        S88Element created = model.model().findById("CALENTAMIENTO_TANQUE_1").orElseThrow();
        assertTrue(created.getStructuredProperty("Reports").containsKey("STATE_CALENTAMIENTO_TANQUE_1"),
                "the module receives the platform variables the same way it receives the rest of the type");
        assertTrue(ClassConformance.of(created).isConforming());
    }

    @Test
    void aModuleThatIsDuplicatedCarriesTheSameVariablesAsItsSource() {
        S88ElementClass type = new S88ElementClass();
        type.setName("CALENTAMIENTO_TANQUE");
        type.setTargetLevel(S88Level.EQUIPMENTMODULE);
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        root.addChild(unit);
        S88Element source = module("CALENTAMIENTO_TANQUE_1");
        unit.addChild(source);
        S88PlantModelFixture model = new S88PlantModelFixture(root);
        model.register(type);
        source.setClass(type);

        List<S88Element> copies = DuplicateElementUseCase.execute(model.model(), source, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertTrue(copy.getStructuredProperty("Reports").containsKey("STATE_CALENTAMIENTO_TANQUE_2"),
                "the copy publishes the variables under its own name, qualified with its own id");
        assertEquals("Reports/STATE", copy.getBaseName("Reports", "STATE_CALENTAMIENTO_TANQUE_2"),
                "and answers the same base name, so a recipe written for one serves the other");
        assertTrue(ClassConformance.of(copy).isConforming());
    }

    @Test
    void aModuleThatPredatedTheVariablesIsGivenThemOnDuplication() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element unit = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        root.addChild(unit);
        S88Element source = module("CALENTAMIENTO_TANQUE_1");
        source.setProperty("Parameters", Map.of("NIVEL_CALENTAMIENTO_TANQUE_1", Map.of("Type", "REAL")));
        unit.addChild(source);
        S88PlantModelFixture model = new S88PlantModelFixture(root);

        List<S88Element> copies = DuplicateElementUseCase.execute(model.model(), source, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertTrue(copy.getStructuredProperty("Reports").containsKey("STATE_CALENTAMIENTO_TANQUE_2"),
                "the copy is not one variable short of what its source has");
        assertTrue(copy.getStructuredProperty("Parameters").containsKey("COMMAND_CALENTAMIENTO_TANQUE_2"));
        assertTrue(copy.getStructuredProperty("Parameters").containsKey("NIVEL_CALENTAMIENTO_TANQUE_2"),
                "and keeps what the source published before them");
    }

    @Test
    void aUnitDuplicatedIsLeftWithoutThemWhileItsModulesGetThem() {
        S88Element root = new S88Element().setId("PLANTA").setLevel(S88Level.AREA);
        S88Element source = new S88Element().setId("TANQUE_1").setLevel(S88Level.UNIT);
        S88Element em = module("CALENTAMIENTO_TANQUE_1");
        source.addChild(em);
        root.addChild(source);
        S88PlantModelFixture model = new S88PlantModelFixture(root);

        List<S88Element> copies = DuplicateElementUseCase.execute(model.model(), source, 1, null, Map.of());

        S88Element copy = copies.get(0);
        assertTrue(copy.getProperties().get("Reports") == null,
                "a unit still reports nothing of its own");
        assertTrue(copy.getChildren().get(0).getStructuredProperty("Reports")
                        .containsKey("STATE_CALENTAMIENTO_TANQUE_2"),
                "the module inside the copy is the one that runs the phase, so it is the one reporting");
    }

    @Test
    void aUserCannotDefineAVariableThePlatformAlreadyPublishes() {
        assertThrows(IllegalArgumentException.class,
                () -> NameValidator.validateEntry("Reports", "STATE"));
        assertThrows(IllegalArgumentException.class,
                () -> NameValidator.validateEntry("Parameters", "COMMAND"));
        assertThrows(IllegalArgumentException.class,
                () -> NameValidator.validateProperty("FAILURE"));
    }

    @Test
    void theMessageSaysWhyTheNameIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> NameValidator.validateEntry("Reports", "STATE"));

        assertTrue(refused.getMessage().contains("every equipment module"), refused.getMessage());
        assertTrue(refused.getMessage().contains("Pick another name"), refused.getMessage());
    }

    @Test
    void aNameThatOnlyStartsLikeAPlatformOneIsStillAccepted() {
        NameValidator.validateEntry("Parameters", "STATE_MACHINE_SELECT");

        assertEquals(PlatformVariable.STATE, PlatformVariable.find("STATE").orElseThrow(),
                "only the base names are taken, so a bit selector of that name is left alone");
    }
}
