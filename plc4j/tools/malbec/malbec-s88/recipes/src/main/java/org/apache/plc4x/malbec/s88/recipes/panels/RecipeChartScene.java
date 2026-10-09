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

import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ParameterValue;
import org.apache.plc4x.malbec.s88.api.PlatformVariable;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88RecipeParameter;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.graph.GraphScene;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;

/**
 * The chart of a recipe, drawn as boxes and bars.
 * <p>
 * Where everything goes is worked out by {@link SfcLayoutEngine} and nothing here decides it. That
 * split is the point: the arithmetic of a sequential chart can be tested without a screen, and the
 * drawing is left with putting the answer on it.
 * <p>
 * <b>A line only joins a box to a bar.</b> That is the model being bipartite, and a line from one box
 * to another leaves the flow with nothing to wait on. The connector refuses it rather than letting it
 * be drawn and leaving the conformance rules to report it afterwards.
 */
final class RecipeChartScene extends GraphScene<String, String> {

    /**
 * Most lines a box writes, which is what its fixed height holds at the chart font size.
     * <p>
     * A box does not grow for a step with a lot to say: a chart of boxes at different heights is a
     * chart whose branches no longer line up. What does not fit is not lost, it is under the pointer.
     */
    private static final int MAX_LABEL_LINES = 3;

    /** Connections below the boxes, so that a box is never hidden behind a line. */
    private final LayerWidget connectionLayer = new LayerWidget(this);
    private final LayerWidget nodeLayer = new LayerWidget(this);

    private Map<String, NodeSpec> nodes = new LinkedHashMap<>();
    private String selected;
    private boolean joining;

    /** The widget of each bar across the branches, by the line it is for. */
    private final Map<String, Widget> syncBars = new LinkedHashMap<>();
    /** What the layout could not arrange, which the drawing says out loud rather than hiding. */
    private List<String> complaints = List.of();

    /** How much room the chart needs, which is what the window is sized from. */
    private Rectangle total = new Rectangle();

    /**
     * What each widget was made from.
     * <p>
     * A widget draws the shape and the label it was made with, so a widget is only still correct
     * while these hold what it was made from.
     */
    private final Map<String, NodeSpec> drawn = new HashMap<>();

    /** Told when the operator joins two things, so that the recipe can be changed to match. */
    private BiConsumer<String, String> onConnected = (from, to) -> {
    };

    /**
     * The lines the recipe holds, which is what tells a line apart from a box or a bar.
     * <p>
     * The library knows a connection and a node apart on its own, but the buttons do not get to ask
     * it: they are given a name and have to say what it names.
     */
    private final Map<String, List<String>> pieces = new LinkedHashMap<>();

    /** Where each drawn piece goes, which is what a click has to be measured against. */
    private final Map<String, List<Point>> paths = new LinkedHashMap<>();

    /** Which line of the recipe each drawn piece belongs to. */
    private final Map<String, String> pieceOf = new LinkedHashMap<>();

    /**
     * Builds the scene.
     * <p>
     * <b>The boxes go in first.</b> The library lays out the children of a scene in the order they
     * were added, and the router asks each box where it is before it draws a line. Adding the layer
     * of boxes before the layer of lines puts every box in place before the first line is drawn.
     */
    RecipeChartScene() {
        addChild(nodeLayer);
        addChild(connectionLayer);
        getActions().addAction(ActionFactory.createPanAction());
    }

    /**
     * Reads a recipe in and draws it.
     * <p>
     * Boxes, then the layout, then the lines: the layout says where everything is, and a line whose
     * ends have no position yet has nothing to be drawn between.
     *
     * @param recipe the recipe to draw
     */
    void draw(S88MasterRecipe recipe) {
        Map<String, NodeSpec> what = nodesOf(recipe);
        placeBoxes(what);
        SfcLayoutEngine.Placement at = new SfcLayoutEngine(recipe.getProcedureLogic(),
                shapesOf(what), labelWidthsOf(what)).place();
        applyLayout(at);
        addSyncBars(at.syncBars());
        addLines(at.links());
        applyPendingSelection();
    }

    /**
     * How wide the text on each node is, so that a bar is wide enough for the comparison written
     * beside it.
     * <p>
     * Measured with a throwaway picture rather than a font, because a bar narrower than its own text
     * clips the text, and a bar wider than it needs pushes the branches apart for nothing.
     */
    private static Map<String, Integer> labelWidthsOf(Map<String, NodeSpec> what) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try {
            Map<String, Integer> widths = new LinkedHashMap<>();
            what.forEach((id, spec) -> widths.put(id, RecipeShapes.textWidth(g, spec.label())));
            return widths;
        } finally {
            g.dispose();
        }
    }

    /**
     * Reads a recipe into a scene, so the scene can be drawn or looked at on its own.
     *
     * @param recipe the recipe to draw
     * @return a scene holding its chart
     */
    static RecipeChartScene forRecipe(S88MasterRecipe recipe) {
        RecipeChartScene scene = new RecipeChartScene();
        scene.draw(recipe);
        return scene;
    }

    /**
     * Says when the operator joins two things.
     *
     * @param listener receives the name of each end, in the order the line was drawn
     */
    void setOnConnected(BiConsumer<String, String> listener) {
        this.onConnected = listener;
    }

    /**
     * Every box and bar of a recipe, each with the shape it is drawn as and the name it says.
     *
     * <p>
     * A step says what it works on under its own name, because the name of a box on the chart is the
     * name of the element in the recipe and says nothing about the plant. An engineer reading the chart
     * has to see which heater a step turns on without opening the recipe.
     *
     * @param recipe the recipe to read
     * @return one entry per box and per bar, keyed by the name the chart knows them by
     */
    static Map<String, NodeSpec> nodesOf(S88MasterRecipe recipe) {
        Map<String, NodeSpec> what = new LinkedHashMap<>();
        S88ProcedureLogic chart = recipe.getProcedureLogic();
        if (chart == null) {
            return what;
        }
        for (S88ProcedureStep step : chart.getSteps()) {
            S88RecipeElement element = recipe.findElement(step.getRecipeElementId()).orElse(null);
            RecipeShapes.Shape shape =
                    element == null ? RecipeShapes.Shape.BOX : RecipeShapes.shapeOf(element.getKind());
            // The start and the stop say what they are with their shape. A name under them would only
            // repeat it, and the room it takes would move the symbol off the line the flow leaves by.
            boolean saysSomething = shape == RecipeShapes.Shape.BOX
                    && element != null
                    && namesEquipment(element);
            what.put(step.getId(), new NodeSpec(shape,
                    saysSomething ? stepLabel(step, element) : null,
                    saysSomething ? stepTooltip(step, element) : null));
        }
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            what.put(transition.getId(), new NodeSpec(
                    RecipeShapes.Shape.TRANSITION, conditionText(transition), transition.getId()));
        }
        return what;
    }

/**
 * What a step says on itself: its name, the equipment it works on, and the values it works with.
 *
 * @param step    the box on the chart
 * @param element the step of the recipe it works on, may be {@code null}
 * @return the lines to write
 */
/**
 * What a step says on itself.
 * <p>
 * <b>A step with no equipment says nothing.</b> Its name at that point is one this editor made up
 * so that the chart could hold it, and putting it on the box would be showing the author a name
 * that means nothing to them. A blank box with the standard width is a step waiting for its
 * equipment, and the dashed border says which waiting one it is.
 * <p>
 * <b>A step bound to a class is not named twice.</b> Binding names the step after the class, so
 * saying the class under its own name writes the same word twice, which reads as two different
 * things and is one.
 * <p>
 * <b>The values the step works with go under the name.</b> A step that says it turns a heater on and
 * nothing else leaves the engineer opening the step to find out how hot, and the chart is what they
 * read instead.
 *
 * @param step    the box on the chart
 * @param element the step of the recipe it works on, may be {@code null}
 * @return the lines to write, or {@code null} when it says nothing
 */
    private static String stepLabel(S88ProcedureStep step, S88RecipeElement element) {
        if (element == null || !namesEquipment(element)) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add(step.getId());
        String equipment = element.getEquipmentClassId();
        if (equipment != null && !equipment.isBlank() && !equipment.trim().equals(step.getId())) {
            lines.add(equipment.trim());
        }
        lines.addAll(parameterLines(element, MAX_LABEL_LINES - lines.size()));
        return String.join("\n", lines);
    }

    /**
     * What the step is being given, as the author would say it, with the unit it is measured in.
     *
     * <p>An enumeration has no unit, so what it would be shown with is the value on its own. A value
     * the step has not been given is left out rather than shown empty, because a line saying nothing
     * is a line the author has to read past.
     *
     * <p>What the batch writes while the recipe runs is left out too: the order a module is given is
     * not the recipe's to state, and a box saying {@code NONE} is a box the author reads as nothing
     * happening.
     *
     * @param element  the step of the recipe
     * @param room     how many lines are still free on the box
     * @return the lines, no more than the room there is
     */
    private static List<String> parameterLines(S88RecipeElement element, int room) {
        List<String> lines = new ArrayList<>();
        for (S88RecipeParameter parameter : element.getParameters()) {
            if (lines.size() >= room) {
                break;
            }
            S88ParameterValue value = parameter.getFirstValue();
            if (value == null || value.getFirstValueString() == null
                    || value.getFirstValueString().isBlank()
                    || PlatformVariable.isWrittenByTheBatch(parameter.getId())) {
                continue;
            }
            String unit = value.getUnitOfMeasure();
            lines.add(value.getFirstValueString() + (unit == null || unit.isBlank()
                    ? "" : " " + unit));
        }
        return lines;
    }

    /**
     * What a box says about itself in full, for the pointer to show over it.
     *
     * <p>A box is written to fit and cut off, so what does not fit is here rather than nowhere.
     */
    private static String stepTooltip(S88ProcedureStep step, S88RecipeElement element) {
        List<String> lines = new ArrayList<>();
        lines.add(step.getId());
        String equipment = element.getEquipmentClassId();
        if (equipment != null && !equipment.isBlank() && !equipment.trim().equals(step.getId())) {
            lines.add(equipment.trim());
        }
        for (S88RecipeParameter parameter : element.getParameters()) {
            S88ParameterValue value = parameter.getFirstValue();
            if (value == null || value.getFirstValueString() == null
                    || value.getFirstValueString().isBlank()
                    || PlatformVariable.isWrittenByTheBatch(parameter.getId())) {
                continue;
            }
            String unit = value.getUnitOfMeasure();
            lines.add(parameter.getId() + " = " + value.getFirstValueString()
                    + (unit == null || unit.isBlank() ? "" : " " + unit));
        }
        return String.join("\n", lines);
    }

    /**
     * Whether a step says what it works on, which is what makes it a step rather than a place the flow
     * goes through.
     */
    private static boolean namesEquipment(S88RecipeElement element) {
        return element.getEquipmentClassId() != null
                || !element.getActualEquipmentIds().isEmpty();
    }

    private static String conditionText(S88ProcedureTransition transition) {
        String condition = transition.getCondition();
        return condition == null || condition.isBlank() ? null : condition;
    }

    private static Map<String, RecipeShapes.Shape> shapesOf(Map<String, NodeSpec> what) {
        Map<String, RecipeShapes.Shape> shapes = new LinkedHashMap<>();
        what.forEach((id, spec) -> shapes.put(id, spec.shape()));
        return shapes;
    }

    /**
     * Puts every box and bar on the chart, and takes off the ones that are no longer in it.
     * <p>
     * Boxes are compared by name instead of being taken off and made again. The recipe is read again
     * on every redraw, and redraw happens on every change, so making everything again would take off
     * and make the same widgets hundreds of times. A widget that stays keeps what it was drawn with.
     *
     * @param what shape and name of every box and bar, keyed by the name the chart knows them by
     */
    private void placeBoxes(Map<String, NodeSpec> what) {
        this.nodes = new LinkedHashMap<>(what);
        for (String id : new ArrayList<>(getNodes())) {
            if (!this.nodes.containsKey(id)) {
                removeNode(id);
                drawn.remove(id);
            }
        }
        forgetSelectionIfItIsGone();
        for (Map.Entry<String, NodeSpec> entry : this.nodes.entrySet()) {
            String id = entry.getKey();
            if (isNode(id) && entry.getValue().equals(drawn.get(id))) {
                continue;
            }
            if (isNode(id)) {
                // A widget draws what it was made with, so a box whose shape or label changed needs
                // a new one. Taking it off clears the anchor of every line that used it.
                removeNode(id);
            }
            addNode(id);
            drawn.put(id, entry.getValue());
        }
    }

    /**
     * Puts everything where the layout says.
     *
     * @param at where everything goes, worked out from the chart alone
     */
    void applyLayout(SfcLayoutEngine.Placement at) {
        total = at.total();
        complaints = at.complaints();
        at.steps().forEach(this::put);
        at.bars().forEach(this::put);
    }

    private void put(String id, Rectangle where) {
        Widget widget = findWidget(id);
        if (widget != null) {
            widget.setPreferredBounds(new Rectangle(0, 0, where.width, where.height));
            widget.setPreferredLocation(new Point(where.x, where.y));
        }
    }

    /**
     * Puts in the bars across the branches of a split or a join, and takes off the ones whose line
     * has gone back to being an ordinary edge.
     *
     * @param bars where each one is, by the line it is for
     */
    private void addSyncBars(Map<String, SfcLayoutEngine.SyncBar> bars) {
        for (String line : new ArrayList<>(syncBars.keySet())) {
            if (!bars.containsKey(line)) {
                nodeLayer.removeChild(syncBars.remove(line));
            }
        }
        for (Map.Entry<String, SfcLayoutEngine.SyncBar> entry : bars.entrySet()) {
            SfcLayoutEngine.SyncBar bar = entry.getValue();
            Widget widget = syncBars.get(entry.getKey());
            if (widget != null) {
                nodeLayer.removeChild(widget);
            }
            widget = new SyncBarWidget(this, bar.doubled());
            widget.setPreferredBounds(new Rectangle(0, 0,
                    bar.where().width, bar.where().height));
            widget.setPreferredLocation(new Point(bar.where().x, bar.where().y));
            nodeLayer.addChild(widget);
            syncBars.put(entry.getKey(), widget);
        }
    }

    /**
     * Draws the lines, and takes off the ones that are no longer in the recipe.
     * <p>
     * Each line is drawn through the points the layout worked out for it, with its ends pinned to the
     * first and last of them, so a line and the layout cannot disagree about where it goes.
     *
     * @param links one entry per line, with the points to draw it through
     */
    private void addLines(List<SfcLayoutEngine.Link> links) {
        Map<String, SfcLayoutEngine.Link> wanted = new LinkedHashMap<>();
        for (SfcLayoutEngine.Link link : links) {
            if (link.path().size() >= 2) {
                wanted.put(link.id(), link);
            }
        }
        for (String id : new ArrayList<>(getEdges())) {
            if (!wanted.containsKey(id)) {
                removeEdge(id);
            }
        }
        pieces.clear();
        paths.clear();
        pieceOf.clear();
        wanted.forEach(this::route);
        forgetSelectionIfItIsGone();
        validate();
    }

    private void route(String id, SfcLayoutEngine.Link link) {
        if (!isEdge(id)) {
            addEdge(id);
        }
        Widget widget = findWidget(id);
        if (!(widget instanceof ConnectionWidget connection)) {
            return;
        }
        List<Point> path = link.path();
        paths.put(id, new ArrayList<>(path));
        pieceOf.put(id, link.lineId());
        pieces.computeIfAbsent(link.lineId(), line -> new ArrayList<>()).add(id);
        connection.setSourceAnchor(new SfcAnchor(this, path.get(0), Anchor.Direction.BOTTOM));
        connection.setTargetAnchor(new SfcAnchor(this, path.get(path.size() - 1),
                Anchor.Direction.TOP));
        connection.setRouter(new SfcRouter(path));
        // A line that goes back up the chart closes a loop, so it is the one that needs saying so
        // with an arrow where it arrives.
        connection.setTargetAnchorShape(
                link.backwards() ? AnchorShape.TRIANGLE_FILLED : AnchorShape.NONE);
        strokeOf(id);
    }

    /**
     * Draws one drawn piece of a line thicker or not.
     *
     * <p>
     * A line has no shape of its own, so being picked has to show in the stroke. Otherwise a line the
     * operator has picked looks exactly like one they have not, and the buttons would act on
     * something nobody can see they chose. Only the width changes, so a line nobody has picked is
     * drawn exactly as it was before anything could be picked.
     */
    private void strokeOf(String piece) {
        String line = pieceOf.get(piece);
        boolean picked = line != null && isSelected(line);
        if (findWidget(piece) instanceof ConnectionWidget connection) {
            float width = picked ? RecipeShapes.LINE_WIDTH * 2f : RecipeShapes.LINE_WIDTH;
            connection.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        }
    }

    /**
     * Draws a line as picked or not picked, in every piece it is drawn as.
     */
    private void restyle(String line) {
        if (line != null) {
            pieces.getOrDefault(line, List.of()).forEach(this::strokeOf);
        }
    }

    /** What the layout could not arrange, which the drawing has to say rather than hide. */
    List<String> complaints() {
        return complaints;
    }

    /**
     * How much room the chart covers, which is what the window is sized from.
     *
     * @return the area, empty when there is nothing on it
     */
    Rectangle contentBounds() {
        validate();
        return total;
    }

    /**
     * Where the top left corner of a node is.
     *
     * @param node name of the node
     * @return the corner, or {@code null} when there is no such node
     */
    Point positionOf(String node) {
        Widget widget = findWidget(node);
        return widget == null ? null : widget.getPreferredLocation();
    }

    /**
     * The point on a node where a line arrives and leaves.
     * <p>
     * Not the middle of the widget: a transition is a bar with its comparison beside it, and it is
     * the bar the line crosses.
     *
     * @param node name of the node
     * @return the point, {@code null} when there is no such node
     */
    Point attachesAt(String node) {
        Rectangle box = boundsOf(node);
        if (box == null) {
            return null;
        }
        int across = isBox(node)
                ? box.x + box.width / 2
                : box.x + SfcMetrics.TRANSITION_WIDTH / 2;
        int along = isBox(node)
                ? box.y + box.height
                : box.y + SfcMetrics.TRANSITION_HEIGHT / 2;
        return new Point(across, along);
    }

    /**
     * Where a node is and how big it is.
     *
     * @param node name of the node
     * @return the area it covers in the coordinates of the chart, {@code null} when there is no such
     *         node
     */
    Rectangle boundsOf(String node) {
        Widget widget = findWidget(node);
        if (widget == null || widget.getPreferredLocation() == null) {
            return null;
        }
        Rectangle size = widget.getPreferredBounds();
        return new Rectangle(widget.getPreferredLocation().x, widget.getPreferredLocation().y,
                size.width, size.height);
    }

    /**
     * Where every node is.
     *
     * @return the corner of each node, by name
     */
    Map<String, Point> positions() {
        Map<String, Point> where = new LinkedHashMap<>();
        Widget widget;
        for (String id : nodes.keySet()) {
            if ((widget = findWidget(id)) != null && widget.getPreferredLocation() != null) {
                where.put(id, widget.getPreferredLocation());
            }
        }
        return where;
    }

    /**
     * Says which node the buttons on the toolbar will act on.
     * <p>
     * The node it leaves has to be drawn again as well as the one it lands on: a node that stops
     * being picked has to lose its frame, and repainting only the new one leaves the old frame on
     * the screen with nothing there.
     *
     * <p>
     * <b>A pick of something not on the chart yet is kept until it is.</b> A button that just added
     * a box asks for it to be picked, and the chart is drawn again a moment later, so at the moment
     * of asking there is no such box. Dropping the ask would leave the operator with nothing picked
     * and no way to tell what the button they just pressed did.
     *
     * @param node name of the node now picked, {@code null} for nothing picked
     */
    void select(String node) {
        pickNow(node == null || nodes.containsKey(node) || pieces.containsKey(node) ? node : null);
    }

    /**
     * Picks a node the chart has not been drawn with yet.
     *
     * <p>
     * <b>Kept until the drawing that brings it.</b> A button that has just added a box asks for it to
     * be picked, and the chart is drawn again a moment later, so at the moment of asking there is no
     * such box. Dropping the ask leaves the operator with nothing picked and no way to tell what the
     * button they just pressed did.
     *
     * <p>
     * Written as its own thing rather than as {@link #select(String)} with a name that might not be
     * there yet, because those are two different mistakes: a name nobody has on the chart is a
     * mistake to be forgotten, and one that is not drawn yet is a pick in flight.
     *
     * @param node name of the node to be picked when it appears
     */
    void selectWhenDrawn(String node) {
        if (node == null) {
            pendingSelection = null;
            select(null);
        } else if (nodes.containsKey(node) || pieces.containsKey(node)) {
            pendingSelection = null;
            select(node);
        } else {
            pendingSelection = node;
        }
    }

    /**
     * Puts the frame on one node and takes it off another.
     *
     * @param wanted name of the node now picked, {@code null} for nothing picked
     */
    private void pickNow(String wanted) {
        if (java.util.Objects.equals(wanted, this.selected)) {
            return;
        }
        String wasId = this.selected;
        this.selected = wanted;
        // A box shows being picked with a frame, and a line with a heavier stroke, so both of the
        // ones that changed have to be drawn again.
        Widget was = findWidget(wasId);
        if (was != null) {
            was.repaint();
        }
        restyle(wasId);
        Widget now = findWidget(wanted);
        if (now != null) {
            now.repaint();
        }
        restyle(wanted);
    }

    /** A pick that was asked for before the chart was drawn again. */
    private String pendingSelection;

    /**
     * Picks whatever was asked for before this drawing, when it is on the chart now.
     * <p>
     * Dropped when it is not, because a pick that never arrives belongs to a change that was taken
     * back, and applying it later would put a frame on something nobody chose.
     */
    private void applyPendingSelection() {
        String asked = pendingSelection;
        if (asked == null) {
            return;
        }
        pendingSelection = null;
        if (nodes.containsKey(asked) || pieces.containsKey(asked)) {
            pickNow(asked);
        }
    }

    /** Forgets a pick that is no longer on the chart, rather than acting on something that is gone. */
    private void forgetSelectionIfItIsGone() {
        if (selected != null && !nodes.containsKey(selected) && !pieces.containsKey(selected)) {
            select(null);
        }
    }

    /**
     * Whether dragging from one node to another joins them.
     * <p>
     * A gesture of its own, because moving a node and joining two of them cannot both be what the
     * same drag means, and the chart no longer has anywhere to put a moved node anyway: where
     * everything goes is worked out from the chart every time it is drawn.
     *
     * @param joining whether joining is what a drag means now
     */
    void setJoining(boolean joining) {
        this.joining = joining;
    }

    /** Whether a drag from one node to another joins them, which is off until it is asked for. */
    boolean isJoining() {
        return joining;
    }

    /** Which node the buttons on the toolbar will act on, {@code null} when nothing is picked. */
    String selected() {
        return selected;
    }

    /**
 * Marks one box as having nothing behind it, so the drawing says so.
 *
 * <p>
 * A box that has not been given equipment is drawn with a dashed border and no name. That is the
 * difference between a step that does something and a step that is only a place the flow goes
 * through, and on a chart of named equipment it is the difference the eye needs most.
 *
 * @param node name of the box to mark, {@code null} for none
 */
    void markEmpty(String node) {
        String wanted = node != null && nodes.containsKey(node) ? node : null;
        String was = empty;
        this.empty = wanted;
        Widget before = findWidget(was);
        Widget after = findWidget(wanted);
        if (before != null) {
            before.repaint();
        }
        if (after != null) {
            after.repaint();
        }
    }

    /** Whether a box is one that has no equipment behind it yet. */
    boolean isEmpty(String node) {
        return empty != null && empty.equals(node);
    }

    /** The box that has no equipment behind it yet, {@code null} when there is none. */
    private String empty;

    /** Whether a box or a bar is the picked one, which is how the drawing knows to mark it. */
    boolean isSelected(String node) {
        return selected != null && selected.equals(node);
    }

    /** Whether a name is a line of the recipe rather than a box or a bar. */
    boolean isLine(String node) {
        return node != null && pieces.containsKey(node);
    }

    /**
     * Where a line of the recipe is drawn.
     *
     * <p>
     * A line with two ends on each side is drawn as one piece per pair of ends, so this is the first
     * of those pieces. Read back from the points the layout gave rather than worked out again, because
     * a line measured somewhere other than where it is drawn would pass while the drawing was wrong.
     *
     * @param line name of the line
     * @return the points it goes through, empty when there is no such line
     */
    List<Point> pathOfLine(String line) {
        List<String> ofIt = pieces.get(line);
        return ofIt == null || ofIt.isEmpty() ? List.of() : paths.getOrDefault(ofIt.get(0), List.of());
    }

    /**
     * The first line of the chart, in the order the recipe holds its lines.
 *
     * @return the name of a line, {@code null} when the chart has none
     */
    String firstLine() {
        return pieces.keySet().stream().findFirst().orElse(null);
    }

    /**
     * The boxes and bars on the chart, so that a point can be measured against them.
     *
     * @return the name of each of them
     */
    java.util.Collection<String> nodes() {
        return nodes.keySet();
    }

    /**
     * Picks whatever the operator pointed at.
     * <p>
     * A box or a bar first, then a line, then nothing. A box wins where a line runs into it, because
     * the line ends there and the operator pointing at that spot is pointing at the step.
     *
     * <p>
     * Without this the buttons have nothing to act on. Nothing else on the chart turns a click into a
     * pick, so without it the toolbar only ever worked on whatever the last button had picked.
     *
     * @param point where the pointer went down, in the coordinates of the scene
     */
    void pickAt(Point point) {
        String node = nodeAt(point);
        select(node != null ? node : lineAt(point));
    }

    /**
     * The line under a point, when it is close enough to one to be meant.
     *
     * @param point where the pointer went down, in the coordinates of the scene
     * @return the name of the line of the recipe, or {@code null} when the point is on empty chart
     */
    String lineAt(Point point) {
        String nearest = null;
        int closest = PICK_RADIUS;
        for (Map.Entry<String, String> piece : pieceOf.entrySet()) {
            int away = distanceTo(paths.getOrDefault(piece.getKey(), List.of()), point);
            if (away < closest) {
                closest = away;
                nearest = piece.getValue();
            }
        }
        return nearest;
    }

    /** How near a point has to be to a line to count as pointing at it. */
    private static final int PICK_RADIUS = 6;

    /**
     * How far a point is from a line, measured to the closest of its pieces.
     *
     * @param path  the points the line goes through, in order
     * @param point where the pointer went down
     * @return the distance in pixels, or {@link Integer#MAX_VALUE} for a line with no pieces
     */
    private static int distanceTo(List<Point> path, Point point) {
        int nearest = Integer.MAX_VALUE;
        for (int i = 1; i < path.size(); i++) {
            nearest = Math.min(nearest, distanceToSegment(path.get(i - 1), path.get(i), point));
        }
        return nearest;
    }

    /**
     * How far a point is from one straight piece of a line.
     *
     * <p>
     * Measured to the closest point of the piece, which is the projection of the point onto it. A
     * piece with no length of its own is measured to its one end, because there is nothing else on it
     * to measure to.
     */
    private static int distanceToSegment(Point from, Point to, Point point) {
        double across = to.x - from.x;
        double down = to.y - from.y;
        double length = across * across + down * down;
        if (length == 0) {
            return (int) point.distance(from);
        }
        double howFar = ((point.x - from.x) * across + (point.y - from.y) * down) / length;
        double at = Math.max(0, Math.min(1, howFar));
        int nearestX = (int) Math.round(from.x + at * across);
        int nearestY = (int) Math.round(from.y + at * down);
        return (int) Math.hypot(point.x - nearestX, point.y - nearestY);
    }

    /**
     * Whether a node is a box that can take a step after it.
     * <p>
     * A bar holds the flow while it waits, so it has no step after it. The start has no step before
     * it either, but a step after the start is the ordinary first step of a recipe.
     *
     * @param node name of the node
     * @return true when the node is a step, false when it is a bar
     */
    boolean isBox(String node) {
        NodeSpec spec = nodes.get(node);
        return spec != null && spec.shape() != RecipeShapes.Shape.TRANSITION;
    }

    /**
     * How many widgets the two layers hold.
     * <p>
     * A widget left behind in a layer after the node is gone still counts here, and that is the leak
     * this count is here to catch.
     *
     * @return the number of widgets the connections and the boxes are drawn on
     */
    int widgetCount() {
        return connectionLayer.getChildren().size() + nodeLayer.getChildren().size();
    }

    /** The node under a point in the coordinates of the scene. */
    String nodeAt(Point point) {
        Widget widget;
        for (String id : nodes.keySet()) {
            if ((widget = findWidget(id)) == null) {
                continue;
            }
            // The size comes from the bounds the widget asked for, and the place from where the
            // layout put it. The resolved bounds carry only a size, and they are null until a window
            // lays the chart out, so neither one on its own is enough.
            java.awt.Dimension size = widget.getPreferredBounds().getSize();
            Point where = widget.getPreferredLocation();
            if (where == null) {
                continue;
            }
            if (new Rectangle(where.x, where.y, size.width, size.height).contains(point)) {
                return id;
            }
        }
        return null;
    }

    /**
     * The node under a point given in the coordinates of the view.
     *
     * @param view the component the library draws in, which is what the point is measured against
     * @param point where the pointer is, in the coordinates of that component
     * @return the name of the node, or {@code null} when the point is on empty chart
     */
    String nodeAtInView(JComponent view, Point point) {
        return nodeAt(convertViewToScene(point));
    }

    /**
     * Picks whatever a click went on, in the coordinates of the view.
     *
     * @param view  the component the library draws in, which is what the point is measured against
     * @param point where the pointer went down, in the coordinates of that component
     */
    void pickAtInView(JComponent view, Point point) {
        pickAt(convertViewToScene(point));
    }

    @Override
    protected Widget attachNodeWidget(String node) {
        NodeSpec spec = nodes.get(node);
        RecipeShapes.Shape shape = spec == null ? RecipeShapes.Shape.BOX : spec.shape();
        RecipeNodeWidget widget = new RecipeNodeWidget(this, node, shape,
                spec == null ? node : spec.label(), spec == null ? node : spec.tooltip());
        nodeLayer.addChild(widget);
        widget.getActions().addAction(new JoinOnRelease());
        return widget;
    }

    /**
     * Joins two things when the operator drags from one to the other, and only then.
     * <p>
     * Nothing is drawn while the pointer moves, so nothing is left behind either: the library's own
     * action draws a line that follows the pointer and keeps it when the drag ends on empty space.
     */
    private final class JoinOnRelease extends org.netbeans.api.visual.action.WidgetAction.Adapter {

        private static final long serialVersionUID = 1L;
        private String dragged;

        @Override
        public org.netbeans.api.visual.action.WidgetAction.State mousePressed(
                Widget widget, org.netbeans.api.visual.action.WidgetAction.WidgetMouseEvent event) {
            dragged = joining ? fromWidget(widget) : null;
            return org.netbeans.api.visual.action.WidgetAction.State.REJECTED;
        }

        @Override
        public org.netbeans.api.visual.action.WidgetAction.State mouseReleased(
                Widget widget, org.netbeans.api.visual.action.WidgetAction.WidgetMouseEvent event) {
            String from = dragged;
            dragged = null;
            if (from == null) {
                return org.netbeans.api.visual.action.WidgetAction.State.REJECTED;
            }
            String to = fromWidget(widget);
            // A box to a bar, or a bar to a box. Two boxes or two bars would leave the flow with
            // nothing to wait on.
            if (to != null && !to.equals(from) && isBar(from) != isBar(to)) {
                onConnected.accept(from, to);
            }
            return org.netbeans.api.visual.action.WidgetAction.State.REJECTED;
        }
    }

    private String fromWidget(Widget widget) {
        if (widget == null) {
            return null;
        }
        Object object = findObject(widget);
        return object == null ? null : object.toString();
    }

    private boolean isBar(String node) {
        NodeSpec spec = nodes.get(node);
        return spec != null && spec.shape() == RecipeShapes.Shape.TRANSITION;
    }

    @Override
    protected Widget attachEdgeWidget(String edge) {
        ConnectionWidget connection = new ConnectionWidget(this);
        connection.setStroke(new BasicStroke(RecipeShapes.LINE_WIDTH));
        connectionLayer.addChild(connection);
        return connection;
    }

    /**
     * Takes the widget of a node out of the layer when the library takes the node out.
     * <p>
     * The library calls this and nothing else. A widget left inside the layer stays there after the
     * node is gone, still holding the anchor that a connection uses to find it.
     */
    @Override
    protected void detachNodeWidget(String node, Widget widget) {
        if (widget != null) {
            nodeLayer.removeChild(widget);
        }
    }

    /**
     * Takes the widget of a connection out of the layer when the library takes the connection out.
     */
    @Override
    protected void detachEdgeWidget(String edge, Widget widget) {
        if (widget != null) {
            connectionLayer.removeChild(widget);
        }
    }

    /**
     * Nothing to do when the library attaches the end of a line to a node.
     * <p>
     * The ends of a line are points the layout worked out rather than widgets, so there is no widget
     * for the library to find one on. {@link #route} pins both ends itself.
     */
    @Override
    protected void attachEdgeSourceAnchor(String edge, String oldSource, String newSource) {
    }

    /** Nothing to do, for the same reason as {@link #attachEdgeSourceAnchor}. */
    @Override
    protected void attachEdgeTargetAnchor(String edge, String oldTarget, String newTarget) {
    }

    /**
     * One box or one bar.
     *
     * @param shape how it is drawn
     * @param label what it says on itself, may be {@code null}
     */
    record NodeSpec(RecipeShapes.Shape shape, String label, String tooltip) {
    }

    /**
     * One box or one bar on the screen.
     * <p>
     * A widget of our own rather than one of the platform's, because a step, a transition and the bar
     * across a split are drawn differently and an icon widget could only ever show a picture of one.
     */
    private static final class RecipeNodeWidget extends Widget {

private final RecipeChartScene chart;
    private final String id;
    private final RecipeShapes.Shape shape;
    private final String label;
    private final String tooltip;

    RecipeNodeWidget(RecipeChartScene chart, String id,
                     RecipeShapes.Shape shape, String label, String tooltip) {
            super(chart);
            this.chart = chart;
            this.id = id;
            this.shape = shape;
            this.label = label;
            this.tooltip = tooltip;
            setToolTipText(tooltip == null ? id : tooltip);
            setPreferredBounds(new Rectangle(0, 0, SfcMetrics.widthOf(shape), SfcMetrics.heightOf(shape)));
        }

        @Override
        protected void paintWidget() {
            Graphics2D g = getGraphics();
            if (g == null) {
                return;
            }
            Rectangle bounds = getBounds();
            RecipeShapes.paint(shape,
                    new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height),
                    g, label);
            if (chart.isSelected(id)) {
                // The frame is inside the widget's own area on purpose: the library clips the drawing
                // to the widget, so a frame outside it is a frame nobody sees.
                g.setColor(Color.DARK_GRAY);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{4f, 3f}, 0f));
                g.drawRect(1, 1, bounds.width - 3, bounds.height - 3);
            }
            if (chart.isEmpty(id)) {
                // Dashed, and not the frame of a pick, so a box that is waiting for its equipment
                // looks different from one that is merely the one the buttons are about.
                g.setColor(Color.GRAY);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{3f, 3f}, 0f));
                g.drawRect(2, 2, bounds.width - 5, bounds.height - 5);
            }
        }
    }
}
