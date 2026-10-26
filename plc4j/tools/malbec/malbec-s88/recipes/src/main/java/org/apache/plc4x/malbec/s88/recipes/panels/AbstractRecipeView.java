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
import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

import org.apache.plc4x.malbec.s88.recipes.data.RecipeDataObject;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.UndoRedo;
import org.openide.cookies.SaveCookie;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

/**
 * What the views of a recipe have in common.
 * <p>
 * All are a picture of the same recipe and some can change it, so all listen to the same editor
 * state and all redraw when it says something changed. Everything that is not about being a
 * particular view is here: the bar of buttons, and the way a view hands the window its undo history
 * so that the window's own buttons work.
 */
public abstract class AbstractRecipeView extends JPanel implements MultiViewElement {

    private static final Logger LOG = Logger.getLogger(AbstractRecipeView.class.getName());

    protected final RecipeEditorModel model;
    private final Lookup context;
    private final JToolBar toolBar = new JToolBar();
    private final Consumer<RecipeEditorModel> onRecipeChanged;
    private transient MultiViewElementCallback callback;

    /**
     * The drawing that is waiting to happen, restarted by every change that asks for one.
     */
    private final Timer pendingRedraw;

    protected AbstractRecipeView(RecipeEditorModel model) {
        this(Lookups.singleton(model), model);
    }

    protected AbstractRecipeView(Lookup context) {
        this(context, modelFrom(context));
    }

    private AbstractRecipeView(Lookup context, RecipeEditorModel model) {
        this.context = context;
        this.model = model;
        this.onRecipeChanged = changed -> askForRedraw();
        pendingRedraw = new Timer(redrawDelayMillis(),
                event -> SwingUtilities.invokeLater(this::redrawSafely));
        pendingRedraw.setRepeats(false);
        setLayout(new BorderLayout());
        toolBar.setFloatable(false);
        add(toolBar, BorderLayout.NORTH);
    }

    private static RecipeEditorModel modelFrom(Lookup context) {
        RecipeDataObject dataObject = context.lookup(RecipeDataObject.class);
        if (dataObject != null) {
            return dataObject.model();
        }
        RecipeEditorModel model = context.lookup(RecipeEditorModel.class);
        if (model == null) {
            throw new IllegalStateException("The recipe view has no recipe model.");
        }
        return model;
    }

    /**
     * How long several changes in a row are gathered before the view is drawn again.
     *
     * @return the delay in milliseconds
     */
    protected int redrawDelayMillis() {
        return 120;
    }

    /**
     * Asks for this view to be drawn again, without drawing it yet.
     */
    private void askForRedraw() {
        if (SwingUtilities.isEventDispatchThread()) {
            pendingRedraw.restart();
        } else {
            SwingUtilities.invokeLater(pendingRedraw::restart);
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
        return callback != null ? callback.createDefaultActions() : new Action[0];
    }

    @Override
    public Lookup getLookup() {
        return context;
    }

    @Override
    public void componentOpened() {
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
    protected final TopComponent window() {
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
     */
    @Override
    public CloseOperationState canCloseElement() {
        if (!model.isModified()) {
            return CloseOperationState.STATE_OK;
        }
        return MultiViewFactory.createUnsafeCloseState(UNSAVED_WARNING,
                new SaveChanges(context, model), new DiscardChanges());
    }

    /**
     * Names the one thing that can be wrong when the window closes, which is what lets the window ask
     * once for a recipe with more than one view rather than once per view.
     */
    static final String UNSAVED_WARNING = "malbec.recipe.unsaved";

    /** Writes the recipe out and says so when it cannot be written. */
    private static final class SaveChanges extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final Lookup context;
        private final RecipeEditorModel model;

        SaveChanges(Lookup context, RecipeEditorModel model) {
            super("Save");
            this.context = context;
            this.model = model;
            putValue(Action.LONG_DESCRIPTION,
                    recipeName(model) + " has changes that are not saved.");
        }

        @Override
        public void actionPerformed(java.awt.event.ActionEvent event) {
            SaveCookie cookie = context.lookup(SaveCookie.class);
            try {
                if (cookie != null) {
                    cookie.save();
                } else {
                    model.save();
                }
            } catch (IOException | RuntimeException failure) {
                if (cookie == null) {
                    reportSaveFailure(model);
                }
            }
        }
    }

    private static void reportSaveFailure(RecipeEditorModel model) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                recipeName(model) + " could not be saved.",
                NotifyDescriptor.ERROR_MESSAGE));
    }

    private static String recipeName(RecipeEditorModel model) {
        return model.getRecipeFile() == null
                ? "The recipe"
                : "'" + model.getRecipeFile().getNameExt() + "'";
    }

    /** Throws the changes away, which is what closing without saving means. */
    private static final class DiscardChanges extends AbstractAction {

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
