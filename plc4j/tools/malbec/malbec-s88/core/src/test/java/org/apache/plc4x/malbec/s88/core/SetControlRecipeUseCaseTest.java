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
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Setting a master recipe for one batch.
 * <p>
 * The one that matters here is the first test: a master written by class has to produce a control
 * recipe still written by class. Turning it into a per-instance recipe would mean inventing an answer
 * about which unit this batch gets, and putting it in the file would mean the file claims something
 * about the plant that is not true until the batch is running.
 */
class SetControlRecipeUseCaseTest {

    @Test
    void aMasterWrittenByClassGivesAControlRecipeStillWrittenByClass() {
        S88MasterRecipe master = master(S88RecipeKind.CLASS);
        assertTrue(master.getAllElements().get(0).getActualEquipmentIds().isEmpty());

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "LOTE_2026_014");

        assertSame(S88RecipeKind.CLASS, control.getKind(),
                "a plant may have a thousand identical tanks and the recipe that names the class is"
                        + " the recipe that lets any of them do the work; choosing one here would be"
                        + " inventing an answer nobody gave");
        assertTrue(control.addressesByClass());
        assertEquals("HEATER", control.getAllElements().get(0).getEquipmentClassId(),
                "and the class comes through, so the recipe still says which class of equipment it"
                        + " needs");
        assertTrue(control.getAllElements().get(0).getActualEquipmentIds().isEmpty(),
                "with nothing turned into a particular module behind the reader's back");
    }

    @Test
    void aMasterWrittenForParticularEquipmentGivesAControlRecipeForParticularEquipment() {
        S88MasterRecipe master = master(S88RecipeKind.INSTANCE);
        master.getAllElements().get(0).addActualEquipmentId("CALENTAMIENTO_TANQUE_1");

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "LOTE_2026_014");

        assertSame(S88RecipeKind.INSTANCE, control.getKind());
        assertEquals(java.util.List.of("CALENTAMIENTO_TANQUE_1"),
                control.getAllElements().get(0).getActualEquipmentIds(),
                "and the module it names comes through untouched, because this one the recipe does"
                        + " know which unit it means");
    }

    @Test
    void aControlRecipeIsSetForTheBatchItIsGiven() {
        S88ControlRecipe control = new SetControlRecipeUseCase().from(master(S88RecipeKind.CLASS),
                "LOTE_2026_014");

        assertEquals("LOTE_2026_014", control.getBatchId());
        assertEquals("CTL_LOTE_2026_014", control.getId());
        assertEquals("REC_MAESTRA", control.getSourceRecipeId());
    }

    @Test
    void theControlRecipeIsACopyAndNotTheMasterItself() {
        S88MasterRecipe master = master(S88RecipeKind.CLASS);

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "L1");

        assertNotSame(master, control);
        assertNotSame(master.getAllElements().get(0), control.getAllElements().get(0),
                "a control recipe is kept as the record of what one run did, so sharing the steps"
                        + " with the master would let an edit to one of them rewrite the other");
        assertNotSame(master.getAllElements().get(0).getParameters().get(0),
                control.getAllElements().get(0).getParameters().get(0));
    }

    @Test
    void theStepsOfTheMasterComeAcrossWhole() {
        S88ControlRecipe control = new SetControlRecipeUseCase()
                .from(master(S88RecipeKind.CLASS), "L1");

        S88RecipeElement element = control.getAllElements().get(0);
        assertEquals(S88RecipeElementKind.OPERATION, element.getKind());
        assertEquals(1, element.getParameters().size());
        S88RecipeParameter parameter = element.getParameters().get(0);
        assertEquals("Reports/STATE", parameter.getId());
        assertEquals("IDLE", parameter.getFirstValue().getFirstValueString());
        assertEquals(DataType.ENUMERATION, parameter.getFirstValue().getDataType());
    }

    @Test
    void theChartOfTheMasterIsCarriedOver() {
        S88MasterRecipe master = master(S88RecipeKind.CLASS);
        master.setProcedureLogic(new S88ProcedureLogic());
        master.getProcedureLogic().addStep(
                new org.apache.plc4x.malbec.s88.api.S88ProcedureStep("BOX", "HEAT"));

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "L1");

        assertTrue(control.hasProcedureLogic());
        assertEquals(1, control.getProcedureLogic().getSteps().size());
    }

    @Test
    void theControlRecipeKeepsItsOwnKindAndMasterRatherThanTheCopyOfThem() {
        S88MasterRecipe master = master(S88RecipeKind.CLASS);

        S88ControlRecipe control = new SetControlRecipeUseCase().from(master, "L1");

        long kindEntries = control.getOtherInformation().stream()
                .filter(info -> org.apache.plc4x.malbec.s88.api.S88OtherInformation.RECIPE_KIND
                        .equalsIgnoreCase(info.getId()))
                .count();
        long sourceEntries = control.getOtherInformation().stream()
                .filter(info -> S88ControlRecipe.SOURCE_RECIPE_ID.equalsIgnoreCase(info.getId()))
                .count();
        assertEquals(1, kindEntries,
                "the kind is written down once, as the control recipe's own answer, rather than"
                        + " carried over from the copy and overwriting it");
        assertEquals(1, sourceEntries);
        assertSame(S88RecipeKind.CLASS, org.apache.plc4x.malbec.s88.api.S88MasterRecipe
                .readStoredKind(control));
    }

    @Test
    void aRecipeWithNoBatchToSetItForIsRefused() {
        SetControlRecipeUseCase useCase = new SetControlRecipeUseCase();
        S88MasterRecipe master = master(S88RecipeKind.CLASS);

        assertThrows(IllegalArgumentException.class, () -> useCase.from(master, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.from(master, "  "),
                "a control recipe with no batch is indistinguishable from any other run, and"
                        + " refusing it here is the one point where the batch is genuinely needed");
    }

    @Test
    void aRecipeWithNoMasterToSetIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new SetControlRecipeUseCase().from(null, "L1"));
    }

    private static S88MasterRecipe master(S88RecipeKind kind) {
        S88MasterRecipe master = new S88MasterRecipe("REC_MAESTRA", kind);
        S88RecipeElement element = new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION);
        if (kind == S88RecipeKind.CLASS) {
            element.setEquipmentClassId("HEATER");
        }
        element.addParameter(
                S88RecipeParameter.of("Reports/STATE", "IDLE", DataType.ENUMERATION, null));
        master.addRecipeElement(element);
        return master;
    }
}