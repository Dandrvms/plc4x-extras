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

import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.openide.util.Lookup;

/**
 * NetBeans integration service for recipe repositories.
 * <p>
 * The counterpart of the one the plant has. What it buys is that a caller names the format and not
 * the class: the recipe repositories are reached the same way the plant ones already are, so adding
 * a format means adding a provider and touching nothing that opens a recipe.
 */
public final class S88RecipeProjectServices {

    private S88RecipeProjectServices() {
        /* This utility class should not be instantiated */
    }

    public static S88RecipeRepository createRepository(String format, S88Storage storage) {
        var providers = Lookup.getDefault().lookupAll(S88RecipeRepositoryProvider.class);
        for (S88RecipeRepositoryProvider provider : providers) {
            if (provider.accepts(format)) {
                return provider.createRepository(storage);
            }
        }
        throw new IllegalArgumentException("Unsupported format: " + format);
    }
}
