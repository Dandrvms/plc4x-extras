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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.link.ChannelNamer;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.ValueCodec;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.PV;
import org.epics.gpclient.PVEvent;
import org.epics.gpclient.PVReader;
import org.epics.vtype.Alarm;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;
import org.epics.vtype.VEnum;
import org.epics.vtype.VNumber;
import org.epics.vtype.VString;
import org.epics.vtype.VType;

/**
 * A {@link ProcessLink} over GPClient channels.
 */
public final class GpClientProcessLink implements ProcessLink {

    private final GPClientInstance client;
    private final String prefix;
    private final java.util.concurrent.Executor notifyOn;
    private final Duration maxRate;
    private final Duration connectionTimeout;
    private final Duration staleAfter;
    private final String sessionId;
    private final Object lock = new Object();
    private final Map<String, List<Subscription>> subscriptions = new HashMap<>();
    private final Map<String, PV<VType, Object>> writers = new HashMap<>();
    private final List<Consumer<LinkStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService staleChecker;

    private volatile LinkStatus status = LinkStatus.DISCONNECTED;
    private volatile boolean connectCalled;
    private volatile boolean closed;
    private ScheduledFuture<?> staleTask;

    public GpClientProcessLink(GPClientInstance client, String prefix, java.util.concurrent.Executor notifyOn,
                               Duration maxRate, Duration connectionTimeout, Duration staleAfter, String sessionId) {
        this.client = Objects.requireNonNull(client, "client");
        this.prefix = prefix == null || prefix.isBlank() ? GpClientLinkConfig.DEFAULT_PREFIX : prefix;
        this.notifyOn = Objects.requireNonNull(notifyOn, "notifyOn");
        this.maxRate = positive(maxRate, "maxRate");
        this.connectionTimeout = positive(connectionTimeout, "connectionTimeout");
        this.staleAfter = positive(staleAfter, "staleAfter");
        this.sessionId = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        this.staleChecker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "malbec-link-stale");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void connect() {
        boolean schedule = false;
        synchronized (lock) {
            if (closed || connectCalled) {
                return;
            }
            connectCalled = true;
            schedule = true;
        }
        if (schedule) {
            setStatus(LinkStatus.CONNECTING);
            long period = Math.max(10, staleAfter.toMillis() / 2);
            synchronized (lock) {
                if (!closed && staleTask == null) {
                    staleTask = staleChecker.scheduleAtFixedRate(this::checkStale, period, period, TimeUnit.MILLISECONDS);
                }
            }
            refreshStatus();
        }
    }

    @Override
    public LinkStatus status() {
        return status;
    }

    @Override
    public void addStatusListener(Consumer<LinkStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        statusListeners.add(listener);
        notifyStatus(listener, status);
    }

    @Override
    public AutoCloseable subscribe(String name, DataType type, Consumer<VariableUpdate> listener) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            throw new IllegalStateException("Link is closed");
        }

        Subscription subscription = new Subscription(name, type, listener);
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Link is closed");
            }
            subscriptions.computeIfAbsent(name, ignored -> new ArrayList<>()).add(subscription);
        }

        try {
            String channel = ChannelNamer.channel(prefix, sessionId, name, type);
            subscription.channel = channel;
            subscription.reader = client.read(channel)
                .notifyOn(notifyOn)
                .maxRate(maxRate)
                .connectionTimeout(connectionTimeout)
                .addReadListener((event, reader) -> onRead(subscription, event, reader))
                .start();
        } catch (RuntimeException e) {
            removeSubscription(subscription);
            throw e;
        }

        refreshStatus();
        return () -> closeSubscription(subscription);
    }

    @Override
    public void read(String name, DataType type, Consumer<VariableUpdate> once) {
        Objects.requireNonNull(once, "once");
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicReference<AutoCloseable> handle = new AtomicReference<>();
        AutoCloseable subscription = subscribe(name, type, update -> {
            if (delivered.compareAndSet(false, true)) {
                once.accept(update);
                closeQuietly(handle.get());
            }
        });
        handle.set(subscription);
        if (delivered.get()) {
            closeQuietly(subscription);
        }
    }

    @Override
    public CompletableFuture<WriteResult> write(String name, DataType type, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (closed) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, "Link is closed"));
        }

        final VType payload;
        try {
            payload = toVType(type, ValueCodec.toWire(type, value));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, e.getMessage()));
        }

        String channel = ChannelNamer.channel(prefix, sessionId, name, type);
        final PV<VType, Object> pv;
        try {
            synchronized (lock) {
                if (closed) {
                    return CompletableFuture.completedFuture(WriteResult.failed(name, "Link is closed"));
                }
                pv = writers.computeIfAbsent(channel, key -> client.readAndWrite(key)
                    .notifyOn(notifyOn)
                    .maxRate(maxRate)
                    .connectionTimeout(connectionTimeout)
                    .addListener(event -> {
                    })
                    .start());
            }
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, messageOf(e)));
        }

        CompletableFuture<WriteResult> result = new CompletableFuture<>();
        attemptWrite(pv, name, payload, result, System.nanoTime() + connectionTimeout.toNanos());
        return result
            .orTimeout(connectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(error -> WriteResult.failed(name, "Write timed out"));
    }

    private void attemptWrite(PV<VType, Object> pv, String name, VType payload,
                              CompletableFuture<WriteResult> result, long deadline) {
        if (result.isDone()) {
            return;
        }
        if (closed) {
            result.complete(WriteResult.failed(name, "Link is closed"));
            return;
        }
        if (pv.isWriteConnected()) {
            try {
                pv.write(payload, (event, writer) -> {
                    if (event.isType(PVEvent.Type.WRITE_SUCCEEDED)) {
                        result.complete(WriteResult.ok(name));
                    } else if (event.isType(PVEvent.Type.WRITE_FAILED)) {
                        Exception error = event.getWriteError();
                        result.complete(WriteResult.failed(name,
                            error == null ? "Write failed" : messageOf(error)));
                    }
                });
                return;
            } catch (RuntimeException e) {
                if (System.nanoTime() >= deadline) {
                    result.complete(WriteResult.failed(name, messageOf(e)));
                    return;
                }
            }
        } else if (System.nanoTime() >= deadline) {
            result.complete(WriteResult.failed(name, "Writer is not connected"));
            return;
        }
        try {
            staleChecker.schedule(() -> attemptWrite(pv, name, payload, result, deadline), 5, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            result.complete(WriteResult.failed(name, messageOf(e)));
        }
    }

    @Override
    public void close() {
        List<Subscription> toClose;
        List<PV<VType, Object>> writersToClose;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = subscriptions.values().stream().flatMap(List::stream).toList();
            writersToClose = List.copyOf(writers.values());
            subscriptions.clear();
            writers.clear();
            if (staleTask != null) {
                staleTask.cancel(false);
                staleTask = null;
            }
        }
        for (Subscription subscription : toClose) {
            closeSubscription(subscription);
        }
        for (PV<VType, Object> writer : writersToClose) {
            closeQuietly(writer);
        }
        staleChecker.shutdownNow();
        setStatus(LinkStatus.DISCONNECTED);
    }

    private void onRead(Subscription subscription, PVEvent event, PVReader<VType> reader) {
        if (closed || subscription.closed) {
            return;
        }
        if (event.isType(PVEvent.Type.EXCEPTION)) {
            markBad(subscription);
        }
        if (event.isType(PVEvent.Type.READ_CONNECTION)) {
            subscription.connectionSeen = true;
            subscription.connected = reader.isConnected();
            if (!subscription.connected) {
                markBad(subscription);
            } else if (!subscription.valueSeen) {
                markBad(subscription);
            }
        }
        if (event.isType(PVEvent.Type.VALUE)) {
            VType rawValue = reader.getValue();
            if (rawValue == null) {
                markBad(subscription);
            } else {
                acceptValue(subscription, rawValue);
            }
        }
        refreshStatus();
    }

    private void acceptValue(Subscription subscription, VType rawValue) {
        try {
            Object decoded = ValueCodec.fromWire(subscription.type, unwrap(rawValue), List.of());
            Instant sourceTime = sourceTime(rawValue);
            subscription.lastValue = decoded;
            subscription.lastSourceTime = sourceTime;
            subscription.lastUpdate = Instant.now();
            subscription.valueSeen = true;
            subscription.quality = Quality.GOOD;
            subscription.badSent = false;
            emit(subscription, new VariableUpdate(subscription.name, decoded, Quality.GOOD, sourceTime));
        } catch (IllegalArgumentException e) {
            markBad(subscription);
        }
    }

    private void markBad(Subscription subscription) {
        subscription.quality = Quality.BAD;
        if (!subscription.badSent) {
            subscription.badSent = true;
            Object value = subscription.valueSeen ? subscription.lastValue : null;
            emit(subscription, new VariableUpdate(subscription.name, value, Quality.BAD, Instant.now()));
        }
    }

    private void checkStale() {
        Instant now = Instant.now();
        List<Subscription> stale = new ArrayList<>();
        synchronized (lock) {
            if (closed) {
                return;
            }
            for (List<Subscription> entries : subscriptions.values()) {
                for (Subscription subscription : entries) {
                    if (subscription.valueSeen
                        && subscription.quality == Quality.GOOD
                        && subscription.lastUpdate.plus(staleAfter).isBefore(now)) {
                        subscription.quality = Quality.STALE;
                        stale.add(subscription);
                    }
                }
            }
        }
        for (Subscription subscription : stale) {
            emit(subscription, new VariableUpdate(subscription.name, subscription.lastValue,
                Quality.STALE, subscription.lastSourceTime));
        }
        if (!stale.isEmpty()) {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        LinkStatus next;
        synchronized (lock) {
            if (closed || !connectCalled) {
                next = LinkStatus.DISCONNECTED;
            } else {
                List<Subscription> all = subscriptions.values().stream().flatMap(List::stream).toList();
                if (all.isEmpty()) {
                    next = LinkStatus.CONNECTING;
                } else {
                    boolean allGood = all.stream().allMatch(s -> s.connected && s.quality == Quality.GOOD);
                    boolean anyGood = all.stream().anyMatch(s -> s.valueSeen && s.quality == Quality.GOOD);
                    boolean anyBad = all.stream().anyMatch(s -> s.valueSeen && s.quality != Quality.GOOD);
                    boolean anyDisconnected = all.stream().anyMatch(s -> s.connectionSeen && !s.connected);
                    if (allGood) {
                        next = LinkStatus.CONNECTED;
                    } else if (anyGood || anyBad || anyDisconnected) {
                        next = LinkStatus.DEGRADED;
                    } else {
                        next = LinkStatus.CONNECTING;
                    }
                }
            }
        }
        setStatus(next);
    }

    private void setStatus(LinkStatus next) {
        if (status == next) {
            return;
        }
        status = next;
        for (Consumer<LinkStatus> listener : statusListeners) {
            notifyStatus(listener, next);
        }
    }

    private void emit(Subscription subscription, VariableUpdate update) {
        if (subscription.closed || closed) {
            return;
        }
        try {
            subscription.listener.accept(update);
        } catch (RuntimeException ignored) {
        }
    }

    private void notifyStatus(Consumer<LinkStatus> listener, LinkStatus next) {
        try {
            listener.accept(next);
        } catch (RuntimeException ignored) {
        }
    }

    private void closeSubscription(Subscription subscription) {
        if (subscription.closed) {
            return;
        }
        subscription.closed = true;
        removeSubscription(subscription);
        closeQuietly(subscription.reader);
        refreshStatus();
    }

    private void removeSubscription(Subscription subscription) {
        synchronized (lock) {
            List<Subscription> entries = subscriptions.get(subscription.name);
            if (entries != null) {
                entries.remove(subscription);
                if (entries.isEmpty()) {
                    subscriptions.remove(subscription.name);
                }
            }
        }
    }

    private static Object unwrap(VType value) {
        if (value instanceof VNumber number) {
            return number.getValue().doubleValue();
        }
        if (value instanceof VString string) {
            return string.getValue();
        }
        if (value instanceof VEnum enumeration) {
            return enumeration.getValue();
        }
        throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
    }

    private static Instant sourceTime(VType value) {
        Time time = Time.timeOf(value);
        return time.isValid() ? time.getTimestamp() : Instant.now();
    }

    private static VType toVType(DataType type, Object wire) {
        return switch (type) {
            case INTEGER, REAL -> VDouble.of((Double) wire, Alarm.none(), Time.now(), Display.none());
            case ENUMERATION, STRING -> VString.of((String) wire, Alarm.none(), Time.now());
        };
    }

    private static String messageOf(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static final class Subscription {
        private final String name;
        private final DataType type;
        private final Consumer<VariableUpdate> listener;
        private volatile String channel;
        private volatile PVReader<VType> reader;
        private volatile boolean connected;
        private volatile boolean connectionSeen;
        private volatile boolean valueSeen;
        private volatile boolean badSent;
        private volatile boolean closed;
        private volatile Quality quality = Quality.BAD;
        private volatile Object lastValue;
        private volatile Instant lastUpdate = Instant.EPOCH;
        private volatile Instant lastSourceTime = Instant.EPOCH;

        private Subscription(String name, DataType type, Consumer<VariableUpdate> listener) {
            this.name = name;
            this.type = type;
            this.listener = listener;
        }
    }
}
