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

import java.awt.Image;
import java.util.function.Function;
import org.netbeans.core.spi.multiview.MultiViewDescription;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.openide.util.HelpCtx;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;

/**
 * One tab of a recipe window, and how to make the view that goes in it.
 * <p>
 * The platform builds a multiview window from an array of these, and this is the recipe editor's
 * version of that declaration. It is written in code rather than declared in a layer, because the
 * recipe files have no registered file type: there is nothing in a layer that could say "these
 * files open this way", so the window is built where it is opened instead.
 * <p>
 * Each tab gets a preferred id built from where it sits, so that two recipe windows open at the same
 * time do not claim the same place in the window's stored layout.
 */
final class RecipeMultiViewDescription implements MultiViewDescription {

    private final String name;
    private final String preferredId;
    private final Function<RecipeEditorModel, MultiViewElement> factory;
    private RecipeEditorModel model;

    private RecipeMultiViewDescription(String name, String preferredId,
                                      Function<RecipeEditorModel, MultiViewElement> factory) {
        this.name = name;
        this.preferredId = preferredId;
        this.factory = factory;
    }

    /**
     * Describes the table of steps of a recipe.
     *
     * @param key whatever tells this window apart from another, such as the recipe file path
     * @return the description
     */
    static RecipeMultiViewDescription steps(String key) {
        return new RecipeMultiViewDescription("Steps", "RecipeSteps/" + key, RecipeTableView::new);
    }

    /**
     * Hands this description the editor state its view is built on.
     * <p>
     * The platform creates views itself and passes nothing to them, so the state has to arrive after
     * the description is made but before the window is opened. Doing it in that order is what lets
     * every tab of a window be given the same recipe, which is what keeps them in step.
     *
     * @param editorModel the recipe this tab is looking at
     */
    void setModel(RecipeEditorModel editorModel) {
        this.model = editorModel;
    }

    @Override
    public int getPersistenceType() {
        // Never remembered across restarts. A recipe window that came back on its own would open onto
        // a file that may since have been renamed or deleted.
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public Image getIcon() {
        return ImageUtilities.loadImage("org/apache/plc4x/malbec/s88/recipes/nodes/recipeset.png");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return new HelpCtx("recipes." + name.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public String preferredID() {
        return preferredId;
    }

    @Override
    public MultiViewElement createElement() {
        return factory.apply(model);
    }
}
