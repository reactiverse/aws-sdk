package io.reactiverse.awssdk.integration.kinesis;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClientBuilder;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamResponse;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;
import software.amazon.awssdk.services.kinesis.model.StreamDescription;
import software.amazon.awssdk.services.kinesis.model.StreamStatus;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.reactiverse.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "kinesis" })
public class VertxKinesisClientSpec extends LocalStackBaseSpec {

    private final static String STREAM = "my-awesome-stream";
    private final static SdkBytes DATA = SdkBytes.fromByteArray("Hello".getBytes());

    private String currentShardIterator;
    private long pollTimer = -1;
    private long streamTimer = -1;

    /**
     * Just create the Stream in a synchronous fashion, not using Vert.x
     * And wait for the stream to be created & ready (with the right nb. of shards)
     */
    @BeforeAll
    public static void createStream() throws Exception {
        System.setProperty(SdkSystemSetting.CBOR_ENABLED.property(), "false");
        KinesisClient client = KinesisClient.builder()
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(new URI(LocalstackDocker.INSTANCE.getEndpointKinesis()))
                .build();
        CreateStreamResponse resp = client.createStream(cs -> cs.streamName(STREAM).shardCount(1));
        assertNotNull(resp);
        // README: Stream creation can take some time. We have to wait or the Stream to be "ACTIVE" before making actual tests
        boolean streamReady = false;
        while(!streamReady) {
            Thread.sleep(1000L); // AWS recommendation: polling-frequency (even for DescribeStream) <= 1000ms
            final StreamDescription desc = client.describeStream(ds -> ds.streamName(STREAM)).streamDescription();
            streamReady = desc.streamStatus().equals(StreamStatus.ACTIVE);
            if (streamReady) {
                // Check that we have the correct number of shards before starting the actual testing
                assertEquals(1, desc.shards().size());
            }
        }
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void testPubSub(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final KinesisAsyncClient kinesis = kinesis(originalContext);
        single(kinesis.describeStream(this::streamDesc))
                .flatMap(descRes -> {
                    String shardId = descRes.streamDescription().shards().get(0).shardId();
                    return single(kinesis.getShardIterator(this.shardIterator(shardId)));
                })
                .doOnSuccess(getShardRes -> {
                    startPolling(vertx, ctx, kinesis, originalContext, getShardRes.shardIterator());
                    publishTestRecord(kinesis);
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private CompletableFuture<PutRecordResponse> publishTestRecord(KinesisAsyncClient kinesis) {
        return kinesis.putRecord(pr -> {
            pr.streamName(STREAM)
                    .partitionKey("Hello") // we don't care there's only 1 shard
                    .data(DATA);
        });
    }

    private void startPolling(Vertx vertx, VertxTestContext ctx, KinesisAsyncClient kinesis, Context originalContext, String shardIteratorId) {
        currentShardIterator = shardIteratorId;
        vertx.setPeriodic(1000L, t -> {
            pollTimer = t;
            kinesis.getRecords(rc ->
                    rc.shardIterator(currentShardIterator).limit(1)
            ).handle((getRecRes, getRecError) -> {
                if (getRecError != null) {
                    ctx.failNow(getRecError);
                    return null;
                }
                final List<Record> recs = getRecRes.records();
                if (recs.size() > 0) {
                    assertEquals(1, recs.size());
                    assertEquals(DATA, recs.get(0).data());
                    if (pollTimer > -1) {
                        vertx.cancelTimer(pollTimer);
                    }
                    ctx.completeNow();
                } else {
                    currentShardIterator = getRecRes.nextShardIterator();
                }
                return null;
            });
        });
    }

    private void streamDesc(DescribeStreamRequest.Builder dsr) {
        dsr.streamName(STREAM);
    }

    private Consumer<GetShardIteratorRequest.Builder> shardIterator(String shardId) {
        return gsi -> gsi.streamName(STREAM)
                        .shardIteratorType(ShardIteratorType.LATEST)
                        .shardId(shardId);
    }

    private KinesisAsyncClient kinesis(Context context) throws Exception {
        final URI kinesisURI = new URI(LocalstackDocker.INSTANCE.getEndpointKinesis());
        final KinesisAsyncClientBuilder builder = KinesisAsyncClient.builder()
                .region(Region.EU_WEST_1)
                .endpointOverride(kinesisURI)
                .credentialsProvider(credentialsProvider);
        return withVertx(builder, context).build();
    }

}
