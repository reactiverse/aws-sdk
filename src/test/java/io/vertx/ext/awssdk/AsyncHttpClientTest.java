package io.vertx.ext.awssdk;

import io.netty.buffer.Unpooled;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.internal.http.async.SimpleHttpContentPublisher;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.async.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@RunWith(VertxUnitRunner.class)
public class AsyncHttpClientTest {

  private Vertx vertx;
  private HttpServer server;
  private SdkAsyncHttpClient client;

  private static final int PORT = 8000;
  private static final String HOST = "localhost";
  private static final String SCHEME = "http";


  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    server = vertx.createHttpServer();
    client = new VertxNioAsyncHttpClient(vertx);
  }

  @After
  public void tearDown(TestContext ctx) {
      if (server == null) {
          return;
      }
      server.close(ctx.asyncAssertSuccess());
  }

  private void startServer(TestContext ctx) {
    server.listen(PORT, HOST, ctx.asyncAssertSuccess());
  }

  @Test
  public void testGet(TestContext ctx) {
    Async async = ctx.async();
    server.requestHandler(req -> {
      System.out.println("got req");
      req.response().end("foo");
    });
    startServer(ctx);
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
            ctx.assertEquals(200, headers.statusCode());
          }
          @Override
          public void onStream(Publisher<ByteBuffer> stream) {
            stream.subscribe(new SimpleSubscriber(body -> {
              ctx.assertEquals("foo", Unpooled.wrappedBuffer(body).toString(StandardCharsets.UTF_8));
              async.complete();
            }));
          }
          @Override
          public void onError(Throwable error) {
            ctx.fail(error);
          }
        })
        .build());
  }

  @Test
  public void testPut(TestContext ctx) {
    Async async = ctx.async();
    server.requestHandler(req -> {
      req.bodyHandler(buff -> {
        req.response().end(buff);
      });
    });
    startServer(ctx);
    SdkHttpFullRequest request = SdkHttpFullRequest
        .builder()
        .protocol(SCHEME)
        .host(HOST)
        .port(PORT)
        .method(SdkHttpMethod.PUT)
        .contentStreamProvider(() -> new ByteArrayInputStream("the-body".getBytes()))
        .build();
    client.execute(AsyncExecuteRequest.builder()
        .request(request)
        .requestContentPublisher(new SimpleHttpContentPublisher(request))
        .responseHandler(new SdkAsyncHttpResponseHandler() {
          @Override
          public void onHeaders(SdkHttpResponse headers) {
            ctx.assertEquals(200, headers.statusCode());
          }
          @Override
          public void onStream(Publisher<ByteBuffer> stream) {
            stream.subscribe(new SimpleSubscriber(body -> {
              ctx.assertEquals("the-body", Unpooled.wrappedBuffer(body).toString(StandardCharsets.UTF_8));
              async.complete();
            }));
          }
          @Override
          public void onError(Throwable error) {
            ctx.fail(error);
          }
        })
        .build());

  }

}
