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

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

/**
 * The chart of a recipe, drawn as boxes and transitions.
 * <p>
 * The recipe is read into the scene on every redraw, so the chart and the table are two drawings of
 * one thing and cannot drift apart: whatever either of them changed is what the other shows.
 * <p>
 * <b>Joining two things is an edit.</b> A line dragged from a box to a transition becomes a link in
 * the recipe, and the connector refuses anything else.
 * <p>
 * <b>Where things are drawn is not part of the recipe.</b> A sequential chart is read from top to
 * bottom, so the chart is arranged again every time it is drawn rather than remembering where a box
 * was when it was last saved.
 */
public final class RecipeChartView extends AbstractRecipeView {

    private final RecipeChartScene scene = new RecipeChartScene();

    /** The orders of this chart, which the bar, the menu and the keys all run. */
    private final RecipeChartToolbar toolbar;

    public RecipeChartView(RecipeEditorModel model) {
        super(model);
        toolbar = new RecipeChartToolbar(this, scene);
        scene.setOnConnected(toolbar::join);
        scene.setOnSelectionChanged(toolbar::refresh);
        view = scene.createView();
        pickWithThePointer();
        bindKeys();
        zoomWithControlAndWheel(view, scene);
        toolbar.addTo(toolBar());
        add(new JScrollPane(view), BorderLayout.CENTER);

        problems = new RecipeProblems();
        problems.onPick(scene::select);
        JPanel below = new JPanel(new BorderLayout());
        below.add(problems, BorderLayout.CENTER);
        add(below, BorderLayout.SOUTH);
    }

    /** The component the library draws in. It is also what turns a pointer into a chart position. */
    private final JComponent view;

    /** What is wrong with the recipe, which only takes up room when there is something to say. */
    private final RecipeProblems problems;

    /** The menu a right click brings up, filled when it comes up. */
    private final JPopupMenu contextMenu = new JPopupMenu();

    /**
     * Picks a box, a transition or a line when the operator clicks on it.
     *
     * <p>A double click draws the chart of the step the box works on, which is the only way in. A
     * right click offers the orders of the chart on whatever was picked. On some platforms a right
     * click is the button going down and on others the button coming up, so both are asked.
     */
    private void pickWithThePointer() {
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                scene.pickAtInView(view, event.getPoint());
                if (event.isPopupTrigger()) {
                    showMenuFor(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    toolbar.openChartOfPickedBox();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenuFor(event);
                }
            }
        });
    }

    /**
     * Offers the orders of the chart on whatever the pointer is over.
     *
     * @param event where the right button went down, which is where the menu comes up
     */
    private void showMenuFor(MouseEvent event) {
        if (contextMenu.isVisible()) {
            return;
        }
        contextMenu.removeAll();
        toolbar.fillMenu(contextMenu);
        contextMenu.show(view, event.getX(), event.getY());
    }

    /**
     * Binds the keys of the chart to the orders, which are the same instances the bar and the menu
     * use.
     */
    private void bindKeys() {
        InputMap keys = view.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap doings = view.getActionMap();
        put(keys, doings, KeyEvent.VK_DELETE, "chart.delete", toolbar.deleteAction());
        put(keys, doings, KeyEvent.VK_F2, "chart.renameOrCondition", toolbar.f2Action());
        put(keys, doings, KeyEvent.VK_ENTER, "chart.openChart", toolbar.openChartAction());
        put(keys, doings, KeyEvent.VK_ESCAPE, "chart.leaveLink", toolbar.leaveLinkAction());
    }

    private static void put(InputMap keys, ActionMap doings, int key, String name, Action action) {
        keys.put(KeyStroke.getKeyStroke(key, 0), name);
        doings.put(name, action);
    }

    /**
     * Zooms with the wheel while the control key is held.
     *
     * @param target the component the library draws in, which is what receives the wheel
     * @param chart  the chart to zoom, which is where the zoom factor is held
     */
    private static void zoomWithControlAndWheel(JComponent target, RecipeChartScene chart) {
        double step = 1.1;
        target.addMouseWheelListener(event -> {
            if (!event.isControlDown()) {
                return;
            }
            double zoom = chart.getZoomFactor();
            if (event.getWheelRotation() < 0) {
                zoom *= step;
            } else {
                zoom /= step;
            }
            chart.setZoomFactor(Math.clamp(zoom, 0.2, 4.0));
            event.consume();
        });
    }

    @Override
    public String viewName() {
        return "Chart";
    }

    /** The editor state this view is drawing. */
    RecipeEditorModel model() {
        return model;
    }

    /**
     * Says whether dragging from one thing to another joins them, which is what the Link order turns
     * on and off.
     *
     * @param joining whether a drag joins two things
     */
    void showJoining(boolean joining) {
        scene.setJoining(joining);
        Cursor cursor = Cursor.getPredefinedCursor(
                joining ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR);
        view.setCursor(cursor);
        scene.setCursor(cursor);
    }

    @Override
    protected void redraw() {
        scene.draw(model.getRecipe(), model.currentStep());
        Graphics2D onScreen = (Graphics2D) view.getGraphics();
        if (onScreen != null) {
            try {
                scene.validate(onScreen);
            } finally {
                onScreen.dispose();
            }
        }
        view.revalidate();
        view.repaint();
        toolbar.refresh();
        problems.show(model.conformance(), scene.complaints(), model.warning());
    }
}
