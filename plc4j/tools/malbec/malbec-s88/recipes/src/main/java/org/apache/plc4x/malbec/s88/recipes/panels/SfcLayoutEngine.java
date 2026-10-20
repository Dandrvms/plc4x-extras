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
import java.util.*;

import org.apache.plc4x.malbec.s88.api.S88IdRef;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.core.StructureQueries;

/**
 * Where everything on a chart goes, worked out from the chart alone.
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

    /** Where the flow starts, worked out once so that a chart's verdict is only given once. */
    private String start;

    /** The lane each line that goes back up is taken out to, by its two ends. */
    private final Map<String, Integer> laneOfEdge = new LinkedHashMap<>();

    /** The optional skips, by the two bars they join. */
    private final Set<String> skips = new LinkedHashSet<>();

    /** Whether each skip is drawn to the right of the work it leaves out. */
    private final Map<String, Boolean> skipToTheRight = new LinkedHashMap<>();

    /** How many skips are drawn to the left, which is how much room they need. */
    private int leftSkips;

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
     * How much room a node needs down.
     *
     * <p>A box is a fixed height, and a step with more to say than its name writes only what fits.
     * What does not fit is not lost: it is under the pointer.
     *
     * @param node name of the node
     * @return the height, in units of the chart
     */
    private int heightOf(String node) {
        return SfcMetrics.heightOf(shapes.get(node));
    }

    /**
     * How far from its line the left of a node sits.
     */
    private int halfWidthOf(String node) {
        return shapes.get(node) == RecipeShapes.Shape.TRANSITION
                ? SfcMetrics.TRANSITION_WIDTH / 2
                : widthOf(node) / 2;
    }

    /**
     * How much room a node needs across.
     *
     * <p>
     * A bar is as wide as its own text and a step as wide as its own name.
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
        if (shape == RecipeShapes.Shape.BOX) {
            return SfcMetrics.stepWidth(labelWidth);
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
            if (StructureQueries.isOptionalSkip(chart, link)) {
                String edgeId = edge(from.getFirst(), to.getFirst());
                boolean right = skipGoesRight(link);
                skips.add(edgeId);
                skipToTheRight.put(edgeId, right);
                if (!right) {
                    leftSkips++;
                }
            }
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


    private void checkAlternation() {
        for (S88ProcedureLink link : links) {
            if (StructureQueries.isOptionalSkip(chart, link)) {
                continue;
            }
            boolean fromIsBar = shapes.get(link.getFrom().getFirst().getValue())
                    == RecipeShapes.Shape.TRANSITION;
            for (S88IdRef end : link.getTo()) {
                if (!shapes.containsKey(end.getValue())) {
                    continue;
                }
                boolean toIsBar = shapes.get(end.getValue()) == RecipeShapes.Shape.TRANSITION;
                if (fromIsBar == toIsBar) {
                    complaints.add("Line '" + link.getId() + "' joins "
                            + (fromIsBar ? "two transitions" : "two steps")
                            + ", and between two of the same there is nothing to wait on.");
                }
            }
        }
    }

    /**
     * Whether the skip is the first side of its split.
     *
     * <p>The split lists its sides left to right, so the first one is drawn on the left and the rest
     * on the right.
     */
    private boolean skipGoesRight(S88ProcedureLink skip) {
        String from = skip.getFrom().getFirst().getValue();
        for (S88ProcedureLink link : chart.getLinks()) {
            if (link.isDivergent()
                    && link.getTo().stream().anyMatch(end -> from.equals(end.getValue()))) {
                return link.getTo().stream()
                        .map(S88IdRef::getValue)
                        .toList()
                        .indexOf(from) != 0;
            }
        }
        return true;
    }

    // ========== Rows ==========

    /**
     * Numbers the rows so everything sits below what it depends on, repeatedly rather than once, so
     * that nothing here depends on the order the nodes come in.
     */
    private void numberRows() {
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

    /** The step of kind begin, or the first node nothing leads to. Worked out once and kept. */
    private String findStart() {
        if (start != null) {
            return start;
        }
        for (String id : shapes.keySet()) {
            if (shapes.get(id) == RecipeShapes.Shape.START) {
                start = id;
                return start;
            }
        }
        for (String id : shapes.keySet()) {
            if (predecessors.get(id).isEmpty()) {
                complaints.add("This chart has no step of kind begin, so the drawing starts at '"
                        + id + "', the first thing nothing leads to.");
                start = id;
                return start;
            }
        }
        complaints.add("This chart has no step to start at: every node has something leading to it.");
        start = shapes.keySet().iterator().next();
        return start;
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
     * Puts each node in the middle of the leaves it is responsible for.
     * <p>
     * A node that splits into two is given one leaf's worth of the chart for each branch, and each
     * branch is given one for whatever it splits into in turn. A node with a single branch below it
     * therefore sits on exactly the same line as that branch all the way down, and the edge between
     * them is straight however wide the two boxes are.
     * <p>
     * A leaf is as wide as the widest thing that stands over it. A bar with a long comparison
     * beside it is wider than the step below it. Two such bars have to sit further apart than two
     * steps do, and if the room is not made for them here they are pushed apart afterward and no
     * longer stand over the branch they belong to.
     */
    private void assignAxis() {
        int used = numberLeaves(findStart(), 0);
        int room = used + shapes.size() + 1;
        int[] leafWidth = new int[room];
        for (Map.Entry<String, int[]> entry : leafRange.entrySet()) {
            int[] range = entry.getValue();
            int covered = Math.max(1, range[1] - range[0]);
            int each = (widthOf(entry.getKey()) + covered - 1) / covered;
            for (int leaf = range[0]; leaf < range[1]; leaf++) {
                leafWidth[leaf] = Math.max(leafWidth[leaf], each);
            }
        }
        int[] where = new int[room + 1];
        for (int leaf = 0; leaf < room; leaf++) {
            where[leaf + 1] = where[leaf] + leafWidth[leaf] + SfcMetrics.GAP;
        }

        widenConvergentRanges();
        for (Map.Entry<String, int[]> entry : leafRange.entrySet()) {
            int[] range = entry.getValue();
            axis.put(entry.getKey(), (where[range[0]] + where[range[1]]) / 2);
        }

        int cursor = where[Math.min(used, room)];
        for (String id : orderOfAppearance()) {
            if (axis.containsKey(id)) {
                continue;
            }
            axis.put(id, cursor + halfWidthOf(id));
            cursor += widthOf(id) + SfcMetrics.GAP;
        }
        orderRowsByTheirLeaves();
        separateRows();
        shiftRight();
    }

    /**
     * Widens the run of an arrival to cover every line that reaches it, so that a join stands over
     * all of its branches and the steps after it keep its line.
     * <p>
     * A split does the opposite: the line that leaves it already covers every branch, and the branch
     * itself is only as wide as the leaves under it. Letting a branch take the split's whole run is
     * what puts a fork's line in the middle of the chart instead of over the fork, and it is not
     * done here.
     */
    private void widenConvergentRanges() {
        boolean changed = true;
        int guard = 0;
        int ceiling = chart.getSteps().size() + 1;
        while (changed && guard++ <= ceiling) {
            changed = false;
            for (S88ProcedureLink line : chart.getLinks()) {
                if (line.isDivergent()) {
                    continue;
                }
                List<String> froms = internalNames(line.getFrom());
                for (String id : internalNames(line.getTo())) {
                    int[] mine = leafRange.get(id);
                    if (mine == null) {
                        continue;
                    }
                    for (String before : froms) {
                        int[] theirs = leafRange.get(before);
                        if (theirs == null) {
                            continue;
                        }
                        int low = Math.min(mine[0], theirs[0]);
                        int high = Math.max(mine[1], theirs[1]);
                        if (low != mine[0] || high != mine[1]) {
                            leafRange.put(id, new int[]{low, high});
                            changed = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Puts each row in the order of the leaves it stands over.
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
        int shift = SfcMetrics.MARGIN - leftmost + leftSkips * SfcMetrics.BACK_EDGE_LANE;
        for (String id : shapes.keySet()) {
            axis.merge(id, shift, Integer::sum);
        }
    }

    /**
     * Every node grouped by the row it is in, worked out once and then kept.
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
                    heightOf(id));
            corners.put(id, box);
            deepest = Math.max(deepest, box.y + box.height);
        }

        Map<String, Boolean> syncBars = linesWithABar(corners);
        Map<String, SyncBar> drawnSyncBars = new LinkedHashMap<>();
        List<Link> drawn = new ArrayList<>();
        int rightmost = rightmostOf(corners);
        assignLanes(corners);
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
        Rectangle total = new Rectangle(0, 0,
                Math.max(1, widestRight + SfcMetrics.BACK_EDGE_LANE),
                Math.max(1, deepest + SfcMetrics.MARGIN));
        return new Placement(cornersOfKind(corners, false), cornersOfKind(corners, true),
                drawnSyncBars, drawn, total, List.copyOf(complaints), goingBackwards.size());
    }

    /**
     * Gives every line that goes back up a lane of its own to the right of everything else.
     * <p>
     * One lane for all of them is one lane they would share, and two loops drawn on the same line
     * is a drawing that cannot be read. Each loop takes the next lane, in the order the recipe
     * wrote the lines in, so the same recipe always comes out the same way.
     */
    private void assignLanes(Map<String, Rectangle> corners) {
        int rightmost = rightmostOf(corners);
        int lane = 1;
        for (S88ProcedureLink link : links) {
            List<String> tos = internalNames(link.getTo());
            boolean takesOne = false;
            for (String from : internalNames(link.getFrom())) {
                for (String to : tos) {
                    if (goingBackwards.contains(edge(from, to))) {
                        laneOfEdge.put(edge(from, to),
                                rightmost + lane * SfcMetrics.BACK_EDGE_LANE);
                        takesOne = true;
                    }
                }
            }
            if (takesOne) {
                lane++;
            }
        }
        int left = 0;
        for (String skip : skips) {
            if (skipToTheRight.getOrDefault(skip, true)) {
                laneOfEdge.put(skip, rightmost + lane++ * SfcMetrics.BACK_EDGE_LANE);
            } else {
                laneOfEdge.put(skip, SfcMetrics.MARGIN + left++ * SfcMetrics.BACK_EDGE_LANE);
            }
        }
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

        int middle = (highestBottom + lowestTop) / 2;
        boolean doubled = link.getLinkType() != null && link.getLinkType().isParallel();
        int height = SfcMetrics.SYNC_BAR_HEIGHT * (doubled ? 2 : 1);
        return new Rectangle(left - SfcMetrics.SYNC_BAR_WIDTH / 2,
                middle - height / 2,
                right - left + SfcMetrics.SYNC_BAR_WIDTH, height);
    }

    /**
     * The line between two things, as the points to draw it through.
     */
    private Link route(String linkId, String from, String to, Map<String, Rectangle> corners,
                       Rectangle syncBar) {
        Rectangle source = corners.get(from);
        Rectangle target = corners.get(to);
        Point leaving = leavesAt(from, source);
        Point arriving = arrivesAt(to, target);

        if (skips.contains(edge(from, to))) {
            return skipRoute(linkId, from, to, source, target);
        }
        if (goingBackwards.contains(edge(from, to))) {
            int lane = laneOfEdge.getOrDefault(edge(from, to),
                    rightmostOf(corners) + SfcMetrics.BACK_EDGE_LANE);
            Point into = sideOf(target);
            int dip = Math.max(leaving.y + SfcMetrics.ROW / 3, into.y - SfcMetrics.ROW / 3);
            return new Link(linkId + ">" + from + ">" + to, linkId, true, false, List.of(
                    leaving,
                    new Point(leaving.x, dip),
                    new Point(lane, dip),
                    new Point(lane, into.y),
                    into));
        }
        if (syncBar == null) {
            if (leaving.x == arriving.x) {
                return new Link(linkId + ">" + from + ">" + to, linkId, false, false,
                        List.of(leaving, arriving));
            }
            int corridor = corridorBetween(leaving, arriving, corners, from, to);
            return new Link(linkId + ">" + from + ">" + to, linkId, false, false, List.of(
                    leaving,
                    new Point(leaving.x, corridor),
                    new Point(arriving.x, corridor),
                    arriving));
        }
        int barY = syncBar.y + SfcMetrics.SYNC_BAR_HEIGHT / 2;
        return new Link(linkId + ">" + from + ">" + to, linkId, false, false, List.of(
                leaving,
                new Point(leaving.x, barY),
                new Point(arriving.x, barY),
                arriving));
    }

    /**
     * A height between two nodes where a sideways stretch crosses no other node.
     */
    private int corridorBetween(Point leaving, Point arriving, Map<String, Rectangle> corners,
                                String from, String to) {
        int low = Math.min(leaving.y, arriving.y) + 1;
        int high = Math.max(leaving.y, arriving.y) - 1;
        if (low > high) {
            return (leaving.y + arriving.y) / 2;
        }
        int middle = (low + high) / 2;
        int left = Math.min(leaving.x, arriving.x);
        int right = Math.max(leaving.x, arriving.x);
        for (int step = 0; step <= high - low; step++) {
            for (int y : new int[]{middle - step, middle + step}) {
                if (y >= low && y <= high && corridorIsClear(y, left, right, corners, from, to)) {
                    return y;
                }
            }
        }
        return middle;
    }

    private static boolean corridorIsClear(int y, int left, int right,
                                           Map<String, Rectangle> corners,
                                           String from, String to) {
        for (Map.Entry<String, Rectangle> node : corners.entrySet()) {
            if (node.getKey().equals(from) || node.getKey().equals(to)) {
                continue;
            }
            Rectangle box = node.getValue();
            if (y >= box.y && y <= box.y + box.height
                    && Math.max(left, box.x) <= Math.min(right, box.x + box.width)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The line that skips a step, taken round the side of the block it leaves.
     *
     * <p>It leaves the bar below its own middle, goes out to a lane clear of every node, comes back
     * above the bar it joins, and drops into it. That keeps it off the step it skips and off the
     * condition written beside either bar.
     */
    private Link skipRoute(String linkId, String from, String to, Rectangle source,
                           Rectangle target) {
        String edgeId = edge(from, to);
        boolean right = skipToTheRight.getOrDefault(edgeId, true);
        int lane = laneOfEdge.getOrDefault(edgeId,
                rightmostOf(Map.of(from, source, to, target)) + SfcMetrics.BACK_EDGE_LANE);
        int sourceX = acrossAt(from, source);
        int sourceY = barOrEdge(from, source, true);
        int targetX = acrossAt(to, target);
        int targetY = barOrEdge(to, target, false);
        int below = sourceY + SfcMetrics.ROW / 3;
        int above = targetY - SfcMetrics.ROW / 3;
        return new Link(linkId + ">" + from + ">" + to, linkId, false, !right, List.of(
                new Point(sourceX, sourceY),
                new Point(sourceX, below),
                new Point(lane, below),
                new Point(lane, above),
                new Point(targetX, above),
                new Point(targetX, targetY)));
    }

    /**
     * Where a line leaves a node.
     */
    private Point leavesAt(String node, Rectangle box) {
        return new Point(acrossAt(node, box), barOrEdge(node, box, true));
    }

    /** Where a line arrives at a node, which is the middle of a bar and the top of a step. */
    private Point arrivesAt(String node, Rectangle box) {
        return new Point(acrossAt(node, box), barOrEdge(node, box, false));
    }

    /**
     * Where a line that goes back up enters a node: its right side.
     * <p>
     * It comes in from the lane it was taken out to, so it arrives at the side rather than over the
     * top of whatever is above the node, which is what it would have to cross to come in from there.
     */
    private Point sideOf(Rectangle box) {
        return new Point(box.x + box.width, box.y + box.height / 2);
    }

    /** Across a node: the middle of a bar, and the middle of a box. */
    private int acrossAt(String node, Rectangle box) {
        return shapes.get(node) == RecipeShapes.Shape.TRANSITION
                ? box.x + SfcMetrics.TRANSITION_WIDTH / 2
                : box.x + box.width / 2;
    }

    /** Along a node: a bar is left and arrived at its own middle, a box at its two edges. */
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
     * @param id          what it is known by inside the scene, which is not the line it came from
     * @param lineId      the line of the recipe this is a piece of
     * @param backwards   whether it goes back up the chart, which is what asks for an arrow
     * @param leavesLeft  whether it starts towards the left, which is what a skip round the left does
     * @param path        the points to draw through, in order
     */
    record Link(String id, String lineId, boolean backwards, boolean leavesLeft, List<Point> path) {
    }
}
