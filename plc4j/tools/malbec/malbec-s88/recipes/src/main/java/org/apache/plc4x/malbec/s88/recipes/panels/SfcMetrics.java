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

/**
 * How big each thing on a chart is and how far apart they sit.
 * <p>
 * One place for every measurement, because the layout and the drawing have to agree on them: a box
 * the layout measured as one size and the drawing drew at another is a chart whose lines do not
 * reach it.
 */
final class SfcMetrics {

    /** A step that does something on a piece of equipment. */
    static final int STEP_WIDTH = 120;
    static final int STEP_HEIGHT = 46;

    /** The start and the stop are a square inside a square, so they are narrower than a step. */
    static final int TERMINAL_WIDTH = 40;
    static final int TERMINAL_HEIGHT = 40;

    /** A transition is a short bar across the link, not a box. */
    static final int TRANSITION_WIDTH = 28;
    static final int TRANSITION_HEIGHT = 6;

    /** What a bar has to be wide and tall enough to be picked with the pointer. */
    static final int TRANSITION_HIT_WIDTH = 40;
    static final int TRANSITION_HIT_HEIGHT = 20;

    /** Room between the bar and the condition written beside it. */
    static final int LABEL_GAP = 10;

        /** The bar that joins the branches of a split or a join. */
    static final int SYNC_BAR_WIDTH = 28;
    static final int SYNC_BAR_HEIGHT = 6;

    /** Distance from one row of the chart to the next. */
    static final int ROW = 70;

    /** Least room between two things side by side, so that nothing touches. */
    static final int GAP = 60;

    /** Left and top of the chart. */
    static final int MARGIN = 30;

    /** How far to the side a link that goes back up the chart is taken. */
    static final int BACK_EDGE_LANE = 40;

    private SfcMetrics() {
    }

    /**
     * @param shape what is being drawn
     * @return how wide it is
     */
    static int widthOf(RecipeShapes.Shape shape) {
        switch (shape) {
            case START:
            case END:
                return TERMINAL_WIDTH;
            case TRANSITION:
                return TRANSITION_WIDTH;
            default:
                return STEP_WIDTH;
        }
    }

        /**
     * @param shape what is being drawn
     * @return how tall it is
     */
    static int heightOf(RecipeShapes.Shape shape) {
        switch (shape) {
            case START:
            case END:
                return TERMINAL_HEIGHT;
            case TRANSITION:
                return TRANSITION_HIT_HEIGHT;
            default:
                return STEP_HEIGHT;
        }
    }

    /**
     * How wide a transition has to be for its bar and the condition beside it.
     * <p>
     * The condition is measured rather than guessed at, because a bar narrower than its own text
     * clips the text, and a bar wider than it needs to be pushes the branches apart for nothing.
     *
     * @param labelWidth how wide the condition is, 0 when the bar waits on nothing
     * @return the width, never less than what the bar itself needs
     */
    static int transitionWidth(int labelWidth) {
        return Math.max(TRANSITION_HIT_WIDTH,
                TRANSITION_WIDTH + (labelWidth > 0 ? LABEL_GAP + labelWidth : 0));
    }
}
