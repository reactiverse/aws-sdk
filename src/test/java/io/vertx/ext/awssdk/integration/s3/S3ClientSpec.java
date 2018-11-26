package io.vertx.ext.awssdk.integration.s3;

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
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "s3" })
public class S3ClientSpec extends LocalStackBaseSpec {

    private final static String BUCKET_NAME = "my-vertx-bucket";
    private final static String ACL = "public-read-write";

    @Test
    @Timeout(value = 25, timeUnit = TimeUnit.SECONDS)
    public void createS3Bucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.createBucket(this::bucketOpts))
                .flatMap(createRes ->
                        single(s3.listBuckets()).doOnSuccess(listRes -> {
                            assertNotNull(listRes);
                            assertEquals(originalContext, vertx.getOrCreateContext());
                            assertEquals(1, listRes.buckets().size());
                            Bucket bucket = listRes.buckets().get(0);
                            assertEquals(BUCKET_NAME, bucket.name());
                            ctx.completeNow();
                }))
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private CreateBucketRequest.Builder bucketOpts(CreateBucketRequest.Builder cbr) {
        return cbr.bucket(BUCKET_NAME)
                .acl(ACL);
    }

    private S3AsyncClient s3(Context context) throws Exception {
        final URI s3Uri = new URI(LocalstackDocker.INSTANCE.getEndpointS3());
        final S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .serviceConfiguration(sc -> sc.pathStyleAccessEnabled(true)) // from localstack documentation
                .credentialsProvider(credentialsProvider)
                .endpointOverride(s3Uri)
                .region(Region.EU_WEST_1);
        return withVertx(builder, context).build();
    }

}
