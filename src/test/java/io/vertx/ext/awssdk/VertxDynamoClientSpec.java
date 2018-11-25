package io.vertx.ext.awssdk;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.cli.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@RunWith(VertxUnitRunner.class)
public class VertxDynamoClientSpec {

    private final URI uri;
    private final Vertx vertx = Vertx.vertx();
    private DynamoDBProxyServer ddb;
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

    public VertxDynamoClientSpec() throws Exception {
        this.uri = new URI("http://localhost:8000");
    }

    @Before
    public void startLocalDynamo(TestContext ctx) {
        Properties props = System.getProperties();
        props.setProperty("sqlite4java.library.path", "build/libs/");
        vertx.executeBlocking(fut -> {
            try {
                ddb = ServerRunner
                        .createServerFromCommandLineArgs(new String[]{"-inMemory"});
                ddb.start();
                fut.complete();
            } catch (Exception e) {
                fut.fail(e);
            }
        }, ctx.asyncAssertSuccess());
    }

    @After
    public void shutDown() throws Exception {
        if (ddb != null) {
            ddb.stop();
        }
    }

    @Test
    public void testCreateTableWithVertxClient(TestContext ctx) throws Exception {
        final Async async = ctx.async();
        final Context originalContext = vertx.getOrCreateContext();
        originalContext.runOnContext(v -> {
            final DynamoDbAsyncClient dynamo = createClient(originalContext);
            final CompletableFuture<CreateTableResponse> createTestTable = dynamo.createTable(this::createTable);
            createTestTable.handle((response, error) -> {
                ctx.assertNull(error);
                ctx.assertNotNull(response);
                Context callbackContext = vertx.getOrCreateContext();
                ctx.assertEquals(originalContext, callbackContext);
                async.complete();
                return createTestTable;
            });
        });
    }


    private DynamoDbAsyncClient createClient(Context context) {
        return DynamoDbAsyncClient.builder()
                .httpClient(new VertxNioAsyncHttpClient(vertx))
                .asyncConfiguration(conf ->
                        conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
                )
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(uri)
                .build();
    }

    private void createTable(CreateTableRequest.Builder builder) {
        builder.tableName("TEST_TABLE")
                .provisionedThroughput(ps -> {
                    ps.writeCapacityUnits(40000L);
                    ps.readCapacityUnits(40000L);
                })
                .attributeDefinitions(ad -> {
                    ad.attributeName("id");
                    ad.attributeType(ScalarAttributeType.S);
                }).keySchema(ks -> {
                    ks.keyType(KeyType.HASH)
                            .attributeName("id");
                }
        );
    }

}
