/**
 * Copyright 2015 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.groupon.vertx.utils.deployment;

import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Future;

import com.groupon.vertx.utils.Logger;

/**
 * Handler that tracks the total number of verticles remaining and number of verticles that failed.
 * After all verticles have been deployed (successful or otherwise), it invokes the provided handler
 *
 * @author Tristan Blease (tblease at groupon dot com)
 * @since 2.0.1
 * @version 2.0.1
 */
public class DeploymentMonitorHandler implements AsyncResultHandler<String> {
    private static final Logger log = Logger.getLogger(DeploymentMonitorHandler.class, "verticleDeployHandler");

    private final AtomicInteger failures;
    private final AtomicInteger deploymentsRemaining;
    private final int totalVerticles;
    private final Future<Void> future;

    /**
     * @param totalVerticles number of verticles to wait for before invoking the finished handler
     * @param finishedHandler handler to invoke after all verticles have deployed
     */
    public DeploymentMonitorHandler(int totalVerticles, AsyncResultHandler<Void> finishedHandler) {
        this.totalVerticles = totalVerticles;

        failures = new AtomicInteger(0);
        deploymentsRemaining = new AtomicInteger(totalVerticles);

        future = Future.future();
        future.setHandler(finishedHandler);
    }

    @Override
    public void handle(AsyncResult<String> result) {
        checkForFailures(result);
        checkForCompletion();
    }

    private void checkForFailures(AsyncResult<String> result) {
        if (result.failed()) {
            failures.incrementAndGet();
            log.error("handle", "error", "Caught exception; failed to deploy verticle", result.cause());
        } else if (result.result().isEmpty()) {
            failures.incrementAndGet();
            log.error("handle", "error", "Empty deployment ID; failed to deploy verticle", new Exception());
        }
    }

    private void checkForCompletion() {
        if (deploymentsRemaining.decrementAndGet() == 0) {
            handleCompletion();
        }
    }

    private void handleCompletion() {
        if (failures.get() == 0) {
            log.info("handleCompletion", "success", new String[]{"message"}, String.format("Deployed %d verticle(s) successfully", totalVerticles));
            future.complete(null);
        } else {
            String reason = String.format("Failed to deploy %d of %d verticle(s)", failures.get(), totalVerticles);
            log.error("handleCompletion", "error", reason);
            future.fail(new Exception(reason));
        }
    }
}
