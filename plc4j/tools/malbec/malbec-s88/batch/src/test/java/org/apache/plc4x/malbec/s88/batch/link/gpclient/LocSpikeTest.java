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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.epics.gpclient.GPClientConfiguration;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PV;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVReader;
import org.epics.gpclient.datasource.ChannelHandler;
import org.epics.gpclient.datasource.CompositeDataSource;
import org.epics.gpclient.datasource.DataSource;
import org.epics.gpclient.datasource.DataSourceProvider;
import org.epics.vtype.Alarm;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VString;
import org.epics.vtype.VType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LocSpikeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final List<GPClientInstance> clients = new ArrayList<>();
    private final List<ScheduledExecutorService> pools = new ArrayList<>();

    @AfterEach
    void closeClients() {
        for (GPClientInstance client : clients) {
            client.close();
        }
        for (ScheduledExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    @Test
    void localChannelReceivesInitialValueAndWrites() throws Exception {
        GPClientInstance client = newClient();
        BlockingQueue<VType> values = new LinkedBlockingQueue<>();
        BlockingQueue<PVEvent.Type> writeEvents = new LinkedBlockingQueue<>();
        AtomicLong firstValueNanos = new AtomicLong(-1);

        long startedAt = System.nanoTime();
        PV<VType, Object> pv = client.readAndWrite("loc://t1<VString>(\"~INIT\")")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        firstValueNanos.compareAndSet(-1, System.nanoTime());
                        values.add(reader.getValue());
                    }
                })
                .addWriteListener((event, writer) -> event.getType().forEach(writeEvents::add))
                .start();

        try {
            VString initial = assertInstanceOf(VString.class, await(values));
            assertEquals("~INIT", initial.getValue());
            assertTrue(firstValueNanos.get() > 0);
            System.out.printf("LocSpike first read: %d ms%n",
                    TimeUnit.NANOSECONDS.toMillis(firstValueNanos.get() - startedAt));
            awaitTrue(pv::isWriteConnected, "The local writer was not connected");

            pv.write("~RUNNING");

            VString running = assertInstanceOf(VString.class, await(values));
            assertEquals("~RUNNING", running.getValue());
            assertTrue(awaitEvent(writeEvents, PVEvent.Type.WRITE_SUCCEEDED),
                    "The local writer did not report WRITE_SUCCEEDED");
        } finally {
            pv.close();
        }
    }

    @Test
    void channelWithoutInitialValueStartsNull() throws Exception {
        GPClientInstance client = newClient();
        BlockingQueue<VType> values = new LinkedBlockingQueue<>();

        PV<VType, Object> pv = client.readAndWrite("loc://null<VString>")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        values.add(reader.getValue());
                    }
                })
                .start();

        try {
            assertNull(pv.getValue(), "A local channel without initial value must start as null");
            awaitTrue(pv::isWriteConnected, "The local writer was not connected");

            pv.write("value");

            VString value = assertInstanceOf(VString.class, await(values));
            assertEquals("value", value.getValue());
        } finally {
            pv.close();
        }
    }

    @Test
    void doubleChannelReceivesVDouble() throws Exception {
        GPClientInstance client = newClient();
        BlockingQueue<VType> values = new LinkedBlockingQueue<>();

        PV<VType, Object> pv = client.readAndWrite("loc://t2<VDouble>(1.0)")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        values.add(reader.getValue());
                    }
                })
                .start();

        try {
            VDouble initial = assertInstanceOf(VDouble.class, await(values));
            assertEquals(1.0, initial.getValue(), 0.0);
            awaitTrue(pv::isWriteConnected, "The local writer was not connected");

            pv.write(VDouble.of(2.5, Alarm.none(), Time.now(), Display.none()));

            VDouble written = assertInstanceOf(VDouble.class, await(values));
            assertEquals(2.5, written.getValue(), 0.0);
        } finally {
            pv.close();
        }
    }

    @Test
    void closingLastSubscriptionDiscardsValue() throws Exception {
        GPClientInstance client = newClient();
        BlockingQueue<VType> values = new LinkedBlockingQueue<>();
        String channel = "loc://close<VString>(\"~INIT\")";

        PVReader<VType> first = client.read(channel)
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        values.add(reader.getValue());
                    }
                })
                .start();
        try {
            VString initial = assertInstanceOf(VString.class, await(values));
            assertEquals("~INIT", initial.getValue());
        } finally {
            first.close();
        }

        awaitTrue(() -> !hasSubscriptions(client, "close"),
                "The local channel still had subscriptions after close");

        BlockingQueue<VType> reopenedValues = new LinkedBlockingQueue<>();
        PVReader<VType> reopened = client.read("loc://close<VString>")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        reopenedValues.add(reader.getValue());
                    }
                })
                .start();
        try {
            assertNull(reopened.getValue(),
                    "Closing the last subscription must discard the local channel value");
            assertNull(reopenedValues.poll(200, TimeUnit.MILLISECONDS),
                    "The reopened channel received the discarded value");
        } finally {
            reopened.close();
        }
    }

    @Test
    void maxRateDropsIntermediateValues() throws Exception {
        GPClientInstance client = newClient();
        BlockingQueue<VDouble> values = new LinkedBlockingQueue<>();

        PV<VType, Object> pv = client.readAndWrite("loc://burst<VDouble>(0.0)")
                .maxRate(Duration.ofMillis(20))
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        values.add((VDouble) reader.getValue());
                    }
                })
                .start();

        try {
            assertEquals(0.0, await(values).getValue(), 0.0);
            awaitTrue(pv::isWriteConnected, "The local writer was not connected");

            long startedAt = System.nanoTime();
            for (int i = 1; i <= 100; i++) {
                pv.write(VDouble.of((double) i, Alarm.none(), Time.now(), Display.none()));
            }

            int notifications = 0;
            VDouble last = null;
            while (last == null || last.getValue() != 100.0) {
                last = await(values);
                notifications++;
            }
            System.out.printf("LocSpike maxRate burst: %d notifications for 100 writes in %d ms%n",
                    notifications, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        } finally {
            pv.close();
        }
    }

    @Test
    void instancesDoNotShareChannels() throws Exception {
        GPClientInstance first = newClient();
        GPClientInstance second = newClient();
        BlockingQueue<VType> firstValues = new LinkedBlockingQueue<>();
        BlockingQueue<VType> secondValues = new LinkedBlockingQueue<>();

        PV<VType, Object> firstPv = first.readAndWrite("loc://isolated<VDouble>(1.0)")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        firstValues.add(reader.getValue());
                    }
                })
                .start();
        PV<VType, Object> secondPv = second.readAndWrite("loc://isolated<VDouble>(2.0)")
                .addReadListener((event, reader) -> {
                    if (event.isType(PVEvent.Type.VALUE)) {
                        secondValues.add(reader.getValue());
                    }
                })
                .start();

        try {
            assertEquals(1.0, assertInstanceOf(VDouble.class, await(firstValues)).getValue(), 0.0);
            assertEquals(2.0, assertInstanceOf(VDouble.class, await(secondValues)).getValue(), 0.0);
            awaitTrue(firstPv::isWriteConnected, "The first local writer was not connected");

            firstPv.write(VDouble.of(3.0, Alarm.none(), Time.now(), Display.none()));

            assertEquals(3.0, assertInstanceOf(VDouble.class, await(firstValues)).getValue(), 0.0);
            VDouble secondValue = assertInstanceOf(VDouble.class, secondPv.getValue());
            assertEquals(2.0, secondValue.getValue(), 0.0);
        } finally {
            firstPv.close();
            secondPv.close();
        }
    }

    @Test
    void pvaDataSourceCanBeInstantiated() throws Exception {
        Class<?> type = Class.forName("org.epics.gpclient.datasource.pva.PVADataSource");
        assertTrue(DataSource.class.isAssignableFrom(type));

        DataSource dataSource = (DataSource) type.getDeclaredConstructor().newInstance();
        try {
            assertNotNull(dataSource);
        } finally {
            dataSource.close();
        }
    }

    private GPClientInstance newClient() {
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        pools.add(pool);
        GPClientInstance client = new GPClientConfiguration()
                .dataSource(DataSourceProvider.createDataSource())
                .notificationExecutor(Runnable::run)
                .dataProcessingThreadPool(pool)
                .defaultMaxRate(Duration.ofMillis(20))
                .build();
        clients.add(client);
        return client;
    }

    private static <T> T await(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(value, "No value within " + TIMEOUT);
        return value;
    }

    private static boolean awaitEvent(BlockingQueue<PVEvent.Type> events, PVEvent.Type expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            long remaining = Math.max(1,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            PVEvent.Type event = events.poll(remaining, TimeUnit.MILLISECONDS);
            if (event == null) {
                return false;
            }
            if (event == expected) {
                return true;
            }
        }
        return false;
    }

    private static void awaitTrue(BooleanSupplier condition, String message)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail(message);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private static boolean hasSubscriptions(GPClientInstance client, String channelName) {
        DataSource dataSource = client.getDefaultDataSource();
        if (!(dataSource instanceof CompositeDataSource composite)) {
            return false;
        }
        ChannelHandler handler = composite.getChannels().get(channelName);
        return handler != null && handler.getUsageCounter() > 0;
    }
}
