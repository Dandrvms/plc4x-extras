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
package org.apache.plc4x.malbec.s88.recipes.data;

import java.beans.BeanInfo;
import java.io.IOException;
import org.apache.plc4x.malbec.s88.recipes.panels.RecipeEditorModel;
import org.netbeans.core.api.multiview.MultiViews;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.UndoRedo;
import org.openide.cookies.CloseCookie;
import org.openide.cookies.OpenCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.loaders.OpenSupport;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.CloneableTopComponent;

@Messages({
    "LBL_Recipe_LOADER=Files of Recipe"
})
@MIMEResolver.NamespaceRegistration(
        displayName = "#LBL_Recipe_LOADER",
        mimeType = "text/b2mml-recipe+xml",
        elementName = "BatchInformation",
        elementNS = {"http://www.mesa.org/xml/B2MML"},
        position = 100
)
@DataObject.Registration(
        mimeType = "text/b2mml-recipe+xml",
        iconBase = "org/apache/plc4x/malbec/s88/recipes/nodes/recipe.png",
        displayName = "#LBL_Recipe_LOADER",
        position = 300
)
@ActionReferences({
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
            position = 100,
            separatorAfter = 200
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
            position = 300
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"),
            position = 400,
            separatorAfter = 500
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"),
            position = 600
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"),
            position = 700,
            separatorAfter = 800
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"),
            position = 1100,
            separatorAfter = 1200
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"),
            position = 1300
    ),
    @ActionReference(
            path = "Loaders/text/b2mml-recipe+xml/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"),
            position = 1400
    )
})
public class RecipeDataObject extends MultiDataObject implements UndoRedo.Provider {

    public static final String MIME_TYPE = "text/b2mml-recipe+xml";

    private RecipeEditorModel model;
    private final SaveCookie saveCookie = new RecipeSaveCookie();

    public RecipeDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(pf, loader);
        getCookieSet().add(new RecipeOpenSupport());
        getCookieSet().assign(UndoRedo.Provider.class, this);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    /**
     * The editor state of this recipe, read from the file the first time it is asked for.
     *
     * @return the editor state, never {@code null}
     */
    public synchronized RecipeEditorModel model() {
        if (model == null) {
            RecipeEditorModel loaded;
            try {
                loaded = RecipeEditorModel.open(getPrimaryFile());
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "'" + getPrimaryFile().getNameExt() + "' could not be read.", failure);
            }
            model = loaded;
            getCookieSet().assign(RecipeEditorModel.class, loaded);
            loaded.getUndoRedo().addChangeListener(event -> applyModifiedState(loaded.isModified()));
            applyModifiedState(loaded.isModified());
        }
        return model;
    }

    private void applyModifiedState(boolean modified) {
        setModified(modified);
        if (modified) {
            if (getCookieSet().getCookie(SaveCookie.class) == null) {
                getCookieSet().add(saveCookie);
            }
        } else {
            getCookieSet().remove(saveCookie);
        }
    }

    private synchronized RecipeEditorModel modelOrNull() {
        return model;
    }

    @Override
    public UndoRedo getUndoRedo() {
        RecipeEditorModel current = modelOrNull();
        return current == null ? UndoRedo.NONE : current.getUndoRedo();
    }

    private final class RecipeOpenSupport extends OpenSupport implements OpenCookie, CloseCookie {

        RecipeOpenSupport() {
            super(RecipeDataObject.this.getPrimaryEntry());
        }

        @Override
        public void open() {
            try {
                model();
            } catch (RuntimeException failure) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        "'" + getPrimaryFile().getNameExt() + "' could not be opened.\n"
                                + failure.getMessage(),
                        NotifyDescriptor.ERROR_MESSAGE));
                return;
            }
            super.open();
        }

        @Override
        protected CloneableTopComponent createCloneableTopComponent() {
            CloneableTopComponent window =
                    MultiViews.createCloneableMultiView(MIME_TYPE, RecipeDataObject.this);
            RecipeEditorModel current = modelOrNull();
            String displayName = current != null && current.getRecipe().getId() != null
                    ? current.getRecipe().getId()
                    : getPrimaryFile().getNameExt();
            window.setName(getPrimaryFile().getName());
            window.setDisplayName(displayName);
            window.setToolTipText(FileUtil.getFileDisplayName(getPrimaryFile()));
            window.setIcon(getNodeDelegate().getIcon(BeanInfo.ICON_COLOR_16x16));
            return window;
        }
    }

    private final class RecipeSaveCookie implements SaveCookie {

        @Override
        public void save() throws IOException {
            try {
                RecipeEditorModel current = model();
                if (!current.isModified()) {
                    return;
                }
                current.save();
                applyModifiedState(false);
            } catch (IOException | RuntimeException failure) {
                reportSaveFailure(failure);
                throw failure;
            }
        }

        @Override
        public String toString() {
            return getPrimaryFile().getNameExt();
        }
    }

    private void reportSaveFailure(Exception failure) {
        String message = "'" + getPrimaryFile().getNameExt() + "' could not be saved.";
        if (failure.getMessage() != null) {
            message += "\n" + failure.getMessage();
        }
        DialogDisplayer.getDefault().notify(
                new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE));
    }
}
