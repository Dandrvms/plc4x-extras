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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * Takes a box off a chart and joins the flow across it.
 *
 * <p>Where the step sits in its branch decides what happens to the bars around it. A step in the
 * middle of a run takes its own bar with it and the step behind carries on through the bar that was
 * behind it. A step that opens a branch hands the bar that opened it to the step behind it, and a
 * step that ends one hands the bar that ended it to the step in front of it. A branch of one step
 * goes with that step, because there is nothing left of it.
 *
 * <p>A bar that another branch also arrives at or leaves from is not the step's to take away, and
 * joining across it means putting the step in that branch's place on the line it already shares,
 * keeping the line the kind of line it is.
 *
 * <p>The whole rearrangement is worked out on a copy and checked before the recipe is touched, so a
 * chart that cannot be joined again leaves the recipe as it was.
 *
 * @see ChartBranchingUseCase
 * @see RecipeDeepCopy#copyChart(S88ProcedureLogic)
 */
public final class ChartDeletionUseCase {

    /** Enough passes for any chart the editor can draw, and a stop if something is looping. */
    private static final int PASSES = 64;

    private ChartDeletionUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * Takes a step off the chart and puts the flow back together behind it.
     *
     * @param recipe recipe being edited, may be {@code null} when no change event is wanted
     * @param step   step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param boxId  box to take off
     * @throws IllegalArgumentException when there is no chart or no such box
     * @throws IllegalStateException    when the box is where the flow starts or stops, or when
     *                                  removing it would leave a chart that cannot be drawn
     */
    public static void deleteBox(S88Recipe recipe, S88RecipeElement step, String boxId) {
        S88ProcedureLogic chart = EditProcedureLogicUseCase.chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to delete from.");
        }
        S88ProcedureStep box = chart.findStep(boxId).orElseThrow(
                () -> new IllegalArgumentException("This chart has no box called '" + boxId + "'."));
        refuseIfEnd(recipe, box);
        String elementId = box.getRecipeElementId();

        S88ProcedureLogic joined = RecipeDeepCopy.copyChart(chart);
        takeStepOff(joined, boxId);
        List<String> sweptUp = sweepUpWhatNothingReaches(joined);
        checkItCanStillBeDrawn(joined, boxId);

        copyOnto(joined, chart);
        recipe.findElement(elementId).ifPresent(recipe::removeRecipeElement);
        sweptUp.forEach(id -> recipe.findElement(id).ifPresent(recipe::removeRecipeElement));
        recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
    }

    /**
     * Where the step sits, and what the bars on either side of it are, decides who takes its place.
     */
    private static void takeStepOff(S88ProcedureLogic chart, String boxId) {
        S88ProcedureLink arriving = onlyTouching(chart, boxId, true);
        S88ProcedureLink leaving = onlyTouching(chart, boxId, false);
        if (arriving == null || leaving == null) {
            return;
        }
        String before = arriving.getFrom().get(0).getValue();
        List<String> barsBehind = names(leaving.getTo());
        if (barsBehind.size() > 1) {
            throw new IllegalStateException("Step '" + boxId + "' leads to " + barsBehind.size()
                    + " bars at once, so the flow splits here and there is no one bar to join"
                    + " across to.");
        }
        String after = barsBehind.get(0);
        List<String> nextSteps = branchEndsAt(chart, leaving, after) ? List.of()
                : theOnlyStepAfter(chart, after);
        List<String> beforeBox = theOnlyBoxBefore(chart, before);

        if (!beforeBox.isEmpty()) {
            if (leaving.getFrom().size() > 1) {
                joinIntoTheConvergence(chart, boxId, before, beforeBox.get(0), leaving);
            } else {
                joinAcross(chart, boxId, before, beforeBox.get(0), after);
            }
            return;
        }
        if (nextSteps.isEmpty()) {
            takeTheBranchOff(chart, boxId, before, after, waitsOnABranchOfItsOwn(chart, before));
        } else {
            handTheBranchToTheNextStep(chart, boxId, before, after, nextSteps.get(0));
        }
    }

    /**
     * Whether this branch is over at the bar behind the step. A parallel branch ends at the bar the
     * other branches come back to, and a branch of the other kind ends at the bar other branches are
     * already on its way out of. Neither has a step of this branch behind it.
     */
    private static boolean branchEndsAt(S88ProcedureLogic chart, S88ProcedureLink leaving,
                                        String after) {
        if (leaving.getFrom().size() > 1) {
            return true;
        }
        S88ProcedureLink onward = onlyTouching(chart, after, false);
        return onward != null && onward.getFrom().size() > 1;
    }

    /** The one step that follows this bar, when this bar is in the middle of the branch. */
    private static List<String> theOnlyStepAfter(S88ProcedureLogic chart, String barId) {
        S88ProcedureLink line = onlyTouching(chart, barId, false);
        if (line == null || line.getFrom().size() > 1 || line.getTo().size() != 1) {
            return List.of();
        }
        return names(line.getTo());
    }

    /**
     * The one box that leads to this bar, or nothing when the bar is not this step's to take away.
     *
     * <p>A bar the flow splits out of is one of several a line leads to, and a bar the flow joins on
     * is reached by more than one box. Neither has the one box in front of it that joining across
     * needs.
     */
    private static List<String> theOnlyBoxBefore(S88ProcedureLogic chart, String barId) {
        S88ProcedureLink line = onlyTouching(chart, barId, true);
        if (line == null || line.getFrom().size() > 1 || line.getTo().size() > 1) {
            return List.of();
        }
        for (S88ProcedureLink other : chart.getLinks()) {
            if (other.getFrom().stream().anyMatch(end -> barId.equals(end.getValue()))
                    && other.getTo().size() > 1) {
                return List.of();
            }
        }
        return names(line.getFrom());
    }

    /** Whether the bar is one of several a line leads to, which is how a branch opens. */
    private static boolean waitsOnABranchOfItsOwn(S88ProcedureLogic chart, String barId) {
        S88ProcedureLink line = onlyTouching(chart, barId, true);
        return line != null && line.getTo().size() > 1;
    }

    /**
     * The bar in front was waiting on this step alone, so it goes with the step and the step in
     * front of it carries on to the bar behind.
     */
    private static void joinAcross(S88ProcedureLogic chart, String boxId, String before,
                                   String previousBox, String after) {
        detach(chart, boxId);
        takeBarOff(chart, before);
        putBack(chart, previousBox, after);
    }

    /**
     * The bar in front was this step's, but the bar behind is one other branches come back to, so
     * the step in front takes this step's place on the line they all arrive on.
     */
    private static void joinIntoTheConvergence(S88ProcedureLogic chart, String boxId, String before,
                                               String previousBox, S88ProcedureLink leaving) {
        swapEndOf(chart, leaving, boxId, previousBox);
        detach(chart, boxId);
        takeBarOff(chart, before);
    }

    /**
     * This step opened its branch, so the step behind it takes its place. When the bar that opened
     * the branch belongs to the branch alone it is kept and linked to the step behind, and when it
     * is a bar the flow splits out of, the step behind it goes on that line in this step's place.
     */
    private static void handTheBranchToTheNextStep(S88ProcedureLogic chart, String boxId,
                                                   String before, String after, String nextStep) {
        boolean barOfItsOwn = waitsOnABranchOfItsOwn(chart, before);
        if (barOfItsOwn) {
            detach(chart, boxId);
            takeBarOff(chart, after);
            putBack(chart, before, nextStep);
            return;
        }
        swapEndOf(chart, onlyTouching(chart, before, false), boxId, nextStep);
        detach(chart, boxId);
        takeBarOff(chart, after);
    }

    /**
     * This step was all its branch had, so the branch goes with it. A bar that belongs to the branch
     * alone goes too, and a bar the flow splits out of stays, with one branch less to split into.
     */
    private static void takeTheBranchOff(S88ProcedureLogic chart, String boxId, String before,
                                         String after, boolean ofItsOwn) {
        detach(chart, boxId);
        if (ofItsOwn) {
            detach(chart, after);
            takeBarOff(chart, after);
            takeBarOff(chart, before);
        }
    }

    /**
     * Takes away what is left with nothing to do, and names the steps it took so the recipe lets go
     * of them too.
 */
    private static List<String> sweepUpWhatNothingReaches(S88ProcedureLogic chart) {
        List<String> taken = new ArrayList<>();
        for (int pass = 0; pass < PASSES; pass++) {
            List<S88ProcedureStep> unreached = new ArrayList<>();
            for (S88ProcedureStep box : chart.getSteps()) {
                if (!arrivalsOf(chart, box.getId()).isEmpty() || box.getId().equals("BEGIN")) {
                    continue;
                }
                unreached.add(box);
            }
            if (unreached.isEmpty()) {
                return taken;
            }
            for (S88ProcedureStep box : unreached) {
                detach(chart, box.getId());
                chart.removeStep(box);
                taken.add(box.getRecipeElementId());
            }
        }
        return taken;
    }

    /**
     * Refuses a rearrangement that cannot be drawn, before any of it reaches the recipe.
     */
    private static void checkItCanStillBeDrawn(S88ProcedureLogic chart, String boxId) {
        List<String> complaints = new ArrayList<>();
        if (!chart.getDanglingReferences().isEmpty()) {
            complaints.add("nothing reaches '" + String.join("', '", chart.getDanglingReferences())
                    + "'");
        }
        if (!EditProcedureLogicUseCase.isChartBipartite(chart)) {
            complaints.add("a line runs from a box to a box or from a bar to a bar");
        }
        for (S88ProcedureStep box : chart.getSteps()) {
            if (!box.getId().equals("BEGIN") && linesArrivingAt(chart, box.getId()) > 1) {
                complaints.add("more than one line arrives at step '" + box.getId() + "'");
            }
            if (!box.getId().equals("END") && linesLeaving(chart, box.getId()) > 1) {
                complaints.add("more than one line leaves step '" + box.getId() + "'");
            }
        }
        for (S88ProcedureTransition bar : chart.getTransitions()) {
            if (linesArrivingAt(chart, bar.getId()) > 1) {
                complaints.add("more than one line arrives at bar '" + bar.getId() + "'");
            }
            if (linesLeaving(chart, bar.getId()) > 1) {
                complaints.add("more than one line leaves bar '" + bar.getId() + "'");
            }
        }
        if (!complaints.isEmpty()) {
            throw new IllegalStateException("Taking '" + boxId + "' off the chart would leave it in a"
                    + " state that cannot be drawn: " + String.join(", and ", complaints)
                    + ". Nothing has been changed.");
        }
    }

    /** Lines are counted, not the names on them: a split is one line reaching two boxes at once. */
    private static int linesArrivingAt(S88ProcedureLogic chart, String id) {
        int count = 0;
        for (S88ProcedureLink line : chart.getLinks()) {
            if (line.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                count++;
            }
        }
        return count;
    }

    private static int linesLeaving(S88ProcedureLogic chart, String id) {
        int count = 0;
        for (S88ProcedureLink line : chart.getLinks()) {
            if (line.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                count++;
            }
        }
        return count;
    }

    private static void refuseIfEnd(S88Recipe recipe, S88ProcedureStep box) {
        S88RecipeElement element = recipe.findElement(box.getRecipeElementId()).orElse(null);
        if (element != null && (element.getKind() == S88RecipeElementKind.BEGIN
                || element.getKind() == S88RecipeElementKind.END)) {
            throw new IllegalStateException("Step '" + box.getId() + "' is where the flow "
                    + (element.getKind() == S88RecipeElementKind.BEGIN ? "starts" : "stops")
                    + ", and a recipe has to have both.");
        }
    }

    /** Puts the chart that was worked out in place of the one the recipe is holding. */
    private static void copyOnto(S88ProcedureLogic from, S88ProcedureLogic to) {
        new ArrayList<>(to.getLinks()).forEach(to::removeLink);
        new ArrayList<>(to.getSteps()).forEach(to::removeStep);
        new ArrayList<>(to.getTransitions()).forEach(to::removeTransition);
        from.getSteps().forEach(to::addStep);
        from.getTransitions().forEach(to::addTransition);
        from.getLinks().forEach(to::addLink);
    }

    /** Takes a bar off the chart along with the lines that met at it. */
    private static void takeBarOff(S88ProcedureLogic chart, String barId) {
        detach(chart, barId);
        chart.findTransition(barId).ifPresent(chart::removeTransition);
    }

    /** Takes one box off the chart, and takes away the lines that are left with nothing on one side. */
    private static void detach(S88ProcedureLogic chart, String id) {
        for (S88ProcedureLink line : new ArrayList<>(chart.getLinks())) {
            if (!names(line.getFrom()).contains(id) && !names(line.getTo()).contains(id)) {
                continue;
            }
            chart.removeLink(line);
            List<S88IdRef> from = new ArrayList<>();
            List<S88IdRef> to = new ArrayList<>();
            line.getFrom().stream().filter(end -> !id.equals(end.getValue())).forEach(from::add);
            line.getTo().stream().filter(end -> !id.equals(end.getValue())).forEach(to::add);
            if (!from.isEmpty() && !to.isEmpty()) {
                // A line with one end at each side is an ordinary line. One that still says it
                // splits or joins is a branch with a whole side of it still there.
                S88LinkType type = from.size() < 2 && to.size() < 2 ? null : line.getLinkType();
                chart.addLink(rebuilt(line.getId(), from, to, type));
            }
        }
    }

    /**
     * Puts one box in another's place on a line, leaving the line the kind of line it is. A branch
     * keeps its bar and its type because the box behind it took the box's place on that very line.
     */
    private static void swapEndOf(S88ProcedureLogic chart, S88ProcedureLink line, String was,
                                  String now) {
        List<S88IdRef> from = new ArrayList<>();
        List<S88IdRef> to = new ArrayList<>();
        for (S88IdRef end : line.getFrom()) {
            from.add(was.equals(end.getValue()) ? S88IdRef.step(now) : end);
        }
        for (S88IdRef end : line.getTo()) {
            to.add(was.equals(end.getValue()) ? S88IdRef.step(now) : end);
        }
        chart.removeLink(line);
        chart.addLink(rebuilt(line.getId(), from, to, line.getLinkType()));
    }

    /** Joins whatever sits on each side of a hole the step left, box to bar or bar to box. */
    private static void putBack(S88ProcedureLogic chart, String from, String to) {
        if (!leaves(chart, from).isEmpty()) {
            return;
        }
        boolean fromIsBar = chart.findTransition(from).isPresent();
        S88ProcedureLink line = new S88ProcedureLink(freeLineName(chart, from, to));
        line.addFrom(fromIsBar ? S88IdRef.transition(from) : S88IdRef.step(from));
        line.addTo(fromIsBar ? S88IdRef.step(to) : S88IdRef.transition(to));
        chart.addLink(line);
    }

    private static String freeLineName(S88ProcedureLogic chart, String from, String to) {
        String candidate = from + "_TO_" + to;
        int more = 1;
        while (chart.findLink(candidate).isPresent()) {
            candidate = from + "_TO_" + to + "_" + more++;
        }
        return candidate;
    }

    private static S88ProcedureLink rebuilt(String id, List<S88IdRef> from, List<S88IdRef> to,
                                            S88LinkType type) {
        S88ProcedureLink line = new S88ProcedureLink(id);
        from.forEach(line::addFrom);
        to.forEach(line::addTo);
        line.setLinkType(type);
        return line;
    }

    private static S88ProcedureLink onlyTouching(S88ProcedureLogic chart, String id, boolean arriving) {
        S88ProcedureLink found = null;
        for (S88ProcedureLink line : chart.getLinks()) {
            boolean touches = arriving
                    ? line.getTo().stream().anyMatch(end -> id.equals(end.getValue()))
                    : line.getFrom().stream().anyMatch(end -> id.equals(end.getValue()));
            if (!touches) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = line;
        }
        return found;
    }

    private static List<String> arrivalsOf(S88ProcedureLogic chart, String id) {
        Set<String> found = new LinkedHashSet<>();
        for (S88ProcedureLink line : chart.getLinks()) {
            if (line.getTo().stream().anyMatch(end -> id.equals(end.getValue()))) {
                line.getFrom().forEach(end -> found.add(end.getValue()));
            }
        }
        found.remove(id);
        return new ArrayList<>(found);
    }

    private static List<String> leaves(S88ProcedureLogic chart, String id) {
        Set<String> found = new LinkedHashSet<>();
        for (S88ProcedureLink line : chart.getLinks()) {
            if (line.getFrom().stream().anyMatch(end -> id.equals(end.getValue()))) {
                line.getTo().forEach(end -> found.add(end.getValue()));
            }
        }
        found.remove(id);
        return new ArrayList<>(found);
    }

    private static List<String> names(List<S88IdRef> ends) {
        Set<String> found = new LinkedHashSet<>();
        ends.forEach(end -> found.add(end.getValue()));
        return new ArrayList<>(found);
    }
}