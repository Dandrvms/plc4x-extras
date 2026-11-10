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
package org.apache.plc4x.malbec.s88.batch.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FaultInjectingProcessLinkTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final FakeProcessLink delegate = new FakeProcessLink();
    private final FaultInjectingProcessLink link = new FaultInjectingProcessLink(delegate);

    @AfterEach
    void closeLink() {
        link.close();
    }

    @Test
    void linkLossDropsUpdatesAndEmitsBadQuality() throws Exception {
        link.connect();
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.subscribe("VALUE", DataType.REAL, updates::add);
        delegate.emit("VALUE", 1.0);
        await(updates, update -> update.quality() == Quality.GOOD && update.value().equals(1.0));

        link.setUp(false);
        assertEquals(LinkStatus.DISCONNECTED, link.status());
        await(updates, update -> update.quality() == Quality.BAD);
        assertFalse(link.write("VALUE", DataType.REAL, 2.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());

        delegate.emit("VALUE", 3.0);
        assertNull(updates.poll(200, TimeUnit.MILLISECONDS), "An update arrived while the link was down");
    }

    @Test
    void restoringTheLinkSubscribesAgainToTheCurrentValue() throws Exception {
        link.connect();
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.subscribe("VALUE", DataType.REAL, updates::add);
        delegate.emit("VALUE", 1.0);
        await(updates, update -> update.quality() == Quality.GOOD && update.value().equals(1.0));

        link.setUp(false);
        delegate.emit("VALUE", 2.0);
        link.setUp(true);

        await(updates, update -> update.quality() == Quality.GOOD && update.value().equals(2.0));
        assertEquals(LinkStatus.CONNECTED, link.status());
    }

    @Test
    void latencyDelaysUpdates() throws Exception {
        link.connect();
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.subscribe("VALUE", DataType.REAL, updates::add);
        link.setLatency(Duration.ofMillis(100));

        delegate.emit("VALUE", 4.0);

        assertNull(updates.poll(20, TimeUnit.MILLISECONDS), "The update did not respect the latency");
        await(updates, update -> update.quality() == Quality.GOOD && update.value().equals(4.0));
    }

    @Test
    void writesCanBeDiscarded() throws Exception {
        link.connect();
        link.setDropWrites(true);
        int before = delegate.writeCount.get();

        WriteResult result = link.write("VALUE", DataType.REAL, 5.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertFalse(result.success());
        assertEquals("Write dropped", result.error());
        assertEquals(before, delegate.writeCount.get());
    }

    private static VariableUpdate await(BlockingQueue<VariableUpdate> updates,
                                        java.util.function.Predicate<VariableUpdate> predicate)
        throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            VariableUpdate update = updates.poll(remaining, TimeUnit.MILLISECONDS);
            if (update != null && predicate.test(update)) {
                return update;
            }
        }
        fail("Expected update did not arrive within " + TIMEOUT);
        return null;
    }

    private static final class FakeProcessLink implements ProcessLink {

        private final List<Consumer<VariableUpdate>> subscribers = new CopyOnWriteArrayList<>();
        private final List<Consumer<LinkStatus>> statusListeners = new CopyOnWriteArrayList<>();
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final AtomicInteger writeCount = new AtomicInteger();
        private LinkStatus status = LinkStatus.DISCONNECTED;

        @Override
        public void connect() {
            setStatus(LinkStatus.CONNECTED);
        }

        @Override
        public LinkStatus status() {
            return status;
        }

        @Override
        public void addStatusListener(Consumer<LinkStatus> listener) {
            statusListeners.add(listener);
            listener.accept(status);
        }

        @Override
        public AutoCloseable subscribe(String name, DataType type, Consumer<VariableUpdate> listener) {
            subscribers.add(listener);
            Object value = values.get(name);
            if (value != null) {
                listener.accept(VariableUpdate.good(name, value, Instant.now()));
            }
            return () -> subscribers.remove(listener);
        }

        @Override
        public void read(String name, DataType type, Consumer<VariableUpdate> once) {
            Object value = values.get(name);
            if (value != null) {
                once.accept(VariableUpdate.good(name, value, Instant.now()));
            }
        }

        @Override
        public CompletableFuture<WriteResult> write(String name, DataType type, Object value) {
            writeCount.incrementAndGet();
            values.put(name, value);
            emit(name, value);
            return CompletableFuture.completedFuture(WriteResult.ok(name));
        }

        @Override
        public void close() {
            setStatus(LinkStatus.DISCONNECTED);
        }

        private void emit(String name, Object value) {
            values.put(name, value);
            for (Consumer<VariableUpdate> subscriber : subscribers) {
                subscriber.accept(VariableUpdate.good(name, value, Instant.now()));
            }
        }

        private void setStatus(LinkStatus next) {
            status = next;
            for (Consumer<LinkStatus> listener : statusListeners) {
                listener.accept(next);
            }
        }
    }
}
