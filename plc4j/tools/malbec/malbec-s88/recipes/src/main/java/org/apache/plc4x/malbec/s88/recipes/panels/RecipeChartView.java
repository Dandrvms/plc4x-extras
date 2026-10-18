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
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;

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

    /** The buttons that act on the chart on show, which move as that chart moves. */
    private RecipeChartToolbar toolbar;

    public RecipeChartView(RecipeEditorModel model) {
        super(model);
        scene.setOnConnected(this::onConnected);
        view = scene.createView();
        pickWithThePointer();
        zoomWithControlAndWheel(view, scene);
        this.toolbar = new RecipeChartToolbar(this, scene);
        toolbar.addTo(toolBar());
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
     *
     * <p>A double click draws the chart of the step the box works on, which is the only way in.
     * A right click offers the actions of the toolbar on whatever was picked.
     */
    private void pickWithThePointer() {
        view.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                scene.pickAtInView(view, event.getPoint());
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openChartOfPickedBox();
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showMenuFor(event);
                }
            }
        });
    }

    /**
     * Draws the chart of the step the picked box works on.
     *
     * <p>A step that holds no chart of its own is told apart from one whose chart has not been made
     * yet: a unit procedure or an operation is meant to have one, so it is offered here rather than
     * being told it is the last level when it is not.
     */
    private void openChartOfPickedBox() {
        String box = scene.pickedBox();
        if (box == null) {
            return;
        }
        S88ProcedureStep picked = model.chartBeingEdited().findStep(box).orElse(null);
        if (picked == null) {
            return;
        }
        S88RecipeElement step = model.getRecipe()
                .findElement(picked.getRecipeElementId()).orElse(null);
        if (step == null) {
            return;
        }
        if (step.getProcedureLogic() == null && !RecipeLevels.allowsSubChart(step.getKind())) {
            JOptionPane.showMessageDialog(this, "Step '" + step.getId() + "' is a "
                    + step.getKind() + ", which is the last level and holds no chart of its own.",
                    "Nothing under it", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        model.editChartOf(step);
    }

    /**
     * Offers the actions of the toolbar on whatever the pointer is over.
     *
     * @param event where the right button went down, which is where the menu comes up
     */
    private void showMenuFor(java.awt.event.MouseEvent event) {
        scene.pickAtInView(view, event.getPoint());
        JPopupMenu menu = new JPopupMenu();
        RecipeChartToolbar.buildMenu(menu, model, scene, this::redraw);
        menu.show(view, event.getX(), event.getY());
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

    /** The chart being drawn, {@code null} for a recipe that has none. */
    S88ProcedureLogic chart() {
        return model.chartBeingEdited();
    }

    /** The step whose chart is being drawn, {@code null} for the chart of the recipe. */
    S88RecipeElement owner() {
        return model.currentStep();
    }

    @Override
    protected void redraw() {
        scene.draw(model.getRecipe(), model.currentStep());
        scene.markEmpty(model.justAddedStep());
        toolbar.refreshUpButton();
        problems.show(RecipeProblems.toShow(model.conformance(), scene.complaints(),
                model.warning()));
    }

    /**
     * Remembers that the step just added has no equipment yet, so the chart marks it.
     *
     * @param elementId id of the step that was added
     */
    void markEmptyStep(String elementId) {
        model.markEmptyStep(elementId);
    }

    /** Forgets which step was empty, once it has been given something to do. */
    void forgetEmptyStep() {
        model.forgetEmptyStep();
    }

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
                        model.getRecipe(), model.currentStep(),
                        nextLinkId(model.chartBeingEdited()),
                        List.of(from), List.of(to), S88LinkType.CONTROL_LINK));
    }

    /**
     * A name no link in this chart is using.
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
