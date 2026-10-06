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
package org.apache.plc4x.malbec.s88.recipes.nodes;

import java.awt.Image;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.recipes.actions.RecipeOpenAction;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
import org.openide.filesystems.FileObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * One recipe file in the tree of a recipe set.
 * <p>
 * A leaf, and deliberately so. A recipe is a single document, and what is inside it is the business
 * of the editor that opens it and not of the tree that lists the files. Showing the steps as children
 * would mean the tree and the editor disagreeing the moment one of them changed.
 * <p>
 * The name comes from the file and not from the recipe inside it. Reading the file to label the node
 * would make the tree depend on every recipe being well formed, and a recipe being opened in order to
 * be repaired is the ordinary case rather than the exceptional one.
 */
public final class RecipeNode extends AbstractNode {

    /**
     * Icon of a recipe in the tree.
     * <p>
     * The recipe set's own icon, because there is no separate one for a recipe yet. A placeholder on
     * purpose: a missing image would leave the node with no icon at all, and one that is the wrong
     * picture is a thing to notice and replace.
     */
    public static final String RECIPE_ICON =
            "org/apache/plc4x/malbec/s88/recipes/nodes/recipeset.png";

    private final FileObject recipeFile;

    RecipeNode(Project project, FileObject recipeFile) {
        // The lookup is built before this node exists, so it carries the file and the project and not
        // the node. An action on this node needs the file to open and the project for context, and
        // nothing needs the node itself.
        super(Children.LEAF, new ProxyLookup(
                Lookups.singleton(recipeFile), project.getLookup()));
        this.recipeFile = recipeFile;
    }

    /** The file this node stands for. */
    public FileObject getRecipeFile() {
        return recipeFile;
    }

    @Override
    public String getDisplayName() {
        return recipeFile.getNameExt();
    }

    @Override
    public Image getIcon(int type) {
        return ImageUtilities.loadImage(RECIPE_ICON);
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public String getShortDescription() {
        return recipeFile.getPath();
    }

    /**
     * Open, then the two things that make sense on a file.
     * <p>
     * There is no open action from the platform here, because a recipe file has no type registered
     * for it and so is not a data object. Opening one is this node's business and is an action of
     * ours.
     */
    @Override
    public Action[] getActions(boolean context) {
        Lookup lookup = getLookup();
        return new Action[]{
                new RecipeOpenAction().createContextAwareInstance(lookup),
                null,
                CommonProjectActions.copyProjectAction(),
                CommonProjectActions.deleteProjectAction()
        };
    }

    @Override
    public boolean canDestroy() {
        // Deleting a recipe is deleting a file, which the platform's own delete action on the file
        // object already does. Saying yes here would only let the node disappear without the file
        // going anywhere.
        return false;
    }

    @Override
    public boolean canRename() {
        // Renaming the file renames the recipe on disk, and an editor may be holding it open. It is
        // left to the platform's own rename action rather than done from here.
        return false;
    }

    @Override
    public String toString() {
        return "RecipeNode[" + recipeFile.getPath() + "]";
    }
}
