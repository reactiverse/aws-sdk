package io.vertx.reactivex.ext.aws;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class TestSingle {

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCompleteNormally(VertxTestContext ctx) {
        String payload = "payload";
        CompletableFuture<String> fut = new CompletableFuture<>();
        RxSdkUtils.single(fut)
                .doOnSuccess(res -> {
                    assertEquals(payload, res);
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
        fut.complete(payload);
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testException(VertxTestContext ctx) {
        RuntimeException e = new RuntimeException("e");
        CompletableFuture<String> fut = new CompletableFuture<>();
        RxSdkUtils.single(fut)
                .subscribe(res -> {
                    ctx.failNow(new RuntimeException("Should not complete without exception"));
                }, error -> {
                    assertEquals(e, error);
                    ctx.completeNow();
                });
        fut.completeExceptionally(e);
    }


}
