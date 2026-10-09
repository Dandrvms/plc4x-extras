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

import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.ChartDeletionUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;

/**
 * * * The buttons that change the chart of a recipe.
 * * <p>
 * They go in the bar that every view already has, so a window has one bar and *
 * not one per view.
 * * <p>
 * Each button acts on the box or the bar that is picked. A recipe that will not
 * * take the change is * said so in a dialog, because an exception from a
 * button * press reaches nobody.
 * * <p>
 * <b>How a chart is built, in the order the buttons do it.</b> Pick the start *
 * and press * {@code Add step} as many times as there are steps: each press *
 * puts one step and its bar in, one * under the other. Pick a step and press *
 * {@code Add parallel branch} to run two steps at once, or * {@code Add branch}
 * * to run one of several; each press adds one more branch to the same fork, so
 * * two * presses make three steps side by side. Pick a step inside a branch
 * and * press {@code Add step} again * and the new step grows that branch, and
 * still * comes back to the same bar as the others.
 * * <p>
 * Bars are named by what they wait on and steps by the equipment behind them, *
 * so neither is typed * in. Every bar is born waiting on nothing, and the *
 * {@code Condition} button says what it waits on.
 */
final class RecipeChartToolbar {

    private final RecipeChartView view;
    private final RecipeChartScene scene;

    RecipeChartToolbar(RecipeChartView view, RecipeChartScene scene) {
        this.view = view;
        this.scene = scene;
    }

    /**
     * * Puts the buttons in a bar.
     */
    void addTo(JToolBar bar) {
        JLabel prompt = new JLabel("Pick a box or a bar:");
        prompt.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 0));
        bar.add(prompt);
        bar.addSeparator();
        bar.add(button("Add step", "Put an empty step and its bar in after the box that is picked." + " Press it again to put another one under that.", this::addStepAfterSelection));
        bar.add(button("Add parallel branch", "Put a step beside the one that is picked, so the two" + " run at once. Press it again for one more beside those.", () -> branch(this::addParallelBranch, "parallel branch")));
        bar.add(button("Add branch", "Put a step beside the one that is picked, with a bar of its own" + " so it can wait on something of its own. Press it again for one more.", () -> branch(this::addAlternativeBranch, "branch")));
        bar.add(button("Equipment", "Give the picked step the equipment it works on, and take its" + " name from it", this::bindEquipment));
        bar.add(button("Values", "Show what the picked step sets and what it reads back", this::showValues));
        bar.add(button("Condition", "Say what the bar that is picked waits on", this::editCondition));
        bar.add(button("Rename", "Give the picked box another name", this::rename));
        bar.add(button("Delete", "Take the picked box or bar off the chart, and join what it joined", this::delete));
        bar.addSeparator();
        bar.add(button("Undo", "Take back the last change", () -> view.model().undo()));
        bar.add(button("Redo", "Put back the change that was taken back", () -> view.model().redo()));
    }

    /**
     * * Runs a branch action on the box that is picked, and picks the step it *
     * made.
     */
    private void branch(java.util.function.Function<String, S88RecipeElement> what, String label) {
        String box = pickedBox();
        if (box == null) {
            return;
        }
        S88RecipeElement[] made = new S88RecipeElement[1];
        view.model().edit("Add " + label, () -> made[0] = what.apply(box));
        if (made[0] != null) {
            scene.selectWhenDrawn("BOX_" + made[0].getId());
        }
    }

    private S88RecipeElement addParallelBranch(String box) {
        return ChartBranchingUseCase.addParallelBranch(view.model().getRecipe(), null, box);
    }

    private S88RecipeElement addAlternativeBranch(String box) {
        return ChartBranchingUseCase.addAlternativeBranch(view.model().getRecipe(), null, box);
    }

    /**
     * * * A button that says why it did nothing.
     * <p>
     * Every change can be refused, and the reason comes from the core. Letting
     * * that escape reaches * the platform's event thread and nobody, so the *
     * operator presses a button and nothing happens * with no explanation.
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
        JOptionPane.showMessageDialog(bar(), failure.getMessage(), failure.getEditName() + " failed", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * * * Puts an empty step after the box that is picked, and asks for
     * nothing.
     * * <p>
     * A step is a piece of equipment, and the equipment is chosen on the box *
     * itself. Asking for a name * here would be asking for the one thing the *
     * operator does not get to choose, because a name typed * in is a name that
     * * can be typed wrong.
     */
    private void addStepAfterSelection() {
        String after = pickedBox();
        if (after == null) {
            return;
        }
        view.model().edit("Add step", () -> {
            S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(view.model().getRecipe(), null, after);
            view.markEmptyStep(empty.getId());
        });
    }

    /**
     * * * Opens the picked step to show what it sets on its equipment and what
     * it * reads back.
     *     * <p>
     * Nothing can be changed for a step with no equipment behind it, because *
     * there is nothing to * give a value to, and a table of nothing would look
     * * like a step that has no parameters.
     */
    private void showValues() {
        String box = pickedBox();
        if (box == null || refusedLine(box)) {
            return;
        }
        S88RecipeElement element = view.model().getRecipe().findElement(view.chart().findStep(box).orElseThrow().getRecipeElementId()).orElse(null);
        if (element == null) {
            return;
        }
        boolean hasEquipment = view.model().getRecipe().addressesByClass() ? element.getEquipmentClassId() != null : !element.getActualEquipmentIds().isEmpty();
        if (!hasEquipment) {
            JOptionPane.showMessageDialog(bar(), "This step has no equipment yet, so there is nothing to give a value to." + " Give it one first.", "No equipment", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new StepEquipmentDialog(javax.swing.SwingUtilities.getWindowAncestor(bar()), view.model(), element).setVisible(true);
    }

    /**
     * * * Gives the picked box its equipment.
     *     * <p>
     * What the step is called comes from the equipment, and so do the *
     * parameters it is given, which * is why nothing about either is asked for
     * * here.
     */
    private void bindEquipment() {
        String box = pickedBox();
        if (box == null || refusedLine(box)) {
            return;
        }
        S88PlantSnapshot plant = view.model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(), "This recipe set has no plant frozen into it, so there is nothing to attach the" + " step to.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean byClass = view.model().getRecipe().addressesByClass();
        List<String> choices = BindRecipeStepUseCase.choices(plant, byClass);
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(bar(), "The plant has no " + (byClass ? "class of equipment" : "equipment") + " a step can be.", "Nothing to choose", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String chosen = (String) JOptionPane.showInputDialog(bar(), byClass ? "Which class of equipment does this step stand for?" : "Which equipment does this step work on?", "Equipment of " + box, JOptionPane.QUESTION_MESSAGE, null, choices.toArray(), choices.get(0));
        if (chosen == null) {
            return;
        }
        view.model().edit("Bind " + box + " to " + chosen, () -> {
            if (byClass) {
                BindRecipeStepUseCase.bindToClass(view.model().getRecipe(), null, box, chosen, plant);
            } else {
                BindRecipeStepUseCase.bindToEquipment(view.model().getRecipe(), null, box, chosen, plant);
            }
        });
        view.forgetEmptyStep();
    }

    /**
     * * * Says what a bar waits on.
     *     * <p>
     * Nothing here is typed as a piece of text. A bar waits on a value that a *
     * piece of equipment * publishes, so the operator picks that equipment, *
     * then the report, then how it compares, and * types only the value itself.
     * * What goes into the recipe is then made of answers that all exist * in
     * the * plant, which is what keeps a bar from being put on something that
     * is not * there.
     */
    private void editCondition() {
        String bar = pickedBar();
        if (bar == null) {
            return;
        }
        S88MasterRecipe recipe = view.model().getRecipe();
        S88PlantSnapshot plant = view.model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(), "This recipe set has no plant frozen into it, so there is nothing to read a" + " value from.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean byClass = recipe.addressesByClass();
        String equipment = null;
        if (!byClass) {
            List<String> withReports = SetBarConditionUseCase.equipmentWithReports(plant);
            if (withReports.isEmpty()) {
                JOptionPane.showMessageDialog(bar(), "No equipment in this plant publishes a report, so there is nothing a bar" + " can wait on.", "No reports", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            equipment = chooseOne(withReports, "Which equipment does " + bar + " read from?", "Equipment to read from");
            if (equipment == null) {
                return;
            }
        }
        List<String> reports = byClass ? SetBarConditionUseCase.reportsToWaitOn(recipe, plant) : SetBarConditionUseCase.reportsOf(plant, equipment);
        if (reports.isEmpty()) {
            JOptionPane.showMessageDialog(bar(), byClass ? "No class of equipment in this recipe publishes a report, so there is" + " nothing to wait on." : equipment + " publishes no report, so there is nothing to wait on.", "No reports", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String report = chooseOne(reports, "Which report does " + bar + " wait on?", "Report to wait on");
        if (report == null) {
            return;
        }
        String operator = chooseOne(comparisonSymbols(), "How is " + report + " compared?", "Comparison");
        if (operator == null) {
            return;
        }
        String value = askForValue(recipe, plant, equipment, report, operator);
        if (value == null) {
            return;
        }
        String reads = equipment;
        try {
            view.model().edit("Condition of " + bar, () -> SetBarConditionUseCase.waitUntil(recipe, null, bar, plant, reads, report, operator, value.trim()));
        } catch (IllegalArgumentException refused) {
            JOptionPane.showMessageDialog(bar(), refused.getMessage(), "Not something to wait on", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * What the bar is told to wait for.
     * <p>
     * A report that is an enumeration has a fixed set of values, and they are offered rather than
     * written, because a value the author has to remember is one they will get wrong and the plant
     * already says which are right. Anything else is written, because a number has no list to show.
     */
    private String askForValue(S88MasterRecipe recipe, S88PlantSnapshot plant, String equipment,
                               String report, String operator) {
        List<String> offered =
                SetBarConditionUseCase.allowedValues(recipe, plant, equipment, report);
        if (!offered.isEmpty()) {
            return chooseOne(offered,
                    "Value of " + report + " has to compare " + operator + " against",
                    "Value to wait for");
        }
        return ask("Value " + report + " has to compare " + operator + " against", "Value to wait for");
    }

    /**
     * * * The comparisons a bar can be given, as the operator reads them.
     * <p>
     * The catch-all is left out, because it is there for a condition written by
     * * hand and nothing this * dialog offers is one.
     */
    private List<String> comparisonSymbols() {
        List<String> symbols = new java.util.ArrayList<>();
        for (S88ConditionOperator one : S88ConditionOperator.values()) {
            if (one != S88ConditionOperator.OTHER) {
                symbols.add(one.getSymbol());
            }
        }
        return symbols;
    }

    /**
     * * * Asks which one of a list of names, offering nothing but the list. * *
     * * @return the name chosen, or {@code null} when the operator backed out
     */
    private String chooseOne(List<String> names, String message, String title) {
        return (String) JOptionPane.showInputDialog(bar(), message, title, JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.get(0));
    }

    /**
     * * * Gives the picked box another name, moving the lines running into it *
     * along.
     * <p>
     * A bar is refused, because a bar is not the operator's to name: what it *
     * waits on is what it is * called after, and a name typed in for it would *
     * be one more thing that can be typed wrong.
     */
    private void rename() {
        String node = picked();
        if (node == null || refusedLine(node)) {
            return;
        }
        if (!scene.isBox(node)) {
            JOptionPane.showMessageDialog(bar(), "A bar is not named here. What it waits on is what it is called after.", "Bars are not named", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String typed = ask("New name for " + node, "Name");
        if (typed == null) {
            return;
        }
        String wanted = typed.trim();
        view.model().edit("Rename " + node + " to " + wanted, () -> EditProcedureLogicUseCase.renameStep(view.model().getRecipe(), null, node, wanted));
        scene.selectWhenDrawn(wanted);
    }

    /**
     * * * Takes the picked thing off the chart.
     * <p>
     * A box or a bar is refused while lines run into it, because leaving those
     * * lines behind gives a * chart with lines that have nothing at one end. A
     * * line is taken off whatever it joined, which is * the only way a chart *
     * ever comes back from being unjoinable.
     */
    private void delete() {
        String node = picked();
        if (node == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(bar(), "Take " + node + " off the chart?", "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        view.model().edit("Delete " + node, () -> {
            if (scene.isLine(node)) {
                EditProcedureLogicUseCase.removeLink(view.model().getRecipe(), null, node);
            } else if (scene.isBox(node)) {
                ChartDeletionUseCase.deleteBox(view.model().getRecipe(), null, node);
            } else {
                EditProcedureLogicUseCase.removeTransition(view.model().getRecipe(), null, node);
            }
        });
        scene.select(null);
    }

    /**
     * * * Says why a line cannot be what the button does. * * @param node the *
     * picked thing * @return true when it is a line, which means the button has
     * * nothing to do
     */
    private boolean refusedLine(String node) {
        if (!scene.isLine(node)) {
            return false;
        }
        JOptionPane.showMessageDialog(bar(), "A line is only the way the flow goes. Take the line off first.", "That is a line", JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    /**
     * * * The node the buttons will act on. * * @return the name of the picked
     * * node, or {@code null} when nothing was picked
     */
    private String picked() {
        String node = scene.selected();
        if (node == null) {
            JOptionPane.showMessageDialog(bar(), "Click a box or a cross on the chart first.", "Nothing picked", JOptionPane.INFORMATION_MESSAGE);
        }
        return node;
    }

    /**
     * * * The box the buttons will act on.
     * <p>
     * Adding a step, a branch or a parallel puts a step after what is picked, *
     * and a cross has no * step after it. Picking a cross says so here rather *
     * than letting the core refuse it later. * * @return the name of the picked
     * * box, or {@code null} when the pick cannot take a step
     */
    private String pickedBox() {
        String node = picked();
        if (refusedLine(node)) {
            return null;
        }
        if (node != null && !scene.isBox(node)) {
            JOptionPane.showMessageDialog(bar(), "A cross holds the flow while it waits. Pick the step before it.", "That is a cross", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return node;
    }

    /**
     * * * The bar the buttons will act on.
     * <p>
     * Only a bar waits on something, so picking a box is refused here rather *
     * than letting the core * look for a bar and find none. * * @return the *
     * name of the picked bar, or {@code null} when the pick cannot take a *
     * condition
     */
    private String pickedBar() {
        String node = picked();
        if (refusedLine(node)) {
            return null;
        }
        if (node != null && scene.isBox(node)) {
            JOptionPane.showMessageDialog(bar(), "A step does the work. Pick the bar the flow waits at.", "That is a step", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return node;
    }

    /**
     * * Asks for something that has to be given.
     */
    private String ask(String message, String title) {
        String answer = JOptionPane.showInputDialog(bar(), message, title, JOptionPane.QUESTION_MESSAGE);
        return answer == null || answer.trim().isEmpty() ? null : answer.trim();
    }

    private static boolean isTaken(S88ProcedureLogic chart, S88MasterRecipe recipe, String candidate) {
        return chart.findStep(candidate).isPresent() || chart.findTransition(candidate).isPresent() || recipe.findElement(candidate).isPresent();
    }

    /**
     * * The window the dialogs belong to.
     */
    private JComponent bar() {
        return view;
    }
}
