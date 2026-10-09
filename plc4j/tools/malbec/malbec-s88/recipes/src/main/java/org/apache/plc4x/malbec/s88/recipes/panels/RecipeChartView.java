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
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;

/**
 * The chart of a recipe, drawn as boxes and bars.
 * <p>
 * The recipe is read into the scene on every redraw, so the chart and the table are two drawings of
 * one thing and cannot drift apart: whatever either of them changed is what the other shows.
 * <p>
 * <b>Joining two things is an edit.</b> A line dragged from a box to a bar becomes a link in the
 * recipe, and the connector refuses anything else.
 * <p>
 * <b>Where things are drawn is not part of the recipe.</b> A sequential chart is read from top to
 * bottom, so the chart is arranged again every time it is drawn rather than remembering where a box
 * was when it was last saved.
 */
public final class RecipeChartView extends AbstractRecipeView {

    private final RecipeChartScene scene = new RecipeChartScene();

    public RecipeChartView(RecipeEditorModel model) {
        super(model);
        scene.setOnConnected(this::onConnected);
        view = scene.createView();
        pickWithThePointer();
        zoomWithControlAndWheel(view, scene);
        new RecipeChartToolbar(this, scene).addTo(toolBar());
        add(new JScrollPane(view), BorderLayout.CENTER);

        problems = new RecipeProblems();
        JPanel below = new JPanel(new BorderLayout());
        below.add(problems, BorderLayout.CENTER);
        add(below, BorderLayout.SOUTH);
    }

    /** The component the library draws in. It is also what turns a pointer into a chart position. */
    private final JComponent view;

    /** What is wrong with the recipe, which only takes up room when there is something to say. */
    private final RecipeProblems problems;

    /**
     * Picks a box, a bar or a line when the operator clicks on it.
     * <p>
     * Without a click doing this, nothing on the chart says which thing the buttons are about, and
     * they can only act on whatever the last button happened to pick.
     */
    private void pickWithThePointer() {
        view.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                scene.pickAtInView(view, event.getPoint());
            }
        });
    }

    /**
     * Zooms with the wheel while the control key is held, and leaves the wheel to the scroll
     * otherwise.
     * <p>
     * A chart that zooms on every tick of the wheel cannot be scrolled, because the two are the same
     * gesture. Asking for the control key is what tells the operator which of the two they are
     * doing.
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
            chart.setZoomFactor(Math.max(0.2, Math.min(4.0, zoom)));
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

    /** The chart being drawn, {@code null} for a recipe that has none. */
    S88ProcedureLogic chart() {
        return model.getRecipe().getProcedureLogic();
    }

    /**
     * Draws the chart again.
     * <p>
     * The whole recipe goes in at once, because the layout has to see the whole chart: where a box
     * sits depends on what leads to it and what leaves it.
     */
    @Override
    protected void redraw() {
        scene.draw(model.getRecipe());
        problems.show(model.conformance());
    }

    /**
 * Remembers that the step just added has no equipment yet, so the chart marks it.
 *
 * <p>
 * <b>An empty box says so on the chart.</b> A step with nothing behind it is where the flow goes
 * and does nothing, and on a chart full of named equipment a box that cannot be told apart from a
 * step that is done is a box an author cannot find again.
 *
 * @param elementId id of the step that was added
 */
    void markEmptyStep(String elementId) {
        emptyStep = elementId;
        scene.markEmpty(elementId);
    }

    /** Forgets which step was empty, once it has been given something to do. */
    void forgetEmptyStep() {
        emptyStep = null;
        scene.markEmpty(null);
    }

    /** The step that was added and has nothing behind it yet, {@code null} when there is none. */
    private String emptyStep;

    /**
     * The problems panel of this view, for a test that reads what it is saying.
     *
     * @return the panel
     */
    RecipeProblems problems() {
        return problems;
    }

    /**
     * Adds a line to the recipe when two things are joined on the chart.
     *
     * @param from name of the node the line leaves
     * @param to   name of the node it arrives at
     */
    private void onConnected(String from, String to) {
        if (chart() == null) {
            return;
        }
        model.edit("Join " + from + " to " + to,
                () -> EditProcedureLogicUseCase.addLink(
                        model.getRecipe(), null, nextLinkId(model.getRecipe().getProcedureLogic()),
                        List.of(from), List.of(to), S88LinkType.CONTROL_LINK));
    }

    /**
     * A name no link in this chart is using, asked for when the line is drawn rather than before it.
     *
     * @param chart the chart the line is being added to
     * @return a name nothing is using
     */
    private static String nextLinkId(S88ProcedureLogic chart) {
        int n = chart.getLinks().size() + 1;
        while (chart.findLink("L" + n).isPresent()) {
            n++;
        }
        return "L" + n;
    }
}
