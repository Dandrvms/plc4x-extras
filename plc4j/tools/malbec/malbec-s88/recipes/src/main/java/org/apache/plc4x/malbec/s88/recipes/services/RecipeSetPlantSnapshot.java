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
package org.apache.plc4x.malbec.s88.recipes.services;

import java.io.IOException;
import org.apache.plc4x.malbec.s88.api.S88PlantModel;
import org.apache.plc4x.malbec.s88.api.S88PlantSnapshot;
import org.apache.plc4x.malbec.s88.api.S88Repository;
import org.apache.plc4x.malbec.s88.api.S88RepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.core.PlantSnapshotUseCase;
import org.apache.plc4x.malbec.s88.data.FileObjectStorage;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 * The plant a recipe set was written against, frozen into its own file.
 *
 * <p>
 * <b>A recipe belongs to the plant it was written against, not to the plant as it is now.</b> A
 * equipment that is renamed, moved or given a different parameter after a recipe was written does not
 * stop being the equipment that recipe steps on, and a recipe that silently followed the change would
 * be a recipe that no longer says what its author meant. So the plant is copied once, into this
 * folder, and every recipe of the set reads that copy.
 *
 * <p>
 * <b>The copy is never taken again.</b> Taking a new one would change what every existing recipe
 * means, which is a question for the author of those recipes and not for the editor. Taking a new
 * snapshot is a migration, and there is none yet.
 *
 * <p>
 * <b>The file is not a recipe and does not say it is one.</b> It has no {@code .xml} extension and
 * the recipe set node leaves out anything that is not a recipe, so there is no node to open, rename
 * or delete. Nothing in the editor writes to it either: it is read and left alone.
 *
 * <p>
 * The plant is found by the name of its own file rather than by asking the plant module, which is
 * the same convention {@code recipes.cfg} and {@code plant.cfg} already follow across the modules of
 * this project. That is what lets a recipe be written without the plant module on the classpath at
 * all, and only the plant it was written against matters from then on.
 *
 * @see #SNAPSHOT_FILE
 * @see #PLANT_FILE
 */
public final class RecipeSetPlantSnapshot {

    /**
     * What the frozen plant is called inside a recipe set.
     * <p>
     * Without an extension on purpose. The recipe set lists what ends in {@code .xml} and nothing
     * else, and a file that has to be excluded by name is a file that can be shown by mistake.
     */
    public static final String SNAPSHOT_FILE = "plant.snapshot";

    /**
     * The name the plant of a project has, which is how it is found from a recipe set.
     * <p>
     * The plant module declares the same name for its own project file, and neither module can ask
     * the other for it without one of them depending on the other.
     */
    public static final String PLANT_FILE = "plant.xml";

    /** The format the frozen plant is written in, which is the one the plant itself is. */
    private static final String FORMAT = "xml";

    private RecipeSetPlantSnapshot() {
        /* This utility class should not be instantiated */
    }

    /**
     * The file the frozen plant is in.
     *
     * @param recipeSet recipe set the file belongs to
     * @return the file, {@code null} when it has not been taken
     */
    public static FileObject fileIn(Project recipeSet) {
        return recipeSet == null || recipeSet.getProjectDirectory() == null
                ? null
                : recipeSet.getProjectDirectory().getFileObject(SNAPSHOT_FILE);
    }

    /**
     * Whether a recipe set has a plant frozen into it.
     *
     * @param recipeSet recipe set to look at
     * @return true when there is a frozen plant to read
     */
    public static boolean isTaken(Project recipeSet) {
        return fileIn(recipeSet) != null;
    }

    /**
     * Freezes the plant of a project into a recipe set that has none yet.
     * <p>
     * Does nothing when the recipe set already has one, because that file is the plant every recipe
     * of the set already means and taking it again would change all of them.
     *
     * @param recipeSet recipe set the plant is frozen into
     * @param plant     plant of the project the recipe set belongs to
     * @return true when a plant was frozen by this call, false when there already was one
     * @throws IOException when the file cannot be written
     * @throws IllegalArgumentException when there is no plant to freeze
     */
public static boolean takeIfMissing(Project recipeSet, S88PlantModel plant) throws IOException {
        if (isTaken(recipeSet)) {
            return false;
        }
        FileObject file = recipeSet.getProjectDirectory().createData(SNAPSHOT_FILE);
        freeze(new FileObjectStorage(file), plant);
        return true;
    }

    /**
     * Reads the frozen plant of a recipe set.
     *
     * @param recipeSet recipe set to read
     * @return the plant as it was, or {@code null} when there is none or it cannot be read
     */
    public static S88PlantSnapshot open(Project recipeSet) {
        FileObject file = fileIn(recipeSet);
        if (file == null) {
            return null;
        }
        S88PlantModel plant = thaw(new FileObjectStorage(file));
        return plant == null ? null : PlantSnapshotUseCase.of(plant);
    }

    private static S88Repository createRepository(S88Storage storage) {
        for (S88RepositoryProvider provider
                : java.util.ServiceLoader.load(S88RepositoryProvider.class)) {
            if (provider.accepts(FORMAT)) {
                return provider.createRepository(storage);
            }
        }
        throw new IllegalStateException("Nothing here knows how to read or write the '" + FORMAT
                + "' format a plant is kept in.");
    }

    /**
     * The plant of the project a recipe set belongs to, read from the file it is kept in.
     *
     * @param parent the project the recipe set is a subproject of
     * @return the plant as it is now, or {@code null} when the project has no plant to read
     */
    public static S88PlantModel plantOf(FileObject parent) {
        if (parent == null) {
            return null;
        }
        for (FileObject folder : parent.getChildren()) {
            if (!folder.isFolder()) {
                continue;
            }
            FileObject file = folder.getFileObject(PLANT_FILE);
            if (file != null) {
                S88PlantModel plant = thaw(new FileObjectStorage(file));
                if (plant != null) {
                    return plant;
                }
            }
        }
        return null;
    }

    /**
     * Writes a plant into somewhere it can be read back.
     *
     * @param storage where the plant is frozen to
     * @param plant   the plant as it is now
     * @throws IOException when the plant cannot be written
     */
    static void freeze(S88Storage storage, S88PlantModel plant) throws IOException {
        if (plant == null) {
            throw new IllegalArgumentException("A recipe set needs a plant to be written against, and"
                    + " this project has none. Create the plant first.");
        }
        createRepository(storage).savePlant(plant);
    }

    /**
     * Reads a plant that was frozen.
     *
     * @param storage where the plant was frozen
     * @return the plant as it was, or {@code null} when there is none to read
     */
    static S88PlantModel thaw(S88Storage storage) {
        return storage == null ? null : createRepository(storage).loadPlant();
    }

    /**
     * The frozen plant of a recipe set, said out loud when there is none.
     *
     * @param recipeSet recipe set to read
     * @return the plant as it was
     * @throws IllegalStateException when the recipe set has no frozen plant
     */
    public static S88PlantSnapshot required(Project recipeSet) {
        S88PlantSnapshot plant = open(recipeSet);
        if (plant == null) {
            throw new IllegalStateException("This recipe set has no plant frozen into it, so what its"
                    + " steps are attached to cannot be said. Take a snapshot of the plant before"
                    + " writing a recipe for it.");
        }
        return plant;
    }
}