/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.recipes.nodes;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.plc4x.malbec.s88.api.S88ProjectLayout;
import org.apache.plc4x.malbec.s88.recipes.impl.Plc4xRecipesSubProjectImpl;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.NodeFactory;
import org.netbeans.spi.project.ui.support.NodeFactorySupport;
import org.netbeans.spi.project.ui.support.NodeList;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.ChangeSupport;
import org.openide.util.Exceptions;

/**
 * Node factory to show the recipes project of a plant.
 */
@NodeFactory.Registration(projectType = "org-plc4x-plant-project", position = 15)
public class Plc4xRecipesSubProjectNodeFactoryImpl implements NodeFactory {

    @Override
    public NodeList<?> createNodes(Project project) {
        return new RecipesProjectNodeList(project);
    }

    private static final class RecipesProjectNodeList implements NodeList<Project>, ChangeListener {

        private final Project plant;
        private final ChangeSupport cs = new ChangeSupport(this);
        private final FileChangeAdapter plantFolderListener;
        private final FileChangeAdapter recipesFolderListener;
        private FileObject watchedRecipes;

        RecipesProjectNodeList(Project plant) {
            this.plant = plant;
            this.plantFolderListener = new FileChangeAdapter() {
                @Override
                public void fileDataCreated(FileEvent fe) {
                    if (isRecipesFolder(fe)) {
                        watchRecipes();
                        cs.fireChange();
                    }
                }

                @Override
                public void fileDeleted(FileEvent fe) {
                    if (isRecipesFolder(fe)) {
                        watchRecipes();
                        cs.fireChange();
                    }
                }

                @Override
                public void fileRenamed(FileRenameEvent fe) {
                    watchRecipes();
                    cs.fireChange();
                }
            };
            this.recipesFolderListener = new FileChangeAdapter() {
                @Override
                public void fileDataCreated(FileEvent fe) {
                    if (isRecipesProjectFile(fe)) {
                        cs.fireChange();
                    }
                }

                @Override
                public void fileDeleted(FileEvent fe) {
                    if (isRecipesProjectFile(fe)) {
                        cs.fireChange();
                    }
                }

                @Override
                public void fileRenamed(FileRenameEvent fe) {
                    cs.fireChange();
                }
            };
        }

        private static boolean isRecipesFolder(FileEvent fe) {
            return S88ProjectLayout.RECIPES_FOLDER.equals(fe.getFile().getNameExt());
        }

        private static boolean isRecipesProjectFile(FileEvent fe) {
            return S88ProjectLayout.RECIPES_PROJECT_FILE.equals(fe.getFile().getNameExt());
        }

        @Override
        public List<Project> keys() {
            Project recipes = recipesProject();
            return recipes == null ? List.of() : List.of(recipes);
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
            cs.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
            cs.removeChangeListener(listener);
        }

        @Override
        public Node node(Project project) {
            try {
                LogicalViewProvider lvp = project.getLookup().lookup(LogicalViewProvider.class);
                if (lvp != null) {
                    return new FilterNode(lvp.createLogicalView());
                }
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
        }

        @Override
        public void addNotify() {
            plant.getProjectDirectory().addFileChangeListener(plantFolderListener);
            watchRecipes();
        }

        @Override
        public void removeNotify() {
            plant.getProjectDirectory().removeFileChangeListener(plantFolderListener);
            if (watchedRecipes != null) {
                watchedRecipes.removeFileChangeListener(recipesFolderListener);
                watchedRecipes = null;
            }
        }

        @Override
        public void stateChanged(ChangeEvent event) {
            cs.fireChange();
        }

        private void watchRecipes() {
            FileObject next = plant.getProjectDirectory()
                    .getFileObject(S88ProjectLayout.RECIPES_FOLDER);
            if (Objects.equals(watchedRecipes, next)) {
                return;
            }
            if (watchedRecipes != null) {
                watchedRecipes.removeFileChangeListener(recipesFolderListener);
            }
            watchedRecipes = next;
            if (watchedRecipes != null && watchedRecipes.isFolder()) {
                watchedRecipes.addFileChangeListener(recipesFolderListener);
            }
        }

        private Project recipesProject() {
            FileObject folder = plant.getProjectDirectory()
                    .getFileObject(S88ProjectLayout.RECIPES_FOLDER);
            if (folder == null || !folder.isFolder()) {
                return null;
            }
            try {
                Project project = ProjectManager.getDefault().findProject(folder);
                return project instanceof Plc4xRecipesSubProjectImpl ? project : null;
            } catch (IOException | IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
