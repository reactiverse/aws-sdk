package io.vertx.ext.awssdk.reactivestreams;

import io.vertx.core.http.HttpClientResponse;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class VertxToSdkResponsePublisher implements Publisher<ByteBuffer> {

    private HttpClientResponse response;
    private CompletableFuture<Void> future;

    public VertxToSdkResponsePublisher(HttpClientResponse response, CompletableFuture<Void> future) {
        this.response = response;
        this.future = future;
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> s) {
        s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                response.fetch(n);
            }

            @Override
            public void cancel() {}
        });
        response.endHandler(v -> {
            s.onComplete();
            future.complete(null);
        });
        response.bodyHandler(buff ->
                s.onNext(ByteBuffer.wrap(buff.getBytes()))
        );
        response.exceptionHandler(s::onError);
    }
}
