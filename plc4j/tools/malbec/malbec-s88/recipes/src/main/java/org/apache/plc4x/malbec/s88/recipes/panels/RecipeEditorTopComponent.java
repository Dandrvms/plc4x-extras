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

import java.util.HashMap;
import java.util.Map;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;

/**
 * The window a recipe is edited in, one per recipe file.
 * <p>
 * <b>It does not extend {@code TopComponent} and carries no window annotations.</b> A top component
 * registered with the platform is one window that the platform remembers, offers once in the Window
 * menu, and will happily open whether there is a recipe behind it or not. A recipe editor has to be
 * the opposite of that: one only for a file that exists, and a different one for every such file.
 * Registering it would also put an "Open" in the Window menu that opens an editor of nothing.
 * <p>
 * The window that is actually shown is the one the platform's multiview factory builds, because that
 * is where the tab bar, the tab lifecycle and the close prompt live. This builds the tabs and hands
 * them over.
 * <p>
 * <b>At most one editor per file.</b> Opening a recipe that is already open brings that one forward
 * instead of making a second window over the same file, because two windows over one file is two of
 * them writing to one file.
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
     * @throws java.io.IOException when the file does not hold a recipe that can be read
     */
    public static TopComponent showFor(FileObject recipeFile) throws java.io.IOException {
        String key = recipeFile.getPath();
        synchronized (RecipeEditorTopComponent.class) {
            TopComponent existing = OPEN.get(key);
            if (existing != null && existing.isOpened()) {
                existing.toFront();
                return existing;
            }
        }

        // Read before any window is made. A recipe that cannot be read has to fail here, because an
        // editor over one that was not read would show an empty recipe and the first save would write
        // that empty recipe over the one on disk.
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
     * <p>
     * The tab list is the place a second view of the same recipe goes. Every tab is handed the same
     * editor state, which is what makes the views of one recipe agree with each other.
     * <p>
     * <b>The chart is a tab, not a window of its own.</b> The steps of a recipe and the chart of
     * those steps are one thing seen twice, and a second window would be two windows over one file
     * writing to one file.
     */
    private static TopComponent build(String key, RecipeEditorModel model) {
        RecipeMultiViewDescription steps = RecipeMultiViewDescription.steps(key);
        RecipeMultiViewDescription chart = RecipeMultiViewDescription.chart(key);
        steps.setModel(model);
        chart.setModel(model);
        RecipeMultiViewDescription[] tabs = {steps, chart};

        TopComponent window = MultiViewFactory.createMultiView(tabs, steps);
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
}
