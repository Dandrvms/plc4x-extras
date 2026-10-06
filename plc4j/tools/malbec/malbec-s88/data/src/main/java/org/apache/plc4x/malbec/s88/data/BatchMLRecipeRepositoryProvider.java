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
package org.apache.plc4x.malbec.s88.data;

import org.apache.plc4x.malbec.s88.api.S88RecipeRepository;
import org.apache.plc4x.malbec.s88.api.S88RecipeRepositoryProvider;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.data.impl.BatchMLRecipeRepositoryImpl;
import org.openide.util.lookup.ServiceProvider;

/**
 * Offers recipes in the B2MML format of MESA.
 * <p>
 * Takes the same {@code xml} name as the plant does, because a recipe and a plant are both this
 * format and a caller asking for it should not have to know which of the two it is about to get.
 * Which one it gets is decided by what it asks the returned repository for, not by the name.
 */
@ServiceProvider(service = S88RecipeRepositoryProvider.class)
public class BatchMLRecipeRepositoryProvider implements S88RecipeRepositoryProvider {

    @Override
    public boolean accepts(String format) {
        return "xml".equalsIgnoreCase(format);
    }

    @Override
    public S88RecipeRepository createRepository(S88Storage storage) {
        return new BatchMLRecipeRepositoryImpl(storage);
    }
}
