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
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JComponent;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
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
            what.put(step.getId(), new NodeSpec(shape,
                    shape == RecipeShapes.Shape.BOX ? step.getId() : null));
        }
        for (S88ProcedureTransition transition : chart.getTransitions()) {
            what.put(transition.getId(), new NodeSpec(
                    RecipeShapes.Shape.TRANSITION, conditionText(transition)));
        }
        return what;
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
        wanted.forEach(this::route);
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
        connection.setSourceAnchor(new SfcAnchor(this, path.get(0), Anchor.Direction.BOTTOM));
        connection.setTargetAnchor(new SfcAnchor(this, path.get(path.size() - 1),
                Anchor.Direction.TOP));
        connection.setRouter(new SfcRouter(path));
        // A line that goes back up the chart closes a loop, so it is the one that needs saying so
        // with an arrow where it arrives.
        connection.setTargetAnchorShape(
                link.backwards() ? AnchorShape.TRIANGLE_FILLED : AnchorShape.NONE);
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
     * @param node name of the node now picked, {@code null} for nothing picked
     */
    void select(String node) {
        String wanted = node == null || nodes.containsKey(node) ? node : null;
        if (java.util.Objects.equals(wanted, this.selected)) {
            return;
        }
        Widget was = findWidget(this.selected);
        this.selected = wanted;
        if (was != null) {
            was.repaint();
        }
        Widget now = findWidget(wanted);
        if (now != null) {
            now.repaint();
        }
    }

    /** Forgets a node that is no longer on the chart, rather than acting on something that is gone. */
    private void forgetSelectionIfItIsGone() {
        if (selected != null && !nodes.containsKey(selected)) {
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

    /** Whether a node is the picked one, which is how the drawing knows to mark it. */
    boolean isSelected(String node) {
        return selected != null && selected.equals(node);
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

    @Override
    protected Widget attachNodeWidget(String node) {
        NodeSpec spec = nodes.get(node);
        RecipeShapes.Shape shape = spec == null ? RecipeShapes.Shape.BOX : spec.shape();
        RecipeNodeWidget widget =
                new RecipeNodeWidget(this, node, shape, spec == null ? node : spec.label());
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
    record NodeSpec(RecipeShapes.Shape shape, String label) {
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

        RecipeNodeWidget(RecipeChartScene chart, String id,
                         RecipeShapes.Shape shape, String label) {
            super(chart);
            this.chart = chart;
            this.id = id;
            this.shape = shape;
            this.label = label;
            setToolTipText(id);
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
        }
    }
}
