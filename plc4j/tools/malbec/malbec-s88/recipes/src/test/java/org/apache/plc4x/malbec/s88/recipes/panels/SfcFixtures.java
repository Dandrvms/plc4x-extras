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

import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88IdScope;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;

/**
 * The six charts the drawing code has to get right, built in code.
 * <p>
 * Every line crosses between a box and a bar, and every node is declared before the line that
 * names it.
 */
final class SfcFixtures {

    private SfcFixtures() {
    }

    /** The name each recipe of {@link #all()} is written under, in the same order. */
    static List<String> names() {
        return List.of(
                "lineal",
                "con-bucle-hacia-atras",
                "divergencia-selectiva",
                "divergencia-paralela",
                "paralela-dentro-de-selectiva",
                "referencias-externas",
                "botones-lineal",
                "botones-paralela",
                "botones-paralela-tres-ramas",
                "botones-paralela-paso-en-una-rama",
                "botones-bifurcacion",
                "botones-bifurcacion-tres-ramas",
                "botones-bifurcacion-paso-en-una-rama");
    }

    /** Every fixture, in the order {@link #names()} lists them. */
    static List<S88MasterRecipe> all() {
        return List.of(
                lineal(),
                withBackwardsLoop(),
                selectiveDivergence(),
                parallelDivergence(),
                nestedParallelInsideSelective(),
                withExternalReferences(),
                Buttons.lineal("BOTONES_LINEAL"),
                Buttons.parallel(),
                Buttons.parallelThreeBranches(),
                Buttons.parallelStepInsideABranch(),
                Buttons.bifurcation(),
                Buttons.bifurcationThreeBranches(),
                Buttons.bifurcationStepInsideABranch());
    }

    /**
 * The charts the toolbar buttons build, one press at a time.
 *
 * <p>Every one of these calls the same use cases a button press calls, so each picture is the
 * recipe for the presses that make it, written down rather than described.
 */
static final class Buttons {

    private Buttons() {
    }

    static S88MasterRecipe newRecipe(String id) {
        return CreateMasterRecipeUseCase.execute(id, org.apache.plc4x.malbec.s88.api.S88RecipeKind.CLASS);
    }

    /** Pick the start, press Add step three times. */
    static S88MasterRecipe lineal(String id) {
        S88MasterRecipe recipe = newRecipe(id);
        String last = "BEGIN";
        for (int n = 0; n < 3; n++) {
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, last);
            last = "BOX_STEP" + (n == 0 ? "" : "_" + n);
        }
        return recipe;
    }

    /** Two steps, then a parallel branch on the second one. */
    static S88MasterRecipe parallel() {
        S88MasterRecipe recipe = lineal("BOTONES_PARALELA");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1");
        return recipe;
    }

    /** The same, pressing Add parallel branch once more. */
    static S88MasterRecipe parallelThreeBranches() {
        S88MasterRecipe recipe = lineal("BOTONES_PARALELA_TRES");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_1");
        ChartBranchingUseCase.addParallelBranch(recipe, null, "BOX_STEP_2");
        return recipe;
    }

    /** Pick a step inside one branch and press Add step, which grows that branch. */
    static S88MasterRecipe parallelStepInsideABranch() {
        S88MasterRecipe recipe = parallel();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
        return recipe;
    }

    /** Two steps, then a branch on the second one. */
    static S88MasterRecipe bifurcation() {
        S88MasterRecipe recipe = lineal("BOTONES_BIFURCACION");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP_1");
        return recipe;
    }

    /** The same, pressing Add branch once more. */
    static S88MasterRecipe bifurcationThreeBranches() {
        S88MasterRecipe recipe = lineal("BOTONES_BIFURCACION_TRES");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP_1");
        ChartBranchingUseCase.addAlternativeBranch(recipe, null, "BOX_STEP_2");
        return recipe;
    }

    /** Pick a step inside one branch and press Add step, which grows that branch. */
    static S88MasterRecipe bifurcationStepInsideABranch() {
        S88MasterRecipe recipe = bifurcation();
        BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BOX_STEP_2");
        return recipe;
    }
}

/** BEGIN, HEAT, MIX, PACK, END, with a bar between every two of them. */
    static S88MasterRecipe lineal() {
        Chart chart = new Chart("LINEAL");
        chart.bar("T_HEAT");
        chart.bar("T_MIX");
        chart.bar("T_PACK");
        chart.bar("T_END");
        chart.down("BEGIN", "T_HEAT");
        chart.up("T_HEAT", "HEAT");
        chart.down("HEAT", "T_MIX");
        chart.up("T_MIX", "MIX");
        chart.down("MIX", "T_PACK");
        chart.up("T_PACK", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** PACK waits for the mixer a second time, so one edge points back up the chart. */
    static S88MasterRecipe withBackwardsLoop() {
        Chart chart = new Chart("BUCLE");
        chart.bar("T_HEAT");
        chart.bar("T_MIX");
        chart.bar("T_PACK");
        chart.bar("T_PACK_DONE");
        chart.bar("T_MIX_AGAIN");
        chart.down("BEGIN", "T_HEAT");
        chart.up("T_HEAT", "HEAT");
        chart.down("HEAT", "T_MIX");
        chart.up("T_MIX", "MIX");
        chart.down("MIX", "T_PACK");
        chart.up("T_PACK", "PACK");
        // Two lines leave PACK, so the flow splits: either the batch is packed and stops, or it
        // goes round the mixer again. The second one points at a bar above it on the chart.
        chart.down("PACK", "T_PACK_DONE");
        chart.down("PACK", "T_MIX_AGAIN");
        chart.up("T_MIX_AGAIN", "MIX");
        return chart.finishFrom("T_PACK_DONE");
    }

    /** One step leaving towards two transitions, and only one of the branches is taken. */
    static S88MasterRecipe selectiveDivergence() {
        Chart chart = new Chart("SELECTIVA");
        chart.bar("T_CHARGED");
        chart.bar("T_HEAT_READY", "Reports/HEAT = TRUE");
        chart.bar("T_COOL_READY", "Reports/COOL = TRUE");
        chart.bar("T_PACKED");
        chart.bar("T_END");
        chart.down("BEGIN", "T_CHARGED");
        chart.up("T_CHARGED", "CHARGE");
        // One line with two arrivals is the selective split: each branch waits on its own
        // comparison, and whichever is true is the one that is taken. There is no other line from
        // CHARGE to T_HEAT_READY, or the drawing would show the split and a plain edge to one of
        // its branches, which is a chart saying two different things at once.
        chart.split("CHARGE", "T_HEAT_READY", "T_COOL_READY", S88LinkType.SERIAL_DIVERGENT);
        chart.up("T_HEAT_READY", "HEAT");
        chart.up("T_COOL_READY", "COOL");
        chart.join("HEAT", "COOL", "T_PACKED", S88LinkType.SERIAL_CONVERGENT);
        chart.up("T_PACKED", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** One bar leaving towards two steps, and both branches run at once. */
    static S88MasterRecipe parallelDivergence() {
        Chart chart = new Chart("PARALELA");
        chart.bar("T_CHARGED");
        chart.bar("T_SPLIT");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.down("BEGIN", "T_CHARGED");
        chart.up("T_CHARGED", "CHARGE");
        chart.down("CHARGE", "T_SPLIT");
        // The parallel split leaves a bar rather than a step, and that is what tells the drawing
        // which symbol it has to use.
        chart.splitFromBar("T_SPLIT", "HEAT", "COOL", S88LinkType.PARALLEL_DIVERGENT);
        chart.join("HEAT", "COOL", "T_MERGE", S88LinkType.PARALLEL_CONVERGENT);
        chart.up("T_MERGE", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** A parallel split inside a selective one, so one branch splits again while the other waits. */
    static S88MasterRecipe nestedParallelInsideSelective() {
        Chart chart = new Chart("ANIDADA");
        chart.bar("T_SPLIT");
        chart.bar("T_SPLIT_INNER");
        chart.bar("T_MERGE_INNER");
        chart.bar("T_STIR_DONE");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.box("BEGIN");
        chart.down("BEGIN", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", "HEAT", "STIR", S88LinkType.SERIAL_DIVERGENT);
        chart.down("HEAT", "T_SPLIT_INNER");
        chart.splitFromBar("T_SPLIT_INNER", "ADD", "SPRAY", S88LinkType.PARALLEL_DIVERGENT);
        chart.join("ADD", "SPRAY", "T_MERGE_INNER", S88LinkType.PARALLEL_CONVERGENT);
        chart.up("T_MERGE_INNER", "INNER_DONE");
        chart.join("INNER_DONE", "STIR", "T_MERGE", S88LinkType.SERIAL_CONVERGENT);
        chart.up("T_MERGE", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /**
     * A line with an end that leaves the chart, which the drawing leaves out for having nothing to
     * draw it to.
     */
    static S88MasterRecipe withExternalReferences() {
        Chart chart = new Chart("EXTERNAS");
        chart.bar("T_PUMPED");
        chart.bar("T_DONE");
        chart.down("BEGIN", "T_PUMPED");
        chart.to("T_PUMPED", outside("OTHER_PART"));
        chart.from(outside("OTHER_PART"), "RETURNED");
        chart.down("RETURNED", "T_DONE");
        return chart.finishFrom("T_DONE");
    }

    /** An end that points at a step of another part of the process. */
    private static S88IdRef outside(String stepId) {
        return new S88IdRef(stepId, S88IdRefType.STEP, S88IdScope.EXTERNAL);
    }

    /** {@code ADDRESS = VALUE} as the comparison a bar waits on. */
    private static S88ConditionExpression conditionOf(String text) {
        int split = text.indexOf('=');
        return S88ConditionExpression.of(
                S88VariableAddress.parse(text.substring(0, split).trim()),
                S88ConditionOperator.EQUALS,
                text.substring(split + 1).trim());
    }

    /**
     * A recipe and its chart under construction.
     * <p>
     * The chart a recipe is born with is taken apart, so that a fixture draws exactly the lines it
     * says it does.
     */
    private static final class Chart {

        private final S88MasterRecipe recipe;
        private final S88ProcedureLogic chart;

        Chart(String id) {
            this.recipe = CreateMasterRecipeUseCase.execute(id, S88RecipeKind.CLASS);
            this.chart = recipe.getProcedureLogic();
            List.copyOf(chart.getLinks()).forEach(chart::removeLink);
            List.copyOf(chart.getTransitions()).forEach(chart::removeTransition);
            List.copyOf(chart.getSteps()).forEach(chart::removeStep);
            // The start and the stop come back with the fixture, in the places it needs them rather
            // than always in the same two.
            List.copyOf(recipe.getRecipeElements()).forEach(recipe::removeRecipeElement);
        }

        /** What kind of step a name means, which is only decided for the two that have one. */
        private static S88RecipeElementKind kindOf(String elementId) {
            if (CreateMasterRecipeUseCase.BEGIN_ID.equals(elementId)) {
                return S88RecipeElementKind.BEGIN;
            }
            if (CreateMasterRecipeUseCase.END_ID.equals(elementId)) {
                return S88RecipeElementKind.END;
            }
            return S88RecipeElementKind.OPERATION;
        }

        /** A bar the flow waits at, with the comparison it waits on or none at all. */
        Chart bar(String barId) {
            return bar(barId, null);
        }

        /**
         * A bar is only made once, so a fixture can give a bar its comparison and join it up in
         * separate steps.
         */
        Chart bar(String barId, String condition) {            if (chart.findTransition(barId).isPresent()) {
                return this;
            }
            S88ProcedureTransition bar = new S88ProcedureTransition();
            bar.setId(barId);
            if (condition != null) {
                bar.setExpression(conditionOf(condition));
            }
            chart.addTransition(bar);
            return this;
        }

        /** A box on the chart working on a step of the recipe of the same name. */
        Chart box(String elementId) {
            if (chart.findStep("BOX_" + elementId).isPresent()) {
                return this;
            }
            if (recipe.findElement(elementId).isEmpty()) {
                S88RecipeElement element = new S88RecipeElement(elementId, kindOf(elementId));
                if (element.getKind() == S88RecipeElementKind.OPERATION) {
                    element.setEquipmentClassId(elementId + "_CLASS");
                }
                recipe.addRecipeElement(element);
            }
            chart.addStep(new S88ProcedureStep("BOX_" + elementId, elementId));
            return this;
        }

        /** A line from the box of a step to a bar. */
        Chart down(String elementId, String barId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step("BOX_" + elementId));
            link.addTo(S88IdRef.transition(barId));
            chart.addLink(link);
            return this;
        }

        /** A line from a bar to the box of the step that follows it. */
        Chart up(String barId, String elementId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step("BOX_" + elementId));
            chart.addLink(link);
            return this;
        }

        /** One line leaving a box towards two bars, which is the selective split. */
        Chart split(String elementId, String firstBar, String secondBar, S88LinkType type) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step("BOX_" + elementId));
            link.addTo(S88IdRef.transition(firstBar));
            link.addTo(S88IdRef.transition(secondBar));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /** One line leaving a bar towards two boxes, which is the parallel split. */
        Chart splitFromBar(String barId, String first, String second, S88LinkType type) {
            box(first);
            box(second);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step("BOX_" + first));
            link.addTo(S88IdRef.step("BOX_" + second));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /** One line bringing two boxes together onto a bar, which is the parallel convergence. */
        Chart join(String first, String second, String barId, S88LinkType type) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step("BOX_" + first));
            link.addFrom(S88IdRef.step("BOX_" + second));
            link.addTo(S88IdRef.transition(barId));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /** A line whose arrival is outside this chart. */
        Chart to(String fromBar, S88IdRef arrival) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(fromBar));
            link.addTo(arrival);
            chart.addLink(link);
            return this;
        }

        /** A line whose departure is outside this chart. */
        Chart from(S88IdRef departure, String toElementId) {
            box(toElementId);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(departure);
            link.addTo(S88IdRef.step("BOX_" + toElementId));
            chart.addLink(link);
            return this;
        }

        /** The stop of the process, with a line from the bar the flow arrives at. */
        S88MasterRecipe finishFrom(String barId) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step("END"));
            chart.addLink(link);
            recipe.addRecipeElement(new S88RecipeElement("END", S88RecipeElementKind.END));
            chart.addStep(new S88ProcedureStep("END", "END"));
            return recipe;
        }

        private String nextLinkId() {
            return "L" + (chart.getLinks().size() + 1);
        }
    }
}
