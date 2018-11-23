package io.vertx.ext.awssdk.reactivestreams;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;

public class SdkToVertxRequestSubscriber implements Subscriber<ByteBuffer> {

    private final HttpClientRequest request;
    private final static long BUFF_SIZE = 1024;
    private Subscription subscribtion;

    public SdkToVertxRequestSubscriber(HttpClientRequest request) {
        this.request = request;
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscribtion = s;
        subscribtion.request(BUFF_SIZE);
    }

    @Override
    public void onNext(ByteBuffer byteBuffer) {
        /*
        Fools AWS signing algorithm
        if (!request.isChunked()) {
            request.setChunked(true);
        }
        */
        request.write(Buffer.buffer(Unpooled.wrappedBuffer(byteBuffer)));
        subscribtion.request(BUFF_SIZE);
    }

    @Override
    public void onError(Throwable t) {
        /// TODO : cancel request ?
        subscribtion.cancel();
    }

    @Override
    public void onComplete() {
        request.end();
    }
}

