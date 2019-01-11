package io.reactiverse.awssdk.reactivestreams;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;

import java.nio.ByteBuffer;

public class HttpClientRequestSubscriber extends WriteStreamSubscriber<HttpClientRequest> {

    public HttpClientRequestSubscriber(HttpClientRequest request) {
        super(request);
    }

    @Override
    public void onNext(ByteBuffer byteBuffer) {
        if (!stream.isChunked() && !stream.headers().contains(HttpHeaders.CONTENT_LENGTH) && byteBuffer.array().length != 0) {
            stream.setChunked(true);
        }
        super.onNext(byteBuffer);
    }

}

