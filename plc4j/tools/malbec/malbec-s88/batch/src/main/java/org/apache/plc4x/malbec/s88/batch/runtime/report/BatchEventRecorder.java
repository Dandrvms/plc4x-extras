/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.malbec.s88.batch.runtime.report;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.plc4x.malbec.s88.batch.model.BatchEvent;
import org.apache.plc4x.malbec.s88.batch.model.EventType;

/** Writes the ordered events of one run to {@code events.csv}. */
public final class BatchEventRecorder implements AutoCloseable {

    private static final String[] HEADERS = {
        "seq", "wallTime", "simTime", "type", "source", "elementId", "state",
        "oldValue", "newValue", "details"
    };

    private final Writer writer;
    private final CSVPrinter printer;
    private final List<BatchEvent> events = new ArrayList<>();
    private final Set<Long> sequences = new LinkedHashSet<>();

    public BatchEventRecorder(RunFolder folder) throws IOException {
        this.writer = Files.newBufferedWriter(folder.eventsFile(), StandardCharsets.UTF_8);
        this.printer = new CSVPrinter(writer, CSVFormat.DEFAULT);
        printer.printRecord((Object[]) HEADERS);
        printer.flush();
    }

    /** Writes the event unless its sequence was already written. */
    public synchronized void record(BatchEvent event) throws IOException {
        if (event == null || !sequences.add(event.seq())) {
            return;
        }
        printer.printRecord(event.seq(), event.wallTime(), event.simTime(), event.type().name(),
                event.source(), event.elementId(), event.state(), event.oldValue(),
                event.newValue(), event.details());
        printer.flush();
        events.add(event);
    }

    public synchronized void recordAll(Collection<BatchEvent> newEvents) throws IOException {
        if (newEvents == null) {
            return;
        }
        for (BatchEvent event : newEvents) {
            record(event);
        }
    }

    /** @return the events in write order */
    public synchronized List<BatchEvent> events() {
        return List.copyOf(events);
    }

    /** Reads an events file, filling in the batch and run that the CSV does not store. */
    public static List<BatchEvent> read(Path file, String batchId, int run) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .setSkipHeaderRecord(true)
                .build();
        List<BatchEvent> events = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)) {
            for (CSVRecord row : parser) {
                events.add(new BatchEvent(
                        Long.parseLong(row.get("seq")),
                        batchId,
                        run,
                        Instant.parse(row.get("wallTime")),
                        Double.parseDouble(row.get("simTime")),
                        EventType.valueOf(row.get("type")),
                        emptyToNull(row.get("source")),
                        emptyToNull(row.get("elementId")),
                        emptyToNull(row.get("state")),
                        emptyToNull(row.get("oldValue")),
                        emptyToNull(row.get("newValue")),
                        emptyToNull(row.get("details"))));
            }
        }
        return List.copyOf(events);
    }

    @Override
    public synchronized void close() throws IOException {
        printer.close();
        writer.close();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
