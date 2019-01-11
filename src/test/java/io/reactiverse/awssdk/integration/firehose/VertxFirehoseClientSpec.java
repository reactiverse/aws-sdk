package io.reactiverse.awssdk.integration.firehose;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.reactiverse.awssdk.VertxSdkClient;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.reactivex.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.RxHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClient;
import software.amazon.awssdk.services.firehose.FirehoseAsyncClientBuilder;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.CreateDeliveryStreamResponse;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamDescription;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamStatus;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamType;
import software.amazon.awssdk.services.firehose.model.PutRecordRequest;
import software.amazon.awssdk.services.firehose.model.PutRecordResponse;
import software.amazon.awssdk.services.firehose.model.Record;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "firehose", "s3" })
public class VertxFirehoseClientSpec extends LocalStackBaseSpec {

    private final static String STREAM = "My-Vertx-Firehose-Stream";
    private final static DeliveryStreamType STREAM_TYPE = DeliveryStreamType.DIRECT_PUT;
    private final static String BUCKET = "firehose-bucket";
    private final static JsonObject FAKE_DATA = new JsonObject().put("producer", "vert.x");

    private FirehoseAsyncClient firehoseClient;
    private S3AsyncClient s3Client;

    /**
     * Just create the Stream in a synchronous fashion, not using Vert.x
     * And wait for the stream to be created & ready
     */
    @BeforeAll
    public static void createStream() throws Exception {
        System.setProperty(SdkSystemSetting.CBOR_ENABLED.property(), "false");
        S3Client s3sync = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.EU_WEST_1)
                .endpointOverride(s3URI())
                .build();
        // Create Sink bucket
        s3sync.createBucket(crb -> crb.bucket(BUCKET).acl("public-read-write"));
        List<S3Object> beforeObjects = s3sync.listObjects(lor -> lor.bucket(BUCKET)).contents();
        assertTrue(beforeObjects.isEmpty());
        // Create delivery stream, pointing to Sink S3 bucket
        FirehoseClient firehoseSync = FirehoseClient.builder()
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(getFirehoseURI())
                .build();
        CreateDeliveryStreamResponse resp = firehoseSync.createDeliveryStream(cs ->
                cs.deliveryStreamName(STREAM)
                    .deliveryStreamType(STREAM_TYPE)
                    .s3DestinationConfiguration(dest ->
                        dest.bucketARN("arn:aws:s3:::" + BUCKET)
                    )
        );
        assertNotNull(resp);
        // README: DeliveryStream creation can take some time. We have to wait or the Stream to be "ACTIVE" before making actual tests
        boolean streamReady = false;
        while(!streamReady) {
            Thread.sleep(1000L); // AWS recommendation: polling-frequency (even for DescribeStream) <= 1000ms
            final DeliveryStreamDescription desc = firehoseSync.describeDeliveryStream(ds -> ds.deliveryStreamName(STREAM)).deliveryStreamDescription();
            streamReady = desc.deliveryStreamStatus().equals(DeliveryStreamStatus.ACTIVE);
            assertEquals(STREAM_TYPE, desc.deliveryStreamType());
            assertEquals(STREAM, desc.deliveryStreamName());
        }
    }

    @Test
    @Timeout(value = 25, timeUnit = TimeUnit.SECONDS)
    public void testPublish(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        createS3Client(originalContext);
        createFirehoseClient(originalContext);
        publishTestData()
                .delay(5, TimeUnit.SECONDS, RxHelper.scheduler(vertx)) // Let localstack deal with Firehose to S3
                .flatMap(pubRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    return lists3Files();
                })
                .doOnSuccess(listRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    final List<S3Object> files = listRes.contents();
                    assertEquals(1, files.size());
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private Single<ListObjectsResponse> lists3Files() {
        return single(
            s3Client.listObjects(lor ->
                lor.bucket(BUCKET)
            )
        );
    }

    private Single<ResponseBytes<GetObjectResponse>> getFile(String bucket, String key) {
        LoggerFactory.getLogger(VertxFirehoseClientSpec.class).error("GETTING {} from S3", key);
        return single(
            s3Client.getObject(gor ->
                gor.bucket(bucket)
                    .key(key)
                    .ifModifiedSince(Instant.now().minus(2, ChronoUnit.HOURS))
            , AsyncResponseTransformer.toBytes())
        );
    }

    private Single<PutRecordResponse> publishTestData() {
        return single(
                firehoseClient.putRecord(VertxFirehoseClientSpec::testRecord)
        );
    }

    private static PutRecordRequest.Builder testRecord(PutRecordRequest.Builder prb) {
        final Record rec = Record.builder()
                .data(SdkBytes.fromUtf8String(FAKE_DATA.encode()))
                .build();
        return prb.deliveryStreamName(STREAM)
                .record(rec);
    }

    private void createS3Client(Context context) throws Exception {
        s3Client = s3(context);
    }

    private void createFirehoseClient(Context context) throws Exception {
        final FirehoseAsyncClientBuilder builder = FirehoseAsyncClient.builder()
                .region(Region.EU_WEST_1)
                .credentialsProvider(credentialsProvider)
                .endpointOverride(getFirehoseURI());
        firehoseClient = VertxSdkClient.withVertx(builder, context).build();
    }

    private static URI getFirehoseURI() throws Exception {
        return new URI(LocalstackDocker.INSTANCE.getEndpointFirehose());
    }
}
