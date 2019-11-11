package io.reactiverse.awssdk.reactivestreams;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class WriteStreamSubscriber<T extends WriteStream<Buffer>> implements Subscriber<ByteBuffer> {

    protected static final long BUFF_SIZE = 1024;

    protected T stream;
    private Subscription subscription;
    private Optional<CompletableFuture<WriteStream<Buffer>>> cf;

    public WriteStreamSubscriber(T stream) {
        this.stream = stream;
        cf = Optional.empty();
    }

    public WriteStreamSubscriber(T stream, CompletableFuture<WriteStream<Buffer>> cf) {
        this.stream = stream;
        this.cf = Optional.of(cf);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(BUFF_SIZE);
    }

    @Override
    public void onNext(ByteBuffer byteBuffer) {
        if (byteBuffer.array().length != 0) {
            Buffer buffer = Buffer.buffer(Unpooled.wrappedBuffer(byteBuffer));
            stream.write(buffer);
        }
        subscription.request(BUFF_SIZE);
    }

    @Override
    public void onError(Throwable t) {
        subscription.cancel();
    }


    @Override
    public void onComplete() {
        stream.end();
        cf.map(fut -> fut.complete(stream));
    }
}
