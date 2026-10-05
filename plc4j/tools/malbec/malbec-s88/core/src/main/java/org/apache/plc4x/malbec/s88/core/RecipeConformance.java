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

import org.apache.plc4x.malbec.s88.api.S88ControlRecipe;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How far a recipe has drifted away from holding together.
 * <p>
 * A recipe is written by hand, saved half finished, and reopened in order to be finished, so
 * most of what is reported here is not a failure but a recipe part way through being written.
 */
public final class RecipeConformance {

    private final List<String> excess = new ArrayList<>();
    private final List<String> deficit = new ArrayList<>();

    private RecipeConformance(List<String> excess, List<String> deficit) {
        this.excess.addAll(excess);
        this.deficit.addAll(deficit);
    }

    /**
     * What the recipe carries that is in the way of running it: ids shared by two steps, steps that
     * name their equipment in both ways at once, a chart that points at something it does not carry,
     * and a split that does not go anywhere.
     */
    public List<String> excess() {
        return Collections.unmodifiableList(excess);
    }

    /**
     * What the recipe leaves out and would need before it could run: no id, no steps, a step that
     * names no equipment, a control recipe with no batch.
     */
    public List<String> deficit() {
        return Collections.unmodifiableList(deficit);
    }

    public boolean isConforming() {
        return excess.isEmpty() && deficit.isEmpty();
    }

    /**
     * Measures a recipe against itself.
     *
     * @param recipe recipe to measure, may be {@code null}
     * @return the report, empty when the recipe holds together
     */
    public static RecipeConformance of(S88Recipe recipe) {
        List<String> excess = new ArrayList<>();
        List<String> deficit = new ArrayList<>();
        if (recipe == null) {
            deficit.add("There is no recipe to check.");
            return new RecipeConformance(excess, deficit);
        }

        checkIdentity(recipe, excess, deficit);
        checkSteps(recipe, excess, deficit);
        checkChart(recipe, excess, deficit);

        return new RecipeConformance(excess, deficit);
    }

    private static void checkIdentity(S88Recipe recipe, List<String> excess, List<String> deficit) {
        if (recipe.getId() == null || recipe.getId().isBlank()) {
            deficit.add("The recipe has no id, so there is nothing to tell it apart from any other.");
        }
        if (recipe.getRecipeElements().isEmpty()) {
            deficit.add("The recipe has no steps.");
        }
        if (recipe instanceof S88ControlRecipe control && !control.hasBatchId()) {
            deficit.add("A control recipe has to name the batch it was set for, and this one does"
                    + " not, so there is nothing to tell its run apart from any other.");
        }
        for (String duplicate : recipe.getDuplicateIds()) {
            excess.add("More than one step is called '" + duplicate + "', so a step that points at"
                    + " it does not say which one it meant.");
        }
    }

    private static void checkSteps(S88Recipe recipe, List<String> excess, List<String> deficit) {
        for (S88RecipeElement element : recipe.getAllElements()) {
            String label = element.getId() != null && !element.getId().isBlank()
                    ? "Step '" + element.getId() + "'"
                    : "A step of kind " + element.getKind();
            if (element.getId() == null || element.getId().isBlank()) {
                deficit.add(label + " has no id, so nothing can point at it.");
                continue;
            }
            if (element.getKind() == null) {
                deficit.add(label + " does not say what kind of step it is.");
            }
            checkAddressing(recipe, element, label, excess, deficit);
        }
    }

    /**
     * Whether a step names its equipment the way the recipe it belongs to writes addresses.
     * <p>
     * The three ways a step can be wrong here are kept apart because they are three different
     * mistakes. Naming neither leaves nothing to run on, which is a missing thing. Naming both means
     * the recipe is not consistent with itself, which is a thing in the way.
     */
    private static void checkAddressing(S88Recipe recipe, S88RecipeElement element, String label,
                                        List<String> excess, List<String> deficit) {
        boolean hasClass = element.getEquipmentClassId() != null;
        int instances = element.getActualEquipmentIds().size();
        boolean addressesByClass = !(recipe instanceof S88MasterRecipe master) || master.addressesByClass();

        if (hasClass && instances == 0) {
            return;
        }
        if (!hasClass && instances == 0) {
            if (addressesByClass) {
                deficit.add(label + " is in a recipe written by class but names no class of"
                        + " equipment, so there is nothing to bind it to.");
            } else {
                deficit.add(label + " is in a recipe written for particular equipment but names"
                        + " none, so there is nothing to run it on.");
            }
            return;
        }
        if (hasClass) {
            excess.add(label + " names a class of equipment as well as a particular module. One"
                    + " recipe addresses its equipment one way only.");
            return;
        }
        if (addressesByClass) {
            excess.add(label + " names a particular module in a recipe written by class.");
        }
    }

    private static void checkChart(S88Recipe recipe, List<String> excess, List<String> deficit) {
        S88ProcedureLogic logic = recipe.getProcedureLogic();
        if (logic == null) {
            return;
        }

        for (String dangling : logic.getDanglingReferences()) {
            excess.add("The chart has a line pointing at '" + dangling + "', which this recipe does"
                    + " not carry.");
        }
        for (S88ProcedureStep step : logic.getSteps()) {
            if (step.getRecipeElementId() != null && !step.getRecipeElementId().isBlank()
                    && recipe.findElement(step.getRecipeElementId()).isEmpty()) {
                excess.add("Step '" + step.getId() + "' of the chart points at '"
                        + step.getRecipeElementId() + "', which this recipe does not carry.");
            }
        }
        checkBranches(logic, excess);
        checkReachability(recipe, logic, excess, deficit);
    }

    /**
     * A link the recipe marks as splitting or rejoining has to have the sides that make it one.
     * <p>
     * A parallel divergent link with a single destination is a split that does not go anywhere, and
     * a convergent link with a single origin is a join that waits for something that never arrives.
     */
    private static void checkBranches(S88ProcedureLogic logic, List<String> excess) {
        for (S88ProcedureLink link : logic.getLinks()) {
            if (link.isDivergent() && link.getTo().size() < 2) {
                excess.add("Link '" + link.getId() + "' is marked as splitting the flow but goes to"
                        + " " + link.getTo().size() + " side, so the branch has nothing on the"
                        + " other end.");
            }
            if (link.isConvergent() && link.getFrom().size() < 2) {
                excess.add("Link '" + link.getId() + "' is marked as bringing the flow back together"
                        + " but comes from " + link.getFrom().size() + " side, so the join waits for"
                        + " something that never arrives.");
            }
        }
    }

    /**
     * The chart has to start somewhere, and every box has to be reachable from where it starts.
     * <p>
     * The walk alternates between boxes and bars. The flow leaves a box, crosses a bar, and arrives
     * at a box. A line drawn straight from one box to another has nothing to follow, which is
     * why {@link #checkBranches} reports one.
     * <p>
     * A bar that waits on nothing is crossed as soon as the box before it has finished, so it is
     * walked through like any other. A chart may loop back to an earlier box retrying. Entering
     * a box twice ends the walk avoiding a forever loop.
     */
    private static void checkReachability(S88Recipe recipe, S88ProcedureLogic logic,
                                          List<String> excess, List<String> deficit) {
        List<S88ProcedureStep> steps = logic.getSteps();
        if (steps.isEmpty()) {
            return;
        }

        S88ProcedureStep begin = firstOfKind(recipe, logic, S88RecipeElementKind.BEGIN);
        S88ProcedureStep end = firstOfKind(recipe, logic, S88RecipeElementKind.END);
        if (begin == null) {
            deficit.add("The chart has no step whose element is of kind Begin, so the flow has"
                    + " nowhere to start.");
        }
        if (end == null) {
            deficit.add("The chart has no step whose element is of kind End, so the flow has nowhere"
                    + " to stop.");
        }

        Set<String> reachable = new LinkedHashSet<>();
        if (begin != null) {
            walkFrom(begin.getId(), logic, reachable);
        }
        for (S88ProcedureStep step : steps) {
            if (step.getId() != null && !reachable.contains(step.getId())) {
                excess.add("Step '" + step.getId() + "' of the chart is not reachable from the start,"
                        + " so the process would never perform it.");
            }
        }
    }

    /**
     * Walks the chart from one box, following the lines out of whatever it reaches and back
     * again where it reaches a bar.
     * <p>
     * Only the end a node is at is followed, so walking from a box leaves along the lines that
     * leave from it and walking from a bar leaves along the lines that leave from it. A box is only
     * ever entered once, so a cycle ends the walk instead of going round forever.
     */
    private static void walkFrom(String startStepId, S88ProcedureLogic logic, Set<String> reached) {
        Deque<S88IdRef> pending = new ArrayDeque<>();
        pending.push(S88IdRef.step(startStepId));
        while (!pending.isEmpty()) {
            S88IdRef here = pending.pop();
            if (here == null || here.getValue() == null) {
                continue;
            }
            boolean isBox = here.getType() == S88IdRefType.STEP;
            if (isBox && !reached.add(here.getValue())) {
                continue;
            }
            for (S88ProcedureLink link : logic.linksTouching(here)) {
                if (!link.getFrom().contains(here)) {
                    continue;
                }
                for (S88IdRef next : link.getTo()) {
                    pending.push(next);
                }
            }
        }
    }
     /**
     * The first step of the chart whose element is of the given kind, which is how the start and the
     * end of the flow are found.
     */
    private static S88ProcedureStep firstOfKind(S88Recipe recipe, S88ProcedureLogic logic,
                                                 S88RecipeElementKind kind) {
        for (S88ProcedureStep step : logic.getSteps()) {
            if (recipe.findElement(step.getRecipeElementId())
                    .map(element -> element.getKind() == kind)
                    .orElse(Boolean.FALSE)) {
                return step;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "RecipeConformance[" + excess.size() + " excess, " + deficit.size() + " deficit]";
    }
}
