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

import java.util.List;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of a chart, read back out of it.
 *
 * <p>Every fixture here is written out by hand rather than made through the buttons, so what is
 * being tested is what a chart with that shape says about itself and not what a button does.
 */
class StructureQueriesTest {

    /** A chart with one line from the start to the stop, through one bar. */
    static S88ProcedureLogic linear() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("LINEAL", S88RecipeKind.CLASS);
        return recipe.getProcedureLogic();
    }

    /**
     * A chart where the flow splits and both halves run at once.
     *
     * <p>One line leaves a bar towards two boxes, and one line brings them back together.
     */
    static S88ProcedureLogic parallel() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("PARALELA", S88RecipeKind.CLASS);
        S88RecipeElement tramo = CreateRecipeElementUseCase.execute(recipe, null, "TRAMO",
                S88RecipeElementKind.UNIT_PROCEDURE);
        tramo.setEquipmentClassId("TRAMO_CLASS");
        Chart chart = new Chart(recipe, tramo);
        chart.box("BEGIN");
        chart.bar("T_START");
        chart.bar("T_SPLIT");
        chart.bar("T_MERGE");
        chart.bar("T_END");
        chart.fromBoxToBar("BEGIN", "T_START");
        chart.fromBarToBox("T_START", "CHARGE");
        chart.fromBoxToBar("CHARGE", "T_SPLIT");
        chart.splitFromBar("T_SPLIT", S88LinkType.PARALLEL_DIVERGENT, "HEAT", "COOL");
        chart.join("T_MERGE", S88LinkType.PARALLEL_CONVERGENT, "HEAT", "COOL");
        chart.fromBarToBox("T_MERGE", "PACK");
        chart.fromBoxToBar("PACK", "T_END");
        chart.finishFromBar("T_END");
        return chart.logic;
    }

    /**
     * A chart where the flow goes one way or the other.
     *
     * <p>One line leaves a box towards two bars, and each bar has its own step after it.
     */
    static S88ProcedureLogic selective() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("SELECTIVA", S88RecipeKind.CLASS);
        S88RecipeElement tramo = CreateRecipeElementUseCase.execute(recipe, null, "TRAMO",
                S88RecipeElementKind.UNIT_PROCEDURE);
        tramo.setEquipmentClassId("TRAMO_CLASS");
        Chart chart = new Chart(recipe, tramo);
        chart.box("BEGIN");
        chart.bar("T_START");
        chart.bar("T_HOT");
        chart.bar("T_COLD");
        chart.bar("T_PACKED");
        chart.bar("T_END");
        chart.fromBoxToBar("BEGIN", "T_START");
        chart.fromBarToBox("T_START", "CHARGE");
        chart.split("CHARGE", S88LinkType.SERIAL_DIVERGENT, "T_HOT", "T_COLD");
        chart.fromBarToBox("T_HOT", "HEAT");
        chart.fromBarToBox("T_COLD", "COLD");
        chart.join("T_PACKED", S88LinkType.SERIAL_CONVERGENT, "HEAT", "COLD");
        chart.fromBarToBox("T_PACKED", "PACK");
        chart.fromBoxToBar("PACK", "T_END");
        chart.finishFromBar("T_END");
        return chart.logic;
    }

    /**
     * A chart where a parallel block is inside a selective one.
     *
     * <p>One branch of the selective split runs in step with itself.
     */
    static S88ProcedureLogic parallelInsideSelective() {
        S88MasterRecipe recipe = CreateMasterRecipeUseCase.execute("ANIDADA", S88RecipeKind.CLASS);
        S88RecipeElement tramo = CreateRecipeElementUseCase.execute(recipe, null, "TRAMO",
                S88RecipeElementKind.UNIT_PROCEDURE);
        tramo.setEquipmentClassId("TRAMO_CLASS");
        Chart chart = new Chart(recipe, tramo);
        chart.box("BEGIN");
        chart.bar("T_START");
        chart.bar("T_HOT");
        chart.bar("T_COLD");
        chart.bar("T_SPLIT_INNER");
        chart.bar("T_MERGE_INNER");
        chart.bar("T_HOT_DONE");
        chart.bar("T_PACKED");
        chart.bar("T_END");
        chart.fromBoxToBar("BEGIN", "T_START");
        chart.fromBarToBox("T_START", "CHARGE");
        chart.split("CHARGE", S88LinkType.SERIAL_DIVERGENT, "T_HOT", "T_COLD");
        chart.fromBarToBox("T_HOT", "HEAT");
        chart.fromBoxToBar("HEAT", "T_SPLIT_INNER");
        chart.splitFromBar("T_SPLIT_INNER", S88LinkType.PARALLEL_DIVERGENT, "ADD", "SPRAY");
        chart.join("T_MERGE_INNER", S88LinkType.PARALLEL_CONVERGENT, "ADD", "SPRAY");
        chart.fromBarToBox("T_MERGE_INNER", "HEAT_DONE");
        chart.fromBarToBox("T_COLD", "COLD");
        chart.join("T_PACKED", S88LinkType.SERIAL_CONVERGENT, "HEAT_DONE", "COLD");
        chart.fromBarToBox("T_PACKED", "PACK");
        chart.fromBoxToBar("PACK", "T_END");
        chart.finishFromBar("T_END");
        return chart.logic;
    }

    /**
     * A chart with a line from one box straight to another, which the drawing cannot follow.
     *
     * <p>That is what a branch leaves behind when it is taken off the chart without the line that
     * held it being put back together.
     */
    private static S88ProcedureLogic withALineThatDoesNotCross() {
        S88ProcedureLogic logic = parallel();
        S88ProcedureLink join = logic.getLinks().stream()
                .filter(link -> link.isConvergent())
                .findFirst()
                .orElseThrow();
        logic.removeLink(join);
        S88ProcedureLink straight = new S88ProcedureLink("L_STRAIGHT");
        straight.addFrom(S88IdRef.step("HEAT"));
        straight.addTo(S88IdRef.step("COOL"));
        logic.addLink(straight);
        return logic;
    }

    /** A chart with no blocks anywhere, so every word about blocks is about it being on none. */
    @Test
    void aChartWithNoBlocksSaysEveryWordAboutItIsEmpty() {
        S88ProcedureLogic logic = linear();

        assertNull(StructureQueries.blockOf(logic, "HEAT"), "and no box is on a block");
        assertNull(StructureQueries.blockOf(logic, "T_HEAT"), "and no bar is on a block");
        assertFalse(StructureQueries.isInsideParallel(logic, "HEAT"), "and nothing runs in step");
        assertFalse(StructureQueries.isInsideSelective(logic, "HEAT"), "and nothing goes one way");
        assertTrue(StructureQueries.enclosingBlocks(logic, "HEAT").isEmpty(),
                "and there is no block around it to be inside of");
    }

    /**
     * A box on a parallel block is inside it, and so is the bar it leaves from.
     *
     * <p>The block is one thing with two sides: the line that splits and the line that joins, so
     * both ends of it say where the flow runs in step.
     */
    @Test
    void aBoxOnAParallelBlockIsInsideItFromBothEnds() {
        S88ProcedureLogic logic = parallel();

        assertTrue(StructureQueries.isInsideParallel(logic, "HEAT"),
                "and the box the split reaches is inside it");
        assertTrue(StructureQueries.isInsideParallel(logic, "COOL"),
                "and so is the other one, from the same split");
        assertTrue(StructureQueries.isInsideParallel(logic, "T_SPLIT"),
                "and the bar the flow splits at is inside it");
        assertFalse(StructureQueries.isInsideParallel(logic, "T_MERGE"),
                "and the bar the flow is held in step at is the end of the block, not inside it");
        assertFalse(StructureQueries.isInsideParallel(logic, "PACK"),
                "and after the block is joined, the flow is on no block again");
        assertEquals(S88LinkType.PARALLEL_DIVERGENT,
                StructureQueries.blockOf(logic, "HEAT"),
                "and the kind it says is the parallel one");
    }

    /** A box on a selective block is inside it, and a parallel one is not. */
    @Test
    void aBoxOnASelectiveBlockIsInsideItAndNotTheParallelOne() {
        S88ProcedureLogic logic = selective();

        assertTrue(StructureQueries.isInsideSelective(logic, "HEAT"),
                "and the box the split reaches is inside it");
        assertTrue(StructureQueries.isInsideSelective(logic, "COLD"),
                "and so is the other one, from the same split");
        assertFalse(StructureQueries.isInsideSelective(logic, "PACK"),
                "and after the block is joined, the flow is on no block again");
        assertFalse(StructureQueries.isInsideParallel(logic, "HEAT"),
                "and it does not run in step, because only one side is taken");
    }

    /**
     * A box inside a block that is inside another one is held by both, the innermost first.
     *
     * <p>The parallel block opens and closes on one branch of the selective one, so the box on that
     * branch is on both.
     */
    @Test
    void aBoxInsideABlockInsideABlockIsHeldByBoth() {
        S88ProcedureLogic logic = parallelInsideSelective();

        List<S88LinkType> held = StructureQueries.enclosingBlocks(logic, "ADD");

        assertTrue(held.contains(S88LinkType.PARALLEL_DIVERGENT),
                "and the box the inner split reaches is inside the parallel one");
        assertTrue(held.contains(S88LinkType.SERIAL_DIVERGENT),
                "and inside the selective one that holds the whole branch");
        assertEquals(S88LinkType.SERIAL_DIVERGENT, held.get(held.size() - 1),
                "and the outermost one is the selective, which is the one that was opened first");
        assertEquals(2, held.size(),
                "and there is one block for each of the two it is inside");
        assertTrue(StructureQueries.isInsideParallel(logic, "ADD"),
                "and on the inner block, so what is added inside it runs in step");
    }

    /** A line from one box straight to another is one the drawing cannot follow. */
    @Test
    void aLineThatDoesNotCrossABarIsSaidOutLoud() {
        S88ProcedureLogic logic = withALineThatDoesNotCross();

        assertFalse(StructureQueries.linesThatDoNotCross(logic).isEmpty(),
                "and the one that lost an end is still there with only one side on it");
        assertTrue(StructureQueries.linesThatDoNotCross(parallel()).isEmpty(),
                "and a chart that holds together has none of them");
    }

    /**
     * A line with an end outside the chart is not checked for crossing a bar.
     *
     * <p>Nothing is known about what alternates with a step of another part of the process.
     */
    @Test
    void aLineLeavingTheChartIsLeftOutOfTheCrossingCheck() {
        S88ProcedureLogic logic = linear();
        S88ProcedureLink out = new S88ProcedureLink("L_OUT");
        out.addFrom(S88IdRef.step("BEGIN"));
        out.addTo(new S88IdRef("OTHER_PART", S88IdRefType.STEP, S88IdScope.EXTERNAL));
        logic.addLink(out);
        S88ProcedureLink back = new S88ProcedureLink("L_BACK");
        back.addFrom(new S88IdRef("OTHER_PART", S88IdRefType.STEP, S88IdScope.EXTERNAL));
        back.addTo(S88IdRef.step("END"));
        logic.addLink(back);

        assertTrue(StructureQueries.linesThatDoNotCross(logic).isEmpty(),
                "and the two ends that leave the chart are left out of the check");
    }

    /** A loop connection is the bar the flow comes back by, and the lines that meet at it. */
    @Test
    void aLoopConnectionIsFoundFromItsBarAndFromItsLines() {
        S88ProcedureLogic logic = withALoopBackToHeat();

        assertEquals("T_BACK", StructureQueries.loopTransitionOf(logic, "T_BACK"),
                "and the bar it comes back by is one");
        assertEquals("T_BACK", StructureQueries.loopTransitionOf(logic, "L_BACK_OUT"),
                "and the line that leaves the step for it");
        assertEquals("T_BACK", StructureQueries.loopTransitionOf(logic, "L_BACK_IN"),
                "and the line that arrives back at the step");
        assertNull(StructureQueries.loopTransitionOf(logic, "T_END"),
                "and a bar of the ordinary flow is not one");
        assertNull(StructureQueries.loopTransitionOf(logic, "HEAT"),
                "and neither is a step");
    }

    /**
     * A chart with a loop that leaves the last step and comes back to one before it.
     *
     * <p>The flow leaves {@code PACK}, waits at a bar of its own and arrives back at {@code HEAT}.
     * {@code PACK} has another way on and {@code HEAT} another way in, so the bar is a loop
     * connection and not one of the bars of the ordinary flow.
     */
    static S88ProcedureLogic withALoopBackToHeat() {
        S88ProcedureLogic logic = parallel();
        logic.addTransition(new S88ProcedureTransition("T_BACK", null));
        S88ProcedureLink out = new S88ProcedureLink("L_BACK_OUT");
        out.addFrom(S88IdRef.step("PACK"));
        out.addTo(S88IdRef.transition("T_BACK"));
        logic.addLink(out);
        S88ProcedureLink in = new S88ProcedureLink("L_BACK_IN");
        in.addFrom(S88IdRef.transition("T_BACK"));
        in.addTo(S88IdRef.step("HEAT"));
        logic.addLink(in);
        return logic;
    }

    /**
     * A chart and its boxes under construction.
     *
     * <p>Every box of the chart is worked on by a step of the recipe of the same name, the way the
     * editor keeps them, so what is read back out of the chart is what a real one holds.
     */
    private static final class Chart {

        private final S88ProcedureLogic logic;
        private final S88MasterRecipe recipe;

        Chart(S88MasterRecipe recipe, S88RecipeElement owner) {
            this.recipe = recipe;
            this.logic = owner.getProcedureLogic();
            List.copyOf(logic.getLinks()).forEach(logic::removeLink);
            List.copyOf(logic.getTransitions()).forEach(logic::removeTransition);
            List.copyOf(logic.getSteps()).forEach(logic::removeStep);
        }

        void box(String elementId) {
            if (logic.findStep(elementId).isPresent()) {
                return;
            }
            if (recipe.findElement(elementId).isEmpty()) {
                CreateRecipeElementUseCase.execute(recipe, ownerOf(), elementId,
                        S88RecipeElementKind.OPERATION);
            }
            logic.addStep(new S88ProcedureStep(elementId, elementId));
        }

        void bar(String barId) {
            logic.addTransition(new S88ProcedureTransition(barId, null));
        }

        void fromBoxToBar(String elementId, String barId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            link.addFrom(S88IdRef.step(elementId));
            link.addTo(S88IdRef.transition(barId));
            logic.addLink(link);
        }

        void fromBarToBox(String barId, String elementId) {
            box(elementId);
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step(elementId));
            logic.addLink(link);
        }

        void split(String elementId, S88LinkType kind, String... barIds) {
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            link.addFrom(S88IdRef.step(elementId));
            for (String barId : barIds) {
                link.addTo(S88IdRef.transition(barId));
            }
            link.setLinkType(kind);
            logic.addLink(link);
        }

        void splitFromBar(String barId, S88LinkType kind, String... elementIds) {
            for (String elementId : elementIds) {
                box(elementId);
            }
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            link.addFrom(S88IdRef.transition(barId));
            for (String elementId : elementIds) {
                link.addTo(S88IdRef.step(elementId));
            }
            link.setLinkType(kind);
            logic.addLink(link);
        }

        void join(String barId, S88LinkType kind, String... elementIds) {
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            for (String elementId : elementIds) {
                link.addFrom(S88IdRef.step(elementId));
            }
            link.addTo(S88IdRef.transition(barId));
            link.setLinkType(kind);
            logic.addLink(link);
        }

        void finishFromBar(String barId) {
            S88ProcedureLink link = new S88ProcedureLink(nextId());
            link.addFrom(S88IdRef.transition(barId));
            link.addTo(S88IdRef.step("END"));
            logic.addLink(link);
            logic.addStep(new S88ProcedureStep("END", "END"));
        }

        private S88RecipeElement ownerOf() {
            return recipe.findElement("TRAMO").orElseThrow();
        }

        private String nextId() {
            return "L" + (logic.getLinks().size() + 1);
        }
    }
}
