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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;

/**
 * Where everything on a chart goes, worked out from the chart alone.
 * <p>
 * No Swing in here: a layout that can only be checked by looking at it cannot be tested, and where
 * a chart's boxes go is arithmetic.
 * <p>
 * Read from top to bottom, one row per node, and a branch centred on where it left. A link that goes
 * back up closes a cycle, and no set of rows satisfies every edge of a cycle, so that link is left
 * out of the numbering and drawn round the side with an arrow.
 */
final class SfcLayoutEngine {

        private final S88ProcedureLogic chart;
    private final Map<String, RecipeShapes.Shape> shapes;

    /**
     * How wide the text on each node is, so that a bar is wide enough for the condition written
     * beside it and a step is wide enough for its name.
     */
    private final Map<String, Integer> labelWidths;

    private final Map<String, Set<String>> successors = new LinkedHashMap<>();
    private final Map<String, Set<String>> predecessors = new LinkedHashMap<>();
    private final List<S88ProcedureLink> links = new ArrayList<>();
    private final Set<String> goingBackwards = new LinkedHashSet<>();
    private final Map<String, Integer> row = new LinkedHashMap<>();
    private final Map<String, int[]> leafRange = new LinkedHashMap<>();

    /** Every node grouped by the row it is in, left to right. Worked out once and kept. */
    private List<List<String>> byRow = List.of();
    private final Map<String, Integer> axis = new LinkedHashMap<>();
    private final List<String> complaints = new ArrayList<>();

    /**
     * @param chart       the chart to arrange
     * @param shapes      what each node is drawn as, which is what decides how much room it takes
     * @param labelWidths how wide the text on each node is, empty when nothing has to be read
     */
    SfcLayoutEngine(S88ProcedureLogic chart, Map<String, RecipeShapes.Shape> shapes,
                    Map<String, Integer> labelWidths) {
        this.chart = chart;
        this.shapes = shapes;
        this.labelWidths = labelWidths == null ? Map.of() : labelWidths;
    }

    /**
     * @param chart  the chart to arrange
     * @param shapes what each node is drawn as
     */
    SfcLayoutEngine(S88ProcedureLogic chart, Map<String, RecipeShapes.Shape> shapes) {
        this(chart, shapes, Map.of());
    }

    /**
     * How far from its line the left of a node sits.
     * <p>
     * A transition is a bar with the comparison written beside it, and the bar is what the flow
     * crosses and what the line has to arrive at. Centring the whole node on the line puts the bar
     * a whole text width to one side of it, so the line arrives at the text instead of at the bar,
     * and two bars whose texts are long push each other out of their branches.
     */
    private int halfWidthOf(String node) {
        return shapes.get(node) == RecipeShapes.Shape.TRANSITION
                ? SfcMetrics.TRANSITION_WIDTH / 2
                : widthOf(node) / 2;
    }

    /**
     * How much room a node needs across.
     *
     * @param node name of the node
     * @return the width, in units of the chart
     */
    private int widthOf(String node) {
        RecipeShapes.Shape shape = shapes.get(node);
        int labelWidth = labelWidths.getOrDefault(node, 0);
        if (shape == RecipeShapes.Shape.TRANSITION) {
            return SfcMetrics.transitionWidth(labelWidth);
        }
                return SfcMetrics.widthOf(shape);
    }

    /**
     * Works out where everything goes.
     *
     * @return the corners of every node and bar, the lines between them, and anything that could not
     *         be arranged
     */
    Placement place() {
        if (chart == null || shapes.isEmpty()) {
            return new Placement(Map.of(), Map.of(), Map.of(), List.of(),
                    new Rectangle(SfcMetrics.MARGIN, SfcMetrics.MARGIN, 1, 1), List.of(), 0);
        }
        readChart();
        numberRows();
        refineOrder();
        assignAxis();
        return build();
    }

    // ========== Reading the chart ==========

    private void readChart() {
        for (String id : shapes.keySet()) {
            successors.put(id, new LinkedHashSet<>());
            predecessors.put(id, new LinkedHashSet<>());
        }
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link == null) {
                continue;
            }
            List<String> from = internalNames(link.getFrom());
            List<String> to = internalNames(link.getTo());
            if (from.size() < link.getFrom().size() || to.size() < link.getTo().size()) {
                complaints.add("Line '" + link.getId() + "' has an end outside this chart, so the"
                        + " part of it that is here is drawn and the rest is not.");
            }
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            links.add(link);
            for (String one : from) {
                for (String other : to) {
                    successors.get(one).add(other);
                    predecessors.get(other).add(one);
                }
            }
        }
        checkAlternation();
    }

    /** The ends of a line that name something this chart carries. */
    private List<String> internalNames(List<S88IdRef> ends) {
        List<String> names = new ArrayList<>();
        for (S88IdRef end : ends) {
            if (end != null && end.isInternal() && shapes.containsKey(end.getValue())) {
                names.add(end.getValue());
            }
        }
        return names;
    }

    /** One that does not is a chart that cannot be read top to bottom, and saying so beats drawing it. */
    private void checkAlternation() {
        for (S88ProcedureLink link : links) {
            boolean fromIsBar = shapes.get(link.getFrom().get(0).getValue())
                    == RecipeShapes.Shape.TRANSITION;
            for (S88IdRef end : link.getTo()) {
                if (!shapes.containsKey(end.getValue())) {
                    continue;
                }
                boolean toIsBar = shapes.get(end.getValue()) == RecipeShapes.Shape.TRANSITION;
                if (fromIsBar == toIsBar) {
                    complaints.add("Line '" + link.getId() + "' joins "
                            + (fromIsBar ? "two bars" : "two steps")
                            + ", and between two of the same there is nothing to wait on.");
                }
            }
        }
    }

    // ========== Rows ==========

    /**
     * Numbers the rows so everything sits below what it depends on, repeatedly rather than once, so
     * that nothing here depends on the order the nodes come in.
     */
    private void numberRows() {
        // One walk over the whole chart, sharing what has been seen and what is on the stack. A walk
        // per node would find a different edge of the same cycle each time, and a cycle whose every
        // edge goes up is a chart with nothing left to draw.
        Deque<String> onStack = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> reached = new ArrayList<>();
        walk(findStart(), onStack, seen, reached);
        for (String id : shapes.keySet()) {
            walk(id, onStack, seen, reached);
        }

        for (String id : orderOfAppearance()) {
            row.put(id, 0);
        }
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ <= shapes.size()) {
            changed = false;
            for (String id : orderOfAppearance()) {
                int deepest = 0;
                for (String previous : predecessors.get(id)) {
                    if (goingBackwards.contains(edge(previous, id))) {
                        continue;
                    }
                    deepest = Math.max(deepest, row.getOrDefault(previous, 0) + 1);
                }
                if (row.get(id) != deepest) {
                    row.put(id, deepest);
                    changed = true;
                }
            }
        }
    }

    /**
     * The nodes in the order the recipe lists them, which is steps then transitions.
     * <p>
     * Only used where the order of the recipe is what matters, never where the order of a hash map
     * is: a chart that comes out the same twice is worth more than one that comes out in the order
     * the model happened to hold its entries.
     */
    private List<String> orderOfAppearance() {
        List<String> ordered = new ArrayList<>();
        for (var step : chart.getSteps()) {
            if (shapes.containsKey(step.getId())) {
                ordered.add(step.getId());
            }
        }
        for (var transition : chart.getTransitions()) {
            if (shapes.containsKey(transition.getId())) {
                ordered.add(transition.getId());
            }
        }
        return ordered;
    }

    /** The step of kind begin, or the first node nothing leads to, which is what it can be. */
    private String findStart() {
        for (String id : shapes.keySet()) {
            if (shapes.get(id) == RecipeShapes.Shape.START) {
                return id;
            }
        }
        for (String id : shapes.keySet()) {
            if (predecessors.get(id).isEmpty()) {
                complaints.add("This chart has no step of kind begin, so the drawing starts at '"
                        + id + "', the first thing nothing leads to.");
                return id;
            }
        }
        complaints.add("This chart has no step to start at: every node has something leading to it.");
        return shapes.keySet().iterator().next();
    }

    /**
     * Walks the chart, taking note of which edges close a cycle. An edge that goes up cannot say
     * which row it lands in, so it is left out of the numbering.
     */
    private void walk(String from, Deque<String> onStack, Set<String> seen,
                      List<String> inDependencyOrder) {
        if (!seen.add(from)) {
            return;
        }
        inDependencyOrder.add(from);
        onStack.push(from);
        for (String next : successors.getOrDefault(from, Set.of())) {
            if (onStack.contains(next)) {
                goingBackwards.add(edge(from, next));
            } else {
                walk(next, onStack, seen, inDependencyOrder);
            }
        }
        onStack.pop();
    }

    private static String edge(String from, String to) {
        return from + ">" + to;
    }

    // ========== Left to right ==========

    /**
     * Orders each row so that branches do not cross: a node is better placed between the ones it is
     * joined to than between the ones next to it, so each row is sorted on the row above and then
     * the one below, a few times over.
     */
    private void refineOrder() {
        List<List<String>> byRow = rowsOfEverything();
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 1; i < byRow.size(); i++) {
                reorder(byRow.get(i), byRow.get(i - 1));
            }
            for (int i = byRow.size() - 2; i >= 0; i--) {
                reorder(byRow.get(i), byRow.get(i + 1));
            }
        }
    }

    private void reorder(List<String> inThisRow, List<String> inTheOther) {
        Map<String, Integer> where = new HashMap<>();
        for (int i = 0; i < inTheOther.size(); i++) {
            where.put(inTheOther.get(i), i);
        }
        Map<String, Double> wanted = new HashMap<>();
        for (String id : inThisRow) {
            wanted.put(id, middleOfNeighbours(id, where).orElse(Double.MAX_VALUE));
        }
        inThisRow.sort(Comparator.comparingDouble(id -> wanted.getOrDefault(id, Double.MAX_VALUE)));
    }

    /** Where a node's neighbours sit in the other row, on average. */
    private java.util.OptionalDouble middleOfNeighbours(String node, Map<String, Integer> where) {
        List<Integer> at = new ArrayList<>();
        for (String other : neighbours(node)) {
            Integer one = where.get(other);
            if (one != null) {
                at.add(one);
            }
        }
        if (at.isEmpty()) {
            return java.util.OptionalDouble.empty();
        }
        double total = 0;
        for (int one : at) {
            total += one;
        }
        return java.util.OptionalDouble.of(total / at.size());
    }

    private List<String> neighbours(String node) {
        List<String> before = new ArrayList<>();
        for (String other : predecessors.getOrDefault(node, Set.of())) {
            if (!goingBackwards.contains(edge(other, node))) {
                before.add(other);
            }
        }
        return before;
    }

    /**
     * Puts each node on the middle of the leaves it is responsible for.
     * <p>
     * A node that splits into two is given one leaf's worth of the chart for each branch, and each
     * branch is given one for whatever it splits into in turn. A node with a single branch below it
     * therefore sits on exactly the same line as that branch all the way down, and the edge between
     * them is straight however wide the two boxes are.
     * <p>
     * Measuring by anything else fails on the first split: a bar is narrower than a step, so two
     * bars side by side fit closer together than two steps do, and the steps then cannot both sit
     * under their own bar. Every edge from the bars down to the steps comes out diagonal.
     * <p>
     * <b>A leaf is as wide as the widest thing that stands over it.</b> A bar with a long comparison
     * beside it is wider than the step below it. Two such bars have to sit further apart than two
     * steps do, and if the room is not made for them here they are pushed apart afterwards and no
     * longer stand over the branch they belong to.
     */
    private void assignAxis() {
        int used = numberLeaves(findStart(), 0);
        int room = used + shapes.size() + 1;
        int[] leafWidth = new int[room];
        for (Map.Entry<String, int[]> entry : leafRange.entrySet()) {
            int width = widthOf(entry.getKey());
            for (int leaf = entry.getValue()[0]; leaf < entry.getValue()[1]; leaf++) {
                leafWidth[leaf] = Math.max(leafWidth[leaf], width);
            }
        }
        int[] where = new int[room + 1];
        for (int leaf = 0; leaf < room; leaf++) {
            where[leaf + 1] = where[leaf] + leafWidth[leaf] + SfcMetrics.GAP;
        }
        for (Map.Entry<String, int[]> entry : leafRange.entrySet()) {
            int[] range = entry.getValue();
            axis.put(entry.getKey(), (where[range[0]] + where[range[1]]) / 2);
        }
        // Anything nothing leads to has no leaves of its own, so it goes after everything that has
        // rather than on top of whatever is already there.
        int next = used;
        for (String id : orderOfAppearance()) {
            if (axis.containsKey(id)) {
                continue;
            }
            axis.put(id, where[next] + halfWidthOf(id));
            next++;
        }
        orderRowsByTheirLeaves();
        separateRows();
        shiftRight();
    }

    /**
     * Puts each row in the order of the leaves it stands over.
     * <p>
     * Without this a row keeps whatever order it happened to be built in, and pushing two nodes
     * apart then has to move one of them all the way to the right of where its own leaves put it.
     * That leaves a branch on the wrong side of its parent and every edge below it diagonal.
     */
    private void orderRowsByTheirLeaves() {
        for (List<String> inRow : rowsOfEverything()) {
            inRow.sort(Comparator.comparingInt(
                    node -> leafRange.getOrDefault(node, new int[]{Integer.MAX_VALUE, 0})[0]));
        }
    }

    /**
     * Gives each node the run of leaves under it that it is responsible for.
     *
     * @param from the first leaf this node is given
     * @return how many leaves it was given in all
     */
    private int numberLeaves(String from, int fromLeaf) {
        int[] already = leafRange.get(from);
        if (already != null) {
            return already[1] - already[0];
        }
        int cursor = fromLeaf;
        int given = 0;
        for (String next : forwards(from)) {
            int got = numberLeaves(next, cursor);
            cursor += got;
            given += got;
        }
        if (given == 0) {
            given = 1;
        }
        leafRange.put(from, new int[]{fromLeaf, fromLeaf + given});
        return given;
    }

    private List<String> forwards(String node) {
        List<String> after = new ArrayList<>();
        for (String other : successors.getOrDefault(node, Set.of())) {
            if (!goingBackwards.contains(edge(node, other))) {
                after.add(other);
            }
        }
        return after;
    }

    /**
     * Pushes nodes apart within a row when two of them asked for the same line.
     * <p>
     * Leaves say two branches need room for the steps under them, but two things joining into the
     * same node are given the same leaves by whichever reached them first and can end up on top of
     * each other. The order of the row is kept, so a node that has to move goes right rather than
     * swapping places with its neighbour.
     */
    private void separateRows() {
        for (List<String> inRow : rowsOfEverything()) {
            for (int i = 1; i < inRow.size(); i++) {
                String here = inRow.get(i);
                String before = inRow.get(i - 1);
                int room = (widthOf(before) + widthOf(here)) / 2 + SfcMetrics.GAP;
                int wanted = axis.getOrDefault(here, 0);
                int earliest = axis.getOrDefault(before, 0) + room;
                if (wanted < earliest) {
                    axis.put(here, earliest);
                }
            }
        }
    }

    /** Moves the whole chart so that nothing hangs off the left of it. */
    private void shiftRight() {
        int leftmost = Integer.MAX_VALUE;
        for (String id : shapes.keySet()) {
            leftmost = Math.min(leftmost, axis.getOrDefault(id, 0) - halfWidthOf(id));
        }
        int shift = SfcMetrics.MARGIN - leftmost;
        for (String id : shapes.keySet()) {
            axis.merge(id, shift, Integer::sum);
        }
    }

    /**
     * Every node grouped by the row it is in, worked out once and then kept.
     * <p>
     * Kept rather than rebuilt because the order within a row is itself an answer: sorting a row
     * that is thrown away afterwards sorts nothing, and the order the chart is drawn in is what
     * comes out of the order the recipe happens to list its nodes.
     */
    private List<List<String>> rowsOfEverything() {
        if (!byRow.isEmpty()) {
            return byRow;
        }
        int deepest = 0;
        for (int at : row.values()) {
            deepest = Math.max(deepest, at);
        }
        List<List<String>> grouped = new ArrayList<>();
        for (int i = 0; i <= deepest; i++) {
            grouped.add(new ArrayList<>());
        }
        for (String id : orderOfAppearance()) {
            grouped.get(row.getOrDefault(id, 0)).add(id);
        }
        byRow = grouped;
        return byRow;
    }

    // ========== Putting the answer together ==========

    private Placement build() {
        Map<String, Rectangle> corners = new LinkedHashMap<>();
        int deepest = 0;
        for (Map.Entry<String, Integer> at : axis.entrySet()) {
            String id = at.getKey();
            Rectangle box = new Rectangle(
                    at.getValue() - halfWidthOf(id),
                    SfcMetrics.MARGIN + row.getOrDefault(id, 0) * SfcMetrics.ROW,
                    widthOf(id),
                    SfcMetrics.heightOf(shapes.get(id)));
            corners.put(id, box);
            deepest = Math.max(deepest, box.y + box.height);
        }

        Map<String, Boolean> syncBars = linesWithABar(corners);
        Map<String, SyncBar> drawnSyncBars = new LinkedHashMap<>();
        List<Link> drawn = new ArrayList<>();
        int rightmost = rightmostOf(corners);
        for (S88ProcedureLink link : links) {
            Rectangle bar = null;
            if (syncBars.containsKey(link.getId())) {
                bar = syncBarBetween(link, corners);
                drawnSyncBars.put(link.getId(), new SyncBar(bar, syncBars.get(link.getId())));
            }
            for (String from : internalNames(link.getFrom())) {
                for (String to : internalNames(link.getTo())) {
                    drawn.add(route(link.getId(), from, to, corners, bar));
                }
            }
        }
        int widestRight = rightmost;
        for (Link link : drawn) {
            for (Point point : link.path()) {
                widestRight = Math.max(widestRight, point.x);
            }
        }
        int height = SfcMetrics.MARGIN + (deepest + SfcMetrics.ROW);
        return new Placement(cornersOfKind(corners, false), cornersOfKind(corners, true),
                drawnSyncBars, drawn,
                new Rectangle(SfcMetrics.MARGIN, SfcMetrics.MARGIN,
                        Math.max(1, widestRight + SfcMetrics.BACK_EDGE_LANE), height),
                List.copyOf(complaints), goingBackwards.size());
    }

    private Map<String, Rectangle> cornersOfKind(Map<String, Rectangle> corners, boolean asBar) {
        Map<String, Rectangle> ofKind = new LinkedHashMap<>();
        for (Map.Entry<String, Rectangle> at : corners.entrySet()) {
            boolean isBar = shapes.get(at.getKey()) == RecipeShapes.Shape.TRANSITION;
            if (isBar == asBar) {
                ofKind.put(at.getKey(), at.getValue());
            }
        }
        return ofKind;
    }

    /**
     * Which lines get a bar across their branches, and whether that bar is doubled.
     * <p>
     * A line with more than one end at either side is a split or a join, and the bar is what says so
     * on the drawing. A line with one end at each side is an ordinary edge and gets nothing.
     */
    private Map<String, Boolean> linesWithABar(Map<String, Rectangle> corners) {
        Map<String, Boolean> which = new LinkedHashMap<>();
        for (S88ProcedureLink link : links) {
            if (link.getTo().size() < 2 && link.getFrom().size() < 2) {
                continue;
            }
            if (link.getLinkType() == null || !(link.isDivergent() || link.isConvergent())) {
                continue;
            }
            if (syncBarBetween(link, corners) != null) {
                which.put(link.getId(), link.getLinkType().isParallel());
            }
        }
        return which;
    }

    /**
     * @return the bar across the branches of a split or a join, {@code null} when both ends are not
     *         on this chart
     */
    private Rectangle syncBarBetween(S88ProcedureLink link, Map<String, Rectangle> corners) {
        List<String> from = internalNames(link.getFrom());
        List<String> to = internalNames(link.getTo());
        if (from.isEmpty() || to.isEmpty()) {
            return null;
        }
        List<Integer> centres = new ArrayList<>();
        int highestBottom = Integer.MIN_VALUE;
        int lowestTop = Integer.MAX_VALUE;
        for (String one : from) {
            Rectangle box = corners.get(one);
            centres.add(box.x + box.width / 2);
            highestBottom = Math.max(highestBottom, leavesAt(one, box).y);
        }
        for (String other : to) {
            Rectangle box = corners.get(other);
            centres.add(box.x + box.width / 2);
            lowestTop = Math.min(lowestTop, arrivesAt(other, box).y);
        }
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        for (int centre : centres) {
            left = Math.min(left, centre);
            right = Math.max(right, centre);
        }
        // The bar runs from just before the leftmost branch to just past the rightmost one, so that
        // the edges have something of it to arrive at rather than a point on it.
        int middle = (highestBottom + lowestTop) / 2;
        boolean doubled = link.getLinkType() != null && link.getLinkType().isParallel();
        int height = SfcMetrics.SYNC_BAR_HEIGHT * (doubled ? 2 : 1);
        return new Rectangle(left - SfcMetrics.SYNC_BAR_WIDTH / 2,
                middle - height / 2,
                right - left + SfcMetrics.SYNC_BAR_WIDTH, height);
    }

    /**
     * The line between two things, as the points to draw it through.
     * <p>
     * Down the chart it is one straight run, or a dogleg through a bar when the branches have to
     * meet. Up the chart it is taken out to a lane on the right, because a line that goes back up has
     * nowhere to go on the way that is not through something else.
     */
    private Link route(String linkId, String from, String to, Map<String, Rectangle> corners,
                       Rectangle syncBar) {
        Rectangle source = corners.get(from);
        Rectangle target = corners.get(to);
        Point leaving = leavesAt(from, source);
        Point arriving = arrivesAt(to, target);

        if (goingBackwards.contains(edge(from, to))) {
            int lane = rightmostOf(corners) + SfcMetrics.BACK_EDGE_LANE;
            int dip = Math.max(leaving.y + SfcMetrics.ROW / 3, arriving.y - SfcMetrics.ROW / 3);
            return new Link(linkId + ">" + from + ">" + to, true, List.of(
                    leaving,
                    new Point(leaving.x, dip),
                    new Point(lane, dip),
                    new Point(lane, arriving.y),
                    arriving));
        }
        if (syncBar == null) {
            return new Link(linkId + ">" + from + ">" + to, false, List.of(leaving, arriving));
        }
        int barY = syncBar.y + SfcMetrics.SYNC_BAR_HEIGHT / 2;
        return new Link(linkId + ">" + from + ">" + to, false, List.of(
                leaving,
                new Point(leaving.x, barY),
                new Point(arriving.x, barY),
                arriving));
    }

    /**
     * Where a line leaves a node.
     * <p>
     * A step is left at the middle of its bottom edge. A bar is left at its own middle, which is
     * where the link passes through it, so that the line in and the line out are one straight run
     * through the bar rather than two that stop short of it.
     */
private Point leavesAt(String node, Rectangle box) {
        return new Point(acrossAt(node, box), barOrEdge(node, box, true));
    }

    /** Where a line arrives at a node, which is the middle of a bar and the top of a step. */
    private Point arrivesAt(String node, Rectangle box) {
        return new Point(acrossAt(node, box), barOrEdge(node, box, false));
    }

    /** Across a node: the middle of a bar, and the middle of a box. */
    private int acrossAt(String node, Rectangle box) {
        return shapes.get(node) == RecipeShapes.Shape.TRANSITION
                ? box.x + SfcMetrics.TRANSITION_WIDTH / 2
                : box.x + box.width / 2;
    }

    /** Along a node: a bar is left and arrived at at its own middle, a box at its two edges. */
    private int barOrEdge(String node, Rectangle box, boolean leaving) {
        if (shapes.get(node) == RecipeShapes.Shape.TRANSITION) {
            return box.y + SfcMetrics.TRANSITION_HEIGHT / 2;
        }
        return leaving ? box.y + box.height : box.y;
    }

    private static int rightmostOf(Map<String, Rectangle> corners) {
        int rightmost = 0;
        for (Rectangle box : corners.values()) {
            rightmost = Math.max(rightmost, box.x + box.width);
        }
        return rightmost;
    }

    /**
     * Where everything ended up.
     *
     * @param steps    corners of the steps, by name
     * @param bars     corners of the transitions, by name
     * @param syncBars the bars across the branches of a split or a join, by line name
     * @param links    every line, as the points to draw it through
     * @param total    the area the chart covers, which is what the window is sized from
     * @param complaints what could not be arranged, empty when the chart holds together
     * @param backwards how many lines go back up the chart
     */
    record Placement(Map<String, Rectangle> steps,
                     Map<String, Rectangle> bars,
                     Map<String, SyncBar> syncBars,
                     List<Link> links,
                     Rectangle total,
                     List<String> complaints,
                     int backwards) {
    }

    /**
     * The bar across the branches of a split or a join.
     *
     * @param where   where it is drawn
     * @param doubled whether the branches run at once, which is what makes it two bars
     */
    record SyncBar(Rectangle where, boolean doubled) {
    }

    /**
     * One drawn line.
     *
     * @param id        what it is known by inside the scene
     * @param backwards whether it goes back up the chart, which is what asks for an arrow
     * @param path      the points to draw through, in order
     */
    record Link(String id, boolean backwards, List<Point> path) {
    }
}
