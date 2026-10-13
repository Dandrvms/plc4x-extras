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
 */
public final class RecipeNode extends AbstractNode {

    public static final String RECIPE_ICON =
            "org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png";

    private final FileObject recipeFile;

    RecipeNode(Project project, FileObject recipeFile) {
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
        return recipeFile.getName();
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


    @Override
    public Action getPreferredAction(){
        return new RecipeOpenAction().createContextAwareInstance(getLookup());
    }

    @Override
    public Action[] getActions(boolean context) {
        Lookup lookup = getLookup();
        return new Action[]{
                new RecipeOpenAction().createContextAwareInstance(lookup),
        };
    }

    @Override
    public String toString() {
        return "RecipeNode[" + recipeFile.getPath() + "]";
    }
}
