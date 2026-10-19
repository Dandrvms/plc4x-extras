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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;

/**
 * The window a recipe is edited in, one per recipe file.
 */
public final class RecipeEditorTopComponent {

    private static final Map<String, TopComponent> OPEN = new HashMap<>();

    private RecipeEditorTopComponent() {
    }

    /**
     * Opens the editor for a recipe file, or brings forward the one that is already open for it.
     *
     * @param recipeFile the recipe to edit
     * @return the window, whether it was made now or was already there
     * @throws IOException when the file does not hold a recipe that can be read
     */
    public static TopComponent showFor(FileObject recipeFile) throws IOException {
        String key = recipeFile.getPath();
        synchronized (RecipeEditorTopComponent.class) {
            TopComponent existing = OPEN.get(key);
            if (existing != null && existing.isOpened()) {
                existing.toFront();
                return existing;
            }
        }


        RecipeEditorModel model = RecipeEditorModel.open(recipeFile);
        TopComponent window = build(key, model);
        window.open();
        synchronized (RecipeEditorTopComponent.class) {
            OPEN.put(key, window);
        }
        return window;
    }

    /**
     * Builds the window for a recipe.
     */
    private static TopComponent build(String key, RecipeEditorModel model) {
        RecipeMultiViewDescription chart = RecipeMultiViewDescription.chart(key);
        RecipeMultiViewDescription steps = RecipeMultiViewDescription.steps(key);

        chart.setModel(model);
        steps.setModel(model);
        RecipeMultiViewDescription[] tabs = {chart, steps};

        TopComponent window = MultiViewFactory.createMultiView(tabs, chart,
                new RecipeCloseHandler(model));
        String label = model.getRecipe().getId() != null
                ? model.getRecipe().getId()
                : model.getRecipeFile().getNameExt();
        window.setDisplayName(label);
        window.setName("Recipe");
        window.setToolTipText(model.getRecipeFile().getPath());
        window.setIcon(ImageUtilities.loadImage(
                "org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png"));
        return window;
    }

    /**
     * Whether a recipe is open right now.
     *
     * @param recipeFile the file
     * @return true when a window for it exists and is showing
     */
    public static synchronized boolean isOpen(FileObject recipeFile) {
        TopComponent window = OPEN.get(recipeFile.getPath());
        return window != null && window.isOpened();
    }


    public static synchronized void unregister(FileObject recipeFile) {
        if (recipeFile != null) {
            OPEN.remove(recipeFile.getPath());
        }
    }
}
