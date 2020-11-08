package io.reactiverse.awssdk;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.LambdaAsyncClientBuilder;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;


@ExtendWith(VertxExtension.class)
class RetryContextTest {

    private static final int PORT = 9876;
    private static final String HOST = "localhost";
    private static final HttpServerOptions SERVER_OPTS = new HttpServerOptions()
            .setHost(HOST)
            .setPort(PORT);

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

    private int attempts = 0;

    @BeforeEach
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void setupFailServer(Vertx vertx, VertxTestContext ctx) {
        attempts = 0;
        vertx.createHttpServer(SERVER_OPTS)
                .requestHandler(req -> {
                    attempts++;
                    if (attempts == 1) { // only fail for first attempt, forcing a retry
                        req.response().setStatusCode(500).end();
                    } else {
                        req.response().setStatusCode(200).end(new JsonObject().encode());
                    }

                })
                .listen(ctx.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    void testRetry(Vertx vertx, VertxTestContext ctx) throws Exception {
        Context originalContext = vertx.getOrCreateContext();
        createLambdaClient(vertx, originalContext)
                .getFunction(gfb -> gfb.functionName("dummy"))
                .whenComplete((resp, error) -> {
                    if (error != null) {
                        ctx.failNow(error);
                    } else {
                        ctx.verify(() -> {
                            assertSame(originalContext, vertx.getOrCreateContext());
                            ctx.completeNow();
                        });

                    }
                });
    }

    LambdaAsyncClient createLambdaClient(Vertx vertx, Context ctx) throws Exception {
        final URI lambdaURI = new URI("http://" + HOST + ":" + PORT);
        final LambdaAsyncClientBuilder builder =
                LambdaAsyncClient.builder()
                        .credentialsProvider(credentialsProvider)
                        .region(Region.EU_WEST_1)
                        .endpointOverride(lambdaURI);
        return VertxSdkClient
                .withVertx(builder, ctx)
                .httpClient(new ContextAssertVertxNioAsyncHttpClient(vertx, ctx)) // override on purpose, we look forward to assert that the context is always the same
                .build();
    }
}
