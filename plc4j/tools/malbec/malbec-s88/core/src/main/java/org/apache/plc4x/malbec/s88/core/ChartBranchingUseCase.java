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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.plc4x.malbec.s88.core;

import java.util.ArrayList;
import java.util.List;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Branches a chart at the line entering a step, and grows by one branch each time it is called.
 *
 * <p>A parallel branch is a step. A branch is a bar and then a step, so there is somewhere to say
 * what that branch waits on.
 *
 * @see EditProcedureLogicUseCase#insertStepAfter(S88Recipe, S88RecipeElement, String, String,
 *         String, String, String)
 */
public final class ChartBranchingUseCase {

    private static final String STEP_STEM = "STEP";
    private static final String BAR_STEM = "T_BRANCH";

    private ChartBranchingUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Adds a branch that runs at the same time as the one that is already there.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step the branch is made at
     * @return the step that was added
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is where the flow starts or stops, or is the
     *                                  source of a branch of the other kind
     */
    public static S88RecipeElement addParallelBranch(S88Recipe recipe, S88RecipeElement step,
                                                      String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        requireTheSameKindOfBlock(chart, boxId, true);
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        if (!takesAnotherArrival(entering) || !takesAnotherDeparture(leaving)) {
            throw new IllegalStateException("Step '" + boxId + "' is on a branch of the other kind, so"
                    + " there is no one line to widen into a parallel branch.");
        }
        String box = newStep(recipe, step, chart);

        put(chart, entering, widenArrival(entering, S88IdRef.step(box), S88LinkType.PARALLEL_DIVERGENT));
        put(chart, leaving, widenDeparture(leaving, S88IdRef.step(box), S88LinkType.PARALLEL_CONVERGENT));

        announce(recipe);
        return recipe.findElement(elementOf(box)).orElseThrow();
    }

    /**
     * Adds a branch of which only one is taken, each with a bar to say what it waits on.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step the branch is made at
     * @return the step that was added
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is where the flow starts or stops, or is
     *                                  reached by more than one bar
     */
    public static S88RecipeElement addAlternativeBranch(S88Recipe recipe, S88RecipeElement step,
                                                         String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        requireTheSameKindOfBlock(chart, boxId, false);
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        if (entering.getTo().size() != 1 || leaving.getTo().size() != 1) {
            throw new IllegalStateException("Step '" + boxId + "' is already fed by a branch point or"
                    + " already goes to more than one place, so there is no one line to widen.");
        }
        if (entering.getFrom().size() != 1) {
            throw new IllegalStateException(("Step '" + boxId + "' is reached by a line the flow joins"
            + " on, so there is no one branch to open another beside."));
        }
        String barBehind = entering.getFrom().getFirst().getValue();
        S88ProcedureLink feeding = arrivingAt(chart, barBehind);
        String barAfter = leaving.getTo().getFirst().getValue();
        S88ProcedureLink carryingOn = leavingFrom(chart, barAfter);

        String branchBar = newBar(recipe, step, chart);
        String finishBar = newBar(recipe, step, chart);
        String box = newStep(recipe, step, chart);

        chart.addLink(barToStep(branchBar + "_TO_" + box, branchBar, box));
        chart.addLink(stepToBar(box + "_TO_" + finishBar, box, finishBar));
        put(chart, feeding, widenArrival(feeding, S88IdRef.transition(branchBar), S88LinkType.SERIAL_DIVERGENT));
        put(chart, carryingOn, widenDeparture(carryingOn, S88IdRef.transition(finishBar), S88LinkType.SERIAL_CONVERGENT));

        announce(recipe);
        return recipe.findElement(elementOf(box)).orElseThrow();
    }

    private static S88ProcedureLogic chartToBranch(S88Recipe recipe, S88RecipeElement step,
                                                   String boxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to branch.");
        }
        S88ProcedureStep box = chart.findStep(boxId).orElseThrow(
                () -> new IllegalArgumentException("This chart has no box called '" + boxId + "'."));
        S88RecipeElementKind kind = kindOf(recipe, box.getRecipeElementId());
        if (kind == S88RecipeElementKind.BEGIN || kind == S88RecipeElementKind.END) {
            throw new IllegalStateException("Step '" + boxId + "' is where the flow "
                    + (kind == S88RecipeElementKind.BEGIN ? "starts" : "stops")
                    + ", so there is nothing to branch.");
        }
        return chart;
    }

    /**
     * Whether a branch of the given kind can be made beside the picked step.
     *
     * <p>A step already inside a block can only be given another side of that same block. Widening
     * the line it stands on with a side of the other kind would say the branches run at once when
     * they go one at a time, or the other way round, so it is refused and the step is offered the
     * button that puts a block of its own under it instead.
     *
     * @param chart  the chart the step is on
     * @param boxId  box of the picked step
     * @param wanted whether the branch asked for is the parallel kind
     * @throws IllegalStateException when the step is inside a block of the other kind
     */
    private static void requireTheSameKindOfBlock(S88ProcedureLogic chart, String boxId,
                                                  boolean wanted) {
        S88LinkType found = StructureQueries.blockOf(chart, boxId);
        if (found == null) {
            return;
        }
        if (found.isParallel() != wanted) {
            throw new IllegalStateException("Step '" + boxId + "' is inside a "
                    + (found.isParallel() ? "parallel" : "selective") + " block, and a "
                    + (wanted ? "parallel" : "selective") + " branch cannot be made beside it"
                    + " without changing what that block is. Use "
                    + (wanted ? "Parallel branch here" : "Selective branch here")
                    + " to put a " + (wanted ? "parallel" : "selective") + " block under it.");
        }
    }

    private static S88RecipeElementKind kindOf(S88Recipe recipe, String elementId) {
        return recipe.findElement(elementId)
                .map(S88RecipeElement::getKind)
                .orElseThrow(() -> new IllegalArgumentException(
                        "This recipe has no step called '" + elementId + "'."));
    }

    private static S88ProcedureLink arrivingAt(S88ProcedureLogic chart, String id) {
        List<S88ProcedureLink> found = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                found.add(link);
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException("Box '" + id + "' has " + found.size() + " lines"
                    + " arriving at it, so there is no one line to branch from.");
        }
        return found.getFirst();
    }

    private static S88ProcedureLink leavingFrom(S88ProcedureLogic chart, String id) {
        List<S88ProcedureLink> found = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                found.add(link);
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException("Box '" + id + "' has " + found.size() + " lines"
                    + " leaving it, so there is no one line to branch from.");
        }
        return found.getFirst();
    }

    /** Whether a line can take one more arrival without changing what kind of line it is. */
    private static boolean takesAnotherArrival(S88ProcedureLink link) {
        return link.getTo().size() == 1 || isParallel(link);
    }

    /** Whether a line can take one more departure without changing what kind of line it is. */
    private static boolean takesAnotherDeparture(S88ProcedureLink link) {
        return link.getFrom().size() == 1 || isParallel(link);
    }

    private static boolean isParallel(S88ProcedureLink link) {
        return link.getLinkType() != null && link.getLinkType().isParallel();
    }

    private static S88ProcedureLink widenArrival(S88ProcedureLink link, S88IdRef extraEnd,
                                                 S88LinkType type) {
        S88ProcedureLink wider = new S88ProcedureLink(link.getId());
        link.getFrom().forEach(wider::addFrom);
        link.getTo().forEach(wider::addTo);
        wider.addTo(extraEnd);
        wider.setLinkType(type);
        return wider;
    }

    /**
     * Puts a parallel block under the picked step, inside the branch it is already in.
     *
     * <p>The step keeps the branch it is on and becomes the first side of a block of its own, with
     * a new empty step beside it, and both come back together where the step used to lead to. The
     * block opens and closes inside the branch, so everything it does stays there.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step the block goes under
     * @return the step that was added
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is where the flow starts or stops
     */
    public static S88RecipeElement nestParallelBranch(S88Recipe recipe, S88RecipeElement step,
                                                     String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        return addParallel(recipe, step, chart, boxId);
    }

    /**
     * Puts a block under the picked step inside the branch it is in, of which only one side is
     * taken.
     *
     * <p>The step keeps its own bars and becomes the first side, with a new empty step and a bar of
     * its own beside it, and both come back together on a bar they share. Each side has a bar at the
     * start and the end, which is what makes it selective rather than parallel.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step the block goes under
     * @return the step that was added
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is where the flow starts or stops, or has no
     *                                  bars of its own to widen
     */
    public static S88RecipeElement nestAlternativeBranch(S88Recipe recipe, S88RecipeElement step,
                                                         String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        return addAlternative(recipe, step, chart, boxId);
    }

    /**
     * A parallel block beside the step, made by widening the two lines the step already has.
     *
     * <p>A step that has bars of its own is arrived at by one line and leaves by one line, and those
     * are the lines the block widens. A step that is a whole branch has neither: its arrival is the
     * block outside splitting and its departure is that block coming back together, and widening
     * those would change the block outside rather than put one under the step.
     *
     * @param recipe recipe being edited
     * @param step   step whose chart the block goes on, {@code null} for the recipe's own
     * @param chart  that chart
     * @param boxId  box of the step the block goes under
     * @return the step that was added
     */
    private static S88RecipeElement addParallel(S88Recipe recipe, S88RecipeElement step,
                                                S88ProcedureLogic chart, String boxId) {
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        requireBarsOfItsOwn(boxId, entering, leaving);
        String box = newStep(recipe, step, chart);
        put(chart, entering, widenArrival(entering, S88IdRef.step(box),
                S88LinkType.PARALLEL_DIVERGENT));
        put(chart, leaving, widenDeparture(leaving, S88IdRef.step(box),
                S88LinkType.PARALLEL_CONVERGENT));
        announce(recipe);
        return recipe.findElement(elementOf(box)).orElseThrow();
    }

    /**
     * A selective block beside the step, made by widening the lines around the step and giving the
     * new side a bar of its own at each end.
     *
     * @param recipe recipe being edited
     * @param step   step whose chart the block goes on, {@code null} for the recipe's own
     * @param chart  that chart
     * @param boxId  box of the step the block goes under
     * @return the step that was added
     */
    private static S88RecipeElement addAlternative(S88Recipe recipe, S88RecipeElement step,
                                                   S88ProcedureLogic chart, String boxId) {
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        requireBarsOfItsOwn(boxId, entering, leaving);
        if (entering.getTo().size() != 1 || leaving.getTo().size() != 1) {
            throw new IllegalStateException("Step '" + boxId + "' is already fed by a branch point"
                    + " or already goes to more than one place, so there is no one line to widen.");
        }
        String barBehind = entering.getFrom().getFirst().getValue();
        S88ProcedureLink feeding = arrivingAt(chart, barBehind);
        String barAfter = leaving.getTo().getFirst().getValue();
        S88ProcedureLink carryingOn = leavingFrom(chart, barAfter);

        String branchBar = newBar(recipe, step, chart);
        String finishBar = newBar(recipe, step, chart);
        String box = newStep(recipe, step, chart);

        chart.addLink(barToStep(branchBar + "_TO_" + box, branchBar, box));
        chart.addLink(stepToBar(box + "_TO_" + finishBar, box, finishBar));
        put(chart, feeding, widenArrival(feeding, S88IdRef.transition(branchBar),
                S88LinkType.SERIAL_DIVERGENT));
        put(chart, carryingOn, widenDeparture(carryingOn, S88IdRef.transition(finishBar),
                S88LinkType.SERIAL_CONVERGENT));

        announce(recipe);
        return recipe.findElement(elementOf(box)).orElseThrow();
    }

    /**
     * Whether the step has one line arriving at it and one leaving it, which is what a block can be
     * put under.
     *
     * <p>A step that is a whole branch of a block has neither. It is arrived at by the line the
     * block splits on and leaves by the line the block comes back together on, so there is no flow
     * of its own to split in two.
     *
     * @param boxId    box of the picked step, named in what is said
     * @param entering the line that arrives at the step
     * @param leaving  the line that leaves the step
     */
    private static void requireBarsOfItsOwn(String boxId, S88ProcedureLink entering,
                                            S88ProcedureLink leaving) {
        if (entering == null || leaving == null) {
            throw new IllegalStateException("Step '" + boxId + "' does not sit between two bars, so"
                    + " there is no flow to put a block under.");
        }
        if (entering.isDivergent() || entering.isConvergent()) {
            throw new IllegalStateException("Step '" + boxId + "' is a whole branch of the block it"
                    + " is in, so there is no flow of its own to put a block under. Put a step beside"
                    + " it with Add step instead.");
        }
        if (leaving.isDivergent() || leaving.isConvergent()) {
            throw new IllegalStateException("Step '" + boxId + "' is where the block it is in comes"
                    + " back together, so there is no flow of its own to put a block under. Put a"
                    + " step beside it with Add step instead.");
        }
    }

    /** The kind of line a block of this kind opens with. */
    private static S88LinkType toSplitType(boolean parallel) {
        return parallel ? S88LinkType.PARALLEL_DIVERGENT : S88LinkType.SERIAL_DIVERGENT;
    }

    /** The kind of line a block of this kind closes with. */
    private static S88LinkType toJoinType(boolean parallel) {
        return parallel ? S88LinkType.PARALLEL_CONVERGENT : S88LinkType.SERIAL_CONVERGENT;
    }

    private static S88ProcedureLink widenDeparture(S88ProcedureLink link, S88IdRef extraEnd,
                                                   S88LinkType type) {
        S88ProcedureLink wider = new S88ProcedureLink(link.getId());
        link.getFrom().forEach(wider::addFrom);
        link.getTo().forEach(wider::addTo);
        wider.addFrom(extraEnd);
        wider.setLinkType(type);
        return wider;
    }

    private static S88ProcedureLink barToStep(String id, String bar, String box) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        line.addFrom(S88IdRef.transition(bar));
        line.addTo(S88IdRef.step(box));
        return line;
    }

    private static S88ProcedureLink stepToBar(String id, String box, String bar) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        line.addFrom(S88IdRef.step(box));
        line.addTo(S88IdRef.transition(bar));
        return line;
    }

    private static void put(S88ProcedureLogic chart, S88ProcedureLink was, S88ProcedureLink now) {
        chart.removeLink(was);
        chart.addLink(now);
    }

    private static String newStep(S88Recipe recipe, S88RecipeElement step,
                                  S88ProcedureLogic chart) {
        String elementId = freeName(recipe, chart, STEP_STEM);
        String box = "BOX_" + elementId;
        CreateRecipeElementUseCase.forEquipmentClass(recipe, step, elementId,
                RecipeLevels.levelOf(step), null);
        EditProcedureLogicUseCase.addStep(recipe, step, box, elementId);
        return box;
    }

    private static String newBar(S88Recipe recipe, S88RecipeElement step,
                                 S88ProcedureLogic chart) {
        String barId = freeName(recipe, chart, BAR_STEM);
        EditProcedureLogicUseCase.addTransition(recipe, step, barId, null);
        return barId;
    }

    private static String elementOf(String boxId) {
        return boxId.startsWith("BOX_") ? boxId.substring("BOX_".length()) : boxId;
    }

    private static String freeName(S88Recipe recipe, S88ProcedureLogic chart, String stem) {
        String candidate = stem;
        int more = 1;
        while (!isFree(recipe, chart, candidate)) {
            candidate = stem + "_" + more++;
        }
        return candidate;
    }

    private static boolean isFree(S88Recipe recipe, S88ProcedureLogic chart, String candidate) {
        return takesNoName(recipe, chart, candidate)
            && takesNoName(recipe, chart, "BOX_" + candidate)
            && takesNoName(recipe, chart, "T_" + candidate);
    }

    private static boolean takesNoName(S88Recipe recipe, S88ProcedureLogic chart, String name) {
        return chart.findStep(name).isEmpty()
            && chart.findTransition(name).isEmpty()
            && (recipe == null || recipe.findElement(name).isEmpty());
    }

    private static void announce(S88Recipe recipe) {
        if (recipe != null) {
            recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
        }
    }
}