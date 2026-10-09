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

import org.apache.plc4x.malbec.s88.api.S88ConditionExpression;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88IdRefType;
import org.apache.plc4x.malbec.s88.api.S88IdScope;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88Recipe;
import org.apache.plc4x.malbec.s88.api.S88RecipeChangeEvent;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Drawing the chart of a recipe: the boxes, the lines between them, and the bars.
 * <p>
 * A box has to name a step the recipe carries. A line has to end somewhere that exists,
 * unless it says it points outside, which is how a line refers to a step of another part of the
 * process.
 * <p>
 * The one thing that is not checked is whether the chart makes sense as a process: whether it starts
 * somewhere, whether a split has two sides, whether a step is reachable. That is
 * {@code RecipeConformance}'s job.
 * <p>
 * A chart belongs to the step it was drawn under, and a step of the chart is a box on it. They are
 * named separately. One names a step of the recipe and the other names a box.
 */
public class EditProcedureLogicUseCase {

    private EditProcedureLogicUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * The chart of a step, made if it is not there yet.
     *
     * @param step step to give a chart to, may be {@code null}
     * @return the chart, or {@code null} when there was no step
     */
    public static S88ProcedureLogic chartOf(S88RecipeElement step) {
        return chartOf(null, step);
    }

    /**
     * The chart being drawn on, made if it is not there yet.
     * <p>
     * A recipe can carry a chart of its own, and that chart is where the process as a whole is
     * drawn. It is reached by passing no step at all, so that the methods below work the same on
     * it as on the chart of any step, instead of the recipe's own chart being the one thing in the
     * model that can be read but not drawn on.
     *
     * @param recipe recipe the chart would belong to, may be {@code null}
     * @param step   step whose chart is wanted, {@code null} for the chart of the recipe itself
     * @return the chart, or {@code null} when neither a recipe nor a step was given
     */
    public static S88ProcedureLogic chartOf(S88Recipe recipe, S88RecipeElement step) {
        if (step == null) {
            if (recipe == null) {
                return null;
            }
            if (!recipe.hasProcedureLogic()) {
                recipe.setProcedureLogic(new S88ProcedureLogic());
            }
            return recipe.getProcedureLogic();
        }
        if (!step.hasProcedureLogic()) {
            step.setProcedureLogic(new S88ProcedureLogic());
        }
        return step.getProcedureLogic();
    }

    /**
     * The chart being read, without making one.
     * <p>
     * Reading and writing are kept apart on purpose. Anything that reads has to be able to find
     * nothing, because the thing it was looking for may not be there, and a method that made a
     * chart to search would leave an empty one behind every time it found nothing.
     *
     * @param recipe recipe the chart would belong to, may be {@code null}
     * @param step   step whose chart is wanted, {@code null} for the chart of the recipe itself
     * @return the chart if there is one, otherwise {@code null}
     */
    private static S88ProcedureLogic existingChart(S88Recipe recipe, S88RecipeElement step) {
        return step != null ? step.getProcedureLogic() : (recipe != null ? recipe.getProcedureLogic() : null);
    }

    /**
     * Puts a box on the chart, working on a step of the recipe.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart is being drawn on
     * @param boxId  name of the box, unique within this chart
     * @param elementId name of the step of the recipe the box works on
     * @return the box
     * @throws IllegalArgumentException when there is no step, the name is empty or already used, or
     *                                  the recipe carries no step of that name
     */
    public static S88ProcedureStep addStep(S88Recipe recipe, S88RecipeElement step,
                                           String boxId, String elementId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (boxId == null || boxId.isBlank()) {
            throw new IllegalArgumentException("A box on the chart needs a name.");
        }
        if (chart.findStep(boxId).isPresent()) {
            throw new IllegalArgumentException("The chart already has a box called '" + boxId + "'.");
        }
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("A box has to work on a step of the recipe.");
        }
        if (recipe != null && recipe.findElement(elementId).isEmpty()) {
            throw new IllegalArgumentException("This recipe has no step called '" + elementId
                    + "', so there is nothing for the box to work on.");
        }
        S88ProcedureStep box = new S88ProcedureStep(boxId, elementId);
        chart.addStep(box);
        announce(recipe);
        return box;
    }

    /**
     * Takes a box off the chart, and refuses while a line runs into it.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the box is on
     * @param boxId  name of the box
     * @throws IllegalStateException when a line still runs into the box
     */
    public static void removeStep(S88Recipe recipe, S88RecipeElement step, String boxId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureStep box = chart != null ? chart.findStep(boxId).orElse(null) : null;
        if (box == null) {
            return;
        }
        List<S88ProcedureLink> lines = chart.linksFrom(boxId);
        if (!lines.isEmpty()) {
            throw new IllegalStateException("Box '" + boxId + "' is still joined by line "
                    + lines.stream().map(S88ProcedureLink::getId).toList()
                    + ". Take those off first, or the chart would be left with a line that has"
                    + " nothing at one end.");
        }
        chart.removeStep(box);
        announce(recipe);
    }

    /**
     * Draws a line between two things on the chart.
     * <p>
     * One end has to be a box and the other a bar. A line straight from box to box is refused.
     *
     * @param recipe  recipe the step belongs to, may be {@code null}
     * @param step    step whose chart is being drawn on
     * @param lineId  name of the line, unique within this chart
     * @param fromIds what the line leaves, at least one, all the same kind
     * @param toIds   what it arrives at, at least one, all the same kind
     * @param type    what it means, {@code null} for an ordinary flow of control
     * @return the line
     * @throws IllegalArgumentException when the name is empty, already used, an end is missing, or
     *                                  an end names something the chart does not carry
     * @throws IllegalStateException    when both ends are of the same kind, which is a chart with
     *                                  no bar between two steps
     */
    public static S88ProcedureLink addLink(S88Recipe recipe, S88RecipeElement step, String lineId,
                                           List<String> fromIds, List<String> toIds, S88LinkType type) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (lineId == null || lineId.isBlank()) {
            throw new IllegalArgumentException("A line needs a name.");
        }
        if (chart.findLink(lineId).isPresent()) {
            throw new IllegalArgumentException("The chart already has a line called '" + lineId + "'.");
        }
        if (fromIds == null || fromIds.isEmpty()) {
            throw new IllegalArgumentException("A line has to leave something.");
        }
        if (toIds == null || toIds.isEmpty()) {
            throw new IllegalArgumentException("A line has to arrive somewhere.");
        }

        boolean fromAreBars = kindOf(chart, fromIds) == S88IdRefType.TRANSITION;
        boolean toAreBars = kindOf(chart, toIds) == S88IdRefType.TRANSITION;
        if (fromAreBars == toAreBars) {
            throw new IllegalStateException("A line cannot join two boxes or two bars. Between one"
                    + " box and the next there is always a bar, and a bar with nothing to wait on is"
                    + " how a chart says it goes straight on.");
        }
        S88ProcedureLink line = new S88ProcedureLink(lineId);
        for (String from : fromIds) {
            line.addFrom(existingEnd(chart, from, "leaves"));
        }
        for (String to : toIds) {
            line.addTo(existingEnd(chart, to, "arrives at"));
        }
        if (type != null) {
            line.setLinkType(type);
        }
        chart.addLink(line);
        announce(recipe);
        return line;
    }

    /**
     * Puts a step between a step and whatever the flow went on to.
     * <p>
     * This splits the line leaving the step rather than adding to either end of the chart, so a step
     * can be put where it belongs after the fact instead of having to be planned before the one it
     * follows exists.
     * <p>
     * <b>One bar is added, not two.</b> The line that is split ran from a box to a bar, and what it
     * has to become is a box to a bar to a box to that same bar: the bar that was already there
     * becomes the one the flow waits at after the new step. Putting a bar on each side instead would
     * leave the flow going from a bar to a bar, which is the one thing this chart cannot have.
     * <p>
     * A step with nothing after it gets the new step and the bar, and the bar hangs at the end for
     * the operator to carry on from. That is the same shape, with nothing to reattach.
     *
     * @param recipe     recipe being edited, may be {@code null} when no change event is wanted
     * @param step       step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param afterBoxId box of the chart the step goes after
     * @param newStepId  name for the step of the recipe being added
     * @param newBoxId   name for its box on the chart
     * @param barId      name for the bar the flow waits at before the new step
     * @param classId    class of equipment the new step works on, {@code null} for a step that names
     *                   none
     * @return the step that was added to the recipe
     * @throws IllegalArgumentException when there is no recipe, a name is missing or already used, or
     *                                  the box is not on this chart
     * @throws IllegalStateException    when the step to go after already goes to more than one place,
     *                                  which cannot be split without choosing between them
     */
    public static S88RecipeElement insertStepAfter(S88Recipe recipe, S88RecipeElement step,
                                                   String afterBoxId, String newStepId,
                                                   String newBoxId, String barId, String classId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to insert into.");
        }
        if (!chart.findStep(afterBoxId).isPresent()) {
            throw new IllegalArgumentException("This chart has no box called '" + afterBoxId + "'.");
        }
        requireRoomAfter(recipe, chart, afterBoxId);

        List<S88ProcedureLink> onward = leaving(chart, afterBoxId);
        if (onward.size() > 1) {
            // More than one thing leaves this step, so it is already a fork. Which side the new step
            // belongs to is a decision this method cannot make, and guessing it would put the step
            // somewhere the author did not mean.
            throw new IllegalStateException("Box '" + afterBoxId + "' already goes to "
                    + onward.size() + " places, so there is no single flow to put a step in the"
                    + " middle of.");
        }
        List<String> whatFollowed = List.of();
        if (onward.size() == 1 && onward.get(0).getTo().size() == 1) {
            whatFollowed = List.of(onward.get(0).getTo().get(0).getValue());
        }
        if (onward.size() == 1 && onward.get(0).getTo().size() > 1) {
            // One line leaving this box with more than one arrival is a bifurcation that sits on
            // this box, and there is no single flow to put a step in the middle of. Carrying on as
            // if there were one arrival would take the other branch off the chart without saying so.
            throw new IllegalStateException("Box '" + afterBoxId + "' goes to "
                    + onward.get(0).getTo().size() + " bars, so it is a branch point and there is no"
                    + " single flow to put a step in. Pick a step inside one of the branches."
                    + " The branches are " + namesOf(onward.get(0).getTo()) + ".");
        }

        // Everything that can be refused is refused before anything is touched. Taking the line off
        // first is what leaves a chart holding a line to nothing when the step that was going to
        // replace it turns out to be named something that is already taken.
        requireFreeStepName(recipe, newStepId);
        requireFreeName(chart, newBoxId, "box");
        requireFreeName(chart, barId, "bar");

        S88LinkType onwardType = onward.isEmpty() ? null : onward.get(0).getLinkType();
        // Only this box is taken off the line, not the whole line. A line leaving several boxes is
        // the convergence of a branch, and taking it off whole would take the other branches with
        // it. The line goes only when this box was the only one leaving it.
        S88ProcedureLink shared = detachEnd(chart, onward.isEmpty() ? null : onward.get(0), afterBoxId);

        S88RecipeElement added = CreateRecipeElementUseCase.forEquipmentClass(
                recipe, step, newStepId, S88RecipeElementKind.OPERATION, classId);
        addStep(recipe, step, newBoxId, newStepId);
        addTransition(recipe, step, barId);
        addLink(recipe, step, afterBoxId + "_TO_" + barId,
                List.of(afterBoxId), List.of(barId), null);
        // The bar the flow waits at has to lead into the new step. Without this line the flow stops
        // at a bar with nothing after it, and the step that was just added is a box nothing reaches.
        addLink(recipe, step, barId + "_TO_" + newBoxId,
                List.of(barId), List.of(newBoxId), null);
        if (!whatFollowed.isEmpty()) {
            // The new step joins the convergence the other branches arrive on, rather than getting a
            // line of its own into the same bar, which would draw the same thing as two separate
            // things arriving there.
            if (shared != null) {
                putWidened(chart, shared, newBoxId);
            } else {
                addLink(recipe, step, newBoxId + "_TO_" + whatFollowed.get(0),
                        List.of(newBoxId), whatFollowed, onwardType);
            }
        }
        announce(recipe);
        return added;
    }

    /** Puts one more departure on a line the other branches of a branch are already arriving on. */
    private static void putWidened(S88ProcedureLogic chart, S88ProcedureLink line, String boxId) {
        S88ProcedureLink wider = new S88ProcedureLink(line.getId());
        line.getFrom().forEach(wider::addFrom);
        line.getTo().forEach(wider::addTo);
        wider.addFrom(S88IdRef.step(boxId));
        wider.setLinkType(line.getLinkType());
        chart.removeLink(line);
        chart.addLink(wider);
    }

    /**
     * Takes one end off a line and puts the line back, or takes the line off when nothing else is
     * leaving or arriving at the same thing.
     *
     * @return the line left on the chart with this end taken off, or {@code null} when the line went
     */
    private static S88ProcedureLink detachEnd(S88ProcedureLogic chart, S88ProcedureLink line,
                                              String name) {
        if (line == null) {
            return null;
        }
        if (line.getFrom().size() <= 1) {
            chart.removeLink(line);
            return null;
        }
        List<S88IdRef> remaining = new ArrayList<>();
        line.getFrom().stream().filter(end -> !name.equals(end.getValue())).forEach(remaining::add);
        S88ProcedureLink kept = new S88ProcedureLink(line.getId());
        remaining.forEach(kept::addFrom);
        line.getTo().forEach(kept::addTo);
        kept.setLinkType(line.getLinkType());
        chart.removeLink(line);
        chart.addLink(kept);
        return kept;
    }

    /**
 * Whether another step can go after a box.
 *
 * <p>
 * <b>The stop of the process has nothing after it.</b> A step after the end is a process that carries
 * on after it has stopped, and the chart would show a flow leaving a ground symbol. The start is the
 * opposite case and is left alone: a step after the start is the ordinary first step of a recipe.
 *
 * @throws IllegalStateException when the box is where the flow stops
 */
    private static void requireRoomAfter(S88Recipe recipe, S88ProcedureLogic chart, String boxId) {
        String elementId = chart.findStep(boxId).orElseThrow().getRecipeElementId();
        boolean stops = recipe.findElement(elementId)
                .map(element -> element.getKind() == S88RecipeElementKind.END)
                .orElse(Boolean.FALSE);
        if (stops) {
            throw new IllegalStateException("Step '" + boxId + "' is where the flow stops, so there is"
                    + " nothing to put after it.");
        }
    }

    /**
     * Whether a step of the recipe can be given that name.
     *
     * @throws IllegalArgumentException when the recipe already carries one
     */
    private static void requireFreeStepName(S88Recipe recipe, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A step of the recipe needs a name.");
        }
        if (recipe != null && recipe.findElement(name).isPresent()) {
            throw new IllegalArgumentException("This recipe already has a step called '" + name
                    + "', and two steps cannot share a name.");
        }
    }

    /**
 * Whether a box or a bar of the chart can be given that name.
 *
     * @throws IllegalArgumentException when the name is blank or the chart already carries it
     */
    private static void requireFreeName(S88ProcedureLogic chart, String name, String what) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A " + what + " on the chart needs a name.");
        }
        if (chart.findStep(name).isPresent() || chart.findTransition(name).isPresent()) {
            throw new IllegalArgumentException("The chart already has a " + what + " called '"
                    + name + "'.");
        }
    }

    /**
     * @throws IllegalArgumentException when the chart does not carry a box or a bar of that name
     */
    private static void requireOnChart(S88ProcedureLogic chart, String name, String what) {
        boolean there = chart.findStep(name).isPresent() || chart.findTransition(name).isPresent();
        if (!there) {
            throw new IllegalArgumentException("This chart has no " + what + " called '" + name
                    + "'.");
        }
    }

private static String namesOf(List<S88IdRef> ends) {
        return ends.stream().map(S88IdRef::getValue).toList().toString();
    }

    /**
     * The lines that leave a thing, and not the ones that arrive at it.
     * <p>
     * Needed because {@link S88ProcedureLogic#linksFrom(String)} answers a different and wider
     * question than its name suggests: it gives the lines that touch the box at either end, which is
     * right when checking whether a box can be removed and wrong when checking where the flow goes.
     */
    private static List<S88ProcedureLink> leaving(S88ProcedureLogic chart, String id) {        List<S88ProcedureLink> leaving = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            for (S88IdRef end : link.getFrom()) {
                if (id.equals(end.getValue())) {
                    leaving.add(link);
                    break;
                }
            }
        }
        return leaving;
    }

    /**
     * Splits the flow leaving a step into two branches, each with its own bar.
     * <p>
     * <b>A step that leaves towards more than one bar is a selective split.</b> Only one of the
     * branches is taken, and which one is what the comparison on each bar decides. The two are one
     * line with two arrivals rather than two lines, because a line with two arrivals is what says
     * "one of these" and two lines out of a step say nothing about how many are taken.
     * <p>
     * Each branch needs a bar, and the operator fills in the comparison on each. Until they do, both
     * branches cross straight away and the chart waits on nothing.
     *
     * @param recipe     recipe being edited, may be {@code null} when no change event is wanted
     * @param step       step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param fromBoxId  box the flow leaves from
     * @param firstBar   name of the bar on one side
     * @param secondBar  name of the bar on the other
     * @return the line carrying the split
     * @throws IllegalArgumentException when there is no recipe, a name is missing or already used, or
     *                                  the box is not on this chart
     */
    public static S88ProcedureLink selectiveFork(S88Recipe recipe, S88RecipeElement step,
                                                 String fromBoxId, String firstBar,
                                                 String secondBar) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to split.");
        }
        requireOnChart(chart, fromBoxId, "box");
        addTransition(recipe, step, firstBar);
        addTransition(recipe, step, secondBar);
        return addLink(recipe, step, fromBoxId + "_TO_" + firstBar + "_OR_" + secondBar,
                List.of(fromBoxId), List.of(firstBar, secondBar), S88LinkType.SERIAL_DIVERGENT);
    }

    /**
     * Splits the flow leaving a bar into two branches, each with its own step.
     * <p>
     * <b>A bar that leaves towards more than one step is a parallel split.</b> Both branches are
     * taken at once, which is the whole difference between this and {@link #selectiveFork}, and it
     * is what the drawing shows as one bar across the branches rather than two.
     * <p>
     * A bar on a chart that reads anywhere near right almost always already leads somewhere, so the
     * split does not leave what it used to lead to stranded: it is moved behind a new bar that
     * both branches arrive at. A bar that led nowhere gets no such bar, because there is nothing to
     * carry on.
     * <p>
     * The branches need recipe steps of their own, which are made here rather than asked for,
     * because a box working on nothing is not a step. The boxes are named after them the same way
     * the rest of the module names a box.
     *
     * @param recipe    recipe being edited, may be {@code null} when no change event is wanted
     * @param step      step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param fromBarId bar the flow leaves
     * @param firstId   name of the step on one side
     * @param secondId  name of the step on the other
     * @return the line carrying the split
     * @throws IllegalArgumentException when there is no recipe, a name is missing or already used, or
     *                                  the bar is not on this chart
     */
    public static S88ProcedureLink parallelFork(S88Recipe recipe, S88RecipeElement step,
                                                String fromBarId, String firstId,
                                                String secondId) {
        return parallelFork(recipe, step, fromBarId, firstId, secondId, null);
    }

    /**
     * Splits the flow leaving a bar into two branches, naming the bar they come back together on.
     *
     * @param recipe    recipe being edited, may be {@code null} when no change event is wanted
     * @param step      step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param fromBarId bar the flow leaves
     * @param firstId   name of the step on one side
     * @param secondId  name of the step on the other
     * @param joinBarId name for the bar both branches come back together on, {@code null} to make
     *                  one up when the split needs it
     * @return the line carrying the split
     * @throws IllegalArgumentException when there is no recipe, a name is missing or already used, or
     *                                  the bar is not on this chart
     * @see #parallelFork(S88Recipe, S88RecipeElement, String, String, String)
     */
    public static S88ProcedureLink parallelFork(S88Recipe recipe, S88RecipeElement step,
                                                String fromBarId, String firstId,
                                                String secondId, String joinBarId) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to split.");
        }
        requireOnChart(chart, fromBarId, "bar");
        if (firstId != null && firstId.equals(secondId)) {
            throw new IllegalStateException("A split leads towards two different steps, not one step"
                    + " twice.");
        }
        String firstBox = boxNameOf(firstId);
        String secondBox = boxNameOf(secondId);
        requireFreeName(chart, firstBox, "box");
        requireFreeName(chart, secondBox, "box");
        List<S88ProcedureLink> wasLeadingTo = linksLeaving(chart, fromBarId);

        CreateRecipeElementUseCase.forEquipmentClass(
                recipe, step, firstId, S88RecipeElementKind.OPERATION, null);
        CreateRecipeElementUseCase.forEquipmentClass(
                recipe, step, secondId, S88RecipeElementKind.OPERATION, null);
        addStep(recipe, step, firstBox, firstId);
        addStep(recipe, step, secondBox, secondId);
        if (!wasLeadingTo.isEmpty()) {
            String carriedOn = joinBarId;
            if (carriedOn == null || carriedOn.isBlank()) {
                carriedOn = freeName(chart, fromBarId + "_BACK_TOGETHER", "bar");
            } else {
                requireFreeName(chart, carriedOn, "bar");
            }
            addTransition(recipe, step, carriedOn);
            addLink(recipe, step, firstBox + "_AND_" + secondBox + "_TO_" + carriedOn,
                    List.of(firstBox, secondBox), List.of(carriedOn),
                    S88LinkType.PARALLEL_CONVERGENT);
            // What the bar used to lead to is moved onto the new bar, keeping the line and its
            // arrival, rather than left where it was: a bar leading both into the branches and
            // straight past them would say the branches can be skipped, which is not what a
            // parallel split says.
            carryOnAfter(recipe, step, wasLeadingTo, carriedOn);
        }

        return addLink(recipe, step, fromBarId + "_TO_" + firstBox + "_AND_" + secondBox,
                List.of(fromBarId), List.of(firstBox, secondBox), S88LinkType.PARALLEL_DIVERGENT);
    }

    /**
     * Moves what several nodes used to lead to onto one bar behind them.
     * <p>
     * The lines keep their identity and their arrival, so nothing about how the flow goes on changes
     * except that it now goes on from one place rather than from several.
     *
     * @param wasLeaving the lines to move, taken before anything was added
     * @param carriedOn  bar they leave from now
     */
    private static void carryOnAfter(S88Recipe recipe, S88RecipeElement step,
                                     List<S88ProcedureLink> wasLeaving, String carriedOn) {
        for (S88ProcedureLink line : wasLeaving) {
            List<String> arrivedAt = new ArrayList<>();
            line.getTo().forEach(end -> arrivedAt.add(end.getValue()));
            S88LinkType wasType = line.getLinkType();
            removeLink(recipe, step, line.getId());
            addLink(recipe, step, line.getId(), List.of(carriedOn), arrivedAt, wasType);
        }
    }

    /**
     * The lines that leave one end of the chart, in the order the chart holds them.
     */
    private static List<S88ProcedureLink> linksLeaving(S88ProcedureLogic chart, String name) {
        List<S88ProcedureLink> leaving = new ArrayList<>();
        for (S88ProcedureLink link : chart.getLinks()) {
            boolean startsHere = link.getFrom().stream()
                    .anyMatch(end -> name.equals(end.getValue()));
            if (startsHere) {
                leaving.add(link);
            }
        }
        return leaving;
    }

    /**
     * The box that works on a step of the recipe, named the way this module names one.
     */
    private static String boxNameOf(String elementId) {
        return elementId == null || elementId.isBlank() ? elementId : "BOX_" + elementId;
    }

    /**
     * A name of that shape that this chart is not already using.
     */
    private static String freeName(S88ProcedureLogic chart, String wanted, String what) {
        String candidate = wanted;
        int more = 2;
        while (chart.findStep(candidate).isPresent() || chart.findTransition(candidate).isPresent()) {
            candidate = wanted + "_" + more++;
        }
        return candidate;
    }

    /**
     * Brings two steps together onto one bar.
     * <p>
     * One line with two departures, because a line with two departures is what says "these two are
     * waited for" and the type says whether they are waited for together or one at a time.
     * <p>
     * <b>The new bar leads nowhere until the author draws where the flow goes on.</b> Unlike a
     * parallel split, there is nothing here that can be carried on for the author: a step always
     * leads to a bar, so what the two of them led to is already bars, and moving those behind a bar
     * would give a chart with bars leading to bars. Which way the flow leaves a join is the author's
     * choice, so it is left to them rather than guessed.
     *
     * @param recipe    recipe being edited, may be {@code null} when no change event is wanted
     * @param step      step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param firstBox  box on one side
     * @param secondBox box on the other
     * @param toBarId   bar both of them arrive at
     * @param type      {@link S88LinkType#SERIAL_CONVERGENT} to wait for whichever arrives first,
     *                  {@link S88LinkType#PARALLEL_CONVERGENT} to wait for both
     * @return the line that joins them
     * @throws IllegalArgumentException when there is no recipe, a name is missing or already used, or
     *                                  a box or bar is not on this chart
     */
    public static S88ProcedureLink joinOnto(S88Recipe recipe, S88RecipeElement step,
                                            String firstBox, String secondBox, String toBarId,
                                            S88LinkType type) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null || recipe == null) {
            throw new IllegalArgumentException("There is no chart to join.");
        }
        if (firstBox != null && firstBox.equals(secondBox)) {
            throw new IllegalStateException("A join brings two different boxes together, not one"
                    + " box with itself.");
        }
        addTransition(recipe, step, toBarId);
        return addLink(recipe, step, firstBox + "_AND_" + secondBox + "_TO_" + toBarId,
                List.of(firstBox, secondBox), List.of(toBarId),
                type == null ? S88LinkType.PARALLEL_CONVERGENT : type);
    }

    /**
     * Puts the comparison a bar waits on, as the text a recipe carries.
     *
     * @param recipe  recipe being edited, may be {@code null} when no change event is wanted
     * @param step    step whose chart is being drawn on, {@code null} for the chart of the recipe
     * @param barId   bar whose comparison is being set
     * @param text    what the bar waits on, {@code null} or empty for a bar that waits on nothing
     * @throws IllegalArgumentException when the bar is not on this chart or the text is not a
     *                                  comparison
     */
    public static void setCondition(S88Recipe recipe, S88RecipeElement step, String barId,
                                    String text) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureTransition bar = chart != null ? chart.findTransition(barId).orElse(null) : null;
        if (bar == null) {
            throw new IllegalArgumentException("This chart has no bar called '" + barId + "'.");
        }
        boolean nothing = text == null || text.trim().isEmpty();
        if (nothing) {
            bar.setExpression(null);
            bar.setCondition(null);
        } else {
            S88ConditionExpression expression = S88ConditionExpression.parse(text);
            if (expression == null) {
                throw new IllegalArgumentException("'" + text.trim() + "' is not something a bar"
                        + " can wait on. It is written as ADDRESS#COMPARISON#LITERAL, for"
                        + " example Reports/STATE#=#COMPLETE");
            }
            bar.setExpression(expression);
            bar.setCondition(expression.toText());
        }
        announce(recipe);
    }

    /**
     * Whether a chart can be drawn as it stands: every line joining a box to a bar.
     *
     * @param chart chart to look at, may be {@code null}
     * @return true when there is no line straight from one box to another
     */
    public static boolean isChartBipartite(S88ProcedureLogic chart) {
        if (chart == null) {
            return true;
        }
        for (S88ProcedureLink link : chart.getLinks()) {
            // Every end on both sides, not only the first: a line with one bar and one step among
            // its ends still does not cross between the two kinds, and looking at the first one
            // only says what the author happened to put first.
            for (S88IdRef from : link.getFrom()) {
                for (S88IdRef to : link.getTo()) {
                    if (from.getType() == to.getType()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * What all the names of one end of a line are, which has to agree: a line either leaves several
     * boxes or leaves several bars, and a line that left both would have two ends of every kind and
     * would draw as two lines sharing a name.
     * <p>
     * Every name is checked against the chart first, so that a name nobody has is reported as the
     * typo it is instead of being taken for a box.
     *
     * @param chart chart the names are on
     * @param names names of one end
     * @return what they all are
     * @throws IllegalArgumentException when a name is not on the chart, or the names are not all the
     *                                  same kind of thing
     */
    private static S88IdRefType kindOf(S88ProcedureLogic chart, List<String> names) {
        for (String name : names) {
            if (name == null || name.isBlank() || isOutside(name)) {
                continue;
            }
            if (chart.findStep(name).isEmpty() && chart.findTransition(name).isEmpty()) {
                throw new IllegalArgumentException("The chart has no box or bar called '" + name
                        + "' for a line to point at.");
            }
        }
        S88IdRefType kind = chart.findTransition(names.get(0)).isPresent()
                ? S88IdRefType.TRANSITION : S88IdRefType.STEP;
        for (String name : names) {
            S88IdRefType one = chart.findTransition(name).isPresent()
                    ? S88IdRefType.TRANSITION : S88IdRefType.STEP;
            if (one != kind) {
                throw new IllegalArgumentException("The names '" + String.join("', '", names)
                        + "' are not all the same kind of thing on this chart, so they cannot all be"
                        + " one end of the same line.");
            }
        }
        return kind;
    }

    private static boolean isOutside(String name) {
        return name.startsWith("!") || name.startsWith("#");
    }

    /**
     * Takes a line off the chart, and says so even when there was none.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the line is on
     * @param lineId name of the line
     * @return true when there was such a line and it is gone
     */
    public static boolean removeLink(S88Recipe recipe, S88RecipeElement step, String lineId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureLink line = chart != null ? chart.findLink(lineId).orElse(null) : null;
        if (line == null) {
            return false;
        }
        chart.removeLink(line);
        announce(recipe);
        return true;
    }

    /**
     * Moves one end of a line to somewhere else, for dragging a box across the chart.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the line is on
     * @param lineId name of the line
     * @param atEnd  whether it is the end the line arrives at that moves
     * @param newId  what it now names, which has to be on the chart
     * @throws IllegalArgumentException when the line is not there, or the new end is not
     */
    public static void moveLinkEnd(S88Recipe recipe, S88RecipeElement step, String lineId,
                                   boolean atEnd, String newId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureLink line = chart != null ? chart.findLink(lineId).orElse(null) : null;
        if (line == null) {
            throw new IllegalArgumentException("The chart has no line called '" + lineId + "'.");
        }
        S88IdRef moved = existingEnd(chart, newId, atEnd ? "arrives at" : "leaves");
        List<S88IdRef> ends = atEnd ? line.getTo() : line.getFrom();
        if (ends.isEmpty()) {
            throw new IllegalArgumentException("Line '" + lineId + "' has no end at that side to move.");
        }
        S88IdRef target = ends.get(0);
        int at = ends.indexOf(target);
if (atEnd) {
            line.removeTo(target);
            List<S88IdRef> to = new ArrayList<>(line.getTo());
            to.add(at, moved);
            line.clearTo();
            line.addTo(to.toArray(new S88IdRef[0]));
        } else {
            line.removeFrom(target);
            List<S88IdRef> from = new ArrayList<>(line.getFrom());
            from.add(at, moved);
            line.clearFrom();
            line.addFrom(from.toArray(new S88IdRef[0]));
        }
        // Back through the chart, because moving an end is a change the chart cannot see otherwise
        // and it works out which ends name nothing from them.
        chart.removeLink(line);
        chart.addLink(line);
        announce(recipe);
    }

    /**
     * Puts a bar on the chart that waits on nothing, which is the bar a chart draws between two
     * steps that simply follow one another. There is always a bar between two boxes; a bar that
     * waits on nothing is how it says it goes straight on.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart is being drawn on
     * @param barId  name of the bar, unique within this chart
     * @return the bar
     */
    public static S88ProcedureTransition addTransition(S88Recipe recipe, S88RecipeElement step,
                                                      String barId) {
        return addTransition(recipe, step, barId, null);
    }

    /**
     * Puts a bar on the chart that waits on something, which is where the recipe reads the plant.
     *
     * @param recipe     recipe the step belongs to, may be {@code null}
     * @param step       step whose chart is being drawn on
     * @param barId      name of the bar, unique within this chart
     * @param expression what to read and what to compare it against, {@code null} for a bar that
     *                   waits on nothing
     * @return the bar
     * @throws IllegalArgumentException when there is no step, or the name is empty or already used
     */
    public static S88ProcedureTransition addTransition(
            S88Recipe recipe, S88RecipeElement step, String barId, S88ConditionExpression expression) {
        S88ProcedureLogic chart = chartOf(recipe, step);
        if (chart == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (barId == null || barId.isBlank()) {
            throw new IllegalArgumentException("A bar needs a name.");
        }
        if (chart.findTransition(barId).isPresent()) {
            throw new IllegalArgumentException("The chart already has a bar called '" + barId + "'.");
        }
        S88ProcedureTransition bar = new S88ProcedureTransition();
        bar.setId(barId);
        bar.setExpression(expression);
        chart.addTransition(bar);
        announce(recipe);
        return bar;
    }

    /**
     * Takes a bar off the chart, and says so even when there was none.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the bar is on
     * @param barId  name of the bar
     * @return true when there was such a bar and it is gone
     */
    public static boolean removeTransition(S88Recipe recipe, S88RecipeElement step, String barId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        var bar = chart != null ? chart.findTransition(barId).orElse(null) : null;
        if (bar == null) {
            return false;
        }
        List<S88ProcedureLink> lines = chart.linksTouching(S88IdRef.transition(barId));
        if (!lines.isEmpty()) {
            throw new IllegalStateException("Bar '" + barId + "' is still joined by line "
                    + lines.stream().map(S88ProcedureLink::getId).toList()
                    + ". Take those off first, or the chart would be left with a line that has"
                    + " nothing at one end.");
        }
        chart.removeTransition(bar);
        announce(recipe);
        return true;
    }

    /**
     * Gives a box another name, and takes the lines running into it along.
     * <p>
     * A name that leaves lines naming the old one would give a chart that cannot be drawn, so the
     * lines are moved rather than left behind.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the box is on
     * @param boxId  name the box has now
     * @param newId  name it is to have
     * @throws IllegalArgumentException when there is no such box, or the new name is not free
     */
    public static void renameStep(S88Recipe recipe, S88RecipeElement step, String boxId,
                                  String newId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureStep box = chart != null ? chart.findStep(boxId).orElse(null) : null;
        if (box == null) {
            throw new IllegalArgumentException("This chart has no box called '" + boxId + "'.");
        }
        if (!boxId.equals(newId)) {
            requireFreeName(chart, newId, "box");
        }
        // Put back under the new name rather than renamed in place: the chart finds its boxes and
        // bars by name, and a box whose name changed while the chart still looked it up by the old
        // one is a box the chart cannot find.
        chart.removeStep(box);
        box.setId(newId);
        chart.addStep(box);
        renameLinkEnds(chart, boxId, newId);
        announce(recipe);
    }

    /**
     * Gives a bar another name, and takes the lines running into it along.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the bar is on
     * @param barId  name the bar has now
     * @param newId  name it is to have
     * @throws IllegalArgumentException when there is no such bar, or the new name is not free
     */
    public static void renameTransition(S88Recipe recipe, S88RecipeElement step, String barId,
                                        String newId) {
        S88ProcedureLogic chart = existingChart(recipe, step);
        S88ProcedureTransition bar = chart != null ? chart.findTransition(barId).orElse(null) : null;
        if (bar == null) {
            throw new IllegalArgumentException("This chart has no bar called '" + barId + "'.");
        }
        if (!barId.equals(newId)) {
            requireFreeName(chart, newId, "bar");
        }
        chart.removeTransition(bar);
        bar.setId(newId);
        chart.addTransition(bar);
        renameLinkEnds(chart, barId, newId);
        announce(recipe);
    }

    /**
     * Points every end of every line at a renamed box or bar.
     *
     * <p>
     * Ends pointing outside this chart are left alone, because their names belong to another chart.
     *
     * <p>
     * <b>Each line that changed is put back through the chart.</b> A line is changed by renaming the
     * ends it holds, and the chart does not see that happen: it works out which ends name nothing
     * whenever the set of lines changes, and a line renamed in place leaves that list still saying
     * the old name is dangling. Taking the line out and putting it back is how the chart is told.
     */
    private static void renameLinkEnds(S88ProcedureLogic chart, String oldId, String newId) {
        for (S88ProcedureLink link : new ArrayList<>(chart.getLinks())) {
            boolean changed = renameEnd(link.getFrom(), oldId, newId)
                    | renameEnd(link.getTo(), oldId, newId);
            if (changed) {
                chart.removeLink(link);
                chart.addLink(link);
            }
        }
    }

    /**
     * Renames the ends of one side of a line that carry the old name.
     *
     * @return true when at least one end was renamed
     */
    private static boolean renameEnd(List<S88IdRef> ends, String oldId, String newId) {
        boolean changed = false;
        for (S88IdRef end : ends) {
            if (end != null && oldId.equals(end.getValue())) {
                end.setValue(newId);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * One end of a line, which has to name something this chart carries.
     * <p>
     * A name beginning with a mark points outside this chart, and is left alone by everything that
     * renames or takes away boxes and bars here. That is the difference between the two kinds of
     * reference a chart has, and it is why a name the chart cannot resolve is a mistake rather than
     * something to resolve later.
     */
    private static S88IdRef existingEnd(S88ProcedureLogic chart, String name, String doing) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A line " + doing + " something that has no name.");
        }
        if (name.startsWith("!") || name.startsWith("#")) {
            return new S88IdRef(name.substring(1), S88IdRefType.STEP, S88IdScope.EXTERNAL);
        }
        if (chart.findStep(name).isPresent()) {
            return S88IdRef.step(name);
        }
        if (chart.findTransition(name).isPresent()) {
            return S88IdRef.transition(name);
        }
        throw new IllegalArgumentException("The chart has no box or bar called '" + name + "' for a"
                + " line that " + doing + ".");
    }

    /**
     * Tells whoever is looking that the chart changed.
     */
    private static void announce(S88Recipe recipe) {
        if (recipe != null) {
            recipe.fireChangeEvent(S88RecipeChangeEvent.chart());
        }
    }

    /** Whether a step of a recipe has a chart drawn under it, which is how the tree knows to offer one. */
    public static boolean hasChart(S88RecipeElement step) {
        return step != null && step.hasProcedureLogic();
    }

    /**
     * Whether a recipe carries a chart of its own, which is where its process as a whole is drawn.
     * <p>
     * Every recipe created through this module has one, so a tree that offers a chart is offering
     * something that is usually already there.
     *
     * @param recipe recipe to look at, may be {@code null}
     * @return true when the recipe itself carries a chart
     */
    public static boolean hasOwnChart(S88Recipe recipe) {
        return recipe != null && recipe.hasProcedureLogic();
    }
}

