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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.plc4x.malbec.s88.api.DataType;
import org.apache.plc4x.malbec.s88.batch.link.LinkStatus;
import org.apache.plc4x.malbec.s88.batch.link.ProcessLink;
import org.apache.plc4x.malbec.s88.batch.link.Quality;
import org.apache.plc4x.malbec.s88.batch.link.VariableUpdate;
import org.apache.plc4x.malbec.s88.batch.link.WriteResult;

/**
 * A process link that can simulate link loss, latency, and failed writes.
 */
public final class FaultInjectingProcessLink implements ProcessLink {

    private final ProcessLink delegate;
    private final ScheduledExecutorService scheduler;
    private final List<WrappedSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final List<Consumer<LinkStatus>> statusListeners = new CopyOnWriteArrayList<>();

    private volatile boolean up = true;
    private volatile boolean dropWrites;
    private volatile Duration latency = Duration.ZERO;
    private volatile boolean closed;
    private volatile LinkStatus status = LinkStatus.DISCONNECTED;

    public FaultInjectingProcessLink(ProcessLink delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "malbec-fault-link");
            thread.setDaemon(true);
            return thread;
        });
        delegate.addStatusListener(this::onDelegateStatus);
    }

    @Override
    public void connect() {
        delegate.connect();
        if (!up) {
            setStatus(LinkStatus.DISCONNECTED);
        }
    }

    @Override
    public LinkStatus status() {
        return !up || closed ? LinkStatus.DISCONNECTED : delegate.status();
    }

    @Override
    public void addStatusListener(Consumer<LinkStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        statusListeners.add(listener);
        notifyStatus(listener, status());
    }

    @Override
    public AutoCloseable subscribe(String name, DataType type, Consumer<VariableUpdate> listener) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            throw new IllegalStateException("Link is closed");
        }
        WrappedSubscription subscription = new WrappedSubscription(name, type, listener);
        subscriptions.add(subscription);
        try {
            subscribeDelegate(subscription);
        } catch (RuntimeException e) {
            subscriptions.remove(subscription);
            throw e;
        }
        if (!up) {
            listener.accept(VariableUpdate.bad(name, Instant.now()));
        }
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
        if (closed) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, "Link is closed"));
        }
        if (!up) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, "Link is down"));
        }
        if (dropWrites) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, "Write dropped"));
        }
        if (latency.isZero()) {
            return writeNow(name, type, value);
        }
        CompletableFuture<WriteResult> result = new CompletableFuture<>();
        try {
            scheduler.schedule(() -> {
                try {
                    result.complete(writeNow(name, type, value).join());
                } catch (RuntimeException e) {
                    result.complete(WriteResult.failed(name, messageOf(e)));
                }
            }, latency.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            result.complete(WriteResult.failed(name, messageOf(e)));
        }
        return result;
    }

    public void setUp(boolean up) {
        if (this.up == up || closed) {
            return;
        }
        this.up = up;
        if (!up) {
            setStatus(LinkStatus.DISCONNECTED);
            for (WrappedSubscription subscription : subscriptions) {
                subscription.listener.accept(new VariableUpdate(subscription.name, subscription.lastValue,
                    Quality.BAD, Instant.now()));
            }
        } else {
            allSubscriptions().forEach(this::subscribeDelegate);
            setStatus(delegate.status());
        }
    }

    public boolean isUp() {
        return up && !closed;
    }

    public void setLatency(Duration latency) {
        this.latency = Objects.requireNonNull(latency, "latency");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency cannot be negative");
        }
    }

    public void setLatency(long millis) {
        setLatency(Duration.ofMillis(millis));
    }

    public Duration latency() {
        return latency;
    }

    public void setDropWrites(boolean dropWrites) {
        this.dropWrites = dropWrites;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        allSubscriptions().forEach(this::closeSubscription);
        scheduler.shutdownNow();
        setStatus(LinkStatus.DISCONNECTED);
        delegate.close();
    }

    private List<WrappedSubscription> allSubscriptions() {
        return subscriptions.stream().filter(subscription -> !subscription.closed).toList();
    }

    private void subscribeDelegate(WrappedSubscription subscription) {
        if (closed || subscription.closed || !up) {
            return;
        }
        closeQuietly(subscription.inner);
        long generation = subscription.generation + 1;
        subscription.generation = generation;
        subscription.inner = delegate.subscribe(subscription.name, subscription.type,
            update -> deliver(subscription, generation, update));
    }

    private void deliver(WrappedSubscription subscription, long generation, VariableUpdate update) {
        if (closed || subscription.closed || !up) {
            return;
        }
        if (latency.isZero()) {
            deliverNow(subscription, generation, update);
            return;
        }
        try {
            scheduler.schedule(() -> deliverNow(subscription, generation, update),
                latency.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException ignored) {
        }
    }

    private void deliverNow(WrappedSubscription subscription, long generation, VariableUpdate update) {
        if (closed || subscription.closed || !up || subscription.generation != generation) {
            return;
        }
        subscription.lastValue = update.value();
        subscription.listener.accept(update);
    }

    private CompletableFuture<WriteResult> writeNow(String name, DataType type, Object value) {
        try {
            return delegate.write(name, type, value);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(WriteResult.failed(name, messageOf(e)));
        }
    }

    private void closeSubscription(WrappedSubscription subscription) {
        if (subscription.closed) {
            return;
        }
        subscription.closed = true;
        subscriptions.remove(subscription);
        closeQuietly(subscription.inner);
    }

    private void onDelegateStatus(LinkStatus next) {
        if (up && !closed) {
            setStatus(next);
        }
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

    private void notifyStatus(Consumer<LinkStatus> listener, LinkStatus next) {
        try {
            listener.accept(next);
        } catch (RuntimeException ignored) {
        }
    }

    private static String messageOf(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
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

    private static final class WrappedSubscription {
        private final String name;
        private final DataType type;
        private final Consumer<VariableUpdate> listener;
        private volatile AutoCloseable inner;
        private volatile Object lastValue;
        private volatile long generation;
        private volatile boolean closed;

        private WrappedSubscription(String name, DataType type, Consumer<VariableUpdate> listener) {
            this.name = name;
            this.type = type;
            this.listener = listener;
        }
    }
}
