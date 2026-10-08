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
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.visual.router.Router;
import org.netbeans.api.visual.widget.ConnectionWidget;

/**
 * Draws one line through the points the layout worked out for it.
 * <p>
 * The library offers a straight line between the two ends, and a router that searches round the
 * other widgets for a path. Neither is what a sequential chart is: the lines are vertical, they go
 * round the side when they loop back, and they bend where a split or a join has to be met halfway.
 * All of that is decided by the layout, so all this has to do is hand the points over.
 */
final class SfcRouter implements Router {

    private static final long serialVersionUID = 1L;

    private final List<Point> path = new ArrayList<>();

    /**
     * @param points the points of one line, first where it leaves and last where it arrives
     */
    SfcRouter(List<Point> points) {
        path.addAll(points);
    }

    @Override
    public List<Point> routeConnection(ConnectionWidget widget) {
        return new ArrayList<>(path);
    }
}
