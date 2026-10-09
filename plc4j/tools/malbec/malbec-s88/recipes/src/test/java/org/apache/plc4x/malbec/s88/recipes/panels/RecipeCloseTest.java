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
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.apache.plc4x.malbec.s88.core.EditProcedureLogicUseCase;
import org.junit.jupiter.api.Test;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe that has changes still on the screen has to say so when its window closes, because the
 * recipe is the one thing in this editor with no other copy.
 *
 * <p>
 * The dialog that asks is not run here. What is checked is the state the view reports, which is what
 * makes the platform ask at all and what carries the two answers.
 */
class RecipeCloseTest {

    /** A recipe opened from a file, which is what a window shows. */
    private static RecipeEditorModel open() {
        try {
            return RecipeBytes.openOn(RecipeBytes.holding(SfcFixtures.lineal()));
        } catch (IOException failure) {
            throw new AssertionError("a recipe that was just written could not be read", failure);
        }
    }

    /** A view with nothing of its own, so that what is checked is the state it reports. */
    private static RecipeEditorViewStub opened() {
        return new RecipeEditorViewStub(open());
    }

    @Test
    void aRecipeWithNothingNewSaysNothingWhenTheWindowCloses() {
        onSwingThread(() -> {
            RecipeEditorViewStub view = opened();

            assertTrue(view.canCloseElement().canClose(),
                    "a window closes without asking about anything that is not there");
        });
    }

    @Test
    void aRecipeWithChangesSaysSoAndBringsBothAnswersWithIt() {
        onSwingThread(() -> {
            RecipeEditorViewStub view = opened();
            addAChangeTo(view);

            CloseOperationState state = view.canCloseElement();

            assertFalse(state.canClose(),
                    "and the window does not close without asking, because the changes are only in"
                            + " the model and nowhere else");
            assertEquals("Save", state.getProceedAction().getValue(Action.NAME),
                    "with writing them out as one of the answers");
            assertEquals("Discard", state.getDiscardAction().getValue(Action.NAME),
                    "and leaving them behind as the other");
        });
    }

    @Test
    void writingTheRecipeOutLetsTheWindowCloseWithoutAskingAgain() {
        onSwingThread(() -> {
            RecipeEditorViewStub view = opened();
            addAChangeTo(view);

            view.canCloseElement().getProceedAction().actionPerformed(
                    new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "save"));

            assertTrue(view.canCloseElement().canClose(),
                    "and once they are on disk there is nothing left to ask about");
        });
    }

    /**
     * Both views of one recipe report the same problem, and the operator is asked about it once.
     */
    @Test
    void aWindowWithTwoViewsAsksAboutTheSameThingOnce() {
        CloseOperationState[] fromBothViews = {unsaved(), unsaved()};

        assertEquals(1, RecipeCloseHandler.howManyKinds(fromBothViews),
                "and asking the same question twice before it was answered once is how an operator"
                        + " answers about one tab and loses the other");
    }

    /** One change on the recipe of a view, which is what makes it worth asking about. */
    private static void addAChangeTo(RecipeEditorViewStub view) {
        view.model.edit("Add step QUENCH",
                () -> EditProcedureLogicUseCase.insertStepAfter(
                        view.model.getRecipe(), null, "BOX_BEGIN", "QUENCH", "BOX_QUENCH",
                        "T_QUENCH", "QUENCH_CLASS"));
    }

    private static CloseOperationState unsaved() {
        return MultiViewFactory.createUnsafeCloseState(AbstractRecipeView.UNSAVED_WARNING,
                MultiViewFactory.NOOP_CLOSE_ACTION, MultiViewFactory.NOOP_CLOSE_ACTION);
    }

    private static void onSwingThread(Runnable what) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    what.run();
                } catch (RuntimeException | AssertionError problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException | java.lang.reflect.InvocationTargetException problem) {
            throw new AssertionError("the check could not be run on the right thread", problem);
        }
        if (failure.get() != null) {
            AssertionError error = new AssertionError("the recipe view failed");
            error.initCause(failure.get());
            throw error;
        }
    }

    /** A view with nothing of its own, so that what is checked is the state it reports. */
    private static final class RecipeEditorViewStub extends AbstractRecipeView {

        private static final long serialVersionUID = 1L;

        RecipeEditorViewStub(RecipeEditorModel model) {
            super(model);
        }

        @Override
        protected void redraw() {
        }

        @Override
        public String viewName() {
            return "Stub";
        }
    }
}