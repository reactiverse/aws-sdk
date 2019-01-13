package io.reactiverse.awssdk;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;

import java.util.concurrent.CompletableFuture;

public class ContextAssertVertxNioAsyncHttpClient extends VertxNioAsyncHttpClient {

    private Vertx vertx;
    private Context creationContext;

    public ContextAssertVertxNioAsyncHttpClient(Vertx vertx, Context context) {
        super(context);
        this.vertx = vertx;
        this.creationContext = context;
    }

    @Override
    void executeOnContext(AsyncExecuteRequest asyncExecuteRequest, CompletableFuture<Void> fut) {
        if (vertx.getOrCreateContext() != this.creationContext) {
            throw new AssertionError("Context should ALWAYS be the same");
        }
        super.executeOnContext(asyncExecuteRequest, fut);
    }
}
