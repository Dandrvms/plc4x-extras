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
import java.awt.Image;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.recipes.actions.RecipeSaveAction;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.openide.awt.UndoRedo;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * What the two views of a recipe have in common.
 * <p>
 * Both are a picture of the same recipe and both can change it, so both listen to the same editor
 * state and both redraw when it says something changed. Everything that is not about being a
 * particular view is here: the bar of buttons, and the way a view hands the window its undo history
 * so that the window's own buttons work.
 */
public abstract class AbstractRecipeView extends JPanel implements MultiViewElement {

    private static final Logger LOG = Logger.getLogger(AbstractRecipeView.class.getName());

    /**
     * How long several changes in a row are gathered before the view is drawn again.
     * <p>
     * A short wait, so that a burst of changes is one drawing and not one drawing each.
     */
    private static final int REDRAW_DELAY_MILLIS = 120;

    protected final RecipeEditorModel model;
    private final JToolBar toolBar = new JToolBar();
    private final Consumer<RecipeEditorModel> onRecipeChanged;
    private transient MultiViewElementCallback callback;

    /**
     * The drawing that is waiting to happen, restarted by every change that asks for one.
     * <p>
     * Serialized rather than dropped, because a change nobody draws is a change the operator cannot
     * see they made. The first change waits and the last one wins, so nothing is lost and nothing is
     * drawn more than once.
     */
    private final javax.swing.Timer pendingRedraw = new javax.swing.Timer(REDRAW_DELAY_MILLIS, event -> {
        javax.swing.SwingUtilities.invokeLater(this::redrawSafely);
    });

    protected AbstractRecipeView(RecipeEditorModel model) {
        this(model, model == null ? null : model.plant());
    }

    /**
     * Builds a view over a recipe that is not in a recipe set, which is what a test and a runtime
     * reading recipes off a piece of equipment have.
     *
     * @param model editor state being shown
     * @param plant  the plant it is written against, {@code null} when there is none
     */
    protected AbstractRecipeView(RecipeEditorModel model, S88PlantSnapshot plant) {
        this.model = model;
        this.onRecipeChanged = changed -> askForRedraw();
        pendingRedraw.setRepeats(false);
        setLayout(new BorderLayout());
        toolBar.setFloatable(false);
        // Save is on every view, because which tab is showing is not the operator's problem when the
        // question is whether what they did has reached the disk.
        toolBar.add(new org.apache.plc4x.malbec.s88.recipes.actions.RecipeSaveAction()
                .createContextAwareInstance(getLookup()));
        add(toolBar, BorderLayout.NORTH);
    }

    /**
     * Asks for this view to be drawn again, without drawing it yet.
     * <p>
     * A recipe arrives here one change at a time, and one change can touch the chart, the table and
     * the list of problems at once. Drawing on every one of them makes the operator watch the same
     * work happen several times, and on a big chart each drawing costs more than the change did.
     */
    private void askForRedraw() {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            pendingRedraw.restart();
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> pendingRedraw.restart());
        }
    }

    /**
     * Draws this view again.
     * <p>
     * Called when the view is opened and shown, and then whenever the recipe changed, whether it
     * was this view that changed it or the other one.
     */
    protected abstract void redraw();

    /** What this view is called in its tab. */
    public abstract String viewName();

    /** The icon of this view in its tab. */
    protected Image viewIcon() {
        return ImageUtilities.loadImage("org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png");
    }

    /** The bar of buttons along the top of this view, for a view that has more. */
    protected final JToolBar toolBar() {
        return toolBar;
    }

    @Override
    public final JComponent getVisualRepresentation() {
        return this;
    }

    @Override
    public final JComponent getToolbarRepresentation() {
        return toolBar;
    }

    @Override
    public Action[] getActions() {
        return new Action[0];
    }

    @Override
    public Lookup getLookup() {
        // The recipe and its file, so that an action on this view can reach both without having to
        // be told which recipe it belongs to.
        return Lookups.singleton(model);
    }

    @Override
    public void componentOpened() {
        // Registered here and not in the constructor, because the platform makes a view and may never
        // open it, and a listener that outlives its view keeps that view alive for as long as the
        // recipe is open.
        model.addChangeListener(onRecipeChanged);
        redrawSafely();
    }

    @Override
    public void componentClosed() {
        pendingRedraw.stop();
        model.removeChangeListener(onRecipeChanged);
    }

    /**
     * Draws this view again, and does not let a failure stop the window.
     * <p>
     * Every redraw goes through here. A redraw runs on the platform's own thread, and an exception
     * from it reaches the window instead of the view, so one bad frame takes the editor down and the
     * operator is left with a window that will not open. The recipe is still in the model either
     * way, so a frame that fails is logged and the next change draws again.
     */
    private void redrawSafely() {
        try {
            redraw();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "The " + viewName() + " view could not be drawn.", failure);
        }
    }

    @Override
    public void componentShowing() {
        redrawSafely();
    }

    /**
     * The window, so that a view can ask about closing when the platform asks.
     *
     * @return the window this view is in, {@code null} when the platform has not said yet
     */
    protected final org.openide.windows.TopComponent window() {
        return callback != null ? callback.getTopComponent() : null;
    }

    @Override
    public void componentHidden() {
    }

    @Override
    public void componentActivated() {
    }

    @Override
    public void componentDeactivated() {
    }

    /**
     * The history of this recipe, handed to the window.
     * <p>
     * The window draws its undo and redo buttons out of this, so returning the real thing here is
     * what puts working buttons on the window rather than buttons of our own.
     */
    @Override
    public UndoRedo getUndoRedo() {
        return model.getUndoRedo();
    }

    @Override
    public void setMultiViewCallback(MultiViewElementCallback callback) {
        this.callback = callback;
    }

    /** The platform's handle on this view, for a view that needs to ask about the window. */
    protected MultiViewElementCallback callback() {
        return callback;
    }

/**
 * Whether the window may close.
 * <p>
 * A recipe with changes still on the screen says so, and the two things the operator can choose come
 * with it: write them out, or leave them. The window asks because the platform only asks when a view
 * refuses, and the state that says what can be done about it is made by a factory of the platform
 * rather than by a constructor this class cannot reach.
 * <p>
 * Every view of one recipe says the same thing about the same recipe, so the window is asked once and
 * not once per tab.
 */
    @Override
    public CloseOperationState canCloseElement() {
        if (!model.isModified()) {
            return CloseOperationState.STATE_OK;
        }
        return MultiViewFactory.createUnsafeCloseState(UNSAVED_WARNING,
                new SaveChanges(model), new DiscardChanges());
    }

    /**
     * Names the one thing that can be wrong when the window closes, which is what lets the window ask
     * once for a recipe with two views rather than twice.
     */
    static final String UNSAVED_WARNING = "malbec.recipe.unsaved";

    /** Writes the recipe out and says so when it cannot be written. */
    private static final class SaveChanges extends javax.swing.AbstractAction {

        private static final long serialVersionUID = 1L;
        private final RecipeEditorModel model;

        SaveChanges(RecipeEditorModel model) {
            super("Save");
            this.model = model;
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent event) {
            org.apache.plc4x.malbec.s88.recipes.actions.RecipeSaveAction.save(model, null);
        }
    }

    /** Throws the changes away, which is what closing without saving means. */
    private static final class DiscardChanges extends javax.swing.AbstractAction {

        private static final long serialVersionUID = 1L;

        DiscardChanges() {
            super("Discard");
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent event) {
            // Nothing to do. The changes live in the model of a window that is closing, and the next
            // open reads the file, which is the point of choosing this.
        }
    }
}
