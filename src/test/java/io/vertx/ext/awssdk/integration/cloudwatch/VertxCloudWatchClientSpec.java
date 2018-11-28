package io.vertx.ext.awssdk.integration.cloudwatch;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.awssdk.integration.LocalStackBaseSpec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;
import software.amazon.awssdk.services.cloudwatch.model.PutDashboardRequest;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "cloudwatch" })
@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
public class VertxCloudWatchClientSpec extends LocalStackBaseSpec {

    private final static String DASHBOARD_NAME = "my-vertx-dashboard";
    /*
        From AWS doc example:
        {
            widgets:[
              {
                 "type":"text",
                 "x":0,
                 "y":7,
                 "width":3,
                 "height":3,
                 "properties":{
                    "markdown":"Hello world"
                 }
              }
            ]
          }
     */
    private final static JsonObject MARKDOWN_WIDGET = new JsonObject()
            .put("type", "text")
            .put("x", 0)
            .put("y", 7)
            .put("width", 3)
            .put("height", 3)
            .put("properties", new JsonObject().put("markdown", "Hello Vert.x"));
    private final static JsonArray DASHBOARD_WIDGETS = new JsonArray().add(MARKDOWN_WIDGET);
    private final static JsonObject DASHBOARD_BODY = new JsonObject().put("widgets", DASHBOARD_WIDGETS);


    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCreateDashboard(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final CloudWatchAsyncClient cloudwatch = cloudwatchClient(originalContext);
        // TODO: create an alarm and send metric data to trigger alarm ?
        single(cloudwatch.putDashboard(VertxCloudWatchClientSpec::createDashboard))
                .doOnSuccess(createRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertTrue(createRes.dashboardValidationMessages().isEmpty());
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private static PutDashboardRequest.Builder createDashboard(PutDashboardRequest.Builder pdr) {
        return pdr.dashboardName(DASHBOARD_NAME)
                .dashboardBody(DASHBOARD_BODY.encode());
    }

    private static CloudWatchAsyncClient cloudwatchClient(Context ctx) throws Exception {
        final URI cloudwatchURI = new URI(LocalstackDocker.INSTANCE.getEndpointCloudWatch());
        final CloudWatchAsyncClientBuilder builder =
                CloudWatchAsyncClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .region(Region.EU_WEST_1)
                    .endpointOverride(cloudwatchURI);
        return withVertx(builder, ctx).build();
    }


}
