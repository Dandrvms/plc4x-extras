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

import java.awt.Point;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.widget.Widget;

/**
 * One end of a line, at a point the layout decided.
 * <p>
 * The library's own anchors work out where an end is from the widget they are on, and offer a
 * rectangular one, a circular one and two that pick the side nearest the other end. None of those is
 * what a sequential chart wants: where a line leaves a box is fixed once the layout has said where
 * the boxes are, and it is the middle of the bottom of one box or the middle of the top of another
 * whichever is being drawn.
 */
final class SfcAnchor extends Anchor {

    private final Point where;
    private final Direction direction;

    /**
     * @param scene     the chart the point is in the coordinates of
     * @param where     the point itself
     * @param direction which way the line leaves this end, which is what puts the arrow on the end
     *                  that arrives
     */
    SfcAnchor(Widget scene, Point where, Direction direction) {
        super(scene);
        this.where = new Point(where);
        this.direction = direction;
    }

    @Override
    public Result compute(Entry entry) {
        return new Result(new Point(where), direction);
    }
}
