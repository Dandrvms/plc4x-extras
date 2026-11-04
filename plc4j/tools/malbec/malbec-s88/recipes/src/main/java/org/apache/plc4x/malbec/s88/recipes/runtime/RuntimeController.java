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
package org.apache.plc4x.malbec.s88.recipes.runtime;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.Timer;
import org.apache.plc4x.malbec.s88.batch.client.BatchClient;
import org.apache.plc4x.malbec.s88.batch.client.BatchUpdateListener;
import org.apache.plc4x.malbec.s88.batch.model.BatchSnapshot;

/** Coalesces link snapshots into redraws on the Event Dispatch Thread. */
final class RuntimeController implements AutoCloseable {

    private static final int REDRAW_MILLIS = 100;
    private static final long STALE_NANOS = 2_000_000_000L;

    private final BatchClient client;
    private final Consumer<BatchSnapshot> onUpdate;
    private final AtomicReference<BatchSnapshot> latest = new AtomicReference<>();
    private final BatchUpdateListener listener = this::received;
    private final Timer timer;

    private volatile long lastUpdateNanos = System.nanoTime();
    private BatchSnapshot delivered;
    private boolean deliveredStale;

    RuntimeController(BatchClient client, Consumer<BatchSnapshot> onUpdate) {
        this.client = client;
        this.onUpdate = onUpdate;
        client.addListener(listener);
        this.latest.set(client.lastSnapshot());
        this.delivered = latest.get();
        this.timer = new Timer(REDRAW_MILLIS, event -> tick());
        this.timer.setCoalesce(true);
        this.timer.start();
    }

    BatchSnapshot latest() {
        return latest.get();
    }

    boolean stale() {
        BatchSnapshot snapshot = latest.get();
        return snapshot != null && snapshot.connected()
                && System.nanoTime() - lastUpdateNanos > STALE_NANOS;
    }

    private void received(BatchSnapshot snapshot) {
        latest.set(snapshot);
        lastUpdateNanos = System.nanoTime();
    }

    private void tick() {
        BatchSnapshot snapshot = latest.get();
        boolean stale = stale();
        if (snapshot == delivered && stale == deliveredStale) {
            return;
        }
        delivered = snapshot;
        deliveredStale = stale;
        if (snapshot != null) {
            onUpdate.accept(snapshot);
        }
    }

    void stop() {
        timer.stop();
    }

    @Override
    public void close() {
        timer.stop();
        client.removeListener(listener);
    }
}
