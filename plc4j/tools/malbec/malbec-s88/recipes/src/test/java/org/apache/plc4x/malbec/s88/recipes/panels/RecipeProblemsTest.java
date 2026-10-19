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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.core.RecipeConformance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the panel under the chart says, which is everything the operator has to be told at once.
 */
class RecipeProblemsTest {

    @Test
    void whatTheChartCannotArrangeIsShownWithWhatTheRecipeCannotRun() {
        String fromTheChart = "Line 'L2' leaves the chart, so what is here is drawn and the rest"
                + " is not.";
        String fromTheEditor = "Pick a step first.";

        List<String> said = RecipeProblems.toShow(
                RecipeConformance.of(SfcFixtures.lineal()),
                List.of(fromTheChart),
                fromTheEditor);

        assertTrue(said.contains(fromTheChart),
                "a chart the layout could not arrange is something the operator has to be told about,"
                        + " and it is not among " + said);
        assertTrue(said.contains(fromTheEditor),
                "and so is what the editor is warning about, beside the recipe's own findings: "
                        + said);
    }

    @Test
    void pickingAFindingPutsTheFrameOnTheStepItIsAbout() {
        RecipeProblems problems = new RecipeProblems();
        List<String> picked = new ArrayList<>();
        problems.onPick(picked::add);

        problems.show(aReportAboutAStep(), List.of("a chart complaint"), "an editor warning");

        problems.pickLineAt(0);

        assertEquals(List.of("ORPHAN"), picked,
                "picking a finding about a step puts the frame on that step");
    }

    @Test
    void pickingALineThatIsAboutNoNodePutsTheFrameNowhere() {
        RecipeProblems problems = new RecipeProblems();
        List<String> picked = new ArrayList<>();
        problems.onPick(picked::add);

        problems.show(aReportAboutAStep(), List.of("a chart complaint"), "an editor warning");

        problems.pickLineAt(problems.saying().size() - 1);

        assertEquals(List.of(), picked,
                "a line about the recipe or the editor names no node, so there is nothing to pick");
    }

    /**
     * A report with a finding about a step: the chart names a step the flow never reaches.
     */
    private static RecipeConformance aReportAboutAStep() {
        S88MasterRecipe recipe = new S88MasterRecipe("R1", S88RecipeKind.CLASS);
        addElement(recipe, "BEGIN", S88RecipeElementKind.BEGIN);
        addElement(recipe, "ORPHAN", S88RecipeElementKind.OPERATION);
        addElement(recipe, "END", S88RecipeElementKind.END);

        S88ProcedureLogic logic = new S88ProcedureLogic();
        logic.addStep(new S88ProcedureStep("BEGIN", "BEGIN"));
        logic.addStep(new S88ProcedureStep("ORPHAN", "ORPHAN"));
        logic.addStep(new S88ProcedureStep("END", "END"));
        recipe.setProcedureLogic(logic);
        return RecipeConformance.of(recipe);
    }

    private static void addElement(S88MasterRecipe recipe, String id, S88RecipeElementKind kind) {
        S88RecipeElement element = new S88RecipeElement(id, kind);
        element.setEquipmentClassId("HEATER");
        recipe.addRecipeElement(element);
    }
}
