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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class S88RecipeHeaderTest {

    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant SAVED = Instant.parse("2026-11-03T11:00:00Z");

    @Test
    void aNewRecipeStartsWithItsHeader() {
        S88RecipeHeader header = S88RecipeHeader.created("operator", "A recipe",
                S88RecipeKind.INSTANCE, "plant.xml", "DEMO.xml", CREATED);

        assertEquals("1.0", header.version());
        assertEquals("2026-11-03T10:00:00Z", header.creationDate());
        assertEquals("2026-11-03T10:00:00Z", header.lastModifiedDate());
        assertEquals("operator", header.author());
        assertEquals("A recipe", header.description());
        assertEquals(S88RecipeKind.INSTANCE, header.kind());
        assertEquals("plant.xml", header.areaModelFile());
        assertEquals("DEMO.xml", header.fileName());
    }

    @Test
    void applyingAHeaderTwiceDoesNotRepeatTheDescription() {
        S88MasterRecipe recipe = new S88MasterRecipe("DEMO", S88RecipeKind.CLASS);
        S88RecipeHeader header = S88RecipeHeader.created("operator", "A recipe",
                S88RecipeKind.INSTANCE, "plant.xml", "DEMO.xml", CREATED);

        header.applyTo(recipe);
        header.applyTo(recipe);

        assertEquals(1, recipe.getDescriptions().size());
        assertEquals("1.0", recipe.getVersion());
        assertEquals(S88RecipeKind.INSTANCE, recipe.getKind());
        assertEquals("plant.xml", recipe.getAreaModelFile());
        assertEquals("DEMO.xml", recipe.getFileName());
    }

    @Test
    void savingChangesOnlyTheModificationDateAndTheFileName() {
        S88RecipeHeader header = S88RecipeHeader.created("operator", "A recipe",
                S88RecipeKind.CLASS, "plant.xml", "OLD.xml", CREATED);

        S88RecipeHeader saved = header.saved(SAVED, "NEW.xml");

        assertEquals(header.creationDate(), saved.creationDate());
        assertEquals(header.author(), saved.author());
        assertEquals(header.description(), saved.description());
        assertEquals("2026-11-03T11:00:00Z", saved.lastModifiedDate());
        assertEquals("NEW.xml", saved.fileName());
    }

    @Test
    void defaultsFillAnOldHeaderWithoutInventingADescription() {
        S88RecipeHeader old = new S88RecipeHeader(null, null, null, null, null,
                S88RecipeKind.CLASS, null, null);

        S88RecipeHeader filled = old.withDefaults(CREATED, "operator");

        assertEquals("1.0", filled.version());
        assertEquals("2026-11-03T10:00:00Z", filled.creationDate());
        assertEquals("2026-11-03T10:00:00Z", filled.lastModifiedDate());
        assertEquals("operator", filled.author());
        assertEquals("plant.xml", filled.areaModelFile());
        assertNull(filled.description());
    }

    @Test
    void aHeaderIsReadBackFromTheRecipe() {
        S88MasterRecipe recipe = new S88MasterRecipe("DEMO", S88RecipeKind.CLASS);
        S88RecipeHeader.created("operator", "A recipe", S88RecipeKind.INSTANCE,
                "plant.xml", "DEMO.xml", CREATED).applyTo(recipe);

        S88RecipeHeader read = S88RecipeHeader.of(recipe);

        assertEquals("operator", read.author());
        assertEquals("A recipe", read.description());
        assertEquals("DEMO.xml", read.fileName());
        assertEquals(S88RecipeKind.INSTANCE, read.kind());
    }
}
