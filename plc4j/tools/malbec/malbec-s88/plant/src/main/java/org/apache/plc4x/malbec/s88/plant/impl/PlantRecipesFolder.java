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
package org.apache.plc4x.malbec.s88.plant.impl;

import java.io.IOException;
import org.apache.plc4x.malbec.s88.api.S88ProjectLayout;
import org.openide.filesystems.FileObject;

/**
 * Ensures a plant has its recipes project.
 */
public final class PlantRecipesFolder {

    private PlantRecipesFolder() {
    }

    /**
     * Creates the recipes folder and its project file when they are missing.
     *
     * @param plantFolder the plant project directory
     * @return the recipes project directory
     * @throws IOException when the plant folder is not a folder or the files cannot be created
     */
    public static FileObject ensure(FileObject plantFolder) throws IOException {
        if (plantFolder == null || !plantFolder.isFolder()) {
            throw new IOException("A plant project needs a folder to keep its recipes in.");
        }
        FileObject recipes = plantFolder.getFileObject(S88ProjectLayout.RECIPES_FOLDER);
        if (recipes == null) {
            recipes = plantFolder.createFolder(S88ProjectLayout.RECIPES_FOLDER);
        }
        if (!recipes.isFolder()) {
            throw new IOException("'" + S88ProjectLayout.RECIPES_FOLDER
                    + "' is a file, so the plant cannot keep recipes in it.");
        }
        if (recipes.getFileObject(S88ProjectLayout.RECIPES_PROJECT_FILE) == null) {
            recipes.createData(S88ProjectLayout.RECIPES_PROJECT_FILE);
        }
        return recipes;
    }
}
