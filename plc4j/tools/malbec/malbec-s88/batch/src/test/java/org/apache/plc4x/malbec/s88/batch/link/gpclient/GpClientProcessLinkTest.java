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
package org.apache.plc4x.malbec.s88.batch.link.gpclient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;
import org.epics.gpclient.GPClientInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GpClientProcessLinkTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final List<GpClientProcessLink> links = new ArrayList<>();
    private final List<GPClientInstance> clients = new ArrayList<>();
    private final List<GpClientLinkConfig> configs = new ArrayList<>();

    @AfterEach
    void closeLinks() {
        for (GpClientProcessLink link : links) {
            link.close();
        }
        for (GPClientInstance client : clients) {
            client.close();
        }
        for (GpClientLinkConfig config : configs) {
            config.close();
        }
    }

    @Test
    void readsAndWritesNumbersThroughLoc() throws Exception {
        GpClientProcessLink link = newLink("numbers", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.connect();
        link.subscribe("SETPOINT", DataType.REAL, updates::add);

        WriteResult result = link.write("SETPOINT", DataType.REAL, 72.0)
            .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertTrue(result.success(), result.error());
        VariableUpdate update = await(updates, value -> value.quality() == Quality.GOOD && value.value().equals(72.0));

        assertEquals("SETPOINT", update.name());
        assertNotNull(update.sourceTime());
        awaitTrue(() -> link.status() == LinkStatus.CONNECTED, "The link did not reach CONNECTED");
    }

    @Test
    void stringsAreNotConvertedToNumbers() throws Exception {
        GpClientProcessLink link = newLink("strings", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.connect();
        link.subscribe("STATE", DataType.STRING, updates::add);

        assertTrue(link.write("STATE", DataType.STRING, "72").get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        VariableUpdate update = await(updates, value -> value.quality() == Quality.GOOD);

        assertEquals("72", update.value());
    }

    @Test
    void staleAfterMarksTheLastValueStale() throws Exception {
        GpClientProcessLink link = newLink("stale", Duration.ofMillis(150));
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.connect();
        link.subscribe("VALUE", DataType.REAL, updates::add);

        assertTrue(link.write("VALUE", DataType.REAL, 1.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        await(updates, update -> update.quality() == Quality.GOOD);
        VariableUpdate stale = await(updates, update -> update.quality() == Quality.STALE);

        assertEquals(1.0, stale.value());
        awaitTrue(() -> link.status() == LinkStatus.DEGRADED, "The link did not reach DEGRADED");
    }

    @Test
    void statusGoesFromConnectingToConnected() throws Exception {
        GpClientProcessLink link = newLink("status", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        List<LinkStatus> statuses = new CopyOnWriteArrayList<>();
        link.addStatusListener(statuses::add);
        link.connect();

        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.subscribe("VALUE", DataType.REAL, updates::add);
        assertTrue(link.write("VALUE", DataType.REAL, 1.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        await(updates, update -> update.quality() == Quality.GOOD);
        awaitTrue(() -> link.status() == LinkStatus.CONNECTED, "The link did not reach CONNECTED");

        assertTrue(statuses.contains(LinkStatus.CONNECTING), statuses.toString());
        awaitTrue(() -> statuses.getLast() == LinkStatus.CONNECTED, statuses.toString());
    }

    @Test
    void instancesWithTheSameSessionDoNotShareValues() throws Exception {
        GpClientProcessLink first = newLink("same", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        GpClientProcessLink second = newLink("same", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        BlockingQueue<VariableUpdate> firstUpdates = new LinkedBlockingQueue<>();
        BlockingQueue<VariableUpdate> secondUpdates = new LinkedBlockingQueue<>();
        first.connect();
        second.connect();
        first.subscribe("VALUE", DataType.REAL, firstUpdates::add);
        second.subscribe("VALUE", DataType.REAL, secondUpdates::add);

        assertTrue(first.write("VALUE", DataType.REAL, 1.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        await(firstUpdates, update -> update.quality() == Quality.GOOD && update.value().equals(1.0));
        assertFalse(hasGoodValue(secondUpdates, 1.0, 200), "The second link received the first value");

        assertTrue(second.write("VALUE", DataType.REAL, 2.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        await(secondUpdates, update -> update.quality() == Quality.GOOD && update.value().equals(2.0));
    }

    @Test
    void closeReleasesTheLinkThreads() throws Exception {
        int before = countThreads("malbec-link-stale") + countThreads("malbec-gpclient");
        GpClientProcessLink link = newLink("close", GpClientLinkConfig.DEFAULT_STALE_AFTER);
        link.connect();
        BlockingQueue<VariableUpdate> updates = new LinkedBlockingQueue<>();
        link.subscribe("VALUE", DataType.REAL, updates::add);
        assertTrue(link.write("VALUE", DataType.REAL, 1.0).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).success());
        await(updates, update -> update.quality() == Quality.GOOD);

        link.close();
        clients.getLast().close();
        configs.getLast().close();
        awaitTrue(() -> countThreads("malbec-link-stale") + countThreads("malbec-gpclient") <= before,
            "Link threads survived close");
    }

    private GpClientProcessLink newLink(String sessionId, Duration staleAfter) {
        GpClientLinkConfig config = new GpClientLinkConfig(GpClientLinkConfig.DEFAULT_PREFIX, Runnable::run,
            Duration.ofMillis(10), TIMEOUT, staleAfter, sessionId);
        GPClientInstance client = config.newClient();
        GpClientProcessLink link = config.newLink(client);
        configs.add(config);
        clients.add(client);
        links.add(link);
        return link;
    }

    private static VariableUpdate await(BlockingQueue<VariableUpdate> updates, Predicate<VariableUpdate> predicate)
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

    private static boolean hasGoodValue(BlockingQueue<VariableUpdate> updates, Object value, long millis)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            VariableUpdate update = updates.poll(remaining, TimeUnit.MILLISECONDS);
            if (update != null && update.quality() == Quality.GOOD && value.equals(update.value())) {
                return true;
            }
        }
        return false;
    }

    private static void awaitTrue(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail(message);
            }
            Thread.sleep(1);
        }
    }

    private static int countThreads(String namePrefix) {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        Thread.getAllStackTraces().keySet().forEach(thread -> {
            if (thread.isAlive() && thread.getName().startsWith(namePrefix)) {
                count.updateAndGet(value -> value + 1);
            }
        });
        return count.get();
    }
}
