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
 * The counterpart of {@link ClassConformance} for the other side of the model: that one measures an
 * element against the class it belongs to, and this one measures a recipe against itself. A recipe
 * is written by hand, saved half finished, and reopened in order to be finished, so most of what is
 * reported here is not a failure but a recipe part way through being written. That is why the
 * result is a report and not an exception, and why an incomplete recipe is still loaded and editable.
 * <p>
 * What is reported is only what can be seen from the recipe alone. Whether the steps name equipment
 * and variables that exist in the plant is a different question, asked by
 * {@code ResolveRecipeUseCase} against a loaded plant, and a recipe is judged here before it has ever
 * met one.
 * <p>
 * The state is derived, never stored, exactly as in {@link ClassConformance}: it is read off the
 * recipe every time it is asked for, so a recipe that is edited in memory cannot go stale against
 * the verdict it was given when it was read.
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
     * the recipe is not consistent with itself, which is a thing in the way. A control recipe naming
     * a class rather than a module is a fourth, handled by the fact that a control recipe addresses
     * by instance whatever it says.
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
     * Neither is repaired here: a chart that was drawn and saved halfway is the normal state of a
     * recipe being written, and quietly inventing the missing side would produce a recipe that looks
     * complete and does not mean what its author intended.
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
     * The chart has to start somewhere, and everything on it has to be reachable from where it
     * starts. A step that no line reaches is a step the process never performs, which is a different
     * mistake from a chart with no start, and is reported as one.
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
     * Walks the chart from one step, following the lines out of whatever it reaches. Links are
     * followed in the order the recipe lists them and a step is only ever entered once, so a cycle
     * ends the walk instead of going round for ever.
     */
    private static void walkFrom(String startStepId, S88ProcedureLogic logic, Set<String> reached) {
        Deque<String> pending = new ArrayDeque<>();
        pending.push(startStepId);
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (current == null || !reached.add(current)) {
                continue;
            }
            for (S88ProcedureLink link : logic.linksFrom(current)) {
                for (S88IdRef ref : link.getTo()) {
                    if (ref.getType() == org.apache.plc4x.malbec.s88.api.S88IdRefType.STEP) {
                        pending.push(ref.getValue());
                    }
                }
            }
        }
    }

    /**
     * The first step of the chart whose element is of the given kind, which is how the start and the
     * end of the flow are found. The kind lives on the element rather than on the step, so the
     * element the step points at is what decides it.
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
