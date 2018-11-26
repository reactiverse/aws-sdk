package io.vertx.ext.awssdk.integration.dynamodb;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.awssdk.integration.LocalStackBaseSpec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "dynamodb" })
public class VertxDynamoClientSpec extends LocalStackBaseSpec {

    private final static String TABLE_NAME = "TEST_TABLE";

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testCreateTableWithVertxClient(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.createTable(this::createTable))
                .flatMap(createRes -> {
                    final Context callbackContext = vertx.getOrCreateContext();
                    assertEquals(originalContext, callbackContext);
                    return single(dynamo.listTables()).doOnSuccess(listResp -> {
                        assertNotNull(listResp);
                        assertTrue(listResp.tableNames().contains(TABLE_NAME));
                        ctx.completeNow();
                    });
                }).doOnError(ctx::failNow).subscribe();
    }

    private CreateTableRequest.Builder createTable(CreateTableRequest.Builder builder) {
        return builder
                .tableName(TABLE_NAME)
                .provisionedThroughput(ps ->
                    ps.writeCapacityUnits(40000L)
                        .readCapacityUnits(40000L)
                ).attributeDefinitions(ad ->
                    ad.attributeName("id")
                        .attributeType(ScalarAttributeType.S)
                ).keySchema(ks ->
                    ks.keyType(KeyType.HASH)
                        .attributeName("id")
                );
    }

    private DynamoDbAsyncClient dynamo(Context context) throws Exception {
        final URI dynamoEndpoint = new URI(LocalstackDocker.INSTANCE.getEndpointDynamoDB());
        return withVertx(
                DynamoDbAsyncClient.builder()
                        .region(Region.EU_WEST_1)
                        .credentialsProvider(credentialsProvider)
                        .endpointOverride(dynamoEndpoint)
                , context)
                .build();
    }

}
