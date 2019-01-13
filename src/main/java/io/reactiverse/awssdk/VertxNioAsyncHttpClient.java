package io.reactiverse.awssdk;

import io.reactiverse.awssdk.converters.MethodConverter;
import io.reactiverse.awssdk.reactivestreams.HttpClientRequestSubscriber;
import io.reactiverse.awssdk.reactivestreams.ReadStreamPublisher;
import io.vertx.core.Context;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;

import java.util.concurrent.CompletableFuture;

public class VertxNioAsyncHttpClient implements SdkAsyncHttpClient {

    private final Context context;

    public VertxNioAsyncHttpClient(Context context) {
        this.context = context;
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest asyncExecuteRequest) {
        final CompletableFuture<Void> fut = new CompletableFuture<>();
        if (Context.isOnEventLoopThread()) {
            executeOnContext(asyncExecuteRequest, fut);
        } else {
            context.runOnContext(v -> executeOnContext(asyncExecuteRequest, fut));
        }
        return fut;
    }

    void executeOnContext(AsyncExecuteRequest asyncExecuteRequest, CompletableFuture<Void> fut) {
        final SdkHttpRequest request = asyncExecuteRequest.request();
        final SdkAsyncHttpResponseHandler responseHandler = asyncExecuteRequest.responseHandler();
        final HttpClient client = context.owner().createHttpClient(getClientOptions(request));
        final String fullPath = request.protocol() + "://" + request.host() + ":" + request.port() + request.encodedPath();

        final HttpClientRequest vRequest = client.request(MethodConverter.awsToVertx(request.method()), fullPath).setFollowRedirects(true);
        request.headers().forEach((headerName, headerValues) ->
                vRequest.putHeader(headerName, String.join(",", headerValues))
        );
        vRequest.exceptionHandler(error -> {
            responseHandler.onError(error);
            fut.completeExceptionally(error);
        });
        vRequest.handler(vResponse -> {
            final SdkHttpFullResponse.Builder builder = SdkHttpResponse.builder()
                    .statusCode(vResponse.statusCode())
                    .statusText(vResponse.statusMessage());
            vResponse.headers().forEach(e ->
                    builder.appendHeader(e.getKey(), e.getValue())
            );
            responseHandler.onHeaders(builder.build());
            responseHandler.onStream(new ReadStreamPublisher<>(vResponse, fut));
        });
        final SdkHttpContentPublisher publisher = asyncExecuteRequest.requestContentPublisher();
        if (publisher != null) {
            publisher.subscribe(new HttpClientRequestSubscriber(vRequest));
        } else {
            vRequest.end();
        }
    }

    private HttpClientOptions getClientOptions(SdkHttpRequest request) {
        HttpClientOptions opts = new HttpClientOptions()
                .setDefaultHost(request.host())
                .setDefaultPort(request.port());
        if ("https".equals(request.protocol())) {
            opts.setSsl(true);
        }
        return opts;
    }

    @Override
    public void close() {
       // Nothing to do on close
    }
}
