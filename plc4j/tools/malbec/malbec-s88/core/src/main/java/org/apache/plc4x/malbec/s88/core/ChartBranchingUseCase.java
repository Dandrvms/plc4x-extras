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
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
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
    private static final String SKIP_STEM = "T_SKIP";

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
        return recipe.findElement(box).orElseThrow();
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
        return recipe.findElement(box).orElseThrow();
    }

    /**
     * Makes the picked step optional by adding a transition that skips it.
     *
     * <p>The transition that feeds the step is widened into a selective split. One side still goes
     * through the step; the other goes straight to the transition after it. The bypass is the one
     * line a chart may draw from one transition to another.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step that becomes optional
     * @return the transition that skips the step
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is where the flow starts or stops, when it has
     *                                  no plain transition on either side, or when it is already at
     *                                  the start of a branch
     */
    public static S88ProcedureTransition addOptionalBranch(S88Recipe recipe, S88RecipeElement step,
                                                            String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        OptionalBranch plan = optionalBranchPlan(chart, boxId);
        String skipBar = newBar(recipe, step, chart, SKIP_STEM);

        put(chart, plan.feeding(), widenArrival(plan.feeding(),
                S88IdRef.transition(skipBar), S88LinkType.SERIAL_DIVERGENT));
        chart.addLink(barToBar(skipBar + "_TO_" + plan.afterBar(), skipBar, plan.afterBar()));

        announce(recipe);
        return chart.findTransition(skipBar).orElseThrow();
    }

    /**
     * Whether the picked step can be made optional.
     *
     * @param recipe recipe being edited, may be {@code null}
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box of the step to check
     * @return whether {@link #addOptionalBranch} would go through
     */
    public static boolean canAddOptionalBranch(S88Recipe recipe, S88RecipeElement step,
                                               String boxId) {
        try {
            optionalBranchPlan(chartToBranch(recipe, step, boxId), boxId);
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** What an optional branch widens, found before anything is changed. */
    private record OptionalBranch(S88ProcedureLink feeding, String afterBar) {
    }

    private static OptionalBranch optionalBranchPlan(S88ProcedureLogic chart, String boxId) {
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        requireBarsOfItsOwn(boxId, entering, leaving);
        if (entering.getFrom().size() != 1 || leaving.getTo().size() != 1) {
            throw new IllegalStateException("Step '" + boxId + "' is already fed by a branch point"
                    + " or already goes to more than one place, so there is no one transition to"
                    + " widen.");
        }
        String incomingBar = entering.getFrom().getFirst().getValue();
        String afterBar = leaving.getTo().getFirst().getValue();
        if (incomingBar.equals(afterBar)) {
            throw new IllegalStateException("Step '" + boxId + "' has the same transition before and"
                    + " after it, so an optional branch would go round in a loop.");
        }
        S88ProcedureLink feeding = arrivingAt(chart, incomingBar);
        if (feeding.getFrom().size() != 1 || feeding.getTo().size() != 1
                || feeding.isDivergent() || feeding.isConvergent()) {
            throw new IllegalStateException("Step '" + boxId + "' is already at the start of a"
                    + " branch, so there is no plain transition to widen into an optional branch.");
        }
        return new OptionalBranch(feeding, afterBar);
    }

    private static S88ProcedureLogic chartToBranch(S88Recipe recipe, S88RecipeElement step,
                                                   String boxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to branch.");
        }
        S88ProcedureStep box = chart.findStep(boxId).orElseThrow(
                () -> new IllegalArgumentException("This chart has no step called '" + boxId + "'."));
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
            throw new IllegalStateException("Step '" + id + "' has " + found.size() + " lines"
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
            throw new IllegalStateException("Step '" + id + "' has " + found.size() + " lines"
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
     * Moves a whole branch one lane sideways, keeping it in the same block.
     *
     * <p>Which lane a branch is in is held by the order of the ends of two lines: the bar that
     * splits the block, and the bar that comes back together. Moving the branch means moving its
     * end in both of them by the same amount, because they are the same set of branches seen from
     * either end.
     *
     * <p>Nothing else changes. The branch is still the same steps in the same order, and the block
     * is still the same block.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart the branch is on, {@code null} for the recipe's own
     * @param boxId  box of any step of the branch to move
     * @param toTheRight whether to move towards the last branch rather than the first
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is not on a branch of a block, or when the
     *                                  branch is already at that end and there is no room to move
     * @see #removeBranch(S88Recipe, S88RecipeElement, String)
     */
    public static void moveBranch(S88Recipe recipe, S88RecipeElement step, String boxId,
                                  boolean toTheRight) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        String first = firstOfItsBranch(chart, boxId);
        String last = lastOfItsBranch(chart, boxId);
        S88ProcedureLink splitting = StructureQueries.arrivingAt(chart, first);
        S88ProcedureLink closing = StructureQueries.leavingFrom(chart, last);
        requireAWholeBranchOfABlock(splitting, closing, boxId);

        int here = laneOf(splitting, first);
        if (here != laneOf(closing, last)) {
            throw new IllegalStateException("Step '" + boxId + "' is on a branch of a block whose two"
                    + " transitions do not list its branches in the same order, so moving it sideways would"
                    + " put it in one lane at the top and another at the bottom.");
        }
        int wanted = toTheRight ? here + 1 : here - 1;
        if (wanted < 0 || wanted >= branchEnds(splitting).size()) {
            throw new IllegalStateException("Step '" + boxId + "' is already the "
                    + (toTheRight ? "last" : "first") + " branch of its block, so it has no other"
                    + " branch to move past.");
        }
        put(chart, splitting, withLanesSwapped(splitting, here, wanted));
        put(chart, closing, withLanesSwapped(closing, here, wanted));
        announce(recipe);
    }

    /**
     * The first step of the branch the box is on.
     *
     * <p>A branch may be a run of steps between two bars. Which lane it is in is held by the bar
     * that splits the block, and that bar points at the first step of each branch rather than at
     * whichever one was picked. So this walks back over plain lines until the bar that splits shows
     * up.
     */
    private static String firstOfItsBranch(S88ProcedureLogic chart, String boxId) {
        String step = boxId;
        while (true) {
            S88ProcedureLink arriving = StructureQueries.arrivingAt(chart, step);
            if (arriving == null || splitsInto(arriving)) {
                return step;
            }
            step = arriving.getFrom().getFirst().getValue();
        }
    }

    /**
     * The last step of the branch the box is on.
     *
     * <p>The mirror of {@link #firstOfItsBranch}, walking forward over plain lines until the bar
     * that comes back together shows up.
     */
    private static String lastOfItsBranch(S88ProcedureLogic chart, String boxId) {
        String step = boxId;
        while (true) {
            S88ProcedureLink leaving = StructureQueries.leavingFrom(chart, step);
            if (leaving == null || comesBackTogetherIn(leaving)) {
                return step;
            }
            step = leaving.getTo().getFirst().getValue();
        }
    }

    /**
     * The ends of a bar that stand for the branches of the block.
     *
     * <p>A bar names its branches at the end it splits towards: the bar that splits the block has
     * them going out, and the bar that comes back together has them coming in. The other end is the
     * single node on the far side of the block.
     */
    private static List<S88IdRef> branchEnds(S88ProcedureLink bar) {
        return comesBackTogetherIn(bar) ? bar.getFrom() : bar.getTo();
    }

    /** Where in a bar's branches the given node sits, which is the lane its branch is in. */
    private static int laneOf(S88ProcedureLink bar, String nodeId) {
        int lane = branchEnds(bar).stream().map(S88IdRef::getValue).toList().indexOf(nodeId);
        if (lane < 0) {
            throw new IllegalStateException("Transition '" + bar.getId() + "' does not reach '" + nodeId
                    + "', so its branches cannot be counted.");
        }
        return lane;
    }

    /** The same bar with two of its branches the other way round. */
    private static S88ProcedureLink withLanesSwapped(S88ProcedureLink bar, int one, int other) {
        List<S88IdRef> branches = new ArrayList<>(branchEnds(bar));
        S88IdRef moved = branches.remove(one);
        branches.add(other, moved);
        S88ProcedureLink swapped = new S88ProcedureLink(bar.getId());
        if (comesBackTogetherIn(bar)) {
            branches.forEach(swapped::addFrom);
            bar.getTo().forEach(swapped::addTo);
        } else {
            bar.getFrom().forEach(swapped::addFrom);
            branches.forEach(swapped::addTo);
        }
        swapped.setLinkType(bar.getLinkType());
        return swapped;
    }

    /**
     * Takes a whole branch off the chart.
     *
     * <p>Only a step that is a branch on its own can go this way. It is arrived at by the bar that
     * split and leaves by the bar that comes back together, and taking it off leaves those bars
     * with one end fewer. A block of three branches is left with two, and a block of two is left
     * with one, which is no longer a block at all.
     *
     * <p>A step that has steps of its own on the same branch has bars of its own, so it is not a
     * whole branch and is refused. Taking a run of steps off is a different order and is not done
     * here.
     *
     * <p>The bars are put back together by {@link ChartDeletionUseCase#deleteBox}, which already
     * drops the kind of line once only one end is left. That is what collapses a block of two.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart the branch is on, {@code null} for the recipe's own
     * @param boxId  box of the step to take off
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the step is not a whole branch of a block, because
     *                                  taking it off would leave lines going to nothing
     * @see ChartDeletionUseCase#deleteBox
     */
    public static void removeBranch(S88Recipe recipe, S88RecipeElement step, String boxId) {
        S88ProcedureLogic chart = chartToBranch(recipe, step, boxId);
        requireAWholeBranchOfABlock(StructureQueries.arrivingAt(chart, boxId),
                StructureQueries.leavingFrom(chart, boxId), boxId);
        ChartDeletionUseCase.deleteBox(recipe, step, boxId);
        announce(recipe);
    }

    /**
     * Whether the box is a branch of a block all by itself.
     *
     * <p>That is what makes it removable as a branch. A step in the middle of a branch has bars of
     * its own and is removed as a step, not as a branch, and a step outside any block is not a
     * branch at all.
     *
     * @param chart chart the box is on
     * @param boxId box to look at
     * @throws IllegalStateException when it is not a whole branch
     */
    private static void requireAWholeBranchOfABlock(S88ProcedureLink splitting,
                                                     S88ProcedureLink closing, String boxId) {
        if (!splitsInto(splitting) || !comesBackTogetherIn(closing)) {
            throw new IllegalStateException("Step '" + boxId + "' is not a whole branch of a block,"
                    + " so there is no branch there to remove. A step in the middle of a branch is"
                    + " taken off as a step.");
        }
    }

    /**
     * Whether this line is the bar that splits a block into the side the step is on.
     *
     * <p>A step in the middle of a branch is arrived at by a plain line, or by the bar that closes
     * a block further back, and neither is a split.
     */
    private static boolean splitsInto(S88ProcedureLink link) {
        return link != null && link.getLinkType() != null && link.getLinkType().isDivergent();
    }

    /**
     * Whether this line is the bar that closes a block on the side the step is on.
     *
     * <p>A step that opens a block of its own leaves by the bar that splits that one, and a step in
     * the middle of a branch leaves by a plain line, so neither is a close.
     */
    private static boolean comesBackTogetherIn(S88ProcedureLink link) {
        return link != null && link.getLinkType() != null && link.getLinkType().isConvergent();
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
        return recipe.findElement(box).orElseThrow();
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
        return recipe.findElement(box).orElseThrow();
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
            throw new IllegalStateException("Step '" + boxId + "' does not sit between two transitions, so"
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

    private static S88ProcedureLink barToBar(String id, String fromBar, String toBar) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        line.addFrom(S88IdRef.transition(fromBar));
        line.addTo(S88IdRef.transition(toBar));
        return line;
    }

    private static void put(S88ProcedureLogic chart, S88ProcedureLink was, S88ProcedureLink now) {
        chart.removeLink(was);
        chart.addLink(now);
    }

    private static String newStep(S88Recipe recipe, S88RecipeElement step,
                                  S88ProcedureLogic chart) {
        String elementId = freeName(recipe, chart, STEP_STEM);
        String box = elementId;
        CreateRecipeElementUseCase.forEquipmentClass(recipe, step, elementId,
                RecipeLevels.levelOf(step), null);
        EditProcedureLogicUseCase.addStep(recipe, step, box, elementId);
        return box;
    }

    private static String newBar(S88Recipe recipe, S88RecipeElement step,
                                 S88ProcedureLogic chart) {
        return newBar(recipe, step, chart, BAR_STEM);
    }

    private static String newBar(S88Recipe recipe, S88RecipeElement step,
                                 S88ProcedureLogic chart, String stem) {
        String barId = freeName(recipe, chart, stem);
        EditProcedureLogicUseCase.addTransition(recipe, step, barId, null);
        return barId;
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