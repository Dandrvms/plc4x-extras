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
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How far a recipe has drifted away from holding together.
 * <p>
 * A recipe is written by hand, saved half finished, and reopened in order to be finished, so
 * most of what is reported here is not a failure but a recipe part way through being written.
 * <p>
 * Every finding says which box, bar or line it is about, so the panel that shows them can put the
 * frame on that node when the finding is picked.
 */
public final class RecipeConformance {

    private final List<Finding> excess;
    private final List<Finding> deficit;
    private final List<Finding> warnings;

    private RecipeConformance(List<Finding> excess, List<Finding> deficit, List<Finding> warnings) {
        this.excess = List.copyOf(excess);
        this.deficit = List.copyOf(deficit);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * One thing a report has to say, and the node of the chart it is about.
     *
     * @param message what is being said
     * @param nodeId  id of the box, bar or line the finding is about, {@code null} when it is about
     *                the recipe rather than one node of its chart
     */
    public record Finding(String message, String nodeId) {

        /** A finding about the recipe rather than one node of its chart. */
        public Finding(String message) {
            this(message, null);
        }
    }

    /**
     * What the recipe carries that is in the way of running it: ids shared by two steps, steps that
     * name their equipment in both ways at once, a chart that points at something it does not carry,
     * a line that does not cross a transition, a split whose branches do not come back together, and
     * a loop that runs through a parallel block.
     */
    public List<String> excess() {
        return messagesOf(excess);
    }

    /**
     * What the recipe leaves out and would need before it could run: no id, no steps, a step that
     * names no equipment, a control recipe with no batch.
     */
    public List<String> deficit() {
        return messagesOf(deficit);
    }

    /**
     * What is worth telling the author about but does not stop the recipe running, such as the
     * transitions of a split being taken one after another without saying which.
     */
    public List<String> warnings() {
        return messagesOf(warnings);
    }

    /** The excess findings with the node each is about, so the panel can pick it. */
    public List<Finding> excessFindings() {
        return excess;
    }

    /** The deficit findings with the node each is about. */
    public List<Finding> deficitFindings() {
        return deficit;
    }

    /** The warnings with the node each is about. */
    public List<Finding> warningFindings() {
        return warnings;
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
        return of(recipe, null);
    }

    /**
     * Measures a recipe against itself and against the plant it was written against.
     *
     * @param recipe recipe to measure, may be {@code null}
     * @param plant  the plant the recipe was written against, {@code null} when there is none
     * @return the report, empty when the recipe holds together
     */
    public static RecipeConformance of(S88Recipe recipe, S88PlantSnapshot plant) {
        List<Finding> excess = new ArrayList<>();
        List<Finding> deficit = new ArrayList<>();
        List<Finding> warnings = new ArrayList<>();
        if (recipe == null) {
            deficit.add(new Finding("There is no recipe to check."));
            return new RecipeConformance(excess, deficit, warnings);
        }

        checkIdentity(recipe, excess, deficit);
        checkSteps(recipe, excess, deficit);
        checkChart(recipe, recipe.getProcedureLogic(), excess, deficit, warnings);
        if (plant != null) {
            checkAgainstPlant(recipe, plant, excess, deficit);
        }

        return new RecipeConformance(excess, deficit, warnings);
    }

    private static List<String> messagesOf(List<Finding> findings) {
        List<String> messages = new ArrayList<>(findings.size());
        for (Finding finding : findings) {
            messages.add(finding.message());
        }
        return Collections.unmodifiableList(messages);
    }

    /**
     * Whether every step that says what it works on is naming something the plant has.
     */
    private static void checkAgainstPlant(S88Recipe recipe, S88PlantSnapshot plant,
                                          List<Finding> excess, List<Finding> deficit) {
        boolean byClass = !(recipe instanceof S88MasterRecipe master) || master.addressesByClass();
        for (S88RecipeElement element : recipe.getAllElements()) {
            if (element.getKind() == S88RecipeElementKind.BEGIN
                    || element.getKind() == S88RecipeElementKind.END) {
                continue;
            }
            String label = "Step '" + element.getId() + "'";
            if (byClass) {
                String classId = element.getEquipmentClassId();
                if (classId != null && plant.findClass(classId) == null) {
                    excess.add(new Finding(label + " names the class of equipment '" + classId
                            + "', which the plant this recipe was written against does not have.",
                            element.getId()));
                }
            } else {
                String uid = element.getEquipmentUid();
                if (uid != null && plant.findByUid(uid).isEmpty()) {
                    excess.add(new Finding(label + " works on equipment this recipe's plant does not"
                            + " have any more, so what it was written against is gone.",
                            element.getId()));
                }
            }
        }
    }

    private static void checkIdentity(S88Recipe recipe, List<Finding> excess, List<Finding> deficit) {
        if (recipe.getId() == null || recipe.getId().isBlank()) {
            deficit.add(new Finding("The recipe has no id, so there is nothing to tell it apart from"
                    + " any other."));
        }
        if (recipe.getRecipeElements().isEmpty()) {
            deficit.add(new Finding("The recipe has no steps."));
        }
        if (recipe instanceof S88ControlRecipe control && !control.hasBatchId()) {
            deficit.add(new Finding("A control recipe has to name the batch it was set for, and this"
                    + " one does not, so there is nothing to tell its run apart from any other."));
        }
        for (String duplicate : recipe.getDuplicateIds()) {
            excess.add(new Finding("More than one step is called '" + duplicate + "', so a step that"
                    + " points at it does not say which one it meant.", duplicate));
        }
    }

    private static void checkSteps(S88Recipe recipe, List<Finding> excess, List<Finding> deficit) {
        for (S88RecipeElement element : recipe.getAllElements()) {
            String label = element.getId() != null && !element.getId().isBlank()
                    ? "Step '" + element.getId() + "'"
                    : "A step of kind " + element.getKind();
            if (element.getId() == null || element.getId().isBlank()) {
                deficit.add(new Finding(label + " has no id, so nothing can point at it."));
                continue;
            }
            if (element.getKind() == null) {
                deficit.add(new Finding(label + " does not say what kind of step it is.",
                        element.getId()));
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
         * <p>
         * The start and the stop of the process are not held to this at all. They say where the flow
         * begins and ends and nothing about a plant, so a recipe in which they name equipment would be
         * claiming something they do not. {@link #checkReachability} is what holds them to being there.
         */
        private static void checkAddressing(S88Recipe recipe, S88RecipeElement element, String label,
                                            List<Finding> excess, List<Finding> deficit) {
        if (element.getKind() == S88RecipeElementKind.BEGIN
                || element.getKind() == S88RecipeElementKind.END) {
            return;
        }

        boolean hasClass = element.getEquipmentClassId() != null;
        int instances = element.getActualEquipmentIds().size();
        boolean addressesByClass = !(recipe instanceof S88MasterRecipe master) || master.addressesByClass();

        if (hasClass && instances == 0) {
            if (!addressesByClass) {
                excess.add(new Finding(label + " names a class of equipment in a recipe written for"
                    + " particular equipment. A recipe of this kind says which module it runs on.",
                    element.getId()));
            }
            return;
        }
        if (!hasClass && instances == 0) {
            if (addressesByClass) {
                deficit.add(new Finding(label + " is in a recipe written by class but names no class of"
                        + " equipment, so there is nothing to bind it to.", element.getId()));
            } else {
                deficit.add(new Finding(label + " is in a recipe written for particular equipment but"
                        + " names none, so there is nothing to run it on.", element.getId()));
            }
            return;
        }
        if (hasClass) {
            excess.add(new Finding(label + " names a class of equipment as well as a particular module."
                    + " One recipe addresses its equipment one way only.", element.getId()));
            return;
        }
        if (addressesByClass) {
            excess.add(new Finding(label + " names a particular module in a recipe written by class.",
                    element.getId()));
        }
    }

    private static void checkChart(S88Recipe recipe, S88ProcedureLogic logic,
                                   List<Finding> excess, List<Finding> deficit, List<Finding> warnings) {
        if (logic == null) {
            return;
        }

        for (String dangling : logic.getDanglingReferences()) {
            excess.add(new Finding("The chart has a line pointing at '" + dangling + "', which this"
                    + " recipe does not carry."));
        }
        for (S88ProcedureStep step : logic.getSteps()) {
            if (step.getRecipeElementId() != null && !step.getRecipeElementId().isBlank()
                    && recipe.findElement(step.getRecipeElementId()).isEmpty()) {
                excess.add(new Finding("Step '" + step.getId() + "' of the chart points at '"
                        + step.getRecipeElementId() + "', which this recipe does not carry.",
                        step.getId()));
            }
        }
        checkBranches(logic, excess);
        checkReachability(recipe, logic, excess, deficit);
        checkEnds(recipe, logic, excess);
        checkAlternation(logic, excess);
        checkNesting(logic, excess);
        checkLoops(logic, excess);
        checkExclusivity(logic, warnings);
    }

    /**
     * A link the recipe marks as splitting or rejoining has to have the sides that make it one.
     * <p>
     * A parallel divergent link with a single destination is a split that does not go anywhere, and
     * a convergent link with a single origin is a join that waits for something that never arrives.
     */
    private static void checkBranches(S88ProcedureLogic logic, List<Finding> excess) {
        for (S88ProcedureLink link : logic.getLinks()) {
            if (link.isDivergent() && link.getTo().size() < 2) {
                excess.add(new Finding("Link '" + link.getId() + "' is marked as splitting the flow"
                        + " but goes to " + link.getTo().size() + " side, so the branch has nothing on"
                        + " the other end.", link.getId()));
            }
            if (link.isConvergent() && link.getFrom().size() < 2) {
                excess.add(new Finding("Link '" + link.getId() + "' is marked as bringing the flow"
                        + " back together but comes from " + link.getFrom().size() + " side, so the"
                        + " join waits for something that never arrives.", link.getId()));
            }
        }
    }

    /**
     * Every line has to cross a bar: the flow leaves a box, waits at a bar and arrives at a box
     * again. A line from a box to a box, or from a bar to a bar, is one the flow has nothing to
     * follow on.
     * <p>
     * Only lines with both ends inside the chart are held to this. A line that leaves the chart to
     * name a step outside it says nothing about what has to alternate here.
     */
    private static void checkAlternation(S88ProcedureLogic logic, List<Finding> excess) {
        for (S88ProcedureLink link : StructureQueries.linesThatDoNotCross(logic)) {
            if (!hasOnlyInternalEnds(link)) {
                continue;
            }
            excess.add(new Finding("Line '" + link.getId() + "' joins "
                    + (isAllSteps(link.getFrom()) ? "two steps" : "two transitions")
                    + ", so the flow has nothing to wait at between them. A line runs from a step to a"
                    + " transition or from a transition to a step.", link.getId()));
        }
    }

    private static boolean hasOnlyInternalEnds(S88ProcedureLink link) {
        return link.getFrom().stream().allMatch(S88IdRef::isInternal)
                && link.getTo().stream().allMatch(S88IdRef::isInternal);
    }

    private static boolean isAllSteps(List<S88IdRef> ends) {
        return ends.stream().allMatch(end -> end.getType() == S88IdRefType.STEP);
    }

    /**
     * The sides a split opened are the ones that come back together.
     * <p>
     * A split is only looked at when at least one of its sides reaches a bar that brings sides back
     * together. A split whose sides reach none at all is one still being drawn, and nothing is said
     * about it. When some sides close and others do not, the ones that do and the ones that do not no
     * longer share a bar, so the block has been left without closing where it opened.
     */
    private static void checkNesting(S88ProcedureLogic logic, List<Finding> excess) {
        for (S88ProcedureLink open : logic.getLinks()) {
            if (!open.isDivergent() || open.getTo().size() < 2) {
                continue;
            }
            if (reachesAnyFrom(logic, open)) {
                continue;
            }
            List<Set<String>> closings = new ArrayList<>();
            boolean anySideCloses = false;
            for (S88IdRef side : open.getTo()) {
                Set<String> reached = convergentLinksReachableFrom(logic, side.getValue());
                anySideCloses |= !reached.isEmpty();
                closings.add(reached);
            }
            if (!anySideCloses) {
                continue;
            }
            Set<String> common = new LinkedHashSet<>(closings.getFirst());
            for (Set<String> reached : closings) {
                common.retainAll(reached);
            }
            if (common.isEmpty()) {
                excess.add(new Finding("The block opened at '" + open.getId() + "' does not come back"
                        + " together, so its sides leave it without closing where they opened.",
                        open.getId()));
            }
        }
    }

    /**
     * Whether a side of the split leads back to where the split opened, which makes it a retry
     * rather than a block. A retry is looked at by {@link #checkLoops} instead.
     */
    private static boolean reachesAnyFrom(S88ProcedureLogic logic, S88ProcedureLink open) {
        Set<String> targets = new LinkedHashSet<>();
        for (S88IdRef from : open.getFrom()) {
            targets.add(from.getValue());
        }
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (S88IdRef side : open.getTo()) {
            if (side.isInternal()) {
                pending.push(side.getValue());
            }
        }
        while (!pending.isEmpty()) {
            String at = pending.pop();
            if (at == null || !seen.add(at)) {
                continue;
            }
            if (targets.contains(at)) {
                return true;
            }
            for (S88ProcedureLink link : logic.getLinks()) {
                if (link.getFrom().stream().noneMatch(end -> at.equals(end.getValue()))) {
                    continue;
                }
                for (S88IdRef next : link.getTo()) {
                    if (next.isInternal()) {
                        pending.push(next.getValue());
                    }
                }
            }
        }
        return false;
    }

    /**
     * Every bar that brings sides back together and can be reached from one side of the chart,
     * following the lines forward.
     */
    private static Set<String> convergentLinksReachableFrom(S88ProcedureLogic logic, String start) {
        Set<String> found = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.push(start);
        while (!pending.isEmpty()) {
            String at = pending.pop();
            if (at == null || !seen.add(at)) {
                continue;
            }
            for (S88ProcedureLink link : logic.getLinks()) {
                if (link.getFrom().stream().noneMatch(end -> at.equals(end.getValue()))) {
                    continue;
                }
                if (link.isConvergent()) {
                    found.add(link.getId());
                }
                for (S88IdRef next : link.getTo()) {
                    if (next.isInternal()) {
                        pending.push(next.getValue());
                    }
                }
            }
        }
        return found;
    }

    /**
     * A retry may go back to an earlier step of its own block, but it may not run through a parallel
     * block: holding one branch back for another is the whole point of a parallel block, and a line
     * that goes back into or out of one takes that away.
     */
    private static void checkLoops(S88ProcedureLogic logic, List<Finding> excess) {
        for (S88ProcedureLink back : backLinks(logic)) {
            if (hasParallelEnd(logic, back)) {
                excess.add(new Finding("Line '" + back.getId() + "' goes back into or out of a"
                        + " parallel block, which a retry may not do.", back.getId()));
            }
        }
    }

    private static boolean hasParallelEnd(S88ProcedureLogic logic, S88ProcedureLink link) {
        for (S88IdRef end : link.getFrom()) {
            if (end.isInternal() && StructureQueries.isInsideParallel(logic, end.getValue())) {
                return true;
            }
        }
        for (S88IdRef end : link.getTo()) {
            if (end.isInternal() && StructureQueries.isInsideParallel(logic, end.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every line the flow goes back on, which is one that leads to a node the walk is still inside.
     * <p>
     * The walk colours a node grey while its lines are being followed and black once they all have
     * been, so a line to a grey node is one that closes a loop.
     */
    private static List<S88ProcedureLink> backLinks(S88ProcedureLogic logic) {
        Map<String, List<S88ProcedureLink>> leaving = new LinkedHashMap<>();
        Set<String> nodes = new LinkedHashSet<>();
        for (S88ProcedureLink link : logic.getLinks()) {
            for (S88IdRef from : link.getFrom()) {
                nodes.add(from.getValue());
                leaving.computeIfAbsent(from.getValue(), key -> new ArrayList<>()).add(link);
            }
            for (S88IdRef to : link.getTo()) {
                nodes.add(to.getValue());
            }
        }

        List<S88ProcedureLink> back = new ArrayList<>();
        Set<String> already = new LinkedHashSet<>();
        Set<String> grey = new LinkedHashSet<>();
        Set<String> black = new LinkedHashSet<>();
        for (String node : nodes) {
            if (!black.contains(node)) {
                colourForBackLinks(node, leaving, grey, black, back, already);
            }
        }
        return back;
    }

    private static void colourForBackLinks(String node, Map<String, List<S88ProcedureLink>> leaving,
                                           Set<String> grey, Set<String> black,
                                           List<S88ProcedureLink> back, Set<String> already) {
        grey.add(node);
        for (S88ProcedureLink link : leaving.getOrDefault(node, List.of())) {
            for (S88IdRef next : link.getTo()) {
                String target = next.getValue();
                if (grey.contains(target)) {
                    if (already.add(link.getId())) {
                        back.add(link);
                    }
                } else if (!black.contains(target)) {
                    colourForBackLinks(target, leaving, grey, black, back, already);
                }
            }
        }
        grey.remove(node);
        black.add(node);
    }

    /**
     * The transitions of a divergence that is taken one way or the other have to be told apart, by
     * their conditions or by being read left to right. A bar that waits on nothing cannot be told
     * apart from the next, so the author is told and the recipe is left alone.
     */
    private static void checkExclusivity(S88ProcedureLogic logic, List<Finding> warnings) {
        for (S88ProcedureLink split : logic.getLinks()) {
            if (!split.isDivergent() || split.getLinkType().isParallel()
                    || split.getTo().size() < 2) {
                continue;
            }
            List<String> unguarded = new ArrayList<>();
            for (S88IdRef side : split.getTo()) {
                Optional<S88ProcedureTransition> bar = logic.findTransition(side.getValue());
                if (bar.isPresent() && !bar.get().isGuarded()) {
                    unguarded.add(bar.get().getId());
                }
            }
            if (!unguarded.isEmpty()) {
                warnings.add(new Finding("The sides of '" + split.getId() + "' are taken one after"
                        + " another, but " + String.join(", ", unguarded) + " wait on nothing, so"
                        + " which one is taken cannot be told and they are read left to right.",
                        split.getId()));
            }
        }
    }

    /**
     * The chart has to start somewhere, and every box has to be reachable from where it starts.
     * <p>
     * The walk alternates between boxes and bars. The flow leaves a box, crosses a bar, and arrives
     * at a box. A line drawn straight from one box to another has nothing to follow, which is
     * why {@link #checkAlternation} reports one.
     * <p>
     * A bar that waits on nothing is crossed as soon as the box before it has finished, so it is
     * walked through like any other. A chart may loop back to an earlier box retrying. Entering
     * a box twice ends the walk avoiding a forever loop.
     */
    private static void checkReachability(S88Recipe recipe, S88ProcedureLogic logic,
                                          List<Finding> excess, List<Finding> deficit) {
        List<S88ProcedureStep> steps = logic.getSteps();
        if (steps.isEmpty()) {
            return;
        }

        S88ProcedureStep begin = firstOfKind(recipe, logic, S88RecipeElementKind.BEGIN);
        S88ProcedureStep end = firstOfKind(recipe, logic, S88RecipeElementKind.END);
        if (begin == null) {
            deficit.add(new Finding("The chart has no step whose element is of kind Begin, so the"
                    + " flow has nowhere to start."));
        }
        if (end == null) {
            deficit.add(new Finding("The chart has no step whose element is of kind End, so the flow"
                    + " has nowhere to stop."));
        }

        Set<String> reachable = new LinkedHashSet<>();
        if (begin != null) {
            walkFrom(begin.getId(), logic, reachable);
        }
        for (S88ProcedureStep step : steps) {
            if (step.getId() != null && !reachable.contains(step.getId())) {
                excess.add(new Finding("Step '" + step.getId() + "' of the chart is not reachable from"
                        + " the start, so the process would never perform it.", step.getId()));
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

    /**
     * Every step of the chart whose element is of the given kind.
     */
    private static List<S88ProcedureStep> allOfKind(S88Recipe recipe, S88ProcedureLogic logic,
                                                     S88RecipeElementKind kind) {
        List<S88ProcedureStep> found = new ArrayList<>();
        for (S88ProcedureStep step : logic.getSteps()) {
            if (recipe.findElement(step.getRecipeElementId())
                    .map(element -> element.getKind() == kind)
                    .orElse(Boolean.FALSE)) {
                found.add(step);
            }
        }
        return found;
    }

     /**
     * A process has one place it starts and one place it stops.
     */
    private static void checkEnds(S88Recipe recipe, S88ProcedureLogic logic, List<Finding> excess) {
        if (logic == null) {
            return;
        }
        for (S88ProcedureElementKindPair ends : S88ProcedureElementKindPair.BOTH) {
            List<S88ProcedureStep> found = allOfKind(recipe, logic, ends.kind());
            if (found.size() > 1) {
                excess.add(new Finding("The chart has " + found.size() + " steps whose element is of"
                        + " kind " + ends.word() + ", so it has more than one place the flow "
                        + (ends.kind() == S88RecipeElementKind.BEGIN ? "starts" : "stops") + "."));
            }
        }
    }

    /**
     * The two kinds of step that bound a process, which the ends rule is written once for.
     *
     * @param kind what kind of step
     * @param word how it is called in a sentence about it
     */
    private record S88ProcedureElementKindPair(S88RecipeElementKind kind, String word) {

        private static final List<S88ProcedureElementKindPair> BOTH = List.of(
                new S88ProcedureElementKindPair(S88RecipeElementKind.BEGIN, "Begin"),
                new S88ProcedureElementKindPair(S88RecipeElementKind.END, "End"));
    }

    @Override
    public String toString() {
        return "RecipeConformance[" + excess.size() + " excess, " + deficit.size() + " deficit, "
                + warnings.size() + " warning]";
    }
}
