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
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.CreateMasterRecipeUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;

/**
 * The six charts the drawing code has to get right, built in code.
 * <p>
 * Every line crosses between a box and a bar, and every node is declared before the line that
 * names it.
 */
final class SfcFixtures {

    private SfcFixtures() {
    }

    /**
     * The charts the toolbar buttons build, one press at a time.
     * <p>
     * Every one of these calls the same use cases a button press calls, so each picture is the
     * recipe for the presses that make it, written down rather than described. A step they have
     * just added is empty, which is why they are not part of {@link #structures()}.
     */
    static List<S88MasterRecipe> buttons() {
        return List.of(
                Buttons.lineal("BOTONES_LINEAL"),
                Buttons.parallel("BOTONES_PARALELA"),
                Buttons.parallelThreeBranches("BOTONES_PARALELA_TRES"),
                Buttons.stepInsideAParallelBranch("BOTONES_PARALELA_PASO_EN_RAMA"),
                Buttons.alternative("BOTONES_ALTERNATIVA"),
                Buttons.alternativeThreeBranches("BOTONES_ALTERNATIVA_TRES"),
                Buttons.stepInsideAnAlternativeBranch("BOTONES_ALTERNATIVA_PASO_EN_RAMA"),
                Buttons.parallelInsideAlternative("BOTONES_PARALELA_EN_ALTERNATIVA"),
                Buttons.alternativeInsideParallel("BOTONES_ALTERNATIVA_EN_PARALELA"),
                Buttons.parallelInsideParallel("BOTONES_PARALELA_EN_PARALELA"),
                Buttons.alternativeInsideAlternative("BOTONES_ALTERNATIVA_EN_ALTERNATIVA"),
                Buttons.subchartTwoLevels("BOTONES_SUBGRAFICO"),
                Buttons.optionalBranch("BOTONES_RAMA_OPCIONAL"));
    }

    /**
     * Lays a chart out the way the drawing lays it out, with no window anywhere.
     *
     * <p>Both the layout tests and the structure tests want this, so it is here once rather than
     * in each of them.
     *
     * @param recipe whose chart to lay out
     * @return where everything landed
     */
    static SfcLayoutEngine.Placement arranged(S88MasterRecipe recipe) {
        return new SfcLayoutEngine(recipe.getProcedureLogic(), shapesOf(recipe),
                labelWidthsOf(recipe)).place();
    }

    /** The shape each node or bar of a chart is drawn in, which comes from the step behind it. */
    private static java.util.Map<String, RecipeShapes.Shape> shapesOf(S88MasterRecipe recipe) {
        java.util.Map<String, RecipeShapes.Shape> shapes = new java.util.HashMap<>();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        for (S88ProcedureStep step : chart.getSteps()) {
            shapes.put(step.getId(), recipe.findElement(step.getRecipeElementId())
                    .map(element -> RecipeShapes.shapeOf(element.getKind()))
                    .orElse(RecipeShapes.Shape.BOX));
        }
        for (S88ProcedureTransition bar : chart.getTransitions()) {
            shapes.put(bar.getId(), RecipeShapes.Shape.TRANSITION);
        }
        return shapes;
    }

    /** How wide the text of each bar is, measured the same way the drawing measures it. */
    private static java.util.Map<String, Integer> labelWidthsOf(S88MasterRecipe recipe) {
        java.awt.image.BufferedImage scratch =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scratch.createGraphics();
        try {
            java.util.Map<String, Integer> widths = new java.util.LinkedHashMap<>();
            recipe.getProcedureLogic().getTransitions().forEach(bar -> widths.put(
                    bar.getId(), RecipeShapes.textWidth(g, bar.getCondition())));
            return widths;
        } finally {
            g.dispose();
        }
    }

    /**
     * The structures that are drawn so they can be shown not to hold together.
     * <p>
     * A test that walks every fixture skips these, because their complaint is the thing they are
     * for. {@link SfcStructureTest} is the one that says what is wrong with them.
     */
    static List<S88MasterRecipe> refused() {
        return List.of(barsJoinedWithNoStep());
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
                "paralela-tres-ramas",
                "selectiva-tres-ramas",
                "selectiva-dentro-de-paralela",
                "paralela-dentro-de-paralela",
                "selectiva-dentro-de-selectiva",
                "rama-opcional",
                "bucle-con-paralela",
                "dos-niveles-de-subgrafico",
                "botones-lineal",
                "botones-paralela",
                "botones-paralela-tres-ramas",
                "botones-paralela-paso-dentro-de-una-rama",
                "botones-alternativa",
                "botones-alternativa-tres-ramas",
                "botones-alternativa-paso-dentro-de-una-rama",
                "botones-paralela-dentro-de-alternativa",
                "botones-alternativa-dentro-de-paralela",
                "botones-paralela-dentro-de-paralela",
                "botones-alternativa-dentro-de-alternativa",
                "botones-subgrafico-dos-niveles",
                "botones-rama-opcional");
    }

    /** Every fixture, in the order {@link #names()} lists them. */
    /**
     * The structures of {@link SfcStructureTest}, in the order {@link #names()} lists them.
     * <p>
     * Every step of one of these is a step of its own, so the conformance has nothing to say about
     * it. The ones {@link #buttons()} builds are left out, because a step the buttons have just
     * added is empty until it is given something to work on.
     */
    static List<S88MasterRecipe> structures() {
        return List.of(
                lineal(),
                withBackwardsLoop(),
                selectiveDivergence(),
                parallelDivergence(),
                nestedParallelInsideSelective(),
                withExternalReferences(),
                parallelWithThreeBranches(),
                selectiveWithThreeBranches(),
                selectiveInsideParallel(),
                parallelInsideParallel(),
                selectiveInsideSelective(),
                optionalBranch(),
                loopAroundParallel(),
                twoLevelsOfSubchart());
    }

    /** Every fixture, in the order {@link #names()} lists them. */
    static List<S88MasterRecipe> all() {
        List<S88MasterRecipe> all = new java.util.ArrayList<>(structures());
        all.addAll(buttons());
        return all;
    }

    /**
     * The charts the toolbar buttons build, one press at a time.
     * <p>
     * Every one of these calls the same use cases a button press calls, so each picture is the
     * recipe for the presses that make it, written down rather than described. A step they have just
     * added is empty, which is why they are not part of {@link #structures()}.
     */
    static final class Buttons {

        private Buttons() {
        }

        static S88MasterRecipe newRecipe(String id) {
            return CreateMasterRecipeUseCase.execute(id, S88RecipeKind.CLASS);
        }

        /** One step between the start and the stop, which is what a block is built on. */
        private static S88MasterRecipe oneStep(String id) {
            S88MasterRecipe recipe = newRecipe(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "BEGIN");
            return recipe;
        }

        /** Pick the start and press Add step three times. */
        static S88MasterRecipe lineal(String id) {
            S88MasterRecipe recipe = oneStep(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP");
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_1");
            return recipe;
        }

        /** Pick the step and press Add parallel branch. */
        static S88MasterRecipe parallel(String id) {
            S88MasterRecipe recipe = oneStep(id);
            ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP");
            return recipe;
        }

        /** The same, pressing Add parallel branch once more on a branch already there. */
        static S88MasterRecipe parallelThreeBranches(String id) {
            S88MasterRecipe recipe = parallel(id);
            ChartBranchingUseCase.addParallelBranch(recipe, null, "STEP_1");
            return recipe;
        }

        /** Pick a step inside one branch and press Add step, which grows that branch. */
        static S88MasterRecipe stepInsideAParallelBranch(String id) {
            S88MasterRecipe recipe = parallel(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_1");
            return recipe;
        }

        /** Pick the step and press Add branch. */
        static S88MasterRecipe alternative(String id) {
            S88MasterRecipe recipe = oneStep(id);
            ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP");
            return recipe;
        }

        /** The same, pressing Add branch once more on a branch already there. */
        static S88MasterRecipe alternativeThreeBranches(String id) {
            S88MasterRecipe recipe = alternative(id);
            ChartBranchingUseCase.addAlternativeBranch(recipe, null, "STEP_1");
            return recipe;
        }

        /** Pick a step inside one branch and press Add step, which grows that branch. */
        static S88MasterRecipe stepInsideAnAlternativeBranch(String id) {
            S88MasterRecipe recipe = alternative(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_1");
            return recipe;
        }

        /** Pick the middle step of a line and press Add optional branch. */
        static S88MasterRecipe optionalBranch(String id) {
            S88MasterRecipe recipe = lineal(id);
            ChartBranchingUseCase.addOptionalBranch(recipe, null, "STEP_1");
            return recipe;
        }

        /** A parallel block put under a step of an alternative branch. */
        static S88MasterRecipe parallelInsideAlternative(String id) {
            S88MasterRecipe recipe = alternativeBranchWithRoom(id);
            ChartBranchingUseCase.nestParallelBranch(recipe, null, "STEP_2");
            return recipe;
        }

        /** A block of turns put under a step of a parallel branch. */
        static S88MasterRecipe alternativeInsideParallel(String id) {
            S88MasterRecipe recipe = parallelBranchWithRoom(id);
            ChartBranchingUseCase.nestAlternativeBranch(recipe, null, "STEP_2");
            return recipe;
        }

        /** A parallel block put under a step of a parallel branch. */
        static S88MasterRecipe parallelInsideParallel(String id) {
            S88MasterRecipe recipe = parallelBranchWithRoom(id);
            ChartBranchingUseCase.nestParallelBranch(recipe, null, "STEP_2");
            return recipe;
        }

        /** A block of turns put under a step of an alternative branch. */
        static S88MasterRecipe alternativeInsideAlternative(String id) {
            S88MasterRecipe recipe = alternativeBranchWithRoom(id);
            ChartBranchingUseCase.nestAlternativeBranch(recipe, null, "STEP_2");
            return recipe;
        }

        /**
         * A branch with two more steps down it, so the middle one has bars of its own to hold a
         * block of its own.
         */
        private static S88MasterRecipe parallelBranchWithRoom(String id) {
            S88MasterRecipe recipe = parallel(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_1");
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_2");
            return recipe;
        }

        /** The same, on an alternative branch. */
        private static S88MasterRecipe alternativeBranchWithRoom(String id) {
            S88MasterRecipe recipe = alternative(id);
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_1");
            BindRecipeStepUseCase.addEmptyStepAfter(recipe, null, "STEP_2");
            return recipe;
        }

        /**
         * A recipe with two charts under the one it starts with, made the way the Chart button makes
         * them.
         */
        static S88MasterRecipe subchartTwoLevels(String id) {
            S88MasterRecipe recipe = newRecipe(id);
            S88RecipeElement tramo = EditProcedureLogicUseCase.insertStepAfter(recipe, null,
                    "BEGIN", "TRAMO", "TRAMO", "T_TRAMO", "TRAMO_CLASS");
            CreateChartUseCase.forStep(recipe, tramo);
            S88RecipeElement paso = EditProcedureLogicUseCase.insertStepAfter(recipe, tramo,
                    "BEGIN", "PASO", "PASO", "T_PASO", "PASO_CLASS");
            CreateChartUseCase.forStep(recipe, paso);
            EditProcedureLogicUseCase.insertStepAfter(recipe, paso, "BEGIN", "PULSO",
                    "PULSO", "T_PULSO", "PULSO_CLASS");
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
            if (chart.findStep(elementId).isPresent()) {
                return this;
            }
            if (recipe.findElement(elementId).isEmpty()) {
                S88RecipeElement element = new S88RecipeElement(elementId, kindOf(elementId));
                if (element.getKind() == S88RecipeElementKind.OPERATION) {
                    element.setEquipmentClassId(elementId + "_CLASS");
                }
                recipe.addRecipeElement(element);
            }
            chart.addStep(new S88ProcedureStep(elementId, elementId));
            return this;
        }

        /** A line from the box of a step to a bar. */
        Chart down(String elementId, String barId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step(elementId));
            link.addTo(S88IdRef.transition(barId));
            chart.addLink(link);
            return this;
        }

        /** A line from a bar to the box of the step that follows it. */
        Chart up(String barId, String elementId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step(elementId));
            chart.addLink(link);
            return this;
        }

        /** One line leaving a box towards two bars, which is the selective split. */
        Chart split(String elementId, String firstBar, String secondBar, S88LinkType type) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step(elementId));
            link.addTo(S88IdRef.transition(firstBar));
            link.addTo(S88IdRef.transition(secondBar));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /**
         * One line leaving a box towards any number of bars, for a split with three or more sides.
         *
         * <p>A split is one line, never one line per side, so a third side is another end on the
         * line the other two sides already share.
         */
        Chart split(String elementId, S88LinkType type, String... bars) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step(elementId));
            for (String bar : bars) {
                link.addTo(S88IdRef.transition(bar));
            }
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
            link.addTo(S88IdRef.step(first));
            link.addTo(S88IdRef.step(second));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /**
         * One line leaving a bar towards any number of boxes, for a parallel split with three or
         * more branches.
         */
        Chart splitFromBar(String barId, S88LinkType type, String... boxes) {
            for (String box : boxes) {
                box(box);
            }
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(barId));
            for (String box : boxes) {
                link.addTo(S88IdRef.step(box));
            }
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /** One line bringing two boxes together onto a bar, which is the parallel convergence. */
        Chart join(String first, String second, String barId, S88LinkType type) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.step(first));
            link.addFrom(S88IdRef.step(second));
            link.addTo(S88IdRef.transition(barId));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /** One line bringing any number of boxes together onto a bar. */
        Chart join(String barId, S88LinkType type, String... boxes) {
            for (String box : boxes) {
                box(box);
            }
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            for (String box : boxes) {
                link.addFrom(S88IdRef.step(box));
            }
            link.addTo(S88IdRef.transition(barId));
            link.setLinkType(type);
            chart.addLink(link);
            return this;
        }

        /**
         * A line straight from one bar to another.
         * <p>
         * A chart with nothing on one side of a split has to draw one of these, which is why the
         * structure that needs it does not hold together.
         */
        Chart barToBar(String fromBar, String toBar) {
            S88ProcedureLink link = new S88ProcedureLink(nextLinkId());
            link.addFrom(S88IdRef.transition(fromBar));
            link.addTo(S88IdRef.transition(toBar));
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
            link.addTo(S88IdRef.step(toElementId));
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
    

    /**
     * A parallel split with three branches.
     * <p>
     * Nothing else builds one of these yet. The Add parallel branch button acts on the line the
     * picked step already stands on, so a third branch opens a second fork beside the first
     * instead of a third side of the same one. Finding N6.
     */
    static S88MasterRecipe parallelWithThreeBranches() {
        Chart chart = new Chart("PARALELA_TRES");
        chart.bar("T_START");
        chart.bar("T_SPLIT");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.down("CHARGE", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", S88LinkType.PARALLEL_DIVERGENT, "HEAT", "COOL", "WASH");
        chart.join("T_MERGE", S88LinkType.PARALLEL_CONVERGENT, "HEAT", "COOL", "WASH");
        chart.up("T_MERGE", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** A selective split with three branches. */
    static S88MasterRecipe selectiveWithThreeBranches() {
        Chart chart = new Chart("SELECTIVA_TRES");
        chart.bar("T_START");
        chart.bar("T_HOT");
        chart.bar("T_COLD");
        chart.bar("T_WARM");
        chart.bar("T_PACKED");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.split("CHARGE", S88LinkType.SERIAL_DIVERGENT, "T_HOT", "T_COLD", "T_WARM");
        chart.up("T_HOT", "HEAT");
        chart.up("T_COLD", "COLD");
        chart.up("T_WARM", "WARM");
        chart.join("T_PACKED", S88LinkType.SERIAL_CONVERGENT, "HEAT", "COLD", "WARM");
        chart.up("T_PACKED", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /**
     * A selective split on one branch of a parallel split.
     * <p>
     * The alternative opens and closes on that one branch, so everything it does stays inside the
     * parallel block that holds it.
     */
    static S88MasterRecipe selectiveInsideParallel() {
        Chart chart = new Chart("SELECTIVA_EN_PARALELA");
        chart.bar("T_START");
        chart.bar("T_SPLIT");
        chart.bar("T_DONE");
        chart.bar("T_SKIPPED");
        chart.bar("T_BRANCH_DONE");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.down("CHARGE", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", S88LinkType.PARALLEL_DIVERGENT, "HEAT", "STIR");
        chart.split("HEAT", S88LinkType.SERIAL_DIVERGENT, "T_DONE", "T_SKIPPED");
        chart.up("T_DONE", "HEAT_DONE");
        chart.up("T_SKIPPED", "HEAT_SKIPPED");
        chart.join("T_BRANCH_DONE", S88LinkType.SERIAL_CONVERGENT, "HEAT_DONE", "HEAT_SKIPPED");
        chart.up("T_BRANCH_DONE", "HEAT_JOINED");
        chart.join("T_MERGE", S88LinkType.PARALLEL_CONVERGENT, "HEAT_JOINED", "STIR");
        chart.up("T_MERGE", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** A parallel split on one branch of another parallel split. */
    static S88MasterRecipe parallelInsideParallel() {
        Chart chart = new Chart("PARALELA_EN_PARALELA");
        chart.bar("T_START");
        chart.bar("T_SPLIT");
        chart.bar("T_SPLIT_INNER");
        chart.bar("T_MERGE_INNER");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.down("CHARGE", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", S88LinkType.PARALLEL_DIVERGENT, "HEAT", "STIR");
        chart.down("HEAT", "T_SPLIT_INNER");
        chart.splitFromBar("T_SPLIT_INNER", S88LinkType.PARALLEL_DIVERGENT, "ADD", "SPRAY");
        chart.join("T_MERGE_INNER", S88LinkType.PARALLEL_CONVERGENT, "ADD", "SPRAY");
        chart.up("T_MERGE_INNER", "INNER_DONE");
        chart.join("T_MERGE", S88LinkType.PARALLEL_CONVERGENT, "INNER_DONE", "STIR");
        chart.up("T_MERGE", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /** A selective split on one branch of another selective split. */
    static S88MasterRecipe selectiveInsideSelective() {
        Chart chart = new Chart("SELECTIVA_EN_SELECTIVA");
        chart.bar("T_START");
        chart.bar("T_HOT");
        chart.bar("T_COLD");
        chart.bar("T_HOT_DONE");
        chart.bar("T_SKIPPED");
        chart.bar("T_INNER_DONE");
        chart.bar("T_PACKED");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.split("CHARGE", S88LinkType.SERIAL_DIVERGENT, "T_HOT", "T_COLD");
        chart.up("T_HOT", "HEAT");
        chart.split("HEAT", S88LinkType.SERIAL_DIVERGENT, "T_HOT_DONE", "T_SKIPPED");
        chart.up("T_HOT_DONE", "HEAT_DONE");
        chart.up("T_SKIPPED", "HEAT_SKIPPED");
        chart.join("T_INNER_DONE", S88LinkType.SERIAL_CONVERGENT, "HEAT_DONE", "HEAT_SKIPPED");
        chart.up("T_INNER_DONE", "INNER_DONE");
        chart.up("T_COLD", "COLD");
        chart.join("T_PACKED", S88LinkType.SERIAL_CONVERGENT, "INNER_DONE", "COLD");
        chart.up("T_PACKED", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /**
     * A branch that is there so it can be taken or jumped.
     * <p>
     * One side of the split carries the work, the other goes straight to the bar the flow joins
     * at. The jumped side is a line from one bar to another, which is allowed only when it skips a
     * step that the other side feeds.
     */
    static S88MasterRecipe optionalBranch() {
        Chart chart = new Chart("RAMA_OPCIONAL");
        chart.bar("T_START");
        chart.bar("T_WORK");
        chart.bar("T_SKIP");
        chart.bar("T_JOIN");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.split("CHARGE", S88LinkType.SERIAL_DIVERGENT, "T_WORK", "T_SKIP");
        chart.up("T_WORK", "WORK");
        chart.down("WORK", "T_JOIN");
        chart.barToBar("T_SKIP", "T_JOIN");
        chart.up("T_JOIN", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /**
     * Two bars joined with no step between them.
     * <p>
     * A chart cannot carry this, because the drawing has no line that does not cross. It is what
     * is left of a broken chart, so the structural tests have one to refuse.
     */
    static S88MasterRecipe barsJoinedWithNoStep() {
        Chart chart = new Chart("BARRAS_UNIDAS");
        chart.bar("T_START");
        chart.bar("T_LEFT");
        chart.bar("T_RIGHT");
        chart.bar("T_END");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "CHARGE");
        chart.down("CHARGE", "T_LEFT");
        chart.barToBar("T_LEFT", "T_RIGHT");
        chart.up("T_RIGHT", "PACK");
        chart.down("PACK", "T_END");
        return chart.finishFrom("T_END");
    }

    /**
     * A loop that goes back round a whole parallel block.
     * <p>
     * The flow leaves the end of the block, splits again, and one side goes back to the step before
     * the block started. A loop that reached into one branch of a parallel block would be outside
     * the rule, so it goes round the whole of it.
     */
    static S88MasterRecipe loopAroundParallel() {
        Chart chart = new Chart("BUCLE_CON_PARALELA");
        chart.bar("T_START");
        chart.bar("T_SPLIT");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.bar("T_AGAIN");
        chart.down("BEGIN", "T_START");
        chart.up("T_START", "MIX");
        chart.down("MIX", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", S88LinkType.PARALLEL_DIVERGENT, "HEAT", "COOL");
        chart.join("T_MERGE", S88LinkType.PARALLEL_CONVERGENT, "HEAT", "COOL");
        chart.up("T_MERGE", "PACK");
        chart.split("PACK", S88LinkType.SERIAL_DIVERGENT, "T_END", "T_AGAIN");
        chart.up("T_AGAIN", "MIX");
        return chart.finishFrom("T_END");
    }

    /**
     * A recipe with two charts under the one it starts with.
     * <p>
     * The top chart holds a unit procedure, the chart of that one holds an operation, and the
     * chart of the operation is the last one there is. Nothing else here draws a subchart, so this
     * one is built through the same use cases a press of the Chart button uses.
     */
    static S88MasterRecipe twoLevelsOfSubchart() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("SUB_GRAFICOS",
                S88RecipeKind.CLASS);
        S88RecipeElement tramo = EditProcedureLogicUseCase.insertStepAfter(recipe, null,
                "BEGIN", "TRAMO", "TRAMO", "T_TRAMO", "TRAMO_CLASS");
        CreateChartUseCase.forStep(recipe, tramo);
        S88RecipeElement paso = EditProcedureLogicUseCase.insertStepAfter(recipe, tramo,
                "BEGIN", "PASO", "PASO", "T_PASO", "PASO_CLASS");
        CreateChartUseCase.forStep(recipe, paso);
        EditProcedureLogicUseCase.insertStepAfter(recipe, paso, "BEGIN", "PULSO",
                "PULSO", "T_PULSO", "PULSO_CLASS");
        return recipe;
    }
}
