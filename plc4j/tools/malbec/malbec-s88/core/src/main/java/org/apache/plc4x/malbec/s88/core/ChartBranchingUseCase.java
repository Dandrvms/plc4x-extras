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
 * <p>Bars are born empty and wait on nothing. The condition is the author's to write, and a bar
 * that already said something would be a bar the author did not write.
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
        S88ProcedureLink entering = arrivingAt(chart, boxId);
        S88ProcedureLink leaving = leavingFrom(chart, boxId);
        if (entering.getTo().size() != 1 || leaving.getTo().size() != 1) {
            throw new IllegalStateException("Step '" + boxId + "' is already fed by a branch point or"
                    + " already goes to more than one place, so there is no one line to widen.");
        }
        String barBehind = entering.getFrom().get(0).getValue();
        S88ProcedureLink feeding = arrivingAt(chart, barBehind);
        String barAfter = leaving.getTo().get(0).getValue();
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
        return found.get(0);
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
        return found.get(0);
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
                S88RecipeElementKind.OPERATION, null);
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
        while (chart.findStep(candidate).isPresent() || chart.findTransition(candidate).isPresent()
                || chart.findStep("BOX_" + candidate).isPresent()
                || (recipe != null && recipe.findElement(candidate).isPresent())) {
            candidate = stem + "_" + more++;
        }
        return candidate;
    }

    private static void announce(S88Recipe recipe) {
        if (recipe != null) {
            recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
        }
    }
}