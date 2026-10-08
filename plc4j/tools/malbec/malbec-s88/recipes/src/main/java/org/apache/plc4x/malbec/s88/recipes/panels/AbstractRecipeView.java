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
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
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

    protected final RecipeEditorModel model;
    private final JToolBar toolBar = new JToolBar();
    private final Consumer<RecipeEditorModel> onRecipeChanged;
    private transient MultiViewElementCallback callback;

    protected AbstractRecipeView(RecipeEditorModel model) {
        this.model = model;
        this.onRecipeChanged = changed -> redrawSafely();
        setLayout(new BorderLayout());
        toolBar.setFloatable(false);
        // Save is on every view, because which tab is showing is not the operator's problem when the
        // question is whether what they did has reached the disk.
        toolBar.add(new org.apache.plc4x.malbec.s88.recipes.actions.RecipeSaveAction()
                .createContextAwareInstance(getLookup()));
        add(toolBar, BorderLayout.NORTH);
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
     * Always yes, and it has to be: the platform only asks about closing when a view refuses, and
     * the type it would have to be built with cannot be made from outside its own package. So there
     * is nothing this can say, and a recipe that was not saved is a recipe the operator can lose by
     * closing the window. That is a hole to be closed by owning the window, not by pretending.
     */
    @Override
    public CloseOperationState canCloseElement() {
        return CloseOperationState.STATE_OK;
    }
}