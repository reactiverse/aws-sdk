package io.vertx.ext.awssdk;

import io.vertx.core.Vertx;

import java.util.concurrent.Executor;

/**
 * Vertx executor that runs the specified command in the current context.
 * Can only work if the runnable is non-blocking
 */
public class VertxExecutor implements Executor {

    private Vertx vertx;

    public VertxExecutor(Vertx vertx) {
        this.vertx = vertx;
    }


    @Override
    public void execute(Runnable command) {
        vertx.runOnContext(v -> command.run());
    }
}
