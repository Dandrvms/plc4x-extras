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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.plc4x.malbec.s88.api.S88MasterRecipe;

/**
 * Draws a recipe's chart into a picture file, with no window anywhere.
 * <p>
 * A picture is the only thing that shows whether a chart reads correctly. Swing can paint into one
 * on its own, through {@code Scene.paint(Graphics2D)}, so a test that draws a chart shows nobody
 * anything.
 */
final class SfcRenderer {

    /** Margin around the drawing, so nothing is cut at the edge of the picture. */
    private static final int MARGIN = 24;

    /** Where the pictures go. Under target, because they are a by-product of the build. */
    static final File OUTPUT = new File("target/sfc");

    /** How much room the chart is measured in before its real size is known. */
    private static final int ROOM = 4000;

    private SfcRenderer() {
    }

    /**
     * Draws every fixture and writes one picture per recipe.
     *
     * @return the files written, in the same order as {@link SfcFixtures#all()}
     * @throws IOException when the picture cannot be written
     */
    static List<File> renderAllFixtures() throws IOException {
        List<S88MasterRecipe> recipes = SfcFixtures.all();
        List<String> names = SfcFixtures.names();
        if (recipes.size() != names.size()) {
            throw new IllegalStateException(
                    "A fixture without a name cannot be written down: " + recipes.size()
                            + " recipes and " + names.size() + " names.");
        }
        java.util.List<File> written = new java.util.ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            written.add(render(recipes.get(i), names.get(i)));
        }
        return written;
    }

    /**
     * Draws one recipe.
     *
     * @param recipe what to draw
     * @param name   the file name to write it under
     * @return the file written
     * @throws IOException when the picture cannot be written
     */
    static File render(S88MasterRecipe recipe, String name) throws IOException {
        RecipeChartScene scene = RecipeChartScene.forRecipe(recipe);

        // Measured first, into a throwaway picture: the library gives nothing a position until it
        // has been laid out, and it will not lay out until it has something to lay out into. It
        // also holds the scene to a maximum size, which in a window comes from the window, so a
        // generous one is given here or the chart is measured as nothing at all.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D probeGraphics = probe.createGraphics();
        try {
            scene.setMaximumBounds(new java.awt.Rectangle(0, 0, ROOM, ROOM));
            scene.validate(probeGraphics);
        } finally {
            probeGraphics.dispose();
        }

        java.awt.Rectangle area = scene.contentBounds();
        int width = Math.max(1, area.width + MARGIN);
        int height = Math.max(1, area.height + MARGIN);
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = picture.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // The scene draws with its own corner at the margin, so a box on the left of the chart
            // is not sitting on the left edge of the paper.
            g.translate(MARGIN, MARGIN);
            scene.setMaximumBounds(new java.awt.Rectangle(0, 0, width, height));
            scene.paint(g);
        } finally {
            g.dispose();
        }

        if (!OUTPUT.isDirectory() && !OUTPUT.mkdirs()) {
            throw new IOException("Could not make '" + OUTPUT.getAbsolutePath() + "'.");
        }
        File file = new File(OUTPUT, name + ".png");
        if (!ImageIO.write(picture, "png", file)) {
            throw new IOException("No writer for a png file in this Java.");
        }
        return file;
    }
}
