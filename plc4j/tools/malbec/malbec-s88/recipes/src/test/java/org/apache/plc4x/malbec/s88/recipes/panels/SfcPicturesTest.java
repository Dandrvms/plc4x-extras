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

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Draws every fixture into a picture file under {@code target/sfc}.
 * <p>
 * This is kept apart from {@link SfcFixturesTest}, so that the pictures still get written when the
 * structural checks of those fixtures are switched off for the structures that are not built yet.
 * <p>
 * A picture is the only thing that shows whether a chart reads correctly, so a run that leaves the
 * structures out of the pictures leaves them unreviewed.
 */
class SfcPicturesTest {

    @Test
    void everyFixtureIsDrawnIntoAPictureFile() throws Exception {
        List<File> written = drawAllFixtures();

        assertEquals(SfcFixtures.names().size(), written.size(), "one picture per fixture");
        for (File file : written) {
            assertTrue(file.isFile() && file.length() > 0,
                    "a picture of nothing is not a picture to look at: " + file);
        }
    }

    /** Draws the fixtures and hands back the pictures, reporting anything that went wrong. */
    private static List<File> drawAllFixtures() throws Exception {
        AtomicReference<List<File>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(SfcRenderer.renderAllFixtures());
                } catch (Throwable problem) {
                    failure.set(problem);
                }
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the chart");
            return null;
        } catch (Exception other) {
            fail("could not reach the chart: " + other);
            return null;
        }
        if (failure.get() != null) {
            if (failure.get() instanceof AssertionError assertion) {
                throw assertion;
            }
            throw new AssertionError("the chart fell over", failure.get());
        }
        return result.get();
    }
}
