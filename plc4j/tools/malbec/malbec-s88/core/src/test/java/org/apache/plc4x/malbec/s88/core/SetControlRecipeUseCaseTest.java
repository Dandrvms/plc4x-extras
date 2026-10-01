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

import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.api.S88ControlRecipe;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Setting a master recipe for one batch.
 * <p>
 * The two things worth pinning here are that the result is a separate recipe rather than the master
 * wearing a batch number, and that a step still naming a class of equipment is reported as waiting
 * rather than quietly pointed at a tank that happens to exist.
 */
class SetControlRecipeUseCaseTest {

    @Test
    void aControlRecipeIsSetForTheBatchItIsGiven() {
        S88ControlRecipe control = new SetControlRecipeUseCase().from(master(), "LOTE_2026_014");

        assertEquals("LOTE_2026_014", control.getBatchId());
        assertEquals("CTL_LOTE_2026_014", control.getId());
        assertEquals("REC_MAESTRA", control.getSourceRecipeId());
    }

    @Test
    void theControlRecipeIsForParticularEquipmentWhateverTheMasterSaid() {
        S88MasterRecipe byClass = master();
        assertSame(S88RecipeKind.CLASS, byClass.getKind());

        S88ControlRecipe control = new SetControlRecipeUseCase().from(byClass, "L1");

        assertSame(S88RecipeKind.INSTANCE, control.getKind(),
                "a recipe on its way to the plant always is, and this is the decision rather than a"
                        + " rule on the data class, so a control recipe that arrives from elsewhere"
                        + " saying otherwise is not silently corrected into something that looks"
                        + " fine");
    }

    @Test
    void aStepStillNamingAClassIsReportedRatherThanPointedAtAModule() {
        SetControlRecipeUseCase useCase = new SetControlRecipeUseCase();

        S88ControlRecipe control = useCase.from(master(), "L1");

        assertFalse(useCase.isFullyBound());
        assertEquals(1, useCase.getUnresolvedSteps().size());
        assertTrue(useCase.getUnresolvedSteps().getFirst().contains("HEATER"),
                "the step says what it still needs, so whoever binds it can be asked which module is"
                        + " free, instead of the recipe arriving at the plant pointing at a guess");
        assertEquals("HEATER", control.getAllElements().getFirst().getEquipmentClassId(),
                "and the class is kept rather than replaced, so the question is still answerable");
    }

    @Test
    void aMasterAlreadyNamingAModulesNeedsNothingDecidingForIt() {
        S88MasterRecipe master = new S88MasterRecipe("REC_MAESTRA", S88RecipeKind.INSTANCE);
        S88RecipeElement element = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        element.addActualEquipmentId("CALENTAMIENTO_TANQUE_1");
        master.addRecipeElement(element);
        SetControlRecipeUseCase useCase = new SetControlRecipeUseCase();

        useCase.from(master, "L1");

        assertTrue(useCase.isFullyBound());
        assertEquals(List.of("CALENTAMIENTO_TANQUE_1"),
                useCase.getControlRecipe().getAllElements().getFirst().getActualEquipmentIds());
    }

    @Test
    void theControlRecipeIsACopyAndNotTheMasterItself() {
        S88MasterRecipe master = master();

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "L1");

        assertNotSame(master, control);
        assertNotSame(master.getAllElements().getFirst(), control.getAllElements().getFirst(),
                "a control recipe is kept as the record of what one run did, so sharing the steps"
                        + " with the master would let an edit to one of them rewrite the other");
        assertNotSame(master.getAllElements().getFirst().getParameters().getFirst(),
                control.getAllElements().getFirst().getParameters().getFirst());
    }

    @Test
    void aRecipeWithNoBatchToSetItForIsRefused() {
        SetControlRecipeUseCase useCase = new SetControlRecipeUseCase();

        assertThrows(IllegalArgumentException.class, () -> useCase.from(master(), null));
        assertThrows(IllegalArgumentException.class, () -> useCase.from(master(), "  "),
                "a control recipe with no batch is indistinguishable from any other run, and"
                        + " refusing it here is the one point where the batch is genuinely required");
    }

    @Test
    void aRecipeWithNoMasterToSetIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SetControlRecipeUseCase().from(null, "L1"));
    }

    @Test
    void theStepsOfTheMasterComeAcrossWhole() {
        S88ControlRecipe control = new SetControlRecipeUseCase().from(master(), "L1");

        S88RecipeElement element = control.getAllElements().getFirst();
        assertEquals(S88RecipeElementKind.OPERATION, element.getKind());
        assertEquals(1, element.getParameters().size());
        S88RecipeParameter parameter = element.getParameters().getFirst();
        assertEquals("Reports/STATE", parameter.getId());
        assertEquals("IDLE", parameter.getFirstValue().getFirstValueString());
        assertEquals(DataType.ENUMERATION, parameter.getFirstValue().getDataType());
    }

    @Test
    void theChartOfTheMasterIsCarriedOver() {
        S88MasterRecipe master = master();
        master.setProcedureLogic(new S88ProcedureLogic());
        master.getProcedureLogic().addStep(new S88ProcedureStep("S1", "HEAT"));

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "L1");

        assertTrue(control.hasProcedureLogic());
        assertEquals(1, control.getProcedureLogic().getSteps().size());
    }

    private static S88MasterRecipe master() {
        S88MasterRecipe master = new S88MasterRecipe("REC_MAESTRA", S88RecipeKind.CLASS);
        S88RecipeElement element = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        element.setEquipmentClassId("HEATER");
        element.addParameter(S88RecipeParameter.of("Reports/STATE", "IDLE", DataType.ENUMERATION, null));
        master.addRecipeElement(element);
        return master;
    }
}

