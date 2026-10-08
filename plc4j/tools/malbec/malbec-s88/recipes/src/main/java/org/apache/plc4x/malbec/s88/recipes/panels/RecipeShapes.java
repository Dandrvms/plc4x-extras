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
    private static final Color END_EDGE = new Color(0x8B, 0x1A, 0x1A);
    private static final double STROKE = 1.4;

    /**
     * How thick a line and a bar are drawn.
     * <p>
     * The library draws its connections at this width, and a bar drawn at a different one reads as a
     * mark stuck on the end of a line rather than as part of it.
     */
    static final float LINE_WIDTH = 1.5f;

    /** What every label on the chart is written at. */
    private static final float LABEL_SIZE = 11f;

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
     * @param label the text
     * @return its width, 0 when there is no text
     */
    static int textWidth(Graphics2D g, String label) {
        if (label == null || label.isEmpty()) {
            return 0;
        }
        return g.getFontMetrics(chartFont(g)).stringWidth(label);
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
        writeIn(bounds, g, label, EDGE, Horizontal.CENTRE, Vertical.MIDDLE);
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
     */
    private static void writeIn(Rectangle2D bounds, Graphics2D g, String label, Color colour,
                                Horizontal horizontal, Vertical vertical) {
        if (label == null || label.isEmpty()) {
            return;
        }
        FontMetrics metrics = g.getFontMetrics(chartFont(g));
        String fitting = shorten(label, (int) bounds.getWidth(), metrics);
        int width = metrics.stringWidth(fitting);
        double x = horizontal == Horizontal.CENTRE
                ? bounds.getCenterX() - width / 2.0
                : bounds.getX();
        double y = vertical == Vertical.MIDDLE
                ? bounds.getCenterY() + (metrics.getAscent() - metrics.getDescent()) / 2.0
                : bounds.getMaxY() + metrics.getAscent();
        g.setColor(colour);
        g.drawString(fitting, (float) x, (float) y);
    }

    /**
     * Writes a text from a point, never going further right than the chart is wide.
     */
    private static void writeAt(Graphics2D g, String label, double x, double middle,
                                Horizontal horizontal, Vertical vertical) {
        FontMetrics metrics = g.getFontMetrics(chartFont(g));
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

    private static java.awt.Font chartFont(Graphics2D g) {
        return g.getFont().deriveFont(LABEL_SIZE);
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