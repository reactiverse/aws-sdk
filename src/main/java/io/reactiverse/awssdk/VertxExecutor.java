package io.reactiverse.awssdk;

import io.vertx.core.Context;

import java.util.concurrent.Executor;

/**
 * Vertx executor that runs the specified command in the current context.
 * Can only work if the runnable is non-blocking
 */
public class VertxExecutor implements Executor {

    private Context context;

    public VertxExecutor(Context context) {
        this.context = context;
    }


    @Override
    public void execute(Runnable command) {
        context.runOnContext(v -> command.run());
    }
}
