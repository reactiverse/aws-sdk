package io.reactiverse.awssdk;

import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;
import software.amazon.awssdk.http.async.SimpleSubscriber;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class AsyncHttpClientTest {

  private Vertx vertx;
  private HttpServer server;
  private SdkAsyncHttpClient client;

  private static final int PORT = 8000;
  private static final String HOST = "localhost";
  private static final String SCHEME = "http";

  @BeforeEach
  public void setUp() {
    vertx = Vertx.vertx();
    server = vertx.createHttpServer();
    client = new VertxNioAsyncHttpClient(vertx.getOrCreateContext());
  }

  @AfterEach
  public void tearDown(VertxTestContext ctx) {
      if (server == null) {
          return;
      }
      server.close(res -> {
          assertTrue(res.succeeded());
          ctx.completeNow();
      });
  }

  @Test
  @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
  public void testGet(VertxTestContext ctx) {
    server.requestHandler(req -> {
      req.response().end("foo");
    });
    server.listen(PORT, HOST, res -> {
        assertTrue(res.succeeded());
        client.execute(AsyncExecuteRequest.builder()
                .request(SdkHttpRequest
                        .builder()
                        .protocol(SCHEME)
                        .host(HOST)
                        .port(PORT)
                        .method(SdkHttpMethod.GET)
                        .build())
                .responseHandler(new SdkAsyncHttpResponseHandler() {
                    @Override
                    public void onHeaders(SdkHttpResponse headers) {
                        assertEquals(200, headers.statusCode());
                    }
                    @Override
                    public void onStream(Publisher<ByteBuffer> stream) {
                        stream.subscribe(new SimpleSubscriber(body -> {
                            assertEquals("foo", Unpooled.wrappedBuffer(body).toString(StandardCharsets.UTF_8));
                            ctx.completeNow();
                        }));
                    }
                    @Override
                    public void onError(Throwable error) {
                        throw new RuntimeException(error);
                    }
                })
                .build());
    });
  }

  @Test
  @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
  public void testPut(VertxTestContext ctx) {
    final byte[] payload = "the-body".getBytes();

    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        req.response().end(buff);
      });
    });
      server.listen(PORT, HOST, res -> {
          assertTrue(res.succeeded());
          SdkHttpFullRequest request = SdkHttpFullRequest
                  .builder()
                  .protocol(SCHEME)
                  .host(HOST)
                  .port(PORT)
                  .method(SdkHttpMethod.PUT)
                  .putHeader("Content-Length", String.valueOf(payload.length))
                  .contentStreamProvider(() -> new ByteArrayInputStream(payload))
                  .build();
          client.execute(AsyncExecuteRequest.builder()
                  .request(request)
                  .requestContentPublisher(new SimpleHttpContentPublisher(request))
                  .responseHandler(new SdkAsyncHttpResponseHandler() {
                      @Override
                      public void onHeaders(SdkHttpResponse headers) {
                          assertEquals(200, headers.statusCode());
                      }

                      @Override
                      public void onStream(Publisher<ByteBuffer> stream) {
                          stream.subscribe(new SimpleSubscriber(body -> {
                              assertEquals("the-body", Unpooled.wrappedBuffer(body).toString(StandardCharsets.UTF_8));
                              ctx.completeNow();
                          }));
                      }

                      @Override
                      public void onError(Throwable error) {
                          throw new RuntimeException(error);
                      }
                  })
                  .build());
      });
  }

}
