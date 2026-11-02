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
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.epics.gpclient.GPClientConfiguration;
import org.epics.gpclient.GPClientInstance;
import org.epics.gpclient.datasource.DataSourceProvider;

/**
 * Options and lifecycle of a GPClient process link.
 */
public final class GpClientLinkConfig implements AutoCloseable {

    public static final String DEFAULT_PREFIX = "loc://";
    public static final Duration DEFAULT_MAX_RATE = Duration.ofMillis(20);
    public static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_STALE_AFTER = Duration.ofSeconds(2);

    private final String prefix;
    private final Executor notifyOn;
    private final Duration maxRate;
    private final Duration connectionTimeout;
    private final Duration staleAfter;
    private final String sessionId;
    private ScheduledExecutorService dataProcessingThreadPool;

    /**
     * @throws IllegalArgumentException when a duration is not positive
     */
    public GpClientLinkConfig(String prefix, Executor notifyOn, Duration maxRate, Duration connectionTimeout,
                              Duration staleAfter, String sessionId) {
        this.prefix = prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix;
        this.notifyOn = Objects.requireNonNull(notifyOn, "notifyOn");
        this.maxRate = positive(maxRate, "maxRate");
        this.connectionTimeout = positive(connectionTimeout, "connectionTimeout");
        this.staleAfter = positive(staleAfter, "staleAfter");
        this.sessionId = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
    }

    public static GpClientLinkConfig defaults(String sessionId) {
        return new GpClientLinkConfig(DEFAULT_PREFIX, Runnable::run, DEFAULT_MAX_RATE,
            DEFAULT_CONNECTION_TIMEOUT, DEFAULT_STALE_AFTER, sessionId);
    }

    public String prefix() {
        return prefix;
    }

    public Executor notifyOn() {
        return notifyOn;
    }

    public Duration maxRate() {
        return maxRate;
    }

    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    public Duration staleAfter() {
        return staleAfter;
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized GPClientInstance newClient() {
        if (dataProcessingThreadPool == null) {
            dataProcessingThreadPool = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "malbec-gpclient");
                thread.setDaemon(true);
                return thread;
            });
        }
        return new GPClientConfiguration()
            .dataSource(DataSourceProvider.createDataSource())
            .notificationExecutor(notifyOn)
            .dataProcessingThreadPool(dataProcessingThreadPool)
            .defaultMaxRate(maxRate)
            .build();
    }

    public GpClientProcessLink newLink(GPClientInstance client) {
        return new GpClientProcessLink(client, prefix, notifyOn, maxRate, connectionTimeout, staleAfter, sessionId);
    }

    @Override
    public synchronized void close() {
        if (dataProcessingThreadPool != null) {
            dataProcessingThreadPool.shutdownNow();
            dataProcessingThreadPool = null;
        }
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
