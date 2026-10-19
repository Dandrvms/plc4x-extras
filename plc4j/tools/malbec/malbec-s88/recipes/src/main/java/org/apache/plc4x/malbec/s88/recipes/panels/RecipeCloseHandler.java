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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.plc4x.malbec.s88.recipes.actions.RecipeSaveAction;
import org.netbeans.core.spi.multiview.CloseOperationHandler;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * Asks the user what to do with the changes of a recipe when its window is closing.
 */
final class RecipeCloseHandler implements CloseOperationHandler {

    private final RecipeEditorModel model;

    RecipeCloseHandler(RecipeEditorModel model) {
        this.model = model;
    }

    @Override
    public boolean resolveCloseOperation(CloseOperationState[] elements) {
        for (CloseOperationState state : oneOfEachKind(elements)) {
            if (!askAbout(state)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The states to ask about, one for each different thing that is wrong.
     *
     * @param elements the states every view reported
     * @return the first state of each kind of problem, in the order they were reported
     */
    private static CloseOperationState[] oneOfEachKind(CloseOperationState[] elements) {
        Set<String> askedAbout = new LinkedHashSet<>();
        List<CloseOperationState> once = new ArrayList<>();
        for (CloseOperationState state : elements) {
            if (state != null && askedAbout.add(state.getCloseWarningID())) {
                once.add(state);
            }
        }
        return once.toArray(new CloseOperationState[0]);
    }

    /**
     * Asks about one problem and does what the operator chose.
     *
     * @param state what is wrong and what could be done about it
     * @return true when the window may close
     */
    private boolean askAbout(CloseOperationState state) {
        Object answer = DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                "This recipe has changes that are not on disk.",
                "Close without saving?",
                NotifyDescriptor.QUESTION_MESSAGE,
                NotifyDescriptor.DEFAULT_OPTION,
                Choice.values(),
                Choice.SAVE));
        Choice choice = answer instanceof Choice chosen ? chosen : Choice.GO_BACK;
        boolean mayClose = decide(choice);
        if (!mayClose && choice == Choice.SAVE) {
            RecipeSaveAction.reportCouldNotSave(model);
        } else if (mayClose && choice == Choice.DISCARD) {
            state.getDiscardAction().actionPerformed(
                    new ActionEvent(state, ActionEvent.ACTION_PERFORMED, "close"));
        }
        return mayClose;
    }

    /**
     * Whether the window closes, once the operator has said what to do.
     *
     * <p>Writing the recipe is part of this, because a save that did not work leaves the changes
     * only in the model, and closing would throw them away. Kept apart from the question itself so
     * that it can be checked without a window.
     *
     * @param choice what the operator chose
     * @return true when the window may close
     */
    boolean decide(Choice choice) {
        if (choice == Choice.SAVE) {
            return RecipeSaveAction.save(model);
        }
        return choice != Choice.GO_BACK;
    }

    /** What the operator can choose when a recipe has changes. */
    enum Choice {
        /** Write the changes out. */
        SAVE("Save"),
        /** Leave the changes behind. */
        DISCARD("Discard"),
        /** Keep the window open. */
        GO_BACK("Go back");

        private final String label;

        Choice(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * One of each kind of problem, which is what a window with two views of one recipe reports.
     *
     * @param elements the states every view reported
     * @return how many different things are wrong
     */
    static int howManyKinds(CloseOperationState[] elements) {
        return oneOfEachKind(elements).length;
    }
}
