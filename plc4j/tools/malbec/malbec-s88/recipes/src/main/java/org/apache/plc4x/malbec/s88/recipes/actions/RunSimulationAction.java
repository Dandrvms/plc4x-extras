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
package org.apache.plc4x.malbec.s88.recipes.actions;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.batch.builder.BatchPayloadFactory;
import org.apache.plc4x.malbec.s88.batch.builder.BatchRequest;
import org.apache.plc4x.malbec.s88.batch.builder.PrepareResult;
import org.apache.plc4x.malbec.s88.batch.simulator.SessionOptions;
import org.apache.plc4x.malbec.s88.batch.simulator.SimulatedSession;
import org.apache.plc4x.malbec.s88.core.EquipmentUidMigration;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.apache.plc4x.malbec.s88.core.RecipeDeepCopy;
import org.apache.plc4x.malbec.s88.recipes.data.RecipeDataObject;
import org.apache.plc4x.malbec.s88.recipes.editor.RecipeEditorModel;
import org.apache.plc4x.malbec.s88.recipes.runtime.BatchRuntimeTopComponent;
import org.apache.plc4x.malbec.s88.recipes.runtime.RuntimeViewModel;
import org.apache.plc4x.malbec.s88.recipes.simulator.SimulatorTopComponent;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** Opens a simulated run of the recipe in focus. */
@ActionID(category = "Recipe",
        id = "org.apache.plc4x.malbec.s88.recipes.actions.RunSimulationAction")
@ActionRegistration(displayName = "Run Simulation", menuText = "Run Simulation")
@ActionReference(path = "Loaders/text/b2mml-recipe+xml/Actions", position = 1250)
public final class RunSimulationAction extends AbstractAction implements ContextAwareAction {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter BATCH_ID =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final Lookup context;

    public RunSimulationAction() {
        this(Lookup.EMPTY);
    }

    private RunSimulationAction(Lookup context) {
        super("Run Simulation");
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        RecipeEditorModel model = modelOf(context);
        if (model != null) {
            run(model, WindowManager.getDefault().getMainWindow());
        }
    }

    @Override
    public boolean isEnabled() {
        return modelOf(context) != null;
    }

    @Override
    public Action createContextAwareInstance(Lookup otherContext) {
        return new RunSimulationAction(otherContext);
    }

    /** Runs a recipe from a view that already holds its editor state. */
    public static void run(RecipeEditorModel model, Window owner) {
        if (model.warning() != null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    model.warning(), NotifyDescriptor.WARNING_MESSAGE));
            return;
        }
        if (model.plant() == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "The recipe set has no plant, so there is nothing to simulate.",
                    NotifyDescriptor.WARNING_MESSAGE));
            return;
        }
        S88PlantSnapshot plant = PlantSnapshotUseCase.of(model.plant());
        Path output = defaultOutput(model);
        NewBatchDialog.BatchDetails details = new NewBatchDialog(owner,
                model.getRecipe(), plant,
                "BATCH-" + BATCH_ID.format(LocalDateTime.now()), output).showDialog();
        if (details == null) {
            return;
        }
        S88MasterRecipe copy = RecipeDeepCopy.copyMasterRecipe(model.getRecipe());
        EquipmentUidMigration.repair(copy, plant);
        BatchRequest request = new BatchRequest(copy, plant, details.batchId(),
                details.equipmentOverrides(), null);
        PrepareResult prepared = BatchPayloadFactory.prepare(request);
        if (prepared.hasErrors()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    String.join("\n", prepared.errors()), NotifyDescriptor.ERROR_MESSAGE));
            return;
        }
        SimulatedSession session = SimulatedSession.create(prepared.payload(),
                SessionOptions.defaults().withSpeed(details.speed()).withClockRunning(false));
        try {
            BatchRuntimeTopComponent.open(prepared.payload(), session.client(), details.outputRoot());
            SimulatorTopComponent.open(session, prepared.payload(),
                    new RuntimeViewModel(prepared.payload().control(), prepared.payload().plant()));
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static Path defaultOutput(RecipeEditorModel model) {
        FileObject recipe = model.getRecipeFile();
        FileObject folder = recipe == null ? null : recipe.getParent();
        File file = folder == null ? null : FileUtil.toFile(folder);
        if (file != null) {
            return file.toPath().resolve("batch-output");
        }
        return Path.of(System.getProperty("user.home", "."), ".malbec", "batch-output");
    }

    private static RecipeEditorModel modelOf(Lookup lookup) {
        RecipeEditorModel model = modelOfLookup(lookup);
        if (model != null) {
            return model;
        }
        TopComponent active = TopComponent.getRegistry().getActivated();
        if (active != null && active.getLookup() != lookup) {
            return modelOfLookup(active.getLookup());
        }
        return null;
    }

    private static RecipeEditorModel modelOfLookup(Lookup lookup) {
        RecipeEditorModel model = lookup == null ? null : lookup.lookup(RecipeEditorModel.class);
        if (model != null) {
            return model;
        }
        RecipeDataObject dataObject = lookup == null ? null : lookup.lookup(RecipeDataObject.class);
        if (dataObject == null) {
            return null;
        }
        try {
            return dataObject.model();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
