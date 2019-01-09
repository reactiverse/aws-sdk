package io.reactiverse.awssdk.integration.lambda;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.reactivex.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.LambdaAsyncClientBuilder;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.reactiverse.awssdk.VertxSdkClient.withVertx;
import static io.reactiverse.awssdk.utils.ZipUtils.zip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "lambda" })
@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
public class VertxLambdaClientSpec extends LocalStackBaseSpec {

    private static final String LAMBDA_NAME = "My-Vertx-Lambda";
    private static final String EXEC_ARG = "name";
    private static final String ARG_VALUE = "Vert.x";
    private static final JsonObject EXPECTED_PAYLOAD = new JsonObject()
            .put("message", "Hello " + ARG_VALUE + "!");

    private LambdaAsyncClient lambdaClient;

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void testCreateThenInvokeLambda(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        lambdaClient = createLambdaClient(originalContext);
        createFunction()
                .flatMap(createRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertEquals(LAMBDA_NAME, createRes.functionName());
                    return invokeFunction();
                })
                .doOnSuccess(invokeRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertNull(invokeRes.functionError());
                    final SdkBytes payload = invokeRes.payload();
                    assertNotNull(payload);
                    final JsonObject received = new JsonObject(payload.asUtf8String());
                    assertEquals(EXPECTED_PAYLOAD, received);
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private Single<CreateFunctionResponse> createFunction() {
        return single(
                lambdaClient.createFunction(VertxLambdaClientSpec::lambdaDefinition)
        );
    }

    private Single<InvokeResponse> invokeFunction() {
        return single(
                lambdaClient.invoke(VertxLambdaClientSpec::lambdaInvokation)
        );
    }

    private static CreateFunctionRequest.Builder lambdaDefinition(CreateFunctionRequest.Builder fnb) {
        final SdkBytes functionFile = SdkBytes.fromByteArray(zip("lambda", "greeter.py"));
        return fnb.functionName(LAMBDA_NAME)
                .handler("greeter")
                .runtime(Runtime.PYTHON3_6)
                .code(vb ->
                    vb.zipFile(functionFile)
                );
    }

    private static InvokeRequest.Builder lambdaInvokation(InvokeRequest.Builder irb) {
        final JsonObject payload = new JsonObject()
                .put(EXEC_ARG, ARG_VALUE);
        return irb.functionName(LAMBDA_NAME)
                .payload(SdkBytes.fromUtf8String(payload.encode()));
    }

    private static LambdaAsyncClient createLambdaClient(Context ctx) throws Exception {
        final URI lambdaURI = new URI(LocalstackDocker.INSTANCE.getEndpointLambda());
        final LambdaAsyncClientBuilder builder =
                LambdaAsyncClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .region(Region.EU_WEST_1)
                    .endpointOverride(lambdaURI);
        return withVertx(builder, ctx).build();
    }


}
