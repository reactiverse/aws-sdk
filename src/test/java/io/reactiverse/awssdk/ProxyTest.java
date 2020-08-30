package io.reactiverse.awssdk;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.ProxyOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClientBuilder;
import software.amazon.awssdk.services.kinesis.model.ListStreamsResponse;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class ProxyTest {

  private Vertx vertx;
  private HttpServer proxyServer;

  private static final int PROXY_PORT = 8000;
  private static final String PROXY_HOST = "localhost";

  private final AtomicInteger proxyAccess = new AtomicInteger(0);
  private static final AwsCredentialsProvider credentialsProvider = () -> new AwsCredentials() {
    @Override
    public String accessKeyId() {
      return "a";
    }

    @Override
    public String secretAccessKey() {
      return "a";
    }
  };

  @BeforeEach
  public void setUp(VertxTestContext ctx) {
    vertx = Vertx.vertx();
    proxyServer = vertx.createHttpServer();
    proxyServer.requestHandler(req -> {
      proxyAccess.incrementAndGet();
      req.response().end();
    });
    proxyServer.listen(PROXY_PORT, PROXY_HOST, res -> {
      assertTrue(res.succeeded());
      ctx.completeNow();
    });
  }

  @AfterEach
  public void tearDown(VertxTestContext ctx) {
      if (proxyServer == null) {
          return;
      }
      proxyAccess.set(0);
      proxyServer.close(res -> {
          assertTrue(res.succeeded());
          ctx.completeNow();
      });
  }

  @Test
  @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
  public void testGetThroughProxy(VertxTestContext ctx) throws Exception {
    final KinesisAsyncClientBuilder builder = KinesisAsyncClient.builder()
      .region(Region.EU_WEST_1)
      .endpointOverride(new URI("http://localhost:1111")) // something that just doesn't exist, the only thing that matters is that every request has traveled through proxy
      .credentialsProvider(credentialsProvider);
    HttpClientOptions throughProxyOptions = new HttpClientOptions().setProxyOptions(new ProxyOptions().setHost(PROXY_HOST).setPort(PROXY_PORT));
    KinesisAsyncClient kinesis = VertxSdkClient.withVertx(builder, throughProxyOptions, vertx.getOrCreateContext()).build();
    assertEquals(proxyAccess.get(), 0, "Proxy access count should have been reset");
    kinesis
      .listStreams()
      .handle((res, err) -> {
        assertTrue(proxyAccess.get() > 0, "Requests should have been transferred through proxy");
        ctx.completeNow();
        return null;
      });
  }

}
