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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.apache.plc4x.malbec.s88.api.S88RecipeElementKind;

/**
 * What each thing on a chart looks like.
 * <p>
 * One definition per thing, drawn with Java2D. Drawing rather than loading also means the chart
 * stays sharp when it is zoomed and there are no image files to keep.
 */
final class RecipeShapes {

    private static final Color EDGE = new Color(0x33, 0x33, 0x33);
    private static final Color LABEL = new Color(0x55, 0x55, 0x55);
    private static final Color START_EDGE = new Color(0x1B, 0x5E, 0x20);
    private static final Color END_EDGE = new Color(51, 51, 51);
    private static final double STROKE = 1.4;

    /**
     * How thick a line and a bar are drawn.
     * <p>
     * The library draws its connections at this width, and a bar drawn at a different one reads as a
     * mark stuck on the end of a line rather than as part of it.
     */
    static final float LINE_WIDTH = 1.5f;

    /** What every label on the chart is written at. */
    /**
 * How big the text on a chart is.
 * <p>
 * Rounded to a whole point because a font cannot be written at a fraction of a point and one that is
 * asked for one gets rounded somewhere else, which is how a measured width stops matching a written
 * one.
 */
private static final int LABEL_SIZE = 10;

    /** The one font every box is measured with and written with. */
    private static final Font CHART_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, LABEL_SIZE);

    private RecipeShapes() {
    }

    /**
     * Draws one thing.
     *
     * @param kind   what it is
     * @param bounds where to draw it
     * @param g      where to draw it onto
     * @param label  the text it carries, may be {@code null}
     */
    static void paint(Shape kind, Rectangle2D bounds, Graphics2D g, String label) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Set rather than assumed, so that anything drawn on top of this text is measured and written
        // with the same font as the text itself.
        g.setFont(chartFont());
        switch (kind) {
            case START:
                paintStart(bounds, g, label);
                return;
            case END:
                paintEnd(bounds, g, label);
                return;
            case TRANSITION:
                paintTransition(bounds, g, label);
                return;
            default:
                paintBox(bounds, g, label);
        }
    }

    /**
     * How wide a text would be written.
 *
 * <p>
 * A label of more than one line is as wide as its widest line, because a box has to hold the widest
 * of them.
 *
 * @param label the text
 * @return its width, 0 when there is no text
 */
    static int textWidth(Graphics2D g, String label) {
        int widest = 0;
        for (String line : linesOf(label)) {
            widest = Math.max(widest, g.getFontMetrics(chartFont()).stringWidth(line));
        }
        return widest;
    }

    /**
 * How tall a text would be written.
 *
 * <p>
 * A label of more than one line is as tall as all of them, because a box has to hold them all. The
 * gap between lines is what stops them sitting on one another.
 *
 * @param label the text
 * @return its height, 0 when there is no text
 */
    static int textHeight(Graphics2D g, String label) {
        java.util.List<String> lines = linesOf(label);
        if (lines.isEmpty()) {
            return 0;
        }
        return lines.size() * g.getFontMetrics(chartFont()).getHeight();
    }

    /**
     * The lines a label is written as.
     *
     * @param label the text, may be {@code null}
     * @return its lines, empty when there is no text
     */
    static java.util.List<String> linesOf(String label) {
        if (label == null || label.isEmpty()) {
            return java.util.List.of();
        }
        return java.util.List.of(label.split("\\R"));
    }

    /**
     * Which of a step's two faces it wears.
     * <p>
     * The start and the stop of a process are steps like any other and carry the same kind in the
     * file; what tells them apart is what they mean.
     *
     * @param kind kind of the step, may be {@code null} for a step that does not say
     * @return the shape to draw it as
     */
    static Shape shapeOf(S88RecipeElementKind kind) {
        if (kind == S88RecipeElementKind.BEGIN) {
            return Shape.START;
        }
        if (kind == S88RecipeElementKind.END) {
            return Shape.END;
        }
        return Shape.BOX;
    }

    private static void paintBox(Rectangle2D bounds, Graphics2D g, String label) {
        g.setColor(Color.WHITE);
        g.fill(bounds);
        g.setColor(EDGE);
        g.setStroke(new BasicStroke((float) STROKE));
        g.draw(bounds);
        // Written inside a small margin, because text that reaches the border reads as cut off even when
        // it is not. The margin is kept small so that a fixed-height box has room for a value as
        // well as for the name, and the box is made wide enough for the text plus this margin.
        Rectangle2D inside = new Rectangle2D.Double(bounds.getX() + SfcMetrics.BOX_PADDING,
                bounds.getY(), Math.max(0, bounds.getWidth() - 2.0 * SfcMetrics.BOX_PADDING),
                bounds.getHeight());
        writeIn(inside, g, label, EDGE, Horizontal.CENTRE, Vertical.MIDDLE);
    }

    /**
     * The start of the process: a square inside a square.
     * <p>
     * Two concentric outlines are what a chart uses to mark where the flow begins.
     */
    private static void paintStart(Rectangle2D bounds, Graphics2D g, String label) {
        double side = Math.min(bounds.getWidth(), bounds.getHeight());
        Rectangle2D symbol = new Rectangle2D.Double(bounds.getX(), bounds.getY(), side, side);
        g.setColor(Color.WHITE);
        g.fill(symbol);
        g.setColor(START_EDGE);
        g.setStroke(new BasicStroke((float) STROKE));
        g.draw(symbol);
        double inner = side / 2;
        g.draw(new Rectangle2D.Double(bounds.getX() + (side - inner) / 2,
                bounds.getY() + (side - inner) / 2, inner, inner));
        writeIn(bounds, g, label, START_EDGE, Horizontal.CENTRE, Vertical.BELOW);
    }

    /**
     * The stop of the process: the ground symbol of a circuit.
     * <p>
     * A vertical stem with three horizontal bars across it, getting shorter from the top one to the
     * bottom one, and the stem running past the last bar so that it reads as a terminal.
     */
    private static void paintEnd(Rectangle2D bounds, Graphics2D g, String label) {
        double side = Math.min(bounds.getWidth(), bounds.getHeight());
        double centre = bounds.getX() + side / 2;
        double top = bounds.getY();
        double bottom = top + side;
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(bounds.getX(), top, side, side));
        g.setColor(END_EDGE);
        g.setStroke(new BasicStroke((float) STROKE));
        g.draw(new Line2D.Double(centre, top, centre, bottom));

        double widest = side * 0.88;
        double barHeight = side / 4.0;
        double step = widest / 3.0;
        for (int i = 0; i < 3; i++) {
            double y = top + barHeight * i;
            double half = widest / 2 - step * i;
            g.draw(new Line2D.Double(centre - half, y, centre + half, y));
        }
        writeIn(bounds, g, label, END_EDGE, Horizontal.CENTRE, Vertical.BELOW);
    }

    /**
     * A transition: a short bar across the link.
     * <p>
     * The bar is what the flow passes through, and it is drawn across the link rather than along it,
     * so a link that goes on either side of it reads as one run through a wait. The comparison that
     * has to be true for the flow to carry on is written to the right of it, where it has room and
     * where nothing else is drawn.
     */
    private static void paintTransition(Rectangle2D bounds, Graphics2D g, String label) {
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        double left = bounds.getX();
        double middle = bounds.getCenterY();
        g.draw(new Line2D.Double(left, middle, left + SfcMetrics.TRANSITION_WIDTH, middle));
        if (label != null && !label.isEmpty()) {
            writeAt(g, label, left + SfcMetrics.TRANSITION_WIDTH + SfcMetrics.LABEL_GAP, middle,
                    Horizontal.LEFT, Vertical.MIDDLE);
        }
    }

    /**
     * The bar across the branches of a split or a join.
     * <p>
     * One line is a split where only one branch is taken, and two lines a short way apart are a
     * split where they all are. That is the whole difference between the two, so it is the whole
     * difference between the two symbols.
     * <p>
     * The branches leave the first of the two lines rather than the middle between them, so that the
     * line the branches run along is one of the two drawn and not a third one between them. Drawn
     * through the middle, the three of them make a box and the symbol stops reading as one.
     *
     * @param bounds  where the bar is
     * @param g       where to draw it onto
     * @param doubled whether the two branches run at once
     */
    static void paintSyncBar(Rectangle2D bounds, Graphics2D g, boolean doubled) {
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        double top = bounds.getY() + SfcMetrics.SYNC_BAR_HEIGHT / 2.0;
        g.draw(new Line2D.Double(bounds.getX(), top, bounds.getMaxX(), top));
        if (doubled) {
            double second = top + SfcMetrics.SYNC_BAR_HEIGHT;
            g.draw(new Line2D.Double(bounds.getX(), second, bounds.getMaxX(), second));
        }
    }

    /** Where a text sits in the shape it is written on. */
    private enum Horizontal {
        LEFT, CENTRE
    }

    /** Where a text sits in the shape it is written on. */
    private enum Vertical {
        MIDDLE, BELOW
    }

    /**
     * Writes a text on a shape, never outside it.
     * <p>
     * A name longer than its box is cut short with an ellipsis rather than drawn over the edge, where
     * it would run into whatever is next to it and be unreadable either way. The whole name is
     * always on the tooltip.
     * <p>
     * A label of more than one line is written as a block centred on the shape, so that a step can
     * say what it is called and what it works on without the box growing for it.
     */
    private static void writeIn(Rectangle2D bounds, Graphics2D g, String label, Color colour,
                                Horizontal horizontal, Vertical vertical) {
        java.util.List<String> lines = linesOf(label);
        if (lines.isEmpty()) {
            return;
        }
        FontMetrics metrics = g.getFontMetrics(chartFont());
        g.setColor(colour);
        if (lines.size() == 1) {
            String fitting = shorten(lines.get(0), (int) bounds.getWidth(), metrics);
            int width = metrics.stringWidth(fitting);
            double x = horizontal == Horizontal.CENTRE
                    ? bounds.getCenterX() - width / 2.0
                    : bounds.getX();
            double y = vertical == Vertical.MIDDLE
                    ? bounds.getCenterY() + (metrics.getAscent() - metrics.getDescent()) / 2.0
                    : bounds.getMaxY() + metrics.getAscent();
            g.drawString(fitting, (float) x, (float) y);
            return;
        }
        int line = metrics.getHeight();
        double first = bounds.getCenterY() - (lines.size() - 1) * line / 2.0
                + (metrics.getAscent() - metrics.getDescent()) / 2.0;
        for (int i = 0; i < lines.size(); i++) {
            String fitting = shorten(lines.get(i), (int) bounds.getWidth(), metrics);
            double x = horizontal == Horizontal.CENTRE
                    ? bounds.getCenterX() - metrics.stringWidth(fitting) / 2.0
                    : bounds.getX();
            g.drawString(fitting, (float) x, (float) (first + i * line));
        }
    }

    /**
     * Writes a text from a point, never going further right than the chart is wide.
     */
    private static void writeAt(Graphics2D g, String label, double x, double middle,
                                Horizontal horizontal, Vertical vertical) {
        FontMetrics metrics = g.getFontMetrics(chartFont());
        g.setColor(LABEL);
        double y = vertical == Vertical.MIDDLE
                ? middle + (metrics.getAscent() - metrics.getDescent()) / 2.0
                : middle;
        g.drawString(label, (float) x, (float) y);
    }

    /**
     * The text as it fits in a width, with an ellipsis when it does not.
     *
     * @param label   what it says
     * @param width   how much room there is
     * @param metrics the text it is written with
     * @return the whole text when it fits, or it cut short
     */
    private static String shorten(String label, int width, FontMetrics metrics) {
        if (metrics.stringWidth(label) <= width) {
            return label;
        }
        String ellipsis = "...";
        int room = width - metrics.stringWidth(ellipsis);
        if (room <= 0) {
            return "";
        }
        int cut = label.length();
        while (cut > 0 && metrics.stringWidth(label.substring(0, cut)) > room) {
            cut--;
        }
        return label.substring(0, cut) + ellipsis;
    }

    /**
     * The one font every text on the chart is written and measured with.
     *
     * <p>
     * A font of its own rather than the one the platform hands over, because a box has to be made wide
     * enough for its own text before anything is painted, and text measured with one font and written
     * with another is a box that is the wrong width for what it ends up saying.
     *
     * <p>
     * Made once and handed out, because it is asked for every box and every bar on every repaint and
     * a font is one of the more expensive things a drawing can build in a loop.
     *
     * @return the font, always the same one
     */
    static java.awt.Font chartFont() {
        return CHART_FONT;
    }

    /** The things that can appear on a recipe chart. */
    enum Shape {
        /** A step that does something on a piece of equipment. */
        BOX,
        /** The step the flow starts at, drawn as a square inside a square. */
        START,
        /** The step the flow stops at, drawn as the ground symbol. */
        END,
        /** What has to be true before the flow carries on, drawn as a bar across the link. */
        TRANSITION
    }
}