package io.vertx.ext.awssdk;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Vertx executor that runs the specified command in the current context.
 * Can only work if the runnable is non-blocking
 */
public class VertxExecutor implements Executor {

    private final static Logger LOG = LoggerFactory.getLogger(VertxExecutor.class);

    private Context context;

    public VertxExecutor(Context context) {
        this.context = context;
    }


    @Override
    public void execute(Runnable command) {
        LOG.info("Executing command from Vert.x Executor");
        context.runOnContext(v -> command.run());
    }
}
