package io.reactiverse.awssdk;

import io.reactiverse.awssdk.converters.MethodConverter;
import io.reactiverse.awssdk.reactivestreams.HttpClientRequestSubscriber;
import io.reactiverse.awssdk.reactivestreams.ReadStreamPublisher;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;
import software.amazon.awssdk.utils.StringUtils;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class VertxNioAsyncHttpClient implements SdkAsyncHttpClient {

    private final Context context;
    private final HttpClient client;
    private final HttpClientOptions clientOptions;

    private static final HttpClientOptions DEFAULT_CLIENT_OPTIONS = new HttpClientOptions()
      .setSsl(true)
      .setKeepAlive(true);

    public VertxNioAsyncHttpClient(Context context) {
      this.context = context;
      this.clientOptions = DEFAULT_CLIENT_OPTIONS;
      this.client = createVertxHttpClient(context.owner());
    }

    public VertxNioAsyncHttpClient(Context context, HttpClientOptions clientOptions) {
      requireNonNull(clientOptions);
      this.context = context;
      this.clientOptions = clientOptions;
      this.client = createVertxHttpClient(context.owner());
    }

    private HttpClient createVertxHttpClient(Vertx vertx) {
      return vertx.createHttpClient(clientOptions);
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

        final HttpMethod method = MethodConverter.awsToVertx(request.method());
        final RequestOptions options = getRequestOptions(request);
        final HttpClientRequest vRequest = client.request(method, options).setFollowRedirects(true);
        request.headers().forEach((headerName, headerValues) ->
                vRequest.putHeader(headerName, String.join(",", headerValues))
        );
        vRequest.putHeader(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);
        vRequest.onFailure(failure -> {
            responseHandler.onError(failure);
            fut.completeExceptionally(failure);
        });
        vRequest.onSuccess(vResponse -> {
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

  private static RequestOptions getRequestOptions(SdkHttpRequest request) {
    return new RequestOptions()
      .setHost(request.host())
      .setPort(request.port())
      .setURI(createRelativeUri(request.getUri()))
      .setSsl("https".equals(request.protocol()));
  }

  private static String createRelativeUri(URI uri) {
    return (StringUtils.isEmpty(uri.getPath()) ? "/" : uri.getPath()) +
      // AWS requires query parameters to be encoded as defined by RFC 3986.
      // see https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
      // uri.toASCIIString() returns the URI encoded in this manner
      (StringUtils.isEmpty(uri.getQuery()) ? "" : "?" + uri.toASCIIString().split("\\?")[1]);
  }

    @Override
    public void close() {
        client.close();
    }
}
