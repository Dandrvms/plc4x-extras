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

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import org.netbeans.api.visual.widget.Widget;

/**
 * The bar across the branches of a split or a join.
 * <p>
 * Not a node of the chart: nothing picks it and nothing joins to it. It is the mark that says a line
 * with more than one end at it is a split rather than two lines that happen to meet.
 */
final class SyncBarWidget extends Widget {

    private final RecipeChartScene chart;
    private final boolean doubled;

    /**
     * @param chart   the chart it is drawn on
     * @param doubled whether the two branches run at once, which is what makes it two bars
     */
    SyncBarWidget(RecipeChartScene chart, boolean doubled) {
        super(chart);
        this.chart = chart;
        this.doubled = doubled;
    }

    @Override
    protected void paintWidget() {
        Graphics2D g = getGraphics();
        if (g == null) {
            return;
        }
        java.awt.Rectangle bounds = getBounds();
        RecipeShapes.paintSyncBar(
                new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height), g, doubled);
    }
}