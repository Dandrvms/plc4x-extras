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

/**
 * Chooses the format a recipe is read and written in.
 * <p>
 * A project may hold its recipes in more than one format, so which repository applies is decided by
 * asking each one whether it accepts the format, rather than by the caller knowing the answer.
 */
public interface S88RecipeRepositoryProvider {

    /**
     * Whether this format is one this provider reads and writes.
     *
     * @param format name of the format, may be {@code null}
     * @return true when the recipe is to be handled this way
     */
    boolean accepts(String format);

    /**
     * A repository over the given storage.
     *
     * @param storage where the recipe is read from and written to
     * @return the repository
     */
    S88RecipeRepository createRepository(S88Storage storage);
}
