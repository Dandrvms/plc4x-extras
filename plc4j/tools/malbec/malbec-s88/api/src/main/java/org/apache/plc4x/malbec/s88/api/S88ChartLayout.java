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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where the things on a chart are drawn.
 * <p>
 * This is presentation and nothing else. A recipe that carries no layout describes the same process
 * as one that does, and the same process can be run from either, so a layout is never required and a
 * chart that cannot be laid out yet is not a chart that is wrong.
 * <p>
 * One entry per box or bar, keyed by the name that thing is known by on its own chart. A box and a
 * bar never share a name on one chart, so the name is enough to say which is meant without also
 * recording which kind it is.
 * <p>
 * <b>Where it is kept.</b> The recipe format gives no room on a chart, a box or a bar for where it
 * sits, and no free-form entry on a chart either. It is therefore kept on whatever owns the chart:
 * the recipe for the recipe's own chart, and the step for the chart of a step. Both of those do have
 * a free-form entry, and both are saved, so a drawing survives being written and read again.
 * <p>
 * <b>How it is written.</b> One line per node, as {@code id|x|y}, inside the free-form entry with the
 * name {@code Layout}. It is read back the same way, and a line that does not parse is left out
 * rather than allowed to fail the load of a recipe whose process is intact.
 */
public final class S88ChartLayout {

    /** What stands between the three parts of a written position. */
    private static final char SEPARATOR = '|';

    private final Map<String, S88Position> positions = new LinkedHashMap<>();

    public S88ChartLayout() {
    }

    /**
     * A layout read from written text.
     *
     * @param lines one line per node, as written; a line that does not parse is skipped
     */
    public static S88ChartLayout of(Collection<String> lines) {
        S88ChartLayout layout = new S88ChartLayout();
        if (lines == null) {
            return layout;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String id = idOf(line);
            if (id != null) {
                parse(line).ifPresent(position -> layout.place(id, position));
            }
        }
        return layout;
    }

    /**
     * A layout read out of the free-form entries of whoever owns the chart.
     *
     * @param entries entries to look in, may be {@code null}
     * @return the layout, empty when there is none, never {@code null}
     */
    public static S88ChartLayout readFrom(List<S88OtherInformation> entries) {
        S88OtherInformation info =
                S88OtherInformation.find(entries, S88OtherInformation.LAYOUT);
        if (info == null) {
            return new S88ChartLayout();
        }
        List<String> lines = new ArrayList<>();
        for (S88ParameterValue value : info.getValues()) {
            lines.addAll(value.getValueStrings());
        }
        return of(lines);
    }

    /**
     * The free-form entry that carries a layout.
     * <p>
     * An empty layout is not carried at all. A chart that has not been drawn on has nothing to say
     * about itself, and saying so with an empty entry would only leave something to be cleaned up.
     *
     * @param layout where things are drawn, may be {@code null}
     * @return the entry to write, or {@code null} when there is nothing to write
     */
    public static S88OtherInformation entryFor(S88ChartLayout layout) {
        if (layout == null || layout.isEmpty()) {
            return null;
        }
        S88ParameterValue value = new S88ParameterValue();
        layout.toLines().forEach(value::addValueString);
        value.setDataInterpretation(S88DataInterpretation.CONSTANT);
        S88OtherInformation info = new S88OtherInformation(S88OtherInformation.LAYOUT);
        info.addValue(value);
        return info;
    }

    /**
     * Where a thing is drawn, when it has been placed.
     *
     * @param id name of the box or bar
     * @return where it is, or empty when it has not been placed
     */
    public Optional<S88Position> positionOf(String id) {
        return id != null ? Optional.ofNullable(positions.get(id)) : Optional.empty();
    }

    /**
     * Puts a box or a bar somewhere.
     * <p>
     * Placing the same node twice moves it, which is what dragging a box across a chart does.
     *
     * @param id       name of the box or bar
     * @param position where it goes
     */
    public void place(String id, S88Position position) {
        if (id != null && !id.isBlank() && position != null) {
            positions.put(id, position);
        }
    }

    /**
     * Puts a box or a bar somewhere.
     *
     * @param id name of the box or bar
     * @param x  how far across
     * @param y  how far down
     */
    public void place(String id, double x, double y) {
        place(id, new S88Position(x, y));
    }

    /** Forgets where a box or a bar was, which is what removing it from the chart means. */
    public void forget(String id) {
        if (id != null) {
            positions.remove(id);
        }
    }

    /** Whether anything has been placed at all. */
    public boolean isEmpty() {
        return positions.isEmpty();
    }

    /** How many things have been placed. */
    public int size() {
        return positions.size();
    }

    /** The name of every thing that has been placed, in the order they were placed. */
    public List<String> placedIds() {
        return List.copyOf(positions.keySet());
    }

    /** The layout as it would be written, one line per node. */
    public List<String> toLines() {
        List<String> lines = new ArrayList<>(positions.size());
        for (Map.Entry<String, S88Position> entry : positions.entrySet()) {
            lines.add(entry.getKey() + SEPARATOR
                    + number(entry.getValue().x()) + SEPARATOR
                    + number(entry.getValue().y()));
        }
        return lines;
    }

    /** The name a written position is for, or {@code null} when the line does not carry one. */
    private static String idOf(String line) {        int end = line.indexOf(SEPARATOR);
        if (end <= 0) {
            return null;
        }
        String id = line.substring(0, end);
        return id.isBlank() ? null : id;
    }

    private static Optional<S88Position> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String[] parts = line.split("\\" + SEPARATOR);
        if (parts.length != 3 || parts[0].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new S88Position(
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())));
        } catch (NumberFormatException e) {
           return Optional.empty();
        }
    }

    private static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    /**
     * Two layouts are the same when they place the same things in the same places, whatever order
     * they were placed in. The order is how the chart was drawn and not what it says, so it is not
     * part of what makes two layouts equal.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof S88ChartLayout that && positions.equals(that.positions);
    }

    @Override
    public int hashCode() {
        return positions.hashCode();
    }

    @Override
    public String toString() {
        return "S88ChartLayout" + positions;
    }
}
