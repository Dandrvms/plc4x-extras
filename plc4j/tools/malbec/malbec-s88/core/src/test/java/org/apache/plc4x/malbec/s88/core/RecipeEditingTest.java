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
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeListener;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Working on a recipe: adding, renaming, taking away, drawing the chart, and giving a step its
 * values.
 * <p>
 * The names throughout are the names the plant gives its equipment, because that is the rule the
 * whole feature rests on: a step that works on a module called {@code HEAT} is called
 * {@code HEAT}, and nothing along the way renames it. Several of these tests fail if that stops
 * being true, which is the point of them.
 */
class RecipeEditingTest {

    // ========== A step is the element, by the name the plant gave it ==========

    @Test
    void aStepWorkingOnAModuleIsCalledWhatThePlantCallsIt() {
        S88Recipe recipe = master();

        S88RecipeElement step = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        assertEquals("HEAT", step.getId());
        assertEquals(List.of("HEAT"), step.getActualEquipmentIds(),
                "the name of the step and what it works on are the same word, so a reader of the"
                        + " recipe and a reader of the plant are looking at the same thing");
    }

    @Test
    void aStepWorkingOnAClassNamesTheClassUntilItIsSetForABatch() {
        S88Recipe recipe = master();

        S88RecipeElement step = CreateRecipeElementUseCase.forEquipmentClass(
                recipe, null, "HEATING", S88RecipeElementKind.OPERATION, "HEATER");

        assertEquals("HEATER", step.getEquipmentClassId());
        assertTrue(step.getActualEquipmentIds().isEmpty());
    }

    @Test
    void aStepGoesIntoTheRecipeOnlyOnceHoweverDeepItIs() {
        S88Recipe recipe = new S88MasterRecipe("REC", S88RecipeKind.CLASS);
        S88RecipeElement procedure = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "PROC", S88RecipeElementKind.PROCEDURE);
        S88RecipeElement nested = CreateRecipeElementUseCase.forEquipment(
                recipe, procedure, "HEAT", S88RecipeElementKind.OPERATION);

        assertEquals(List.of("PROC"), recipe.getRecipeElements().stream()
                .map(S88RecipeElement::getId).toList(),
                "a step added inside another is not also a step of the recipe, or the tree would"
                        + " show it twice");
        assertSame(procedure, nested.getParent());
        assertFalse(nested.isTopLevel());
        assertTrue(recipe.findElement("HEAT").isPresent(),
                "and it is still reachable by name from the top, which is what a chart needs");
        assertEquals(2, recipe.getAllElements().size());
    }

    @Test
    void aStepCannotBeAddedUnderSomethingThatIsNotInTheRecipe() {
        S88Recipe recipe = master();
        S88RecipeElement stranger = new S88RecipeElement("ELSEWHERE", S88RecipeElementKind.OPERATION);

        assertThrows(IllegalArgumentException.class, () -> CreateRecipeElementUseCase.forEquipment(
                recipe, stranger, "HEAT", S88RecipeElementKind.OPERATION));
    }

    @Test
    void twoStepsCannotBeCalledTheSameThing() {
        S88Recipe recipe = master();
        CreateRecipeElementUseCase.forEquipment(recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        assertThrows(IllegalArgumentException.class, () -> CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION));
    }

    // ========== Renaming follows every pointer to it ==========

    @Test
    void renamingAStepRewritesEveryBoxThatWorkedOnIt() {
        S88Recipe recipe = completeChart();
        S88RecipeElement heat = recipe.findElement("HEAT").orElseThrow();

        RenameRecipeElementUseCase.execute(recipe, heat, "HEAT_2");

        assertEquals("HEAT_2", heat.getId());
        assertEquals("HEAT_2", chartOf(recipe).findStep("BOX_HEAT").orElseThrow()
                .getRecipeElementId(), "the box on the chart now works on the step's new name");
    }

    @Test
    void renamingAStepLeavesTheNamesOfTheBoxesAndTheLinesAlone() {
        S88Recipe recipe = completeChart();
        var line = chartOf(recipe).findLink("L1").orElseThrow();
        String fromBefore = line.getFrom().get(0).getValue();
        String toBefore = line.getTo().get(0).getValue();

        RenameRecipeElementUseCase.execute(recipe, recipe.findElement("HEAT").orElseThrow(), "HEAT_2");

        assertEquals("BOX_HEAT", chartOf(recipe).findStep("BOX_HEAT").orElseThrow().getId(),
                "a box is the chart's own naming and not the step's, so a step that changes what it"
                        + " is called leaves the box that works on it as it was");
        assertEquals(fromBefore, line.getFrom().get(0).getValue(),
                "and a line names the box it runs between, not the step that box works on");
        assertEquals(toBefore, line.getTo().get(0).getValue());
    }

    @Test
    void aRenameThatIsRefusedLeavesTheRecipeExactlyAsItWas() {
        S88Recipe recipe = completeChart();
        CreateRecipeElementUseCase.forEquipment(recipe, null, "MIX", S88RecipeElementKind.OPERATION);

        assertThrows(IllegalArgumentException.class, () -> RenameRecipeElementUseCase.execute(
                recipe, recipe.findElement("HEAT").orElseThrow(), "MIX"),
                "a name the recipe already has is refused by the name check, before anything moves");

        assertEquals("HEAT", recipe.findElement("HEAT").orElseThrow().getId());
        assertEquals("HEAT", chartOf(recipe).findStep("BOX_HEAT").orElseThrow().getRecipeElementId(),
                "with the chart still pointing at the name it had, rather than half renamed and"
                        + " left to be found broken");
    }

    @Test
    void renamingAStepToTheNameItAlreadyHasIsNotAMistake() {
        S88Recipe recipe = completeChart();
        S88RecipeElement heat = recipe.findElement("HEAT").orElseThrow();

        RenameRecipeElementUseCase.execute(recipe, heat, "HEAT");

        assertEquals("HEAT", heat.getId());
        assertEquals("HEAT", chartOf(recipe).findStep("BOX_HEAT").orElseThrow().getRecipeElementId(),
                "the one name the recipe already has is the name it is being called, so asking for"
                        + " it again is not a clash with itself");
    }

    @Test
    void aRecipeThatAlreadyHasTwoStepsOfOneNameIsNotRenamedIntoIt() {
        S88Recipe recipe = master();
        S88RecipeElement first = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);
        recipe.addRecipeElement(new S88RecipeElement("HEAT", S88RecipeElementKind.OPERATION));

        assertThrows(IllegalStateException.class,
                () -> RenameRecipeElementUseCase.execute(recipe, first, "WARMER"));
    }

    // ========== Taking a step away ==========

    @Test
    void aStepNothingPointsAtCanBeTakenAway() {
        S88Recipe recipe = master();
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        DeleteRecipeElementUseCase.execute(recipe, heat);

        assertTrue(recipe.findElement("HEAT").isEmpty(), "and its name is gone from the index too,"
                + " so a chart still holding it finds nothing rather than a step that is not there");
    }

    @Test
    void aStepABoxWorksOnIsNotTakenAwayUntilTheBoxIsToo() {
        S88Recipe recipe = completeChart();
        S88RecipeElement heat = recipe.findElement("HEAT").orElseThrow();

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DeleteRecipeElementUseCase.execute(recipe, heat));
        assertTrue(refused.getMessage().contains("BOX_HEAT"),
                "the refusal names the box holding it, so the reader knows which one to take off"
                        + " rather than being told the step is used");

        EditProcedureLogicUseCase.removeLink(recipe, recipe.findElement("PROC").orElseThrow(), "L1");
        EditProcedureLogicUseCase.removeStep(recipe, recipe.findElement("PROC").orElseThrow(), "BOX_HEAT");
        DeleteRecipeElementUseCase.execute(recipe, heat);
        assertTrue(recipe.findElement("HEAT").isEmpty());
    }

    @Test
    void takingAStepAwayTakesWhatWasUnderItToo() {
        S88Recipe recipe = master();
        S88RecipeElement procedure = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "PROC", S88RecipeElementKind.PROCEDURE);
        CreateRecipeElementUseCase.forEquipment(recipe, procedure, "HEAT", S88RecipeElementKind.OPERATION);

        DeleteRecipeElementUseCase.execute(recipe, procedure);

        assertTrue(recipe.findElement("PROC").isEmpty());
        assertTrue(recipe.findElement("HEAT").isEmpty(),
                "a phase with no operations under it is not a phase");
    }

    // ========== Giving a step its values ==========

    @Test
    void aValueIsFiledAgainstTheVariableItIsFor() {
        S88Recipe recipe = master();
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        UpdateRecipeParameterUseCase.execute(recipe, heat, "Reports/STATE", "IDLE",
                DataType.ENUMERATION, null);

        S88RecipeParameter parameter = heat.findParameter("Reports/STATE").orElseThrow();
        assertEquals("IDLE", parameter.getFirstValue().getFirstValueString());
        assertEquals(DataType.ENUMERATION, parameter.getFirstValue().getDataType());
    }

    @Test
    void settingAValueTwiceLeavesOneValueAndNotTwo() {
        S88Recipe recipe = master();
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        UpdateRecipeParameterUseCase.execute(recipe, heat, "Reports/STATE", "IDLE",
                DataType.ENUMERATION, null);
        UpdateRecipeParameterUseCase.execute(recipe, heat, "Reports/STATE", "RUNNING",
                DataType.ENUMERATION, null);

        S88RecipeParameter parameter = heat.findParameter("Reports/STATE").orElseThrow();
        assertEquals(1, parameter.getValues().size(),
                "a recipe that says a step is set to two different things is a recipe that says two"
                        + " things, and which one the plant would honour is nobody's guess to make");
        assertEquals("RUNNING", parameter.getFirstValue().getFirstValueString());
        assertEquals(1, heat.getParameters().size());
    }

    @Test
    void aValueCanBeRemovedAndIsNotThereAfterwards() {
        S88Recipe recipe = master();
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);
        UpdateRecipeParameterUseCase.execute(recipe, heat, "Parameters/TARGET", "75", DataType.REAL, "degC");

        assertTrue(UpdateRecipeParameterUseCase.remove(recipe, heat, "Parameters/TARGET"));
        assertTrue(heat.findParameter("Parameters/TARGET").isEmpty());
        assertFalse(UpdateRecipeParameterUseCase.remove(recipe, heat, "Parameters/TARGET"),
                "and taking away what is not there says so, rather than claiming it worked");
    }

    // ========== Drawing the chart ==========

    @Test
    void aChartIsDrawnByNamingTheStepsItWorksOn() {
        S88Recipe recipe = master();
        S88RecipeElement procedure = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "PROC", S88RecipeElementKind.PROCEDURE);
        CreateRecipeElementUseCase.forEquipment(recipe, procedure, "HEAT", S88RecipeElementKind.OPERATION);
        S88RecipeElement end = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "END", S88RecipeElementKind.END);

        EditProcedureLogicUseCase.addStep(recipe, procedure, "BOX_HEAT", "HEAT");
        EditProcedureLogicUseCase.addStep(recipe, procedure, "BOX_END", "END");
        EditProcedureLogicUseCase.addLink(recipe, procedure, "L1", List.of("BOX_HEAT"),
                List.of("BOX_END"), null);

        S88ProcedureLogic chart = procedure.getProcedureLogic();
        assertEquals(2, chart.getSteps().size());
        assertEquals(1, chart.getLinks().size());
        assertEquals("HEAT", chart.findStep("BOX_HEAT").orElseThrow().getRecipeElementId(),
                "and the chart says which step each box works on, by the name the plant gave it");
        assertTrue(EditProcedureLogicUseCase.hasChart(procedure));
        assertFalse(EditProcedureLogicUseCase.hasChart(end));
    }

    @Test
    void aBoxCannotWorkOnAStepTheRecipeDoesNotHave() {
        S88Recipe recipe = master();
        S88RecipeElement procedure = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "PROC", S88RecipeElementKind.PROCEDURE);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.addStep(recipe, procedure, "BOX", "NOT_THERE"));
        assertTrue(refused.getMessage().contains("NOT_THERE"),
                "a box naming a step nobody has is a name somebody mistyped, not a decision, and"
                        + " saying so is more use than leaving it to be found later");
    }

    @Test
    void aLineCannotRunIntoSomethingTheChartDoesNotHave() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> EditProcedureLogicUseCase.addLink(
                recipe, proc, "LBAD", List.of("BOX_HEAT"), List.of("NO_SUCH_BOX"), null));
    }

    @Test
    void aLineMayNameSomethingOutsideTheChart() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        EditProcedureLogicUseCase.addLink(recipe, proc, "LEXT", List.of("!STEP_ELSEWHERE"),
                List.of("BOX_END"), null);

        var external = proc.getProcedureLogic().findLink("LEXT").orElseThrow().getFrom().get(0);
        assertFalse(external.isInternal(),
                "which is how a line says it refers to a step of another part of the process rather"
                        + " than one of its own");
    }

    @Test
    void aBoxWithALineStillRunningIntoItIsNotTakenOffTheChart() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        assertThrows(IllegalStateException.class,
                () -> EditProcedureLogicUseCase.removeStep(recipe, proc, "BOX_HEAT"));
        assertTrue(chartOf(recipe).findStep("BOX_HEAT").isPresent());
    }

    @Test
    void aSplitIsDrawnByGivingALineMoreThanOneEnd() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();
        EditProcedureLogicUseCase.addStep(recipe, proc, "BOX_MIX", "HEAT");

        EditProcedureLogicUseCase.addLink(recipe, proc, "LSPLIT", List.of("BOX_HEAT"),
                List.of("BOX_MIX", "BOX_END"), S88LinkType.PARALLEL_DIVERGENT);

        var split = proc.getProcedureLogic().findLink("LSPLIT").orElseThrow();
        assertEquals(2, split.getTo().size());
        assertTrue(split.isDivergent());
    }

    @Test
    void aBarWaitsOnAReportOfTheEquipment() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        EditProcedureLogicUseCase.addTransition(recipe, proc, "T_TEMP_OK", "Reports/STATE");

        var bar = proc.getProcedureLogic().findTransition("T_TEMP_OK").orElseThrow();
        assertTrue(bar.isGuarded());
        assertEquals("Reports/STATE", bar.conditionAddress().toText(),
                "a bar is a report to read off the equipment, written the way a recipe written by"
                        + " class names one");
    }

    @Test
    void aBarWithNothingToWaitOnIsAllowed() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        EditProcedureLogicUseCase.addTransition(recipe, proc, "T_TEMP_OK", null);

        var bar = proc.getProcedureLogic().findTransition("T_TEMP_OK").orElseThrow();
        assertFalse(bar.isGuarded());
        assertSame(null, bar.conditionAddress(),
                "a bar crossed because the step before it finished is a real thing to draw");
    }

    @Test
    void aBarThatNamesSomethingWhichIsNotAnAddressIsRefused() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> EditProcedureLogicUseCase.addTransition(recipe, proc, "T_BAD", "Reports/"));
    }

    // ========== Being told what changed ==========

    @Test
    void whoeverIsWatchingIsToldAboutEveryChange() {
        S88Recipe recipe = completeChart();
        List<S88RecipeChangeEvent> seen = new ArrayList<>();
        recipe.addChangeListener(seen::add);
        S88RecipeElement heat = recipe.findElement("HEAT").orElseThrow();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        UpdateRecipeParameterUseCase.execute(recipe, heat, "Reports/STATE", "RUNNING",
                DataType.ENUMERATION, null);
        EditProcedureLogicUseCase.addStep(recipe, proc, "BOX_MIX", "HEAT");
        EditProcedureLogicUseCase.removeStep(recipe, proc, "BOX_MIX");
        EditProcedureLogicUseCase.removeLink(recipe, proc, "L1");
        EditProcedureLogicUseCase.removeStep(recipe, proc, "BOX_HEAT");
        EditProcedureLogicUseCase.removeStep(recipe, proc, "BOX_END");
        DeleteRecipeElementUseCase.execute(recipe, heat);

        assertTrue(seen.stream().anyMatch(e -> e.type() == S88RecipeChangeEvent.Type.UPDATED));
        assertTrue(seen.stream().anyMatch(e -> e.type() == S88RecipeChangeEvent.Type.CHART_UPDATED),
                "a change to the chart is told as its own kind, because it belongs to no one step"
                        + " and a view that repaints only a step would leave the chart behind");
        assertTrue(seen.stream().anyMatch(e -> e.type() == S88RecipeChangeEvent.Type.REMOVED));
    }

    @Test
    void aViewThatFailsDoesNotStopTheOthersFromBeingTold() {
        S88Recipe recipe = master();
        List<S88RecipeChangeEvent> seen = new ArrayList<>();
        recipe.addChangeListener(event -> {
            throw new IllegalStateException("this view is broken");
        });
        recipe.addChangeListener(seen::add);

        CreateRecipeElementUseCase.forEquipment(recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        assertEquals(1, seen.size(),
                "the change has already happened, so a tree left out of step with it is worse than"
                        + " one broken view");
    }

    @Test
    void aListenerThatIsNoLongerInterestedIsNotCalled() {
        S88Recipe recipe = master();
        List<S88RecipeChangeEvent> seen = new ArrayList<>();
        S88RecipeChangeListener listener = seen::add;
        recipe.addChangeListener(listener);
        recipe.removeChangeListener(listener);

        CreateRecipeElementUseCase.forEquipment(recipe, null, "HEAT", S88RecipeElementKind.OPERATION);

        assertTrue(seen.isEmpty());
    }

    // ========== Copying ==========

    @Test
    void aCopySharesNothingWithWhatItWasCopiedFrom() {
        S88Recipe recipe = completeChart();
        S88RecipeElement proc = recipe.findElement("PROC").orElseThrow();

        S88RecipeElement copy = RecipeDeepCopy.copyElement(proc);

        assertNotSame(proc, copy);
        assertNotSame(chartOf(recipe).getLinks().get(0).getFrom().get(0),
                copy.getProcedureLogic().getLinks().get(0).getFrom().get(0),
                "a line whose ends were still the original's would take a rename of one chart as a"
                        + " rename of the other");
        assertNotSame(chartOf(recipe), copy.getProcedureLogic());
    }

    @Test
    void aCopyOfAParameterDoesNotShareTheValueEither() {
        S88Recipe recipe = completeChart();
        S88RecipeElement heat = recipe.findElement("HEAT").orElseThrow();

        S88RecipeElement copy = RecipeDeepCopy.copyElement(heat);

        assertNotSame(heat.getParameters().get(0), copy.getParameters().get(0));
        assertNotSame(heat.getParameters().get(0).getFirstValue(),
                copy.getParameters().get(0).getFirstValue(),
                "a shared value is one number in two places, and saving one of them writes into the"
                        + " other");
    }

    @Test
    void aCopyKeepsTheSameNamesSoItStillMeansTheSame() {
        S88Recipe copy = RecipeDeepCopy.copyRecipe(completeChart());

        assertEquals("HEAT", copy.findElement("HEAT").orElseThrow().getId());
        assertEquals("HEAT", copy.findElement("PROC").orElseThrow().getProcedureLogic().findStep("BOX_HEAT").orElseThrow()
                .getRecipeElementId());
    }

    @Test
    void aCopyOfAControlRecipeIsStillAControlRecipe() {
        var control = new org.apache.plc4x.malbec.s88.api.S88ControlRecipe("C1", "LOTE_014");

        S88Recipe copy = RecipeDeepCopy.copyRecipe(control);

        assertSame(S88RecipeKind.INSTANCE, ((S88MasterRecipe) copy).getKind());
        assertEquals("LOTE_014", ((org.apache.plc4x.malbec.s88.api.S88ControlRecipe) copy).getBatchId(),
                "a copy that came back as a plain master would lose the batch it is for");
    }

    // ========== Names ==========

    @Test
    void aStepNameCannotBeEmptyNorHoldTheSeparatorAParameterUses() {
        S88Recipe recipe = master();

        assertFalse(RecipeNameValidator.isValidName(recipe, "  "));
        assertFalse(RecipeNameValidator.isValidName(recipe, "Reports/STATE"),
                "a step name with a slash in it cannot be told apart from a parameter address");
        assertTrue(RecipeNameValidator.isValidName(recipe, "HEAT"));
    }

    @Test
    void aStepNameIsNoLongerThanThePlantAllows() {
        assertEquals(60, RecipeNameValidator.MAX_LENGTH);
        assertTrue(RecipeNameValidator.isValidName(master(), "H".repeat(60)));
        assertFalse(RecipeNameValidator.isValidName(master(), "H".repeat(61)));
    }

    // ========== Fixtures ==========

    /** The chart the fixture drew, which is on the procedure rather than on the recipe itself. */
    private static S88ProcedureLogic chartOf(S88Recipe recipe) {
        return recipe.findElement("PROC").orElseThrow().getProcedureLogic();
    }

    /** An empty recipe written by class, which is what most of these start from. */
    private static S88MasterRecipe master() {
        return new S88MasterRecipe("REC_CALENTAMIENTO", S88RecipeKind.CLASS);
    }

    /**
     * A procedure with a chart on it: a box on the step that heats, a box on the end of the process,
     * a line between them, and a bar waiting on the state the heating step reports.
     */
    private static S88Recipe completeChart() {
        S88Recipe recipe = master();
        S88RecipeElement proc = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "PROC", S88RecipeElementKind.PROCEDURE);
        S88RecipeElement heat = CreateRecipeElementUseCase.forEquipment(
                recipe, null, "HEAT", S88RecipeElementKind.OPERATION);
        CreateRecipeElementUseCase.forEquipment(recipe, null, "END", S88RecipeElementKind.END);
        UpdateRecipeParameterUseCase.execute(recipe, heat, "Reports/STATE", "IDLE",
                DataType.ENUMERATION, null);

        EditProcedureLogicUseCase.addStep(recipe, proc, "BOX_HEAT", "HEAT");
        EditProcedureLogicUseCase.addStep(recipe, proc, "BOX_END", "END");
        EditProcedureLogicUseCase.addLink(recipe, proc, "L1", List.of("BOX_HEAT"),
                List.of("BOX_END"), null);
        EditProcedureLogicUseCase.addTransition(recipe, proc, "T3", "Reports/STATE");
        return recipe;
    }
}


