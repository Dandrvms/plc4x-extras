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
        recipe.getProcedureLogic().addLink(S88ProcedureLink.betweenSteps("LB", "END", "BEGIN"));

        RecipeConformance report = RecipeConformance.of(recipe);

        // A loop back to an earlier step is how a recipe retries, so it is not reported. What matters
        // is that following the links round the cycle terminates rather than walking it forever,
        // and that the steps on the way are still seen as reachable.
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

    // ========== Fixtures ==========

    /** A master recipe written by class, which is the shape most recipes start out in. */
    private static S88MasterRecipe master() {
        return new S88MasterRecipe("R1", S88RecipeKind.CLASS);
    }

    private static S88RecipeElement step(String id, S88RecipeElementKind kind) {
        return new S88RecipeElement(id, kind);
    }

    /** Begin, one operation and End, joined in a line and nothing else wrong with it. */
    private static S88MasterRecipe completeChartRecipe() {
        S88MasterRecipe recipe = master();
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "HEAT", S88RecipeElementKind.OPERATION);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addLink(S88ProcedureLink.betweenSteps("L1", "BEGIN", "HEAT"));
        logic.addLink(S88ProcedureLink.betweenSteps("L2", "HEAT", "END"));
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("HEAT", "HEAT"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        logic.addTransition(new S88ProcedureTransition("T1", "TEMP_OK"));
        recipe.setProcedureLogic(logic);
        return recipe;
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


