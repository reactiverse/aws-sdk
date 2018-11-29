package io.vertx.ext.awssdk.integration.dynamodb;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.awssdk.integration.LocalStackBaseSpec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "dynamodb" })
public class VertxDynamoClientSpec extends LocalStackBaseSpec {

    private final static String TABLE_NAME = "BOOKS";
    private final static String ISBN_FIELD = "isbn";
    private final static String ISBN_VALUE = "9781617295621";
    private final static Map<String, AttributeValue> ITEM = new HashMap<>();
    static {
        ITEM.put(ISBN_FIELD, AttributeValue.builder().s(ISBN_VALUE).build());
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void createTableThenInsertThenGet(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.createTable(VertxDynamoClientSpec::createTable))
                .flatMap(createRes -> {
                    final Context callbackContext = vertx.getOrCreateContext();
                    assertEquals(originalContext, callbackContext);
                    return single(dynamo.listTables());
                })
                .flatMap(listResp -> {
                    final Context callbackContext = vertx.getOrCreateContext();
                    assertEquals(originalContext, callbackContext);
                    assertTrue(listResp.tableNames().contains(TABLE_NAME));
                    return single(dynamo.putItem(VertxDynamoClientSpec::putItem));
                }).flatMap(putRes -> {
                    final Context callbackContext = vertx.getOrCreateContext();
                    assertEquals(originalContext, callbackContext);
                    return single(dynamo.getItem(VertxDynamoClientSpec::getItem));
                }).doOnSuccess(getRes -> {
                    final Context callbackContext = vertx.getOrCreateContext();
                    assertEquals(originalContext, callbackContext);
                    final AttributeValue isbn = getRes.item().get(ISBN_FIELD);
                    assertNotNull(isbn);
                    assertEquals(isbn.s(), ISBN_VALUE);
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private static CreateTableRequest.Builder createTable(CreateTableRequest.Builder builder) {
        return builder
                .tableName(TABLE_NAME)
                .provisionedThroughput(ps ->
                    ps.writeCapacityUnits(40000L)
                        .readCapacityUnits(40000L)
                ).attributeDefinitions(ad ->
                    ad.attributeName(ISBN_FIELD)
                        .attributeType(ScalarAttributeType.S)
                ).keySchema(ks ->
                    ks.keyType(KeyType.HASH)
                        .attributeName(ISBN_FIELD)
                );
    }

    private static PutItemRequest.Builder putItem(PutItemRequest.Builder pib) {
        return pib.tableName(TABLE_NAME)
                .item(ITEM);
    }

    private static GetItemRequest.Builder getItem(GetItemRequest.Builder gib) {
        return gib.tableName(TABLE_NAME)
                .attributesToGet(ISBN_FIELD)
                .key(ITEM);
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
