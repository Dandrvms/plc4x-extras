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
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a recipe gets told about itself.
 * <p>
 * None of these throws. A recipe is written by hand and saved half finished, so the useful question
 * is never whether it is broken but what it is missing, and the most of these cases are the
 * ordinary middle of writing one rather than a mistake worth refusing to load.
 */
class RecipeConformanceTest {

    // ========== Deficit: what is missing ==========

    @Test
    void aRecipeWithNoIdAndNoStepsIsReportedRatherThanRejected() {
        RecipeConformance report = RecipeConformance.of(new S88MasterRecipe());

        assertFalse(report.isConforming());
        assertEquals(2, report.deficit().size(), report.deficit().toString());
    }

    @Test
    void aClassStepThatNamesNoClassIsReported() {
        S88MasterRecipe recipe = master();
        recipe.addRecipeElement(step("HEAT", S88RecipeElementKind.OPERATION));

        assertTrue(deficitMentions(recipe, "names no class of equipment"),
                "a recipe written by class with nothing to bind to cannot be run, and the person"
                        + " writing it has to be told while writing it");
    }

    @Test
    void anInstanceStepThatNamesNoEquipmentIsReported() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.INSTANCE);
        recipe.addRecipeElement(step("HEAT", S88RecipeElementKind.OPERATION));

        assertTrue(deficitMentions(recipe, "names none"),
                "a recipe written for particular equipment that names none has nothing to run on");
    }

    @Test
    void aControlRecipeWithoutABatchIsReported() {
        S88ControlRecipe control = new S88ControlRecipe("C1", null, S88RecipeKind.INSTANCE);
        S88RecipeElement element = step("HEAT", S88RecipeElementKind.OPERATION);
        element.addActualEquipmentId("CALENTAMIENTO_TANQUE_1");
        control.addRecipeElement(element);

        assertTrue(deficitMentions(control, "batch"),
                "a control recipe with no batch is indistinguishable from any other run");
    }

    @Test
    void aStepWithNoKindIsReported() {
        S88MasterRecipe recipe = master();
        recipe.addRecipeElement(new S88RecipeElement("HEAT", null));

        assertTrue(deficitMentions(recipe, "what kind of step it is"));
    }

    // ========== Excess: what is in the way ==========

    @Test
    void twoStepsWithTheSameNameAreReportedAsNotRunnable() {
        S88MasterRecipe recipe = master();
        recipe.addRecipeElement(step("HEAT", S88RecipeElementKind.OPERATION));
        recipe.addRecipeElement(step("HEAT", S88RecipeElementKind.OPERATION));

        assertTrue(excessMentions(recipe, "HEAT"),
                "a step that points at that id does not say which one it meant");
    }

    @Test
    void aStepThatMixesTheTwoWaysOfAddressingIsReported() {
        S88MasterRecipe recipe = master();
        S88RecipeElement element = step("HEAT", S88RecipeElementKind.OPERATION);
        element.setEquipmentClassId("HEATER");
        element.addActualEquipmentId("CALENTAMIENTO_TANQUE_1");
        recipe.addRecipeElement(element);

        assertTrue(excessMentions(recipe, "recipe addresses its equipment one way only"),
                "one recipe addresses its equipment one way, so a step doing both is a mistake the"
                        + " reader would otherwise have to notice unaided");
    }

    @Test
    void aChartPointingAtAStepTheRecipeDoesNotCarryIsReported() {
        S88MasterRecipe recipe = master();
        recipe.addRecipeElement(step("HEAT", S88RecipeElementKind.OPERATION));
        recipe.getRecipeElements().getFirst().setEquipmentClassId("HEATER");
        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("S1", "GHOST"));
        logic.addStep(new S88ProcedureStep("SB", "HEAT"));
        logic.addStep(new S88ProcedureStep("SE", "HEAT"));
        recipe.setProcedureLogic(logic);

        assertTrue(excessMentions(recipe, "GHOST"),
                "a chart that names a step that is not there cannot be run, and the recipe is still"
                        + " opened so it can be put right");
    }

    @Test
    void aChartLinePointingAtSomethingItDoesNotCarryIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.getProcedureLogic().addLink(S88ProcedureLink.betweenSteps("LX", "GONE", "HEAT"));

        assertTrue(excessMentions(recipe, "GONE"));
    }

    // ========== The chart has to hold together ==========

    @Test
    void aChartWithNoStartAndNoEndIsReported() {
        S88MasterRecipe recipe = master();
        S88RecipeElement element = step("HEAT", S88RecipeElementKind.OPERATION);
        element.setEquipmentClassId("HEATER");
        recipe.addRecipeElement(element);
        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("S1", "HEAT"));
        recipe.setProcedureLogic(logic);

        assertTrue(deficitMentions(recipe, "kind Begin"));
        assertTrue(deficitMentions(recipe, "kind End"));
    }

    @Test
    void aChartWithTwoStartsIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.addRecipeElement(step("AGAIN", S88RecipeElementKind.BEGIN));
        recipe.getProcedureLogic().addStep(new S88ProcedureStep("S_AGAIN", "AGAIN"));

        assertTrue(excessMentions(recipe, "more than one place the flow starts"),
                "a recipe with two starts has no single answer to where the flow begins, so a runtime"
                        + " has to pick one and the recipe does not say which");
    }

    @Test
    void aChartWithTwoStopsIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.addRecipeElement(step("FINISH", S88RecipeElementKind.END));
        recipe.getProcedureLogic().addStep(new S88ProcedureStep("S_FINISH", "FINISH"));

        assertTrue(excessMentions(recipe, "more than one place the flow stops"),
                "and the same goes for two stops");
    }

    @Test
    void aChartThatGoesBackToItsStartIsNotReportedForIt() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.getProcedureLogic().addTransition(new S88ProcedureTransition("T_BACK", null));
        addLine(recipe.getProcedureLogic(), "L_BACK_OUT",
                S88IdRef.step("HEAT"), S88IdRef.transition("T_BACK"));
        addLine(recipe.getProcedureLogic(), "L_BACK",
                S88IdRef.transition("T_BACK"), S88IdRef.step("BEGIN"));

        assertFalse(RecipeConformance.of(recipe).excess().stream()
                        .anyMatch(saying -> saying.contains("where the flow starts")),
                "a retry back to the start is what ISA-88 asks for, so a line that does it is not a"
                        + " mistake");
    }

    @Test
    void aStepTheFlowNeverReachesIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.addRecipeElement(step("ORPHAN", S88RecipeElementKind.OPERATION));
        recipe.findElement("ORPHAN").orElseThrow().setEquipmentClassId("HEATER");
        recipe.getProcedureLogic().addStep(new S88ProcedureStep("S_ORPHAN", "ORPHAN"));

        assertTrue(excessMentions(recipe, "S_ORPHAN"),
                "a step nothing reaches is a step the process would never perform, which is a"
                        + " different mistake from a chart with no start");
    }

    @Test
    void aStepTheFlowCannotGetPastIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.getProcedureLogic().removeLink(
                recipe.getProcedureLogic().findLink("L4").orElseThrow());

        assertTrue(excessMentions(recipe, "T_OUT"),
                "a bar with no line out is where the flow can get stuck, and it is not left to be"
                        + " found by walking the drawing");
    }

    @Test
    void aSplitWithOneSideIsReportedAndNotRepaired() {
        S88MasterRecipe recipe = completeChartRecipe();
        S88ProcedureLink split = S88ProcedureLink.betweenSteps("LS", "BEGIN", "HEAT");
        split.setLinkType(S88LinkType.PARALLEL_DIVERGENT);
        recipe.getProcedureLogic().addLink(split);

        assertTrue(excessMentions(recipe, "LS"),
                "a split with one side is a branch that goes nowhere");
        assertEquals(1, recipe.getProcedureLogic().getLinks().stream()
                        .filter(l -> "LS".equals(l.getId()))
                        .count(),
                "and it is left as written, because inventing the missing side would produce a"
                        + " chart that looks complete and does not mean what its author intended");
    }

    @Test
    void aJoinWithOneSideIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        S88ProcedureLink join = S88ProcedureLink.betweenSteps("LJ", "HEAT", "END");
        join.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        recipe.getProcedureLogic().addLink(join);

        assertTrue(excessMentions(recipe, "LJ"), "a join that waits for something that never arrives");
    }

    @Test
    void aChartThatGoesBackToAnEarlierStepIsNotAMistakeButStillEndsTheWalk() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.getProcedureLogic().addTransition(new S88ProcedureTransition("T_RETRY", null));
        addLine(recipe.getProcedureLogic(), "L_RETRY_OUT",
                S88IdRef.step("END"), S88IdRef.transition("T_RETRY"));
        addLine(recipe.getProcedureLogic(), "L_RETRY",
                S88IdRef.transition("T_RETRY"), S88IdRef.step("BEGIN"));

        RecipeConformance report = RecipeConformance.of(recipe);

        assertTrue(report.isConforming(),
                "a loop back to an earlier step is a retry, not a defect: "
                        + report.excess() + " / " + report.deficit());
    }

    @Test
    void aChartThatIsSoundHasNothingToReport() {
        RecipeConformance report = RecipeConformance.of(completeChartRecipe());

        assertTrue(report.isConforming(),
                "unexpected: " + report.excess() + " / " + report.deficit());
    }

    @Test
    void thereIsNoRecipeToCheckIsSaidRatherThanThrown() {
        assertFalse(RecipeConformance.of(null).isConforming());
    }

    // ========== The rules of a structured chart ==========

    @Test
    void aLineFromOneStepToAnotherIsReported() {
        S88MasterRecipe recipe = completeChartRecipe();
        recipe.getProcedureLogic().addLink(
                S88ProcedureLink.betweenSteps("L_DIRECT", "HEAT", "BEGIN"));

        assertTrue(excessMentions(recipe, "L_DIRECT"),
                "a line straight from one step to another has no bar to wait at, so the flow has"
                        + " nothing to follow");
    }

    @Test
    void aSplitWhoseSidesDoNotComeBackTogetherIsReported() {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "LEFT", S88RecipeElementKind.OPERATION);
        addElement(recipe, "RIGHT", S88RecipeElementKind.OPERATION);
        addElement(recipe, "OTHER", S88RecipeElementKind.OPERATION);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("LEFT", "LEFT"));
        logic.addStep(new S88ProcedureStep("RIGHT", "RIGHT"));
        logic.addStep(new S88ProcedureStep("OTHER", "OTHER"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        logic.addTransition(new S88ProcedureTransition("T_OPEN", null));
        logic.addTransition(new S88ProcedureTransition("T_JOIN", null));
        logic.addTransition(new S88ProcedureTransition("T_RIGHT", null));
        addLine(logic, "L1", S88IdRef.step("BEGIN"), S88IdRef.transition("T_OPEN"));

        S88ProcedureLink open = new S88ProcedureLink("L_OPEN");
        open.addFrom(S88IdRef.transition("T_OPEN"));
        open.addTo(S88IdRef.step("LEFT"));
        open.addTo(S88IdRef.step("RIGHT"));
        open.addTo(S88IdRef.step("OTHER"));
        open.setLinkType(S88LinkType.SERIAL_DIVERGENT);
        logic.addLink(open);

        S88ProcedureLink join = new S88ProcedureLink("L_JOIN");
        join.addFrom(S88IdRef.step("LEFT"));
        join.addFrom(S88IdRef.step("RIGHT"));
        join.addTo(S88IdRef.transition("T_JOIN"));
        join.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        logic.addLink(join);

        addLine(logic, "L_JOIN_ON", S88IdRef.transition("T_JOIN"), S88IdRef.step("END"));
        addLine(logic, "L_OTHER", S88IdRef.step("OTHER"), S88IdRef.transition("T_RIGHT"));
        addLine(logic, "L_RIGHT_ON", S88IdRef.transition("T_RIGHT"), S88IdRef.step("END"));
        recipe.setProcedureLogic(logic);

        assertTrue(excessMentions(recipe, "T_JOIN"),
                "two sides close on one bar and the third closes on none, so the bar waits for a"
                        + " token that its split never gives it and the flow can stop there");
    }

    @Test
    void aLineThatGoesBackThroughAParallelBlockIsReported() {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "WORK", S88RecipeElementKind.OPERATION);
        addElement(recipe, "OTHER", S88RecipeElementKind.OPERATION);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("WORK", "WORK"));
        logic.addStep(new S88ProcedureStep("OTHER", "OTHER"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        logic.addTransition(new S88ProcedureTransition("T_OPEN", null));
        logic.addTransition(new S88ProcedureTransition("T_CLOSE", null));
        logic.addTransition(new S88ProcedureTransition("T_RETRY", null));
        addLine(logic, "L1", S88IdRef.step("BEGIN"), S88IdRef.transition("T_OPEN"));

        S88ProcedureLink open = new S88ProcedureLink("L_OPEN");
        open.addFrom(S88IdRef.transition("T_OPEN"));
        open.addTo(S88IdRef.step("WORK"));
        open.addTo(S88IdRef.step("OTHER"));
        open.setLinkType(S88LinkType.PARALLEL_DIVERGENT);
        logic.addLink(open);

        S88ProcedureLink close = new S88ProcedureLink("L_CLOSE");
        close.addFrom(S88IdRef.step("WORK"));
        close.addFrom(S88IdRef.step("OTHER"));
        close.addTo(S88IdRef.transition("T_CLOSE"));
        close.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        logic.addLink(close);

        addLine(logic, "L_ON", S88IdRef.transition("T_CLOSE"), S88IdRef.step("END"));
        addLine(logic, "L_RETRY_OUT", S88IdRef.step("END"), S88IdRef.transition("T_RETRY"));
        addLine(logic, "L_RETRY", S88IdRef.transition("T_RETRY"), S88IdRef.step("WORK"));
        recipe.setProcedureLogic(logic);

        assertTrue(excessMentions(recipe, "parallel block"),
                "a retry into a parallel branch holds that branch back for the others, which a"
                        + " parallel block is not");
    }

    @Test
    void anAlternativeSplitWhoseSidesSayNothingIsWarnedAbout() {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        logic.addTransition(new S88ProcedureTransition("T_ONE", null));
        logic.addTransition(new S88ProcedureTransition("T_TWO", null));

        S88ProcedureLink split = new S88ProcedureLink("L_SPLIT");
        split.addFrom(S88IdRef.step("BEGIN"));
        split.addTo(S88IdRef.transition("T_ONE"));
        split.addTo(S88IdRef.transition("T_TWO"));
        split.setLinkType(S88LinkType.SERIAL_DIVERGENT);
        logic.addLink(split);

        addLine(logic, "L_ONE_ON", S88IdRef.transition("T_ONE"), S88IdRef.step("END"));
        addLine(logic, "L_TWO_ON", S88IdRef.transition("T_TWO"), S88IdRef.step("END"));
        recipe.setProcedureLogic(logic);

        RecipeConformance report = RecipeConformance.of(recipe);

        assertTrue(report.warnings().stream().anyMatch(said -> said.contains("cannot be told")),
                "the sides are taken one after another and neither says how, so the author has to"
                        + " be told which one is read first: " + report.warnings());
        assertTrue(report.isConforming(),
                "but a warning is not something in the way of running it: "
                        + report.excess() + " / " + report.deficit());
    }

    @Test
    void aPartialSynchronizationIsNotRefused() {
        S88MasterRecipe recipe = withSteps(List.of("A", "B", "C", "D", "E"));
        S88ProcedureLogic logic = recipe.getProcedureLogic();
        logic.addTransition(new S88ProcedureTransition("T_OPEN", null));
        logic.addTransition(new S88ProcedureTransition("T_MID", null));
        logic.addTransition(new S88ProcedureTransition("T_CLOSE", null));
        addLine(logic, "L_BEGIN", S88IdRef.step("BEGIN"), S88IdRef.transition("T_OPEN"));
        addSplit(logic, "L_OPEN", S88IdRef.transition("T_OPEN"), List.of("A", "B", "C"),
                S88LinkType.PARALLEL_DIVERGENT);
        addJoin(logic, "L_MID", List.of("A", "B", "C"), "T_MID");
        addSplit(logic, "L_SPLIT", S88IdRef.transition("T_MID"), List.of("D", "E"),
                S88LinkType.PARALLEL_DIVERGENT);
        addJoin(logic, "L_CLOSE", List.of("D", "E"), "T_CLOSE");
        addLine(logic, "L_END", S88IdRef.transition("T_CLOSE"), S88IdRef.step("END"));

        RecipeConformance report = RecipeConformance.of(recipe);

        assertTrue(report.isConforming(),
                "a transition with many branches in and many out is a valid synchronization, not a"
                        + " badly nested block: " + report.excess() + " / " + report.deficit());
    }

    @Test
    void aSkipAcrossATransitionIsWarnedAboutRatherThanRefused() {
        S88MasterRecipe recipe = withSteps(List.of("HEAT"));
        S88ProcedureLogic logic = recipe.getProcedureLogic();
        logic.addTransition(new S88ProcedureTransition("T_IN", null));
        logic.addTransition(new S88ProcedureTransition("T_SKIP", null));
        logic.addTransition(new S88ProcedureTransition("T_OUT", null));
        addLine(logic, "L_BEGIN", S88IdRef.step("BEGIN"), S88IdRef.transition("T_IN"));
        addLine(logic, "L_STEP", S88IdRef.transition("T_IN"), S88IdRef.step("HEAT"));
        addLine(logic, "L_SKIP_OUT", S88IdRef.step("HEAT"), S88IdRef.transition("T_SKIP"));
        addLine(logic, "L_SKIP", S88IdRef.transition("T_SKIP"), S88IdRef.transition("T_OUT"));
        addLine(logic, "L_END", S88IdRef.transition("T_OUT"), S88IdRef.step("END"));

        RecipeConformance report = RecipeConformance.of(recipe);

        assertTrue(report.isConforming(),
                "a skipped step is unusual, not a reason to refuse the chart: "
                        + report.excess() + " / " + report.deficit());
        assertTrue(report.warnings().stream().anyMatch(said -> said.contains("skips a step")),
                "but the author is told which transition skips it: " + report.warnings());
    }

    // ========== Fixtures ==========

    /** A recipe whose chart is a straight line through the given step ids. */
    private static S88MasterRecipe withSteps(List<String> stepIds) {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        for (String id : stepIds) {
            addElement(recipe, id, S88RecipeElementKind.OPERATION);
        }
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        for (String id : stepIds) {
            logic.addStep(new S88ProcedureStep(id, id));
        }
        logic.addStep(new S88ProcedureStep("END", "END"));
        recipe.setProcedureLogic(logic);
        return recipe;
    }

    private static void addSplit(S88ProcedureLogic logic, String id, S88IdRef from,
                                 List<String> to, S88LinkType type) {
        S88ProcedureLink split = new S88ProcedureLink(id);
        split.addFrom(from);
        for (String step : to) {
            split.addTo(S88IdRef.step(step));
        }
        split.setLinkType(type);
        logic.addLink(split);
    }

    private static void addJoin(S88ProcedureLogic logic, String id, List<String> from, String to) {
        S88ProcedureLink join = new S88ProcedureLink(id);
        for (String step : from) {
            join.addFrom(S88IdRef.step(step));
        }
        join.addTo(S88IdRef.transition(to));
        join.setLinkType(S88LinkType.PARALLEL_CONVERGENT);
        logic.addLink(join);
    }

    /** A master recipe written by class, which is the shape most recipes start out in. */
    private static S88MasterRecipe master() {
        return new S88MasterRecipe("R1", S88RecipeKind.CLASS);
    }

    private static S88RecipeElement step(String id, S88RecipeElementKind kind) {
        return new S88RecipeElement(id, kind);
    }

    /**
     * Begin, one operation and End, each line crossing a bar and nothing else wrong with it.
     */
    private static S88MasterRecipe completeChartRecipe() {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "HEAT", S88RecipeElementKind.OPERATION);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("HEAT", "HEAT"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        logic.addTransition(new S88ProcedureTransition("T_IN", "START"));
        logic.addTransition(new S88ProcedureTransition("T_OUT", "TEMP_OK"));
        addLine(logic, "L1", S88IdRef.step("BEGIN"), S88IdRef.transition("T_IN"));
        addLine(logic, "L2", S88IdRef.transition("T_IN"), S88IdRef.step("HEAT"));
        addLine(logic, "L3", S88IdRef.step("HEAT"), S88IdRef.transition("T_OUT"));
        addLine(logic, "L4", S88IdRef.transition("T_OUT"), S88IdRef.step("END"));
        recipe.setProcedureLogic(logic);
        return recipe;
    }

    private static void addLine(S88ProcedureLogic logic, String id, S88IdRef from, S88IdRef to) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        line.addFrom(from);
        line.addTo(to);
        logic.addLink(line);
    }

    private static void addElement(S88MasterRecipe recipe, String id, S88RecipeElementKind kind) {
        S88RecipeElement element = step(id, kind);
        element.setEquipmentClassId("HEATER");
        element.addParameter(S88RecipeParameter.of("Reports/STATE", "IDLE", DataType.ENUMERATION, null));
        recipe.addRecipeElement(element);
    }

    private static boolean deficitMentions(S88Recipe recipe, String fragment) {
        return mentions(RecipeConformance.of(recipe).deficit(), fragment);
    }

    private static boolean excessMentions(S88Recipe recipe, String fragment) {
        return mentions(RecipeConformance.of(recipe).excess(), fragment);
    }

    /**
     * Looks for a phrase in a report, ignoring where the lines were wrapped, since a message that
     * reads well when printed is wrapped to fit a terminal and should not be matched literally.
     */
    private static boolean mentions(List<String> report, String fragment) {
        String needle = fragment.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return report.stream()
                .map(line -> line.replaceAll("\\s+", " "))
                .anyMatch(line -> line.contains(needle));
    }
}


