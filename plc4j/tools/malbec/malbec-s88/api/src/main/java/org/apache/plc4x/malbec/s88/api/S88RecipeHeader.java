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
package org.apache.plc4x.malbec.s88.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The ISA-88 header of a master recipe.
 *
 * @param creationDate the date the first version was written
 * @param areaModelFile the name of the plant.xml the recipe was written against
 * @param fileName     the recipe file with its extension
 */
public record S88RecipeHeader(String version, String creationDate, String lastModifiedDate,
                              String author, String description, S88RecipeKind kind,
                              String areaModelFile, String fileName) {

    public static final String FIRST_VERSION = "1.0";
    public static final String PLANT_FILE = "plant.xml";
    public static final String UNKNOWN_AUTHOR = "unknown";

    /** The header of a recipe that was just read. */
    public static S88RecipeHeader of(S88MasterRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        List<String> descriptions = recipe.getDescriptions();
        return new S88RecipeHeader(recipe.getVersion(), recipe.getVersionDate(),
                recipe.getLastModifiedDate(), recipe.getAuthor(),
                descriptions.isEmpty() ? null : descriptions.get(0),
                recipe.getKind() != null ? recipe.getKind() : S88RecipeKind.CLASS,
                recipe.getAreaModelFile(), recipe.getFileName());
    }

    /** The header a new recipe starts with. */
    public static S88RecipeHeader created(String author, String description, S88RecipeKind kind,
                                          String areaModelFile, String fileName, Instant now) {
        String timestamp = Objects.requireNonNull(now, "now").toString();
        return new S88RecipeHeader(FIRST_VERSION, timestamp, timestamp,
                author == null || author.isBlank() ? UNKNOWN_AUTHOR : author,
                description, kind == null ? S88RecipeKind.INSTANCE : kind,
                areaModelFile == null || areaModelFile.isBlank() ? PLANT_FILE : areaModelFile,
                fileName);
    }

    /** Fills the fields an old recipe did not carry. */
    public S88RecipeHeader withDefaults(Instant now, String defaultAuthor) {
        String timestamp = Objects.requireNonNull(now, "now").toString();
        String fallbackAuthor = defaultAuthor == null || defaultAuthor.isBlank()
                ? UNKNOWN_AUTHOR : defaultAuthor;
        return new S88RecipeHeader(
                version == null || version.isBlank() ? FIRST_VERSION : version,
                creationDate == null || creationDate.isBlank() ? timestamp : creationDate,
                lastModifiedDate == null || lastModifiedDate.isBlank() ? timestamp : lastModifiedDate,
                author == null || author.isBlank() ? fallbackAuthor : author,
                description, kind == null ? S88RecipeKind.CLASS : kind,
                areaModelFile == null || areaModelFile.isBlank() ? PLANT_FILE : areaModelFile,
                fileName);
    }

    /** The same header after a save. */
    public S88RecipeHeader saved(Instant now, String currentFileName) {
        return new S88RecipeHeader(version, creationDate, Objects.requireNonNull(now, "now").toString(),
                author, description, kind, areaModelFile,
                currentFileName == null || currentFileName.isBlank() ? fileName : currentFileName);
    }

    /** Writes the header into the recipe, without adding the description twice. */
    public void applyTo(S88MasterRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        recipe.setVersion(version);
        recipe.setVersionDate(creationDate);
        recipe.setLastModifiedDate(lastModifiedDate);
        recipe.setAuthor(author);
        recipe.setAreaModelFile(areaModelFile);
        recipe.setFileName(fileName);
        S88RecipeKind wantedKind = kind == null ? S88RecipeKind.CLASS : kind;
        if (recipe.getKind() != wantedKind) {
            recipe.setKind(wantedKind);
        }
        if (description != null && !description.isBlank()
                && !recipe.getDescriptions().contains(description)) {
            recipe.addDescription(description);
        }
    }
}
