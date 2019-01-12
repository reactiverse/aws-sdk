package io.reactiverse.awssdk.reactivestreams;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class ReadStreamPublisher<T extends Buffer> implements Publisher<ByteBuffer> {

    private ReadStream<T> stream;
    private CompletableFuture<Void> future;

    public ReadStreamPublisher(ReadStream<T> readStream) {
        this(readStream, null);
    }

    public ReadStreamPublisher(ReadStream<T> readStream, CompletableFuture<Void> future) {
        this.stream = readStream;
        this.future = future;
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> s) {
        s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                stream.fetch(n);
            }

            /**
             * Cannot really do anything on the stream
             * stream.pause() maybe ?
             */
            @Override
            public void cancel() {}
        });
        stream.endHandler(v -> {
            s.onComplete();
            if (future != null) {
                future.complete(null);
            }
        });
        stream.handler(buff ->
                s.onNext(ByteBuffer.wrap(buff.getBytes()))
        );
        stream.exceptionHandler(s::onError);
    }
}
