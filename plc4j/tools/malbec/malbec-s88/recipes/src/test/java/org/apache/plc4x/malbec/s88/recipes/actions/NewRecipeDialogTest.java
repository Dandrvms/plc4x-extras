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

import org.apache.plc4x.malbec.s88.api.S88RecipeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewRecipeDialogTest {

    @Test
    void theNameIsTrimmedAndTheXmlExtensionIsNotRepeated() {
        assertEquals("DEMO", NewRecipeDialog.normalizedName("  DEMO.xml  "));
        assertEquals("DEMO.xml", NewRecipeDialog.fileName("DEMO"));
        assertEquals("DEMO.xml", NewRecipeDialog.fileName("DEMO.xml"));
    }

    @Test
    void theDraftKeepsTheDescriptionAndTheKind() {
        NewRecipeDialog.RecipeDraft draft = new NewRecipeDialog.RecipeDraft(
                "DEMO", "A recipe", S88RecipeKind.CLASS);

        assertEquals("DEMO", draft.name());
        assertEquals("A recipe", draft.description());
        assertEquals(S88RecipeKind.CLASS, draft.kind());
        assertEquals("DEMO.xml", draft.fileName());
    }
}
