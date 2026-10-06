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
import org.apache.plc4x.malbec.s88.api.S88VariableAddress;

import java.util.ArrayList;
import java.util.List;

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
            List<S88IdRef> from = link.getFrom();
            List<S88IdRef> to = link.getTo();
            if (!from.isEmpty() && !to.isEmpty() && from.get(0).getType() == to.get(0).getType()) {
                return false;
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
        chart.removeTransition(bar);
        announce(recipe);
        return true;
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
        }        if (chart.findTransition(name).isPresent()) {
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

