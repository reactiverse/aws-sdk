package io.vertx.ext.awssdk;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.awssdk.reactivestreams.SdkToVertxRequestSubscriber;
import io.vertx.ext.awssdk.reactivestreams.VertxToSdkResponsePublisher;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;

import java.util.concurrent.CompletableFuture;

import static io.vertx.ext.awssdk.converters.MethodConverter.awsToVertx;

public class VertxNioAsyncHttpClient implements SdkAsyncHttpClient {

    private final Vertx vertx;

    public VertxNioAsyncHttpClient(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncExecuteRequest) {

        final SdkHttpRequest request = asyncExecuteRequest.request();



        final HttpClient client = vertx.createHttpClient(getClientOptions(request));
        final String fullPath = request.protocol() + "://" + request.host() + ":" + request.port() + "/" + request.encodedPath();
        HttpClientRequest vRequest = client.request(awsToVertx(request.method()), fullPath);
        request.headers().forEach((headerName, headerValues) -> {
            vRequest.putHeader(headerName, String.join(",", headerValues));
        });
        final SdkAsyncHttpResponseHandler responseHandler = asyncExecuteRequest.responseHandler();
        final CompletableFuture<Void> fut = new CompletableFuture<>();
        vRequest.exceptionHandler(e -> {
            responseHandler.onError(e);
            fut.completeExceptionally(e);
        });
        vRequest.handler(vResponse -> {
            final SdkHttpFullResponse.Builder builder = SdkHttpResponse.builder()
                    .statusCode(vResponse.statusCode())
                    .statusText(vResponse.statusMessage());
            vResponse.headers().forEach(e ->
                    builder.appendHeader(e.getKey(), e.getValue())
            );
            responseHandler.onHeaders(builder.build());
            responseHandler.onStream(new VertxToSdkResponsePublisher(vResponse, fut));
        });
        SdkHttpContentPublisher publisher = asyncExecuteRequest.requestContentPublisher();
        if (publisher != null) {
            publisher.subscribe(new SdkToVertxRequestSubscriber(vRequest));
        } else {
            vRequest.end();
        }
        return fut;
    }

    private HttpClientOptions getClientOptions(SdkHttpRequest request) {
        return new HttpClientOptions()
                .setDefaultHost(request.host())
                .setDefaultPort(request.port());
    }


    @Override
    public void close() {
        // TODO : close the client ?
        // nope ? many requests can come
    }
}
