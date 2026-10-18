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

import java.awt.Window;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;

import org.apache.plc4x.malbec.s88.api.S88ConditionOperator;
import org.apache.plc4x.malbec.s88.api.S88LinkType;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.ChartDeletionUseCase;
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.SetBarConditionUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;
import org.apache.plc4x.malbec.s88.core.StructureQueries;

/**
 * The buttons that change the chart of a recipe.
 * <p>
 * They go in the bar that every view already has, so a window has one bar and
 * not one per view.
 * <p>
 * Each button acts on the box or the bar that is picked. A recipe that will not
 * take the change is said so in a dialog.
 * <p>
 * <b>How a chart is built, in the order the buttons do it.</b> Pick the start
 * and press {@code Add step} as many times as there are steps: each press
 * puts one step and its bar in, one under the other. Pick a step and press
 * {@code Add parallel branch} to run two steps at once, or {@code Add branch}
 * to run one of several; each press adds one more branch to the same fork, so
 * two presses make three steps side by side. Pick a step inside a branch
 * and press {@code Add step} again * and the new step grows that branch, and
 * still comes back to the same bar as the others.
 * <p>
 * Bars are named by what they wait on and steps by the equipment behind them.
 * Every bar is born waiting on nothing, and the {@code Condition} button says what
 * it waits on.
 */
final class RecipeChartToolbar {

    private final RecipeChartView view;
    private final RecipeChartScene scene;

    /** The recipe the actions change, which a menu passes in because it has no view. */
    private final RecipeEditorModel owned;

    RecipeChartToolbar(RecipeChartView view, RecipeChartScene scene) {
        this.view = view;
        this.scene = scene;
        this.owned = view.model();
    }

    /**
     * A toolbar with no view behind it, for the actions a menu offers.
     *
     * <p>Every action it runs reaches the model through the scene, which the view set up. A menu
     * has no view, and the actions do not need one.
     *
     * @param view  ignored, must be {@code null}
     * @param scene the drawing, which says what is picked
     * @param owned the recipe being edited
     */
    RecipeChartToolbar(RecipeChartView view, RecipeChartScene scene,
                       RecipeEditorModel owned) {
        this.view = view;
        this.scene = scene;
        this.owned = owned;
    }

    /** The recipe the actions change. */
    RecipeEditorModel model() {
        return view == null ? owned : view.model();
    }

    /**
     * Draws the chart that holds the one on show, which is one level up.
     *
     * <p>A step at the top of the recipe goes straight back to the chart of the recipe, because
     * that is the chart that holds it. A step inside another goes to the chart of the step that
     * holds it.
     */
    private void goUp() {
        S88RecipeElement step = model().currentStep();
        model().editChartOf(step == null ? null : step.getParent());
    }

    /** The step whose chart is drawn if the operator goes up, {@code null} for the recipe. */
    private S88RecipeElement oneLevelUp() {
        S88RecipeElement step = model().currentStep();
        return step == null ? null : step.getParent();
    }

    /**
     * Names what going up leads to, so the button says where the operator is as well as where it
     * goes.
     */
    private String upLabel() {
        S88RecipeElement up = oneLevelUp();
        return "Up to " + (up == null ? "Recipe" : up.getId());
    }

    /** The button that draws the chart one level up, whose label says where that is. */
    private JButton upButton;

    /**
     * Brings the up button into line with the chart on show.
     *
     * <p>The label names where the button leads, which is also where the operator is, so it has to
     * move every time the drawing does.
     */
    void refreshUpButton() {
        if (upButton == null) {
            return;
        }
        boolean inside = model().currentStep() != null;
        upButton.setText(upLabel());
        upButton.setEnabled(inside);
        upButton.setToolTipText(inside
                ? "Draw the chart of " + upLabel().substring("Up to ".length())
                : "The chart of the recipe is already on show");
    }

    /**
     * * Puts the buttons in a bar.
     */
    void addTo(JToolBar bar) {
        upButton = button(upLabel(), "Draw the chart that holds this one", this::goUp);
        bar.add(upButton);
        bar.add(button("Add step", "Put an empty step and its bar in after the box that is picked." + " Press it again to put another one under that.", this::addStepAfterSelection));
        bar.add(button("Add parallel branch", "Put a step beside the one that is picked, so the two" + " run at once. Press it again for one more beside those.", () -> branch(this::addParallelBranch, "parallel branch")));
        bar.add(button("Add branch", "Put a step beside the one that is picked, with a bar of its own" + " so it can wait on something of its own. Press it again for one more.", () -> branch(this::addAlternativeBranch, "branch")));
        bar.add(button("Equipment", "Give the picked step the equipment it works on, and take its" + " name from it", this::bindEquipment));
        bar.add(button("Values", "Show what the picked step sets and what it reads back", this::showValues));
        bar.add(button("Condition", "Say what the bar that is picked waits on", this::editCondition));
        bar.add(button("Delete", "Take the picked box or bar off the chart, and join what it joined", this::delete));
        bar.addSeparator();
        bar.add(button("Undo", "Take back the last change", () -> model().undo()));
        bar.add(button("Redo", "Put back the change that was taken back", () -> model().redo()));
    }

    /**
     * Runs a branch action on the box that is picked, and picks the step it made.
     */
    private void branch(Function<String, S88RecipeElement> what, String label) {
        String box = pickedBox();
        if (box == null) {
            return;
        }
        S88RecipeElement[] made = new S88RecipeElement[1];
        model().edit("Add " + label, () -> made[0] = what.apply(box));
        if (made[0] != null) {
            scene.selectWhenDrawn(made[0].getId());
        }
    }

    private S88RecipeElement addParallelBranch(String box) {
        return ChartBranchingUseCase.addParallelBranch(model().getRecipe(), actionStep(), box);
    }

    private S88RecipeElement addAlternativeBranch(String box) {
        return ChartBranchingUseCase.addAlternativeBranch(model().getRecipe(), actionStep(), box);
    }

    private S88RecipeElement nestParallelBranch(String box) {
        return ChartBranchingUseCase.nestParallelBranch(model().getRecipe(), actionStep(), box);
    }

    private S88RecipeElement nestAlternativeBranch(String box) {
        return ChartBranchingUseCase.nestAlternativeBranch(model().getRecipe(), actionStep(), box);
    }

    /**
     * A button that says why it did nothing.
     */
    private JButton button(String name, String tip, Runnable what) {
        JButton button = new JButton(name);
        button.setToolTipText(tip);
        button.addActionListener(event -> {
            try {
                what.run();
            } catch (RecipeEditException refused) {
                showFailureOf(name, refused);
            } catch (RuntimeException refused) {
                showFailureOf(name, new RecipeEditException(name, refused));
            }
        });
        return button;
    }

    /**
     * Says what an action could not do, and why.
     *
     * <p>An edit that could not be made carries what went wrong underneath, and that is what the
     * operator has to read. The name of the edit is only in the title of the box.
     *
     * @param action  name of the action that was refused
     * @param refused the refusal, wrapped or as it was thrown
     */
    private void showFailureOf(String action, RecipeEditException refused) {
        JOptionPane.showMessageDialog(bar(), reasonWithing(refused), action + " failed",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Puts an empty step after the box that is picked, and asks for
     * nothing.
     * <p>
     * A step is a piece of equipment, and the equipment is chosen on the box
     * itself.
     */
    private void addStepAfterSelection() {
        String after = pickedBox();
        if (after == null) {
            return;
        }
        model().edit("Add step", () -> {
            S88RecipeElement empty = BindRecipeStepUseCase.addEmptyStepAfter(model().getRecipe(), actionStep(), after);
            model().markEmptyStep(empty.getId());
        });
    }

    /**
     * Opens the picked step to show what it sets on its equipment and what
     * it reads back.
     */
    private void showValues() {
        String box = pickedBox();
        if (box == null || refusedLine(box)) {
            return;
        }
        S88RecipeElement element = model().getRecipe()
                .findElement(model().chartBeingEdited().findStep(box)
                        .orElseThrow().getRecipeElementId()).orElse(null);
        if (element == null) {
            return;
        }
        boolean hasEquipment = model().getRecipe().addressesByClass() ? element.getEquipmentClassId() != null : !element.getActualEquipmentIds().isEmpty();
        if (!hasEquipment) {
            JOptionPane.showMessageDialog(bar(), "This step has no equipment yet, so there is nothing to give a value to." + " Give it one first.", "No equipment", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = view == null ? null : SwingUtilities.getWindowAncestor(view);
        new StepEquipmentDialog(owner, model(), element).setVisible(true);
    }

    /**
     * Gives the picked box its equipment.
     * <p>
     * What the step is called comes from the equipment, and so do the
     * parameters it is given, which is why nothing about either is asked for
     * here.
     */
    /**
     * The step whose chart every button acts on.
     *
     * <p>The drawing shows the chart of one step, so that is the step the buttons edit. It is
     * {@code null} while the chart of the recipe is open.
     */
    private S88RecipeElement actionStep() {
        return model().currentStep();
    }

    /** The kind of step the buttons put in, which is what the chart being drawn holds. */
    private S88RecipeElementKind levelBeingEdited() {
        return model().levelBeingEdited();
    }

    /**
     * The step of the recipe the picked box works on.
     */
    private S88RecipeElement pickedStep() {
        String box = pickedBox();
        if (box == null) {
            return null;
        }
        return model().chartBeingEdited()
                .findStep(box)
                .flatMap(stepBox -> model().getRecipe()
                        .findElement(stepBox.getRecipeElementId()))
                .orElse(null);
    }

    private void bindEquipment() {
        String box = pickedBox();
        if (box == null || refusedLine(box)) {
            return;
        }
        S88PlantSnapshot plant = model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(), "This recipe set has no plant frozen into it, so there is nothing to attach the" + " step to.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean byClass = model().getRecipe().addressesByClass();
        S88RecipeElement step = pickedStep();
        List<String> choices =
                BindRecipeStepUseCase.choices(plant, byClass, step == null ? null : step.getKind());
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(bar(), "This step is an " + (step == null ? "" : step.getKind())
                    + " and groups others, so it works on no equipment of its own.", "Nothing to choose", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String chosen = (String) JOptionPane.showInputDialog(bar(), byClass ? "Which class of equipment does this step stand for?" : "Which equipment does this step work on?", "Equipment of " + box, JOptionPane.QUESTION_MESSAGE, null, choices.toArray(), choices.get(0));
        if (chosen == null) {
            return;
        }
        model().edit("Bind " + box + " to " + chosen, () -> {
            if (byClass) {
                BindRecipeStepUseCase.bindToClass(model().getRecipe(), actionStep(), box, chosen, plant);
            } else {
                BindRecipeStepUseCase.bindToEquipment(model().getRecipe(), actionStep(), box, chosen, plant);
            }
        });
        model().forgetEmptyStep();
    }

    /**
     * Says what a bar waits on.
     * <p>
     * Nothing here is typed as a piece of text. A bar waits on a value that a
     * piece of equipment publishes, so the operator picks that equipment,
     * then the report, then how it compares, and types only the value itself.
     */
    /**
     * What an edit refused says, unwrapped from the edit that carried it.
     *
     * <p>An edit that could not be made wraps what went wrong, and what the operator needs to read
     * is the reason underneath, not the name of the thing that carried it.
     *
     * @param refused the edit that could not be made
     * @return what the operator has to read
     */
    private static String reasonWithing(RecipeEditException refused) {
        return refused.getCause() == null ? refused.getMessage() : refused.getCause().getMessage();
    }

    private void editCondition() {
        String bar = pickedBar();
        if (bar == null) {
            return;
        }
        S88MasterRecipe recipe = model().getRecipe();
        S88RecipeElement owner = actionStep();
        S88PlantSnapshot plant = model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(), "This recipe set has no plant frozen into it, so there is nothing to read a" + " value from.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean byClass = recipe.addressesByClass();
        String equipment = null;
        if (!byClass) {
            List<String> withReports = SetBarConditionUseCase.equipmentWithReports(plant, owner);
            if (withReports.isEmpty()) {
                JOptionPane.showMessageDialog(bar(), "No equipment in this plant publishes a report, so there is nothing a bar" + " can wait on.", "No reports", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            equipment = chooseOne(withReports, "Which equipment does " + bar + " read from?", "Equipment to read from");
            if (equipment == null) {
                return;
            }
        }
        List<String> reports = byClass ? SetBarConditionUseCase.reportsToWaitOn(recipe, owner, bar, plant) : SetBarConditionUseCase.reportsOf(plant, equipment);
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
        String value = askForValue(recipe, owner, bar, plant, equipment, report, operator);
        if (value == null) {
            return;
        }
        String reads = equipment;
        try {
            model().edit("Condition of " + bar,
                    () -> SetBarConditionUseCase.waitUntil(model().getRecipe(), owner, bar,
                            plant, reads, report, operator, value.trim()));
        } catch (RecipeEditException refused) {
            JOptionPane.showMessageDialog(bar(), reasonWithing(refused),
                    "Not something to wait on", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * What the bar is told to wait for.
     * <p>
     * A report that is an enumeration has a fixed set of values, and they are offered rather than
     * written. Anything else is written.
     */
    private String askForValue(S88MasterRecipe recipe, S88RecipeElement owner, String barId,
                               S88PlantSnapshot plant, String equipment, String report,
                               String operator) {
        List<String> offered = SetBarConditionUseCase.allowedValues(recipe, owner, barId, plant,
                equipment, report);
        if (!offered.isEmpty()) {
            return chooseOne(offered,
                    "Value of " + report + " has to compare " + operator + " against",
                    "Value to wait for");
        }
        return ask("Value " + report + " has to compare " + operator + " against", "Value to wait for");
    }

    /**
     * The comparisons a bar can be given, as the operator reads them.
     * <p>
     * The catch-all is left out, because it is there for a condition written by
     * hand and nothing this dialog offers is one.
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
     * Asks which one of a list of names, offering nothing but the list.
     * @return the name chosen, or {@code null} when the operator backed out
     */
    private String chooseOne(List<String> names, String message, String title) {
        return (String) JOptionPane.showInputDialog(bar(), message, title, JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.getFirst());
    }

    /**
     * Gives the picked box another name, moving the lines running into it
     * along.
     * <p>
     * A bar is refused, because a bar is not the operator's to name: what it
     * waits on is what it is * called after, and a name typed in for it would
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
        model().edit("Rename " + node + " to " + wanted, () -> EditProcedureLogicUseCase.renameStep(model().getRecipe(), actionStep(), node, wanted));
        scene.selectWhenDrawn(wanted);
    }

    /**
     * Takes the picked thing off the chart.
     * <p>
     * A box or a bar is refused while lines run into it, because leaving those
     * lines behind gives a chart with lines that have nothing at one end. A
     * line is taken off whatever it joined, which is the only way a chart
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
        model().edit("Delete " + node, () -> {
            if (scene.isLine(node)) {
                EditProcedureLogicUseCase.removeLink(model().getRecipe(), actionStep(), node);
            } else if (scene.isBox(node)) {
                ChartDeletionUseCase.deleteBox(model().getRecipe(), actionStep(), node);
            } else {
                EditProcedureLogicUseCase.removeTransition(model().getRecipe(), actionStep(), node);
            }
        });
        scene.select(null);
    }

    /**
     * Says why a line cannot be what the button does.
     * @param node the picked thing
     * @return true when it is a line, which means the button has
     * nothing to do
     */
    private boolean refusedLine(String node) {
        if (!scene.isLine(node)) {
            return false;
        }
        JOptionPane.showMessageDialog(bar(), "A line is only the way the flow goes. Take the line off first.", "That is a line", JOptionPane.INFORMATION_MESSAGE);
        return true;
    }

    /**
     * The node the buttons will act on.
     * @return the name of the picked node, or {@code null} when nothing was picked
     */
    private String picked() {
        String node = scene.selected();
        if (node == null) {
            JOptionPane.showMessageDialog(bar(), "Click a box or a cross on the chart first.", "Nothing picked", JOptionPane.INFORMATION_MESSAGE);
        }
        return node;
    }

    /**
     * The box the buttons will act on.
     * <p>
     * Adding a step, a branch or a parallel puts a step after what is picked,
     * and a cross has no step after it.
     * @return the name of the picked box, or {@code null} when the pick cannot take a step
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
     * The bar the buttons will act on.
     * <p>
     * Only a bar waits on something, so picking a box is refused.
     * @return the name of the picked bar, or {@code null} when the pick cannot take a
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
     * Puts the actions of the toolbar in a menu, which is where a right click finds them.
     *
     * <p>A menu has no toolbar behind it, so the actions are built over the drawing and the model
     * alone, and the caller does the redrawing after each one.
     *
     * @param menu   menu to fill, must not be {@code null}
     * @param model  the recipe being edited
     * @param scene  the drawing, which says what is picked
     * @param redraw what to call after an action changed the chart
     */
    static void buildMenu(JPopupMenu menu, RecipeEditorModel model, RecipeChartScene scene,
                          Runnable redraw) {
        new RecipeChartToolbar(null, scene, model).addActionsTo(menu, redraw);
    }

    /**
     * Adds the actions of the toolbar to a menu, filtered by the level of the chart on show.
     *
     * @param menu   menu to fill, must not be {@code null}
     * @param redraw what to call after an action changed the chart
     */
    private void addActionsTo(JPopupMenu menu, Runnable redraw) {
        if (model().currentStep() != null) {
            menu.add(item(upLabel(), "Draw the chart that holds this one", this::goUp, redraw));
        }
        menu.add(item("Add step", "Put an empty step and its bar in after the box that is picked",
                this::addStepAfterSelection, redraw));
        menu.add(item("Add parallel branch", "Put a step beside the one that is picked",
                () -> branch(this::addParallelBranch, "parallel branch"), redraw));
        menu.add(item("Add branch", "Put a step beside the one that is picked, with a bar of its own",
                () -> branch(this::addAlternativeBranch, "branch"), redraw));
        if (canHaveABlockUnderIt(pickedBox())) {
            menu.add(item("Parallel branch here", "Put a parallel block under the step that is"
                            + " picked, inside the branch it is in",
                    () -> branch(this::nestParallelBranch, "parallel branch here"), redraw));
            menu.add(item("Selective branch here", "Put a block under the step that is picked where"
                            + " only one side is taken, inside the branch it is in",
                    () -> branch(this::nestAlternativeBranch, "selective branch here"), redraw));
        }
        S88RecipeElement picked = pickedStep();
        if (holdsAChart(picked)) {
            if (picked.getProcedureLogic() == null) {
                menu.add(item("Create chart", "Put the chart this step is meant to have under it",
                        () -> openOrCreateChartOf(picked), redraw));
            } else {
                menu.add(item("Chart", "Draw what the picked step does, step by step",
                        () -> openChartOfSelection(redraw), redraw));
            }
        }
        if (holdingAnUnbornChart()) {
            menu.add(item("Create chart", "Put the chart the step on show is meant to have",
                    this::createChartOfCurrentStep, redraw));
        }
        menu.add(item("Equipment", "Give the picked step the equipment it works on",
                this::bindEquipment, redraw));
        menu.add(item("Values", "Show what the picked step sets and what it reads back",
                this::showValues, redraw));
        menu.add(item("Condition", "Say what the bar that is picked waits on",
                this::editCondition, redraw));
        menu.add(item("Rename", "Give the picked box another name", this::rename, redraw));
        menu.addSeparator();
        menu.add(item("Delete", "Take the picked box or bar off the chart", this::delete, redraw));
        menu.addSeparator();
        menu.add(item("Undo", "Take back the last change", model()::undo, redraw));
        menu.add(item("Redo", "Put back the change that was taken back", model()::redo,
                redraw));
    }

    /**
     * Whether a block can be put under the box that is picked.
     *
     * <p>A box has to sit between two bars of its own for that. A box that is a whole branch of a
     * block has none: it is arrived at by the line the block splits on and leaves by the line the
     * block comes back together on, so there is no flow of its own to put a block under.
     *
     * <p>This is the same question {@code ChartBranchingUseCase} asks before it refuses, so the two
     * offered here are the two that would go through.
     *
     * @param boxId box of the picked step, may be {@code null}
     * @return whether a block would go under it
     */
    private boolean canHaveABlockUnderIt(String boxId) {
        if (boxId == null) {
            return false;
        }
        S88ProcedureLogic chart = model().chartBeingEdited();
        if (chart == null) {
            return false;
        }
        S88ProcedureLink arriving = StructureQueries.arrivingAt(chart, boxId);
        S88ProcedureLink leaving = StructureQueries.leavingFrom(chart, boxId);
        return arriving != null && leaving != null
                && !arriving.isDivergent() && !arriving.isConvergent()
                && !leaving.isDivergent() && !leaving.isConvergent();
    }

    /**
     * Whether there is anything to draw under the given step.
     *
     * <p>A step whose kind holds a chart does, whether it has one yet or not, so it is offered even
     * when nothing has been made for it. A last-level step has nothing under it at all.
     *
     * @param step the picked step, may be {@code null}
     * @return whether the chart action has anything to lead to
     */
    private static boolean holdsAChart(S88RecipeElement step) {
        return step != null && RecipeLevels.allowsSubChart(step.getKind());
    }

    /**
     * Whether the step on show is meant to hold a chart and has not been given one yet.
     *
     * <p>Going into such a step leaves an empty drawing with nothing on it, so the way out is to
     * make the chart rather than only to go back up.
     *
     * @return whether creating a chart is what the step on show is waiting for
     */
    private boolean holdingAnUnbornChart() {
        S88RecipeElement current = model().currentStep();
        return holdsAChart(current) && current.getProcedureLogic() == null;
    }

    /**
     * Makes the chart the step on show is meant to have, and draws it.
     *
     * <p>The chart goes through an edit, so taking it back is the same as taking any other change
     * back.
     */
    private void createChartOfCurrentStep() {
        try {
            model().createChartForCurrentStep();
        } catch (RecipeEditException refused) {
            JOptionPane.showMessageDialog(bar(), reasonWithing(refused),
                    "No chart to create", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Makes the chart of the given step if it has none, then draws that step's chart. */
    private void openOrCreateChartOf(S88RecipeElement step) {
        try {
            if (step.getProcedureLogic() == null) {
                model().edit("Create chart of " + step.getId(),
                        () -> CreateChartUseCase.forStep(model().getRecipe(), step));
            }
            model().editChartOf(step);
        } catch (RecipeEditException refused) {
            JOptionPane.showMessageDialog(bar(), reasonWithing(refused),
                    "No chart to create", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Draws the chart of the step the picked box works on, and brings the drawing up to date.
     *
     * @param after what to call once the chart on show is the new one
     */
    private void openChartOfSelection(Runnable after) {
        S88RecipeElement step = pickedStep();
        if (step == null) {
            return;
        }
        if (step.getProcedureLogic() == null
                && !RecipeLevels.allowsSubChart(step.getKind())) {
            JOptionPane.showMessageDialog(bar(), "Step '" + step.getId() + "' is a "
                    + step.getKind() + ", which is the last level and holds no chart of its own.",
                    "Nothing under it", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        model().editChartOf(step);
        after.run();
    }

    /**
     * Puts one action in a menu, with the drawing brought up to date after it runs.
     *
     * <p>A menu action is refused in the same way a button is, because it runs the same code: a step
     * inside a block of the other kind is refused, and a refused edit throws rather than returning.
     * Catching it here is what keeps it from reaching the thread that draws, where it would arrive
     * with nothing to read and nothing to show it in.
     *
     * <p>The drawing is brought up to date whether the action went through or not, so a refused one
     * leaves the chart as it was rather than as it was left.
     */
    private JMenuItem item(String name, String hint, Runnable what, Runnable after) {
        JMenuItem item = new JMenuItem(name);
        item.setToolTipText(hint);
        item.addActionListener(event -> {
            try {
                what.run();
            } catch (RecipeEditException refused) {
                showFailureOf(name, refused);
            } catch (RuntimeException refused) {
                showFailureOf(name, new RecipeEditException(name, refused));
            } finally {
                after.run();
            }
        });
        return item;
    }

    /**
     * * The window the dialogs belong to.
     */
    private JComponent bar() {
        return view;
    }
}
