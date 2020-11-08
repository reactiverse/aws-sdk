package io.reactiverse.awssdk.integration.dynamodb;

import cloud.localstack.Localstack;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.reactiverse.awssdk.VertxSdkClient;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "dynamodb" }, imageTag = "0.12.2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    @Order(1)
    public void createTable(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.createTable(VertxDynamoClientSpec::createTable))
                .subscribe(createRes -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    @Order(2)
    public void listTables(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.listTables())
                .subscribe(listResp -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.verify(() -> {
                        assertTrue(listResp.tableNames().contains(TABLE_NAME));
                        ctx.completeNow();
                    });
                }, ctx::failNow);
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    @Order(3)
    public void addItemToTable(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.putItem(VertxDynamoClientSpec::putItemReq))
                .subscribe(putRes -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    @Order(4)
    public void getItemFromTable(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final DynamoDbAsyncClient dynamo = dynamo(originalContext);
        single(dynamo.getItem(VertxDynamoClientSpec::getItem))
                .subscribe(getRes -> {
                    assertContext(vertx, originalContext, ctx);
                    final AttributeValue isbn = getRes.item().get(ISBN_FIELD);
                    ctx.verify(() -> {
                        assertNotNull(isbn);
                        assertEquals(isbn.s(), ISBN_VALUE);
                        ctx.completeNow();
                    });
                }, ctx::failNow);
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

    private static PutItemRequest.Builder putItemReq(PutItemRequest.Builder pib) {
        return pib.tableName(TABLE_NAME)
                .item(ITEM);
    }

    private static GetItemRequest.Builder getItem(GetItemRequest.Builder gib) {
        return gib.tableName(TABLE_NAME)
                .attributesToGet(ISBN_FIELD)
                .key(ITEM);
    }

    private DynamoDbAsyncClient dynamo(Context context) throws Exception {
        final URI dynamoEndpoint = new URI(Localstack.INSTANCE.getEndpointDynamoDB());
        return VertxSdkClient.withVertx(
                DynamoDbAsyncClient.builder()
                        .region(Region.EU_WEST_1)
                        .credentialsProvider(credentialsProvider)
                        .endpointOverride(dynamoEndpoint)
                , context)
                .build();
    }

}
