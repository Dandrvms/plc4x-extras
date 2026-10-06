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

import java.util.Objects;

/**
 * Where one thing on a chart is drawn.
 * <p>
 * The unit is whatever the drawing tool works in, and this says nothing about it. A chart that was
 * drawn on one screen and opened on another has to be laid out again either way, so the numbers are
 * only ever compared with other numbers from the same drawing.
 * <p>
 * Two numbers and nothing else, because that is all a position is. Anything more would be a
 * description of the shape being drawn, and the shape is the recipe's to say, not the position's.
 */
public final class S88Position {

    private final double x;
    private final double y;

    public S88Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** How far across it is. */
    public double x() {
        return x;
    }

    /** How far down it is. */
    public double y() {
        return y;
    }

    /**
     * A copy moved by the given amounts.
     * <p>
     * Offsets rather than a new set of absolute numbers, because a chart is dragged by moving one
     * box and the lines into it have to follow, and every line that ran into the box knows its own
     * end rather than knowing where the box went.
     *
     * @param dx how far across to move
     * @param dy how far down to move
     * @return the position after moving
     */
    public S88Position moved(double dx, double dy) {
        return new S88Position(x + dx, y + dy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof S88Position that)) {
            return false;
        }
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "S88Position[x=" + x + ", y=" + y + "]";
    }
}
