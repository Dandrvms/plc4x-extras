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

import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe the moment it is created.
 * <p>
 * What is checked here is that a new recipe already is a process rather than a blank page, and that
 * the two steps it is built on cannot be taken away. Both matter before any editor exists: a recipe
 * that starts life with nothing in it has to be completed by whoever opens it, and one whose start
 * can be deleted can be left with no way to run at all.
 */
class CreateMasterRecipeUseCaseTest {

    @Test
    void aNewRecipeHasItsStartAndItsStop() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        assertEquals(2, recipe.getRecipeElements().size());
        assertEquals(S88RecipeElementKind.BEGIN, recipe.getRecipeElements().get(0).getKind());
        assertEquals(S88RecipeElementKind.END, recipe.getRecipeElements().get(1).getKind());
        assertEquals("BEGIN", recipe.getRecipeElements().get(0).getId());
        assertEquals("END", recipe.getRecipeElements().get(1).getId());
    }

    @Test
    void theStartAndTheStopNameNoEquipment() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        for (var element : recipe.getRecipeElements()) {
            assertNull(element.getEquipmentClassId(),
                    "a recipe that began by naming equipment would be claiming something about a"
                            + " plant that has not been chosen yet");
            assertTrue(element.getActualEquipmentIds().isEmpty());
        }
    }

    @Test
    void aNewRecipeCarriesAChartThatRunsFromTheStartToTheStop() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        S88ProcedureLogic chart = recipe.getProcedureLogic();
        assertEquals(2, chart.getSteps().size());
        assertEquals(1, chart.getTransitions().size(), "the model is bipartite, so the flow crosses a bar");
        assertEquals(2, chart.getLinks().size());

        assertEquals("BEGIN", chart.findStep("BEGIN").orElseThrow().getRecipeElementId());
        assertEquals("END", chart.findStep("END").orElseThrow().getRecipeElementId());
        assertTrue(EditProcedureLogicUseCase.isChartBipartite(chart),
                "every line has to run from a box to a bar or from a bar to a box");
    }

    @Test
    void theBarOfANewRecipeWaitsOnNothing() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        assertFalse(recipe.getProcedureLogic()
                        .findTransition(CreateMasterRecipeUseCase.FIRST_TRANSITION_ID)
                        .orElseThrow().isGuarded(),
                "an empty recipe has nothing to wait for, so the bar carries no condition");
    }

    @Test
    void theLinesOfANewRecipeSayWhichSideOfTheBarTheyAreOn() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);
        S88ProcedureLogic chart = recipe.getProcedureLogic();

        S88ProcedureLink first = chart.findLink(CreateMasterRecipeUseCase.FIRST_LINK_ID).orElseThrow();
        assertSame(S88IdRefType.STEP, first.getFrom().get(0).getType());
        assertSame(S88IdRefType.TRANSITION, first.getTo().get(0).getType(),
                "which side of the bar a line points at is said on each end, so a chart that runs"
                        + " box to bar to box comes back knowing which is which");

        S88ProcedureLink last = chart.findLink(CreateMasterRecipeUseCase.LAST_LINK_ID).orElseThrow();
        assertSame(S88IdRefType.TRANSITION, last.getFrom().get(0).getType());
        assertSame(S88IdRefType.STEP, last.getTo().get(0).getType());
    }

    @Test
    void aNewRecipeHasNothingWrongWithIt() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        RecipeConformance report = RecipeConformance.of(recipe);

        assertEquals(List.of(), report.excess(),
                "a recipe that has just been created and not been touched is not a mistake");
        assertEquals(List.of(), report.deficit(),
                "and it is not missing its start or its stop, which is what it was created with");
    }

    @Test
    void theStartOfARecipeCannotBeDeleted() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeleteRecipeElementUseCase.execute(recipe, recipe.findElement("BEGIN").orElseThrow()));

        assertTrue(failure.getMessage().contains("start"),
                "and it says which one it was, because END is refused the same way and the operator"
                        + " has to be able to tell them apart");
    }

    @Test
    void theStopOfARecipeCannotBeDeleted() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeleteRecipeElementUseCase.execute(recipe, recipe.findElement("END").orElseThrow()));

        assertTrue(failure.getMessage().contains("stop"));
    }

    @Test
    void theStartAndTheStopAreRefusedEvenWhenNothingPointsAtThem() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);
        recipe.setProcedureLogic(null);

        assertThrows(IllegalStateException.class,
                () -> DeleteRecipeElementUseCase.execute(recipe, recipe.findElement("BEGIN").orElseThrow()));
    }

    @Test
    void aStepNamedOnTheChartOfTheRecipeItselfIsNotDeletedOutFromUnderIt() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipmentClass(recipe, null, "HEAT",
                S88RecipeElementKind.OPERATION, "HEATER");

        EditProcedureLogicUseCase.addStep(recipe, null, "BOX", heat.getId());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DeleteRecipeElementUseCase.execute(recipe, heat));

        assertTrue(failure.getMessage().contains("BOX"),
                "the chart of the recipe is a chart like any other, and a box on it works on the"
                        + " step it points at");
    }

    @Test
    void aNameTheRecipeCannotCarryIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> CreateMasterRecipeUseCase.execute(null, S88RecipeKind.CLASS));
        assertThrows(IllegalArgumentException.class,
                () -> CreateMasterRecipeUseCase.execute("  ", S88RecipeKind.CLASS));
    }

    @Test
    void aRecipeWithNoKindNamesEquipmentByClass() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", null);

        assertSame(S88RecipeKind.CLASS, recipe.getKind(),
                "naming equipment by class is what a recipe that has not been bound to a plant can"
                        + " be relied on to mean");
    }

    @Test
    void aRecipeWrittenForParticularEquipmentSaysSo() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_LOTE", S88RecipeKind.INSTANCE);

        assertSame(S88RecipeKind.INSTANCE, recipe.getKind());
    }

    @Test
    void anotherStepCanBeAddedToTheChartOfANewRecipe() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("CAFE_NEGRO", S88RecipeKind.CLASS);
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipmentClass(recipe, null, "HEAT",
                S88RecipeElementKind.OPERATION, "HEATER");
        EditProcedureLogicUseCase.addStep(recipe, null, "BOX", heat.getId());

        assertEquals(3, recipe.getProcedureLogic().getSteps().size(),
                "the chart of the recipe takes new boxes, so a recipe can be built outwards from"
                        + " the start it was created with");
    }
}
