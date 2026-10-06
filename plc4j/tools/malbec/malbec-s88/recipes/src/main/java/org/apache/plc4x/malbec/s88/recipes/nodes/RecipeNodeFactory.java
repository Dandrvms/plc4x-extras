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

import java.util.List;
import java.util.Locale;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;

import org.openide.filesystems.FileObject;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;

/**
 * Builds the recipe nodes of a recipe set.
 * <p>
 * Only recipe files are listed. A recipe set folder also holds a marker file saying the folder is a
 * recipe set, and that is bookkeeping rather than something anybody needs to see in the tree.
 * <p>
 * The folder is watched, so a recipe created by the new recipe action, or deleted or renamed from
 * anywhere, appears and disappears on its own. Without that the tree would be wrong until something
 * else happened to rebuild it.
 */
public final class RecipeNodeFactory extends ChildFactory<FileObject> {

    /** What a file in a recipe set has to be called to be a recipe. */
    private static final String RECIPE_EXTENSION = ".xml";

    private final Project project;
    private final FileObject folder;

    public RecipeNodeFactory(Project project) {
        this.project = project;
        this.folder = project.getProjectDirectory();
        folder.addFileChangeListener(new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent event) {
                refresh(true);
            }
        });
    }

    @Override
    protected boolean createKeys(List<FileObject> toPopulate) {
        for (FileObject child : folder.getChildren()) {
            if (isRecipe(child)) {
                toPopulate.add(child);
            }
        }
        return true;
    }

    @Override
    protected Node createNodeForKey(FileObject key) {
        return new RecipeNode(project, key);
    }

    private static boolean isRecipe(FileObject file) {
        return file != null
                && !file.isFolder()
                && file.getNameExt().toLowerCase(Locale.ROOT).endsWith(RECIPE_EXTENSION);
    }
}
