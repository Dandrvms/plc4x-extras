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
import javax.swing.Action;
import org.netbeans.core.spi.multiview.CloseOperationHandler;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * Asks the operator what to do with the changes of a recipe when its window is closing.
 *
 * <p>
 * The platform has a dialog for this, and it offers two answers and no third. A recipe is the one
 * thing in this editor that has no other copy: there is no document behind it that the platform can
 * reopen, so the question that matters most here is the one that dialog does not ask, which is to
 * stop and go back to the work.
 *
 * <p>
 * <b>The question is asked once for a window.</b> Every view of one recipe reports the same thing
 * about the same recipe, and asking again for the other tab would be asking the same operator the
 * same question twice before they had answered it once.
 *
 * <p>
 * What each answer does is not decided here. It arrives with the state that reports the problem, so
 * this only asks and carries out.
 */
final class RecipeCloseHandler implements CloseOperationHandler {

    /** What the operator can choose instead of closing, which the platform's own dialog leaves out. */
    static final String GO_BACK = "Go back";

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
    private static boolean askAbout(CloseOperationState state) {
        Answer write = new Answer("Save", state.getProceedAction());
        Answer leave = new Answer("Discard", state.getDiscardAction());
        Answer goBack = new Answer(GO_BACK, null);
        Object answer = DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                "This recipe has changes that are not on disk.",
                "Close without saving?",
                NotifyDescriptor.QUESTION_MESSAGE,
                NotifyDescriptor.DEFAULT_OPTION,
                new Object[]{write, leave, goBack},
                write));
        if (answer == goBack || answer == null) {
            return false;
        }
        if (answer instanceof Answer chosen && chosen.does() != null) {
            chosen.does().actionPerformed(
                    new ActionEvent(chosen, ActionEvent.ACTION_PERFORMED, "close"));
        }
        return true;
    }

    /**
     * One of the things the operator can choose, with the label it is shown with.
     *
     * <p>
     * A wrapper rather than the action itself, because the dialog writes down whatever it is given
     * with {@code toString()}, and an action says nothing useful about itself that way. The operator
     * is offered "Save" and not the name of a class with a memory address on the end.
     *
     * @param label how the choice is written on the button
     * @param does  what choosing it carries out, {@code null} for a choice that only answers
     */
    private record Answer(String label, Action does) {

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