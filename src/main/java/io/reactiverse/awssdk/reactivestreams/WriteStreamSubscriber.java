package io.reactiverse.awssdk.reactivestreams;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;

public class WriteStreamSubscriber<T extends WriteStream<Buffer>> implements Subscriber<ByteBuffer> {

    protected final static long BUFF_SIZE = 1024;

    protected T stream;
    private Subscription subscription;

    public WriteStreamSubscriber(T stream) {
        this.stream = stream;
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
    }
}
