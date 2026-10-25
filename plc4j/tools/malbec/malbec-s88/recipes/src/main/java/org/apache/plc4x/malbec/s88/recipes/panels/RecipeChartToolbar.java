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
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.*;

import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLink;
import org.apache.plc4x.malbec.s88.api.S88ProcedureLogic;
import org.apache.plc4x.malbec.s88.api.S88ProcedureStep;
import org.apache.plc4x.malbec.s88.api.S88ProcedureTransition;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88RecipeElement;
import org.apache.plc4x.malbec.s88.core.BindRecipeStepUseCase;
import org.apache.plc4x.malbec.s88.core.ChartBranchingUseCase;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities;
import org.apache.plc4x.malbec.s88.core.ChartCapabilities.ChartOp;
import org.apache.plc4x.malbec.s88.core.ChartGraph;
import org.apache.plc4x.malbec.s88.core.CreateChartUseCase;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeLevels;
import org.apache.plc4x.malbec.s88.core.StructureQueries;

/**
 * The orders that change the chart of a recipe, as the same {@link Action} everywhere.
 * <p>
 * A bar of buttons, the menu a right click brings up and the keys of the chart all run these
 * instances, so an order is enabled, named and refused in one place rather than three. An order is
 * enabled or disabled by what is picked, so a button that cannot do anything is greyed out instead of
 * opening a dialog that says nothing is picked.
 * <p>
 * <b>How a chart is built, in the order the orders do it.</b> Pick the start and press
 * {@code Add step} as many times as there are steps: each press puts one step and its bar in, one
 * under the other. Pick a step and press {@code Add parallel branch} to run two steps at once, or
 * {@code Add branch} to run one of several; each press adds one more branch to the same fork, so two
 * presses make three steps side by side. Pick a step inside a branch and press {@code Add step} again
 * and the new step grows that branch, and still comes back to the same bar as the others.
 * <p>
 * Bars are named by what they wait on and steps by the equipment behind them. Every bar is born
 * waiting on nothing, and the {@code Condition} order says what it waits on.
 * <p>
 * {@code Link} turns dragging from one thing to another into a join, which is how a loop or a jump is
 * made. {@code Esc} leaves that mode.
 */
final class RecipeChartToolbar {

    private static final String UP = "up";
    private static final String ADD_STEP = "addStep";
    private static final String ADD_PARALLEL = "addParallel";
    private static final String ADD_BRANCH = "addBranch";
    private static final String ADD_OPTIONAL = "addOptional";
    private static final String PARALLEL_HERE = "parallelHere";
    private static final String SELECTIVE_HERE = "selectiveHere";
    private static final String CHART = "chart";
    private static final String CREATE_CHART = "createChart";
    private static final String CREATE_CHART_UNDER = "createChartUnder";
    private static final String EQUIPMENT = "equipment";
    private static final String VALUES = "values";
    private static final String CONDITION = "condition";
    private static final String RENAME = "rename";
    private static final String DELETE = "delete";
    private static final String INSERT_BEFORE = "insertBefore";
    private static final String INSERT_AFTER = "insertAfter";
    private static final String INSERT_IN_BRANCH = "insertInBranch";
    private static final String INSERT_BEFORE_FORK = "insertBeforeFork";
    private static final String INSERT_AFTER_JOIN = "insertAfterJoin";
    private static final String REMOVE_BRANCH = "removeBranch";
    private static final String REMOVE_SKIP = "removeSkip";
    private static final String MOVE_LEFT = "moveLeft";
    private static final String MOVE_RIGHT = "moveRight";
    private static final String LOOP_STEP = "loopStep";
    private static final String LINK = "link";

    private final RecipeChartView view;
    private final RecipeChartScene scene;

    /** The hidden view the actions work over, so a menu and a bar share one set. */
    private final RecipeEditorModel owned;

    /** Every order, by the name the chart knows it by. */
    private final Map<String, Action> actions = new LinkedHashMap<>();

    /** What says whether each order can run, asked again whenever the pick changes. */
    private final Map<Action, BooleanSupplier> whenItRuns = new LinkedHashMap<>();

    /** The orders that ask {@link ChartCapabilities} before they run, by the order they ask about. */
    private final Map<Action, ChartOp> capabilityOf = new LinkedHashMap<>();

    /** What each order does, so a refusal can put it back under the name. */
    private final Map<Action, String> hints = new LinkedHashMap<>();

    RecipeChartToolbar(RecipeChartView view, RecipeChartScene scene) {
        this.view = view;
        this.scene = scene;
        this.owned = view.model();
        buildActions();
    }

    /** The recipe the orders change. */
    private RecipeEditorModel model() {
        return owned;
    }

    /** The component the dialogs belong to. */
    private JComponent bar() {
        return view;
    }

    // ========== Building the actions ==========

    private void buildActions() {
        act(UP, upLabel(), "Draw the chart that holds this one", this::canGoUp, this::goUp);
        act(ADD_STEP, "Add step",
                "Put an empty step and its transition in after the step that is picked. Press it again to"
                        + " put another one under that.", this::canAddStep,
                this::addStepAfterSelection);
        act(ADD_PARALLEL, "Add parallel branch",
                "Put a step beside the one that is picked, so the two run at once. Press it again for"
                        + " one more beside those.", this::canAddStep,
                () -> branch(this::addParallelBranch, "parallel branch"));
        act(ADD_BRANCH, "Add branch",
                "Put a step beside the one that is picked, with a transition of its own so it can"
                        + " wait on something of its own. Press it again for one more.",
                this::canAddStep, () -> branch(this::addAlternativeBranch, "branch"));
        act(ADD_OPTIONAL, "Add optional branch",
                "Put a transition beside the one before the picked step, so the step can be skipped.",
                this::canAddOptionalBranch, this::addOptionalBranch);
        act(PARALLEL_HERE, "Parallel branch here",
                "Put a parallel block under the step that is picked, inside the branch it is in",
                () -> canHaveABlockUnderIt(pickedBox()),
                () -> branch(this::nestParallelBranch, "parallel branch here"));
        act(SELECTIVE_HERE, "Selective branch here",
                "Put a block under the step that is picked where only one side is taken, inside the"
                        + " branch it is in", () -> canHaveABlockUnderIt(pickedBox()),
                () -> branch(this::nestAlternativeBranch, "selective branch here"));
        act(CHART, "Chart", "Draw what the picked step does, step by step",
                () -> holdsAChart(pickedStep()), this::openChartOfSelection);
        act(CREATE_CHART, "Create chart", "Put the chart this step is meant to have under it",
                () -> holdsAChart(pickedStep()) && Objects.requireNonNull(pickedStep()).getProcedureLogic() == null,
                () -> openOrCreateChartOf(pickedStep()));
        act(CREATE_CHART_UNDER, "Create chart", "Put the chart the step on show is meant to have",
                this::holdingAnUnbornChart, this::createChartOfCurrentStep);
        act(EQUIPMENT, "Equipment",
                "Give the picked step the equipment it works on, and take its name from it",
                this::canAddStep, this::bindEquipment);
        act(VALUES, "Values", "Show what the picked step sets and what it reads back",
                this::canAddStep, this::showValues);
        act(CONDITION, "Condition", "Say what the transition that is picked waits on",
                this::canCondition, this::editCondition);
        act(RENAME, "Rename", "Give the picked step another name", this::canAddStep, this::rename);
        actCapability(DELETE, "Delete",
                "Take what is picked off the chart. Hold Ctrl to pick more than one; one undo puts"
                        + " every one of them back.",
                ChartOp.DELETE, this::delete);
        actCapability(INSERT_BEFORE, "Insert step before",
                "Put an empty step in front of the picked step.", ChartOp.INSERT_BEFORE,
                () -> change(ChartOp.INSERT_BEFORE, "Insert step before"));
        actCapability(INSERT_AFTER, "Insert step after",
                "Put an empty step after the picked step.", ChartOp.INSERT_AFTER,
                () -> change(ChartOp.INSERT_AFTER, "Insert step after"));
        actCapability(INSERT_IN_BRANCH, "Insert step in branch",
                "Put an empty step at the start of the picked branch.", ChartOp.INSERT_IN_BRANCH,
                () -> change(ChartOp.INSERT_IN_BRANCH, "Insert step in branch"));
        actCapability(INSERT_BEFORE_FORK, "Insert step before fork",
                "Put an empty step on the one flow that leads into the picked fork.",
                ChartOp.INSERT_BEFORE_FORK,
                () -> change(ChartOp.INSERT_BEFORE_FORK, "Insert step before fork"));
        actCapability(INSERT_AFTER_JOIN, "Insert step after join",
                "Put an empty step on the one flow that leaves the picked join.",
                ChartOp.INSERT_AFTER_JOIN,
                () -> change(ChartOp.INSERT_AFTER_JOIN, "Insert step after join"));
        actCapability(REMOVE_BRANCH, "Remove branch",
                "Take the chosen branch off, and put the block back together without it.",
                ChartOp.REMOVE_BRANCH, () -> change(ChartOp.REMOVE_BRANCH, "Remove branch"));
        actCapability(REMOVE_SKIP, "Remove skip",
                "Take the skip off, so the flow only goes through the step it skipped.",
                ChartOp.REMOVE_SKIP, () -> change(ChartOp.REMOVE_SKIP, "Remove skip"));
        actCapability(MOVE_LEFT, "Move branch left",
                "Put the picked branch one place to the left.", ChartOp.MOVE_BRANCH_LEFT,
                () -> change(ChartOp.MOVE_BRANCH_LEFT, "Move branch left"));
        actCapability(MOVE_RIGHT, "Move branch right",
                "Put the picked branch one place to the right.", ChartOp.MOVE_BRANCH_RIGHT,
                () -> change(ChartOp.MOVE_BRANCH_RIGHT, "Move branch right"));
        actCapability(LOOP_STEP, "Loop step",
                "Put a bar after the picked step that waits and goes back into the same step.",
                ChartOp.LOOP_STEP, () -> change(ChartOp.LOOP_STEP, "Loop step"));
        buildLinkAction();
    }

    /** Makes an order whose answer comes from {@link ChartCapabilities}. */
    private Action actCapability(String key, String name, String hint, ChartOp op, Runnable what) {
        Action action = act(key, name, hint, () -> ask(op).enabled(), what);
        capabilityOf.put(action, op);
        return action;
    }

    /**
     * Makes one order.
     *
     * <p>Every order runs through {@link #run}, which is the one place a refusal is shown, so no
     * order can reach the thread that draws with nothing to read.
     */
    private Action act(String key, String name, String hint, BooleanSupplier when, Runnable what) {
        AbstractAction action = new AbstractAction(name) {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                run(name, what);
            }
        };
        action.putValue(Action.SHORT_DESCRIPTION, hint);
        action.setEnabled(when.getAsBoolean());
        whenItRuns.put(action, when);
        hints.put(action, hint);
        actions.put(key, action);
        return action;
    }

    /** The order that joins two steps, with a transition of their own in between. */
    private void buildLinkAction() {
        AbstractAction link = new AbstractAction("Link") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                boolean on = !scene.isJoining();
                putValue(Action.SELECTED_KEY, on);
                view.showJoining(on);
            }
        };
        link.putValue(Action.SHORT_DESCRIPTION,
                "Drag from one step to another to join them, with a transition in between."
                        + " Esc leaves this mode.");
        link.setEnabled(true);
        whenItRuns.put(link, () -> true);
        actions.put(LINK, link);
    }

    /**
     * Brings every order into line with what is picked.
     *
     * <p>Called whenever the pick or the chart changed, so a button that cannot act on what is picked
     * is greyed out rather than opening a dialog that says nothing is picked.
     */
    void refresh() {
        for (Map.Entry<Action, BooleanSupplier> order : whenItRuns.entrySet()) {
            Action action = order.getKey();
            ChartOp op = capabilityOf.get(action);
            if (op == null) {
                action.setEnabled(order.getValue().getAsBoolean());
                continue;
            }
            ChartCapabilities.Capability answer = ask(op);
            action.setEnabled(answer.enabled());
            action.putValue(Action.SHORT_DESCRIPTION,
                    answer.enabled() ? hints.get(action) : answer.reason());
        }
        Action up = actions.get(UP);
        if (up != null) {
            String label = upLabel();
            up.putValue(Action.NAME, label);
            up.putValue(Action.SHORT_DESCRIPTION, canGoUp()
                    ? "Draw the chart of " + label.substring("Up to ".length())
                    : "The chart of the recipe is already on show");
        }
    }

    // ========== Putting them in a bar or a menu ==========

    /** Puts the orders in a bar of buttons. */
    void addTo(JToolBar bar) {
        bar.add(actions.get(UP));
        bar.add(actions.get(ADD_STEP));
        bar.add(actions.get(ADD_PARALLEL));
        bar.add(actions.get(ADD_BRANCH));
        bar.add(actions.get(ADD_OPTIONAL));
        bar.addSeparator();
        bar.add(actions.get(INSERT_BEFORE));
        bar.add(actions.get(INSERT_AFTER));
        bar.add(actions.get(INSERT_BEFORE_FORK));
        bar.add(actions.get(INSERT_AFTER_JOIN));
        bar.add(actions.get(LOOP_STEP));
        bar.add(actions.get(REMOVE_BRANCH));
        bar.add(actions.get(EQUIPMENT));
        bar.add(actions.get(VALUES));
        bar.add(actions.get(CONDITION));
        bar.add(actions.get(DELETE));
        bar.addSeparator();
        bar.add(new JToggleButton(actions.get(LINK)));
    }

    /**
     * Fills a menu with the orders that have something to lead to.
     *
     * <p>The menu is filled when it comes up, so an order that does not apply to what is picked is
     * not offered rather than offered and refused.
     */
    void fillMenu(JPopupMenu menu) {
        if (canGoUp()) {
            menu.add(actions.get(UP));
        }
        menu.add(actions.get(ADD_STEP));
        menu.add(actions.get(ADD_PARALLEL));
        menu.add(actions.get(ADD_BRANCH));
        menu.add(actions.get(ADD_OPTIONAL));
        menu.addSeparator();
        menu.add(actions.get(INSERT_BEFORE));
        menu.add(actions.get(INSERT_AFTER));
        menu.add(actions.get(INSERT_IN_BRANCH));
        menu.add(actions.get(INSERT_BEFORE_FORK));
        menu.add(actions.get(INSERT_AFTER_JOIN));
        menu.add(actions.get(LOOP_STEP));
        menu.add(actions.get(REMOVE_BRANCH));
        menu.add(actions.get(REMOVE_SKIP));
        menu.add(actions.get(MOVE_LEFT));
        menu.add(actions.get(MOVE_RIGHT));
        if (canHaveABlockUnderIt(pickedBox())) {
            menu.add(actions.get(PARALLEL_HERE));
            menu.add(actions.get(SELECTIVE_HERE));
        }
        S88RecipeElement picked = pickedStep();
        if (holdsAChart(picked)) {
            menu.add(picked.getProcedureLogic() == null
                    ? actions.get(CREATE_CHART) : actions.get(CHART));
        }
        if (holdingAnUnbornChart()) {
            menu.add(actions.get(CREATE_CHART_UNDER));
        }
        menu.add(actions.get(EQUIPMENT));
        menu.add(actions.get(VALUES));
        menu.add(actions.get(CONDITION));
        menu.add(actions.get(RENAME));
        menu.addSeparator();
        menu.add(actions.get(DELETE));
        menu.addSeparator();
        menu.add(actions.get(LINK));
    }

    /**
     * The order that takes the picked thing off the chart, for a key to run.
     */
    Action deleteAction() {
        return actions.get(DELETE);
    }

    /** The order that renames a step or writes a condition, for the F2 key. */
    Action f2Action() {
        return new AbstractAction("Rename or Condition") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (canCondition()) {
                    run("Condition", RecipeChartToolbar.this::editCondition);
                } else if (canAddStep()) {
                    run("Rename", RecipeChartToolbar.this::rename);
                }
            }
        };
    }

    /** The order that draws the chart of the picked step, for the Enter key. */
    Action openChartAction() {
        return new AbstractAction("Open chart") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                run("Chart", RecipeChartToolbar.this::openChartOfSelection);
            }
        };
    }

    /** The order that leaves the mode where a drag joins two things, for the Esc key. */
    Action leaveLinkAction() {
        return new AbstractAction("Leave link mode") {

            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                leaveLinkMode();
            }
        };
    }

    /** Leaves the mode where a drag joins two things. */
    void leaveLinkMode() {
        Action link = actions.get(LINK);
        link.putValue(Action.SELECTED_KEY, false);
        view.showJoining(false);
    }

    // ========== Running an order ==========

    /**
     * Runs an order and says what could not be done.
     *
     * <p>An edit that could not be made carries what went wrong underneath, and that is what the
     * operator has to read. The name of the order is only in the title of the box.
     */
    private void run(String action, Runnable what) {
        try {
            what.run();
        } catch (RecipeEditException refused) {
            showFailureOf(action, refused);
        } catch (RuntimeException refused) {
            showFailureOf(action, new RecipeEditException(action, refused));
        }
    }

    private void showFailureOf(String action, RecipeEditException refused) {
        JOptionPane.showMessageDialog(bar(), reasonWithing(refused), action + " failed",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * What an edit refused says, unwrapped from the edit that carried it.
     *
     * @param refused the edit that could not be made
     * @return what the operator has to read
     */
    private static String reasonWithing(RecipeEditException refused) {
        return refused.getCause() == null ? refused.getMessage() : refused.getCause().getMessage();
    }

    // ========== What is picked ==========

    /** The node the orders act on, {@code null} when nothing is picked. */
    private String picked() {
        return scene.selected();
    }

    /** The step the orders act on, {@code null} when it is not a step. */
    private String pickedBox() {
        String node = picked();
        return node != null && scene.isBox(node) ? node : null;
    }

    /** The transition the orders act on, {@code null} when it is not a transition. */
    private String pickedBar() {
        String node = picked();
        return node != null && !scene.isBox(node) && !scene.isLine(node) ? node : null;
    }

    private boolean canGoUp() {
        return model().currentStep() != null;
    }

    private boolean canAddStep() {
        return pickedBox() != null;
    }

    /** Whether the picked step can be made optional. */
    private boolean canAddOptionalBranch() {
        String box = pickedBox();
        return box != null && ChartBranchingUseCase.canAddOptionalBranch(
                model().getRecipe(), actionStep(), box);
    }

    private boolean canCondition() {
        return pickedBar() != null;
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

    /** The step whose chart every order acts on, {@code null} while the chart of the recipe is open. */
    private S88RecipeElement actionStep() {
        return model().currentStep();
    }

    // ========== The orders ==========

    /**
     * Draws the chart that holds the one on show, which is one level up.
     *
     * <p>A step at the top of the recipe goes straight back to the chart of the recipe, because that
     * is the chart that holds it. A step inside another goes to the chart of the step that holds it.
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
     * Names what going up leads to, so the order says where the operator is as well as where it goes.
     */
    private String upLabel() {
        S88RecipeElement up = oneLevelUp();
        return "Up to " + (up == null ? "Recipe" : up.getId());
    }

    /**
     * Puts an empty step after the box that is picked, and asks for nothing.
     * <p>
     * A step is a piece of equipment, and the equipment is chosen on the box itself.
     */
    private void addStepAfterSelection() {
        String after = pickedBox();
        if (after == null) {
            return;
        }
        model().edit("Add step",
                () -> BindRecipeStepUseCase.addEmptyStepAfter(model().getRecipe(), actionStep(), after));
    }

    /**
     * Runs a branch order on the box that is picked, and picks the step it made.
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

    /** Makes the picked step optional by adding a transition that skips it. */
    private void addOptionalBranch() {
        String box = pickedBox();
        if (box == null) {
            return;
        }
        S88ProcedureTransition[] made = new S88ProcedureTransition[1];
        model().edit("Add optional branch", () -> made[0] =
                ChartBranchingUseCase.addOptionalBranch(model().getRecipe(), actionStep(), box));
        if (made[0] != null) {
            scene.selectWhenDrawn(made[0].getId());
        }
    }

    /**
     * Opens the picked step to show what it sets on its equipment and what it reads back.
     */
    private void showValues() {
        String box = pickedBox();
        if (box == null) {
            return;
        }
        S88RecipeElement element = model().getRecipe()
                .findElement(model().chartBeingEdited().findStep(box)
                        .orElseThrow().getRecipeElementId()).orElse(null);
        if (element == null) {
            return;
        }
        boolean hasEquipment = model().getRecipe().addressesByClass()
                ? element.getEquipmentClassId() != null
                : !element.getActualEquipmentIds().isEmpty();
        if (!hasEquipment) {
            JOptionPane.showMessageDialog(bar(),
                    "This step has no equipment yet, so there is nothing to give a value to."
                            + " Give it one first.", "No equipment", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = view == null ? null : SwingUtilities.getWindowAncestor(view);
        new StepEquipmentDialog(owner, model(), element).setVisible(true);
    }

    /**
     * Gives the picked box its equipment.
     * <p>
     * What the step is called comes from the equipment, and so do the parameters it is given, which
     * is why nothing about either is asked for here.
     */
    private void bindEquipment() {
        String box = pickedBox();
        if (box == null) {
            return;
        }
        S88PlantSnapshot plant = model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(),
                    "This recipe set has no plant frozen into it, so there is nothing to attach the"
                            + " step to.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        boolean byClass = model().getRecipe().addressesByClass();
        S88RecipeElement step = pickedStep();
        List<String> choices =
                BindRecipeStepUseCase.choices(plant, byClass, step == null ? null : step.getKind());
        if (choices.isEmpty()) {
            JOptionPane.showMessageDialog(bar(), "This step is an "
                    + (step == null ? "" : step.getKind())
                    + " and groups others, so it works on no equipment of its own.",
                    "Nothing to choose", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String chosen = (String) JOptionPane.showInputDialog(bar(),
                byClass ? "Which class of equipment does this step stand for?"
                        : "Which equipment does this step work on?",
                "Equipment of " + box, JOptionPane.QUESTION_MESSAGE, null, choices.toArray(),
                choices.get(0));
        if (chosen == null) {
            return;
        }
        model().edit("Bind " + box + " to " + chosen, () -> {
            if (byClass) {
                BindRecipeStepUseCase.bindToClass(model().getRecipe(), actionStep(), box, chosen, plant);
            } else {
                BindRecipeStepUseCase.bindToEquipment(
                        model().getRecipe(), actionStep(), box, chosen, plant);
            }
        });
    }

    /**
     * Says what a transition waits on.
     * <p>
     * Nothing here is typed as a piece of text. A transition waits on a value that a piece of
     * equipment publishes, so the operator picks that equipment, then the report, then how it
     * compares, and types only the value itself.
     */
    private void editCondition() {
        String bar = pickedBar();
        if (bar == null) {
            return;
        }
        S88MasterRecipe recipe = model().getRecipe();
        S88RecipeElement owner = actionStep();
        S88PlantSnapshot plant = model().plant();
        if (plant == null) {
            JOptionPane.showMessageDialog(bar(),
                    "This recipe set has no plant frozen into it, so there is nothing to read a value"
                            + " from.", "No plant", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String current = model().chartBeingEdited().findTransition(bar)
                .map(S88ProcedureTransition::getCondition).orElse(null);
        Window window = view == null ? null : SwingUtilities.getWindowAncestor(view);
        TransitionConditionDialog dialog =
                new TransitionConditionDialog(window, recipe, owner, bar, plant, current);
        dialog.setVisible(true);
        if (!dialog.wasAccepted()) {
            return;
        }
        String text = dialog.conditionText();
        run("Condition of " + bar, () -> model().edit("Condition of " + bar,
                () -> EditProcedureLogicUseCase.setCondition(model().getRecipe(), owner, bar, text)));
    }

    /**
     * Gives the picked box another name, moving the lines running into it along.
     */
    private void rename() {
        String node = pickedBox();
        if (node == null) {
            return;
        }
        String typed = ask("New name for " + node, "Name");
        if (typed == null) {
            return;
        }
        String wanted = typed.trim();
        model().edit("Rename " + node + " to " + wanted,
                () -> EditProcedureLogicUseCase.renameStep(
                        model().getRecipe(), actionStep(), node, wanted));
        scene.selectWhenDrawn(wanted);
    }

    /**
     * Takes what is picked off the chart.
     * <p>
     * Steps, transitions, and lines can all go. Holding Ctrl picks more than one, and they all go
     * inside one edit, so one undo puts every one of them back. The pick moves to the closest thing
     * that stays, so the operator does not have to find the chart again.
     */
    private void delete() {
        Set<String> going = scene.selection();
        if (going.isEmpty()) {
            return;
        }
        String what = going.size() == 1 ? going.iterator().next() : going.size() + " things";
        int answer = JOptionPane.showConfirmDialog(bar(),
                "Take " + what + " off the chart? One undo puts everything back.", "Delete",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        String anchor = anchorFor(going);
        model().edit("Delete " + what,
                () -> ChartCapabilities.perform(
                        model().getRecipe(), actionStep(), ChartOp.DELETE, going));
        reselect(anchor);
    }

    /** Asks {@link ChartCapabilities} whether an order would go through. */
    private ChartCapabilities.Capability ask(ChartOp op) {
        return ChartCapabilities.can(model().getRecipe(), actionStep(), op, scene.selection());
    }

    /**
     * Runs an order through {@link ChartCapabilities} and picks what it made or the closest thing
     * that stayed.
     */
    private void change(ChartOp op, String label) {
        String anchor = anchorFor(scene.selection());
        String[] made = new String[1];
        model().edit(label, () -> made[0] = ChartCapabilities.perform(
                model().getRecipe(), actionStep(), op, scene.selection()));
        reselect(made[0] != null ? made[0] : anchor);
    }

    /** Picks what an edit made or the nearest thing that stayed, never a node that is now gone. */
    private void reselect(String wanted) {
        S88ProcedureLogic chart = model().chartBeingEdited();
        String pick = wanted;
        if (pick == null || chart == null || (chart.findStep(pick).isEmpty()
                && chart.findTransition(pick).isEmpty() && chart.findLink(pick).isEmpty())) {
            pick = chart == null ? null : chart.getSteps().stream()
                    .map(S88ProcedureStep::getId)
                    .findFirst()
                    .orElse(null);
        }
        scene.selectWhenDrawn(pick);
    }

    /**
     * Finds the closest thing that an edit is not taking away, so the pick can move there.
     *
     * @param going the nodes and lines about to be taken off
     * @return a node or line that stays, {@code null} when there is none
     */
    private String anchorFor(Set<String> going) {
        S88ProcedureLogic chart = model().chartBeingEdited();
        if (chart == null || going.isEmpty()) {
            return null;
        }
        ChartGraph graph = ChartGraph.of(chart);
        for (String node : going) {
            List<String> around = new ArrayList<>(graph.preds(node));
            around.addAll(graph.succs(node));
            for (String neighbour : around) {
                if (!going.contains(neighbour)
                        && (graph.isStep(neighbour) || graph.isTransition(neighbour))) {
                    return neighbour;
                }
            }
            S88ProcedureLink line = chart.findLink(node).orElse(null);
            if (line != null) {
                for (var from : line.getFrom()) {
                    if (!going.contains(from.getValue())) {
                        return from.getValue();
                    }
                }
                for (var to : line.getTo()) {
                    if (!going.contains(to.getValue())) {
                        return to.getValue();
                    }
                }
            }
        }
        return null;
    }

    /** Asks for something that has to be given. */
    private String ask(String message, String title) {
        String answer = JOptionPane.showInputDialog(bar(), message, title, JOptionPane.QUESTION_MESSAGE);
        return answer == null || answer.trim().isEmpty() ? null : answer.trim();
    }

    // ========== Joining two things ==========

    /**
     * Joins one step to another, with a transition of its own in between.
     *
     * <p>This is the same path a button takes, so a join that the recipe refuses is said so rather
     * than dying in the thread that draws. The transition is picked when it is drawn, which is
     * where the operator gives it its condition.
     *
     * @param from name of the box the line leaves
     * @param to   name of the box it arrives at
     */
    void join(String from, String to) {
        if (model().chartBeingEdited() == null) {
            return;
        }
        S88ProcedureTransition[] made = new S88ProcedureTransition[1];
        run("Join " + from + " to " + to,
                () -> model().edit("Join " + from + " to " + to,
                        () -> made[0] = EditProcedureLogicUseCase.joinStepsThroughATransition(
                                model().getRecipe(), model().currentStep(), from, to)));
        if (made[0] != null) {
            scene.selectWhenDrawn(made[0].getId());
        }
    }

    // ========== What the chart will take ==========

    /**
     * Whether a block can be put under the box that is picked.
     *
     * <p>A box has to sit between two transitions of its own for that. A box that is a whole branch
     * of a block has none: it is arrived at by the line the block splits on and leaves by the line the
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
     * @return whether the chart order has anything to lead to
     */
    private static boolean holdsAChart(S88RecipeElement step) {
        return step != null && RecipeLevels.allowsSubChart(step.getKind());
    }

    /**
     * Whether the step on show is meant to hold a chart and has not been given one yet.
     *
     * <p>Going into such a step leaves an empty drawing with nothing on it, so the way out is to make
     * the chart rather than only to go back up.
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
            JOptionPane.showMessageDialog(bar(), reasonWithing(refused), "No chart to create",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Makes the chart of the given step if it has none, then draws that step's chart. */
    private void openOrCreateChartOf(S88RecipeElement step) {
        if (step == null) {
            return;
        }
        try {
            if (step.getProcedureLogic() == null) {
                model().edit("Create chart of " + step.getId(),
                        () -> CreateChartUseCase.forStep(model().getRecipe(), step));
            }
            model().editChartOf(step);
        } catch (RecipeEditException refused) {
            JOptionPane.showMessageDialog(bar(), reasonWithing(refused), "No chart to create",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Draws the chart of the step the picked box works on, which is what a double click does.
     */
    void openChartOfPickedBox() {
        run("Chart", this::openChartOfSelection);
    }

    /** Draws the chart of the step the picked box works on. */
    private void openChartOfSelection() {
        S88RecipeElement step = pickedStep();
        if (step == null) {
            return;
        }
        if (step.getProcedureLogic() == null && !RecipeLevels.allowsSubChart(step.getKind())) {
            JOptionPane.showMessageDialog(bar(), "Step '" + step.getId() + "' is a " + step.getKind()
                    + ", which is the last level and holds no chart of its own.", "Nothing under it",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        model().editChartOf(step);
    }
}
