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
package org.apache.plc4x.malbec.s88.recipes.panels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;
import org.apache.plc4x.malbec.s88.api.S88Storage;
import org.apache.plc4x.malbec.s88.data.impl.BatchMLRecipeRepositoryImpl;

/**
 * A recipe written out to bytes, so that one recipe can be compared with another.
 * <p>
 * Comparing the objects would compare identity, and going through the fields would compare as much
 * as somebody remembered to write down. Writing both out says what the file would say, which is what
 * has to match for an edit to be undoable.
 */
final class RecipeBytes {

    private RecipeBytes() {
    }

    /**
     * @param recipe what to write out
     * @return the recipe as a file would hold it
     */
    static byte[] of(S88MasterRecipe recipe) {
        Memory memory = new Memory();
        new BatchMLRecipeRepositoryImpl(memory).saveRecipe(recipe);
        return memory.written();
    }

    /**
     * A storage that already holds a recipe, written and read back so that what a model opens is the
     * bytes a file would have rather than the object that produced them.
     *
     * @param recipe what the storage holds
     * @return the storage
     */
    static Memory holding(S88MasterRecipe recipe) {
        Memory memory = new Memory();
        new BatchMLRecipeRepositoryImpl(memory).saveRecipe(recipe);
        return memory;
    }

    /**
     * Opens an editor on a recipe held in a storage.
     *
     * @param storage where the recipe is
     * @return the editor state
     */
    static RecipeEditorModel openOn(Memory storage) throws IOException {
        return RecipeEditorModel.open(null, storage);
    }

    /**
     * @param one  a recipe as written
     * @param other another one
     * @return how they differ, or empty when they do not
     */
    static String difference(byte[] one, byte[] other) {
        if (Arrays.equals(one, other)) {
            return "";
        }
        String left = new String(one, StandardCharsets.UTF_8);
        String right = new String(other, StandardCharsets.UTF_8);
        return "the two are " + left.length() + " and " + right.length() + " characters long;\n"
                + "first differs at " + firstDifference(left, right) + ":\n"
                + head(left, firstDifference(left, right)) + "\nversus\n"
                + head(right, firstDifference(left, right));
    }

    private static int firstDifference(String left, String right) {
        int shared = Math.min(left.length(), right.length());
        for (int i = 0; i < shared; i++) {
            if (left.charAt(i) != right.charAt(i)) {
                return i;
            }
        }
        return shared;
    }

    private static String head(String text, int from) {
        int start = Math.max(0, from - 40);
        int end = Math.min(text.length(), from + 120);
        return "..." + text.substring(start, end) + "...";
    }

    /** Where a repository reads and writes, held in memory. */
    static final class Memory implements S88Storage {

        private byte[] bytes = new byte[0];

        @Override
        public InputStream openInput() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public OutputStream openOutput() {
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            return new OutputStream() {
                @Override
                public void write(int b) {
                    sink.write(b);
                }

                @Override
                public void close() {
                    bytes = sink.toByteArray();
                }
            };
        }

        /** @return what has been written to here */
        byte[] written() {
            return bytes;
        }
    }
}
