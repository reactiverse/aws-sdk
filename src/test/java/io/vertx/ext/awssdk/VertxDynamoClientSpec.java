package io.vertx.ext.awssdk;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class VertxDynamoClientSpec {

    private final Vertx vertx = Vertx.vertx();
    private DynamoDBProxyServer ddb;
    private static final AwsCredentialsProvider credentialsProvider = new AwsCredentialsProvider() {
        @Override
        public AwsCredentials resolveCredentials() {
            return new AwsCredentials() {
                @Override
                public String accessKeyId() {
                    return "a";
                }

                @Override
                public String secretAccessKey() {
                    return "a";
                }
            };
        }
    };

    @Before
    public void startLocalDynamo() throws Exception {
        Properties props = System.getProperties();
        props.setProperty("sqlite4java.library.path", "build/libs/");
        ddb = ServerRunner
                .createServerFromCommandLineArgs(new String[]{"-inMemory"});
        ddb.start();
    }

    @After
    public void shutDown() throws Exception {
        if (ddb != null) {
            ddb.stop();
        }
    }

    @Test
    public void testCreateTableWithVertxClient() throws Exception {
        DynamoDbAsyncClient dynamo = DynamoDbAsyncClient.builder()
                .httpClient(new VertxNioAsyncHttpClient(vertx))
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(new URI("http://localhost:8000"))
                .build();
        CompletableFuture<CreateTableResponse> createTestTable = dynamo.createTable(builder -> {
            builder.tableName("TEST_TABLE");
        });
        CreateTableResponse response = createTestTable.get();
    }


}
