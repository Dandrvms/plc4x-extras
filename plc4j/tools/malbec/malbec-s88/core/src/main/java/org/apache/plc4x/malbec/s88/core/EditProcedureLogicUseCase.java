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
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing the chart of a recipe: the boxes, the lines between them, and the bars.
 * <p>
 * Nothing in the plant decides any of this, so what these use cases check is narrower than it is
 * elsewhere. A box has to name a step the recipe carries, because a name nobody can resolve is a
 * mistake somebody made typing it rather than a decision. A line has to end somewhere that exists,
 * unless it says it points outside, which is how a line refers to a step of another part of the
 * process.
 * <p>
 * The one thing that is not checked is whether the chart makes sense as a process: whether it starts
 * somewhere, whether a split has two sides, whether a step is reachable. That is
 * {@code RecipeConformance}'s job, and it reports rather than refuses, because a chart saved half
 * drawn is the ordinary state of a recipe being written. These refuse only what cannot be drawn at
 * all.
 * <p>
 * A chart belongs to the step it was drawn under, and a step of the chart is a box on it. They are
 * named separately because one names a step of the recipe and the other names a box, and a recipe
 * whose flow is drawn twice, at two levels, has two boxes of the same name meaning two different
 * things.
 */
public class EditProcedureLogicUseCase {

    private EditProcedureLogicUseCase() {
        /* This utility class should not be instantiated */
    }

    /**
     * The chart of a step, made if it is not there yet. Drawing a chart is the ordinary thing to do
     * with a step, so asking for the chart brings one into being rather than being a separate step
     * the caller has to remember.
     *
     * @param step step to give a chart to, may be {@code null}
     * @return the chart, or {@code null} when there was no step
     */
    public static S88ProcedureLogic chartOf(S88RecipeElement step) {
        if (step == null) {
            return null;
        }
        if (!step.hasProcedureLogic()) {
            step.setProcedureLogic(new S88ProcedureLogic());
        }
        return step.getProcedureLogic();
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
        S88ProcedureLogic chart = chartOf(step);
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
     * <p>
     * The lines are the reason this is not simply a matter of detaching it. A line left with one end
     * names something the chart no longer has, and choosing which of the ends was meant to go would
     * be guessing at what the reader wanted. So the line is taken off first, by
     * {@link #removeLink}, and this one then has nothing to refuse about.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the box is on
     * @param boxId  name of the box
     * @throws IllegalStateException when a line still runs into the box
     */
    public static void removeStep(S88Recipe recipe, S88RecipeElement step, String boxId) {
        S88ProcedureLogic chart = step != null ? step.getProcedureLogic() : null;
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
     *
     * @param recipe  recipe the step belongs to, may be {@code null}
     * @param step    step whose chart is being drawn on
     * @param lineId  name of the line, unique within this chart
     * @param fromIds what the line leaves, at least one
     * @param toIds   what it arrives at, at least one
     * @param type    what it means, {@code null} for an ordinary flow of control
     * @return the line
     * @throws IllegalArgumentException when the name is empty, already used, an end is missing, or
     *                                  an end names something the chart does not carry
     */
    public static S88ProcedureLink addLink(S88Recipe recipe, S88RecipeElement step, String lineId,
                                           List<String> fromIds, List<String> toIds, S88LinkType type) {
        S88ProcedureLogic chart = chartOf(step);
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
     * Takes a line off the chart, and says so even when there was none. A line is the one thing a
     * chart can lose without anything else being left inconsistent, so this is allowed to be asked
     * for something that is not there: the editor asks to undo a drawing, and whether the second
     * undo finds the line or not is the editor's business.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the line is on
     * @param lineId name of the line
     * @return true when there was such a line and it is gone
     */
    public static boolean removeLink(S88Recipe recipe, S88RecipeElement step, String lineId) {
        S88ProcedureLogic chart = step != null ? step.getProcedureLogic() : null;
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
        S88ProcedureLogic chart = step != null ? step.getProcedureLogic() : null;
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
        announce(recipe);
    }

    /**
     * Puts a bar on the chart, waiting on a report of the element the box works on.
     *
     * @param recipe    recipe the step belongs to, may be {@code null}
     * @param step      step whose chart is being drawn on
     * @param barId     name of the bar, unique within this chart
     * @param reportAddress address of the report to wait on, such as {@code Reports/STATE}, or
     *                     {@code null} for a bar crossed because the step before it finished
     * @return the bar
     */
    public static S88ProcedureTransition addTransition(
            S88Recipe recipe, S88RecipeElement step, String barId, String reportAddress) {
        S88ProcedureLogic chart = chartOf(step);
        if (chart == null) {
            throw new IllegalArgumentException("Step cannot be null");
        }
        if (barId == null || barId.isBlank()) {
            throw new IllegalArgumentException("A bar needs a name.");
        }
        if (chart.findTransition(barId).isPresent()) {
            throw new IllegalArgumentException("The chart already has a bar called '" + barId + "'.");
        }
        if (reportAddress != null && !reportAddress.isBlank()
                && S88VariableAddress.parse(reportAddress) == null) {
            throw new IllegalArgumentException("'" + reportAddress + "' is not an address of a"
                    + " report, so there is nothing for the bar to wait on.");
        }
        var bar = new S88ProcedureTransition(barId, reportAddress);
        chart.addTransition(bar);
        announce(recipe);
        return bar;
    }

    /**
     * Takes a bar off the chart, and says so even when there was none, for the same reason as lines.
     *
     * @param recipe recipe the step belongs to, may be {@code null}
     * @param step   step whose chart the bar is on
     * @param barId  name of the bar
     * @return true when there was such a bar and it is gone
     */
    public static boolean removeTransition(S88Recipe recipe, S88RecipeElement step, String barId) {
        S88ProcedureLogic chart = step != null ? step.getProcedureLogic() : null;
        var bar = chart != null ? chart.findTransition(barId).orElse(null) : null;
        if (bar == null) {
            return false;
        }
        chart.removeTransition(bar);
        announce(recipe);
        return true;
    }

    /**
     * One end of a line, which has to name something this chart carries.
     * <p>
     * A name ending in a mark means the line points at a step of another part of the process rather
     * than at one of its own, and is left alone by everything that renames or takes away steps here.
     * That is the difference between the two kinds of reference the recipe format has, and it is why
     * a name the chart cannot resolve is a mistake rather than a thing to resolve later.
     */
    private static S88IdRef existingEnd(S88ProcedureLogic chart, String name, String doing) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A line " + doing + " something that has no name.");
        }
        if (name.startsWith("!") || name.startsWith("#")) {
            return new S88IdRef(name.substring(1), S88IdRefType.STEP, S88IdScope.EXTERNAL);
        }
        if (chart.findStep(name).isEmpty()) {
            throw new IllegalArgumentException("The chart has no box called '" + name + "' for a line"
                    + " that " + doing + ".");
        }
        return S88IdRef.step(name);
    }

    /**
     * Tells whoever is looking that the chart changed. Carries no step, because a line or a box
     * belongs to the chart and not to any one step of the recipe.
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
}

