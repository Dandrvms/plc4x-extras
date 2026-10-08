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

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;

/**
 * The buttons that change the chart of a recipe.
 * <p>
 * They go in the bar that every view already has, so a window has one bar and not one per view.
 * <p>
 * Each button acts on the box or bar that is picked on the chart. A recipe that will not take the
 * change is said so in a dialog, because an exception from a button press reaches nobody.
 * <p>
 * <b>Splitting is one button because whether both branches run is not the operator's choice to
 * make.</b> A box splitting is a selective split and a bar splitting is a parallel one, and the
 * symbol the split leaves is what says which. Two buttons would have had the same two names over
 * and over with the difference only in what they drew, and the operator would have had to know the
 * difference before being allowed to make it.
 */
final class RecipeChartToolbar {

    private final RecipeChartView view;
    private final RecipeChartScene scene;

    RecipeChartToolbar(RecipeChartView view, RecipeChartScene scene) {
        this.view = view;
        this.scene = scene;
    }

    /** Puts the buttons in a bar. */
    void addTo(JToolBar bar) {
        JLabel prompt = new JLabel("Pick a box or bar:");
        prompt.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 0));
        bar.add(prompt);
        bar.addSeparator();
        bar.add(button("Add step", "Add a step after the box that is picked",
                this::addStepAfterSelection));
        bar.add(button("Add transition", "Add a bar the flow waits at", this::addTransition));
        bar.add(button("Split", "Split what is picked: a box towards two bars, one of them taken;"
                + " a bar towards two steps, both of them run", this::split));
        bar.add(button("Join", "Bring two bars together onto a new one", this::join));
        bar.add(button("Condition", "Say what the bar that is picked waits on",
                this::editCondition));
        bar.add(button("Rename", "Give the picked box or bar another name", this::rename));
        bar.add(button("Delete", "Take the picked box or bar off the chart", this::delete));
        bar.addSeparator();
        bar.add(joinMode());
    }

    /**
     * The button that says whether dragging joins two things.
     * <p>
     * Joining is a gesture of its own rather than always on, because a drag cannot mean both
     * "put these two together" and "move this one here" at once, and guessing which one the operator
     * meant is how a chart ends up with a line nobody asked for.
     */
    private javax.swing.JToggleButton joinMode() {
        javax.swing.JToggleButton toggle = new javax.swing.JToggleButton("Link");
        toggle.setToolTipText("While this is on, drag from a box to a bar to join them");
        toggle.addActionListener(event -> scene.setJoining(toggle.isSelected()));
        return toggle;
    }

    /**
     * A button that says why it did nothing.
     * <p>
     * Every change can be refused, and the reason comes from the core. Letting that escape reaches
     * the platform's event thread and nobody, so the operator presses a button and nothing happens
     * with no explanation.
     */
    private JButton button(String name, String tip, Runnable what) {
        JButton button = new JButton(name);
        button.setToolTipText(tip);
        button.addActionListener(event -> {
            try {
                what.run();
            } catch (RecipeEditException failure) {
                showFailure(failure);
            } catch (RuntimeException failure) {
                showFailure(new RecipeEditException(name, failure));
            }
        });
        return button;
    }

    private void showFailure(RecipeEditException failure) {
        JOptionPane.showMessageDialog(bar(), failure.getMessage(),
                failure.getEditName() + " failed", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * The name the step will be given, which cannot be one the recipe already uses.
     *
     * @param name what the operator typed
     * @return a name nothing is using
     */
    private String stepName(String name) {
        return unusedName("STEP_", name);
    }

    /**
     * The equipment class the step will name.
     *
     * @param asked what the operator typed, empty for a step that names none
     * @return the class id, or {@code null} for a step that names none
     */
    private String equipmentClass(String asked) {
        return asked == null || asked.trim().isEmpty() ? null : asked.trim();
    }

    /**
     * A bar on its own. A bar with nothing waiting at it is a chart waiting to be joined up.
     */
    private void addStepAfterSelection() {
        String after = pickedBox();
        if (after == null) {
            return;
        }
        String typed = ask("Name for the step", "Step name");
        if (typed == null) {
            return;
        }
        String asked = askOptional("Equipment class, or leave empty for none",
                "Equipment class");
        if (asked == null) {
            return;
        }
        String name = stepName(typed);
        String equipmentId = equipmentClass(asked);
        String bar = unusedName("T", name + "_BAR");
        String box = unusedName("BOX_", name);
        view.model().edit("Add step " + name,
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        view.model().getRecipe(), null, after, name, box, bar, equipmentId));
        scene.select(box);
    }

    /** Adds a bar on its own. A bar with nothing waiting at it is a chart waiting to be joined up. */
    private void addTransition() {
        String name = ask("Name for the transition", "Transition name");
        if (name == null) {
            return;
        }
        view.model().edit("Add transition " + name,
                () -> EditProcedureLogicUseCase.addTransition(
                        view.model().getRecipe(), null, name));
        scene.select(name);
    }

    /**
     * Splits the flow leaving what is picked.
     * <p>
     * <b>Which of the two shapes this is depends on what is picked</b>, and that is the whole point
     * of asking. A box splitting is a selective split: two bars, one of which the flow takes. A bar
     * splitting is a parallel split: two steps, both of which run at once. Offering the same button
     * for both is what a chart drawn the way a person would draw it needs, because whether both
     * branches run is decided by the symbol the split leaves, not by the button the operator
     * pressed.
     */
    private void split() {
        String node = picked();
        if (node == null) {
            return;
        }
        if (scene.isBox(node)) {
            String one = unusedName("T_", node + "_OR");
            String two = unusedName("T_", node + "_OTHERWISE");
            view.model().edit("Split " + node,
                    () -> EditProcedureLogicUseCase.selectiveFork(
                            view.model().getRecipe(), null, node, one, two));
            return;
        }
        String first = ask("First step on the branch, which runs as well", "Step name");
        if (first == null) {
            return;
        }
        String second = ask("Second step on the branch, which runs as well", "Step name");
        if (second == null) {
            return;
        }
        String one = unusedName("STEP_", first.trim());
        String two = unusedName("STEP_", second.trim());
        view.model().edit("Split " + node + " into " + one + " and " + two,
                () -> EditProcedureLogicUseCase.parallelFork(
                        view.model().getRecipe(), null, node, one, two));
        scene.select("BOX_" + one);
    }

    /**
     * Brings two bars together onto a new one.
     * <p>
     * Both bars are waited for, because a chart whose two branches come together into one bar is
     * saying the flow cannot go on until both are through.
     */
    private void join() {
        String one = ask("First bar to bring together", "Bar name");
        if (one == null) {
            return;
        }
        String two = ask("Second bar to bring together", "Bar name");
        if (two == null) {
            return;
        }
        String first = one.trim();
        String second = two.trim();
        String joinedOn = unusedName("T_", "BOTH_" + first + "_" + second);
        view.model().edit("Join " + first + " and " + second,
                () -> EditProcedureLogicUseCase.joinOnto(view.model().getRecipe(), null,
                        first, second, joinedOn, S88LinkType.PARALLEL_CONVERGENT));
        scene.select(joinedOn);
    }

    /**
     * Says what a bar waits on.
     * <p>
     * Empty leaves the bar with nothing to wait on, which is a chart saying the flow goes straight
     * on rather than a bar that never opens.
     */
    private void editCondition() {
        String bar = pickedBar();
        if (bar == null) {
            return;
        }
        String typed = askOptional("What the bar waits on, written as ADDRESS#COMPARISON#LITERAL,"
                + " or leave empty for nothing", "Condition");
        if (typed == null) {
            return;
        }
        view.model().edit("Condition of " + bar,
                () -> EditProcedureLogicUseCase.setCondition(
                        view.model().getRecipe(), null, bar, typed));
    }

    /** Gives the picked box or bar another name, moving the lines running into it along. */
    private void rename() {
        String node = picked();
        if (node == null) {
            return;
        }
        String typed = ask("New name for " + node, "Name");
        if (typed == null) {
            return;
        }
        String wanted = typed.trim();
        view.model().edit("Rename " + node + " to " + wanted,
                () -> {
                    if (scene.isBox(node)) {
                        EditProcedureLogicUseCase.renameStep(
                                view.model().getRecipe(), null, node, wanted);
                    } else {
                        EditProcedureLogicUseCase.renameTransition(
                                view.model().getRecipe(), null, node, wanted);
                    }
                });
        scene.select(wanted);
    }

    /**
     * Takes the picked box or bar off the chart.
     * <p>
     * Refused while lines run into it, because leaving those lines behind gives a chart with lines
     * that have nothing at one end, which is not a chart anybody can run.
     */
    private void delete() {
        String node = picked();
        if (node == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(bar(),
                "Take " + node + " off the chart?", "Delete",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        view.model().edit("Delete " + node,
                () -> {
                    if (scene.isBox(node)) {
                        EditProcedureLogicUseCase.removeStep(view.model().getRecipe(), null, node);
                    } else {
                        EditProcedureLogicUseCase.removeTransition(view.model().getRecipe(), null, node);
                    }
                });
        scene.select(null);
    }

    /**
     * The node the buttons will act on.
     *
     * @return the name of the picked node, or {@code null} when nothing was picked
     */
    private String picked() {
        String node = scene.selected();
        if (node == null) {
            JOptionPane.showMessageDialog(bar(),
                    "Click a box or a cross on the chart first.",
                    "Nothing picked", JOptionPane.INFORMATION_MESSAGE);
        }
        return node;
    }

    /**
     * The box the buttons will act on.
     * <p>
     * Adding a step, a branch or a parallel puts a step after what is picked, and a cross has no
     * step after it. Picking a cross says so here rather than letting the core refuse it later.
     *
     * @return the name of the picked box, or {@code null} when the pick cannot take a step
     */
    private String pickedBox() {
        String node = picked();
        if (node != null && !scene.isBox(node)) {
            JOptionPane.showMessageDialog(bar(),
                    "A cross holds the flow while it waits. Pick the step before it.",
                    "That is a cross", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return node;
    }

    /**
     * The bar the buttons will act on.
     * <p>
     * Only a bar waits on something, so picking a box is refused here rather than letting the core
     * look for a bar and find none.
     *
     * @return the name of the picked bar, or {@code null} when the pick cannot take a condition
     */
    private String pickedBar() {
        String node = picked();
        if (node != null && scene.isBox(node)) {
            JOptionPane.showMessageDialog(bar(),
                    "A step does the work. Pick the bar the flow waits at.",
                    "That is a step", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return node;
    }

    /** Asks for something that has to be given. */
    private String ask(String message, String title) {
        String answer = JOptionPane.showInputDialog(bar(), message, title,
                JOptionPane.QUESTION_MESSAGE);
        return answer == null || answer.trim().isEmpty() ? null : answer.trim();
    }

    /**
     * Asks for something that may be left out.
     *
     * @return what was typed, empty for nothing, {@code null} when the box was cancelled
     */
    private String askOptional(String message, String title) {
        JTextField field = new JTextField(20);
        JPanel panel = new JPanel(new java.awt.BorderLayout(6, 0));
        panel.add(new JLabel(message), java.awt.BorderLayout.WEST);
        panel.add(field, java.awt.BorderLayout.CENTER);
        int answer = JOptionPane.showConfirmDialog(bar(), panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? field.getText() : null;
    }

    /**
     * A name nothing in the recipe is using.
     * <p>
     * Checked against the elements of the recipe as well as against the chart, because a box is named
     * after the step it works on and the two would otherwise be left sharing a name.
     */
    private String unusedName(String prefix, String wanted) {
        S88ProcedureLogic chart = view.chart();
        S88MasterRecipe recipe = view.model().getRecipe();
        String candidate = wanted;
        int n = 1;
        while (isTaken(chart, recipe, candidate)) {
            candidate = prefix + n++;
        }
        return candidate;
    }

    private static boolean isTaken(S88ProcedureLogic chart, S88MasterRecipe recipe, String candidate) {
        return chart.findStep(candidate).isPresent()
                || chart.findTransition(candidate).isPresent()
                || recipe.findElement(candidate).isPresent();
    }

    /** The window the dialogs belong to. */
    private JComponent bar() {
        return view;
    }
}
