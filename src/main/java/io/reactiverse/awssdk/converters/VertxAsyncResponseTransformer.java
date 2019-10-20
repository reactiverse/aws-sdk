package io.reactiverse.awssdk.converters;

import io.reactiverse.awssdk.reactivestreams.WriteStreamSubscriber;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.SdkPublisher;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class VertxAsyncResponseTransformer<ResponseT> implements AsyncResponseTransformer<ResponseT, WriteStream<Buffer>> {

    private volatile CompletableFuture<WriteStream<Buffer>> cf;
    private volatile ResponseT response;
    private volatile WriteStream<Buffer> writeStream;

    public VertxAsyncResponseTransformer(WriteStream<Buffer> ws) {
        this.writeStream = ws;
    }

    @Override
    public CompletableFuture<WriteStream<Buffer>> prepare() {
        cf = new CompletableFuture<>();
        return cf;
    }

    @Override
    public void onResponse(ResponseT response) {
        this.response = response;
    }

    @Override
    public void onStream(SdkPublisher<ByteBuffer> publisher) {
        publisher.subscribe(new WriteStreamSubscriber<>(writeStream, cf));
    }

    @Override
    public void exceptionOccurred(Throwable error) {
        cf.completeExceptionally(error);
    }
}
