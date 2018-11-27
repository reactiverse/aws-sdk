package io.vertx.ext.awssdk.integration.s3;

import cloud.localstack.docker.LocalstackDocker;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.awssdk.integration.LocalStackBaseSpec;
import io.vertx.ext.awssdk.reactivestreams.ReadStreamPublisher;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.vertx.ext.awssdk.VertxSdkClient.withVertx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "s3" })
@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
public class VertxS3ClientSpec extends LocalStackBaseSpec {

    private final static String BUCKET_NAME = "my-vertx-bucket";
    private final static String IMG_FOLDER = "src/test/resources/";
    private final static String RESOURCE_PATH = "s3/cairn_little.jpg";
    private final static String IMG_PATH = IMG_FOLDER + RESOURCE_PATH;
    private final static String IMG_NAME = "MyImage";
    private final static String ACL = "public-read-write";
    private final static OpenOptions READ_ONLY = new OpenOptions().setRead(true);

    private long fileSize;

    @BeforeEach
    public void fileSize() throws Exception {
        fileSize = ClassLoader.getSystemResource(RESOURCE_PATH).openConnection().getContentLength();
    }

    @Test
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    public void createS3BucketThenPushFile(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        final io.vertx.reactivex.core.Vertx rxVertx = new io.vertx.reactivex.core.Vertx(vertx);
        single(s3.createBucket(VertxS3ClientSpec::bucketOpts))
                .flatMap(createRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    return single(s3.listBuckets());
                })
                .flatMap(listRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertEquals(1, listRes.buckets().size());
                    final Bucket bucket = listRes.buckets().get(0);
                    assertEquals(BUCKET_NAME, bucket.name());
                    return rxVertx.fileSystem().rxOpen(IMG_PATH, READ_ONLY);
                })
                .flatMap(file -> {
                    final AsyncRequestBody body = AsyncRequestBody.fromPublisher(new ReadStreamPublisher<>(file.getDelegate()));
                    return single(s3.putObject(VertxS3ClientSpec::uploadImg, body));
                })
                .flatMap(putFileRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertNotNull(putFileRes.eTag());
                    return single(s3.listObjects(VertxS3ClientSpec::listObjects));
                })
                .flatMap(listRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertEquals(1, listRes.contents().size());
                    final S3Object myImg = listRes.contents().get(0);
                    assertNotNull(myImg);
                    assertEquals(IMG_NAME, myImg.key());
                    return single(s3.getObject(VertxS3ClientSpec::getImg, AsyncResponseTransformer.toBytes()));
                }).doOnSuccess(getRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    byte[] bytes = getRes.asByteArray();
                    assertEquals(fileSize, bytes.length); // We've sent, then received the whole file
                    ctx.completeNow();
                })
                .doOnError(ctx::failNow)
                .subscribe();
    }

    private static PutObjectRequest.Builder uploadImg(PutObjectRequest.Builder por) {
        return por.bucket(BUCKET_NAME)
                .key(IMG_NAME)
                .contentType(Mimetype.MIMETYPE_OCTET_STREAM);
    }

    private static CreateBucketRequest.Builder bucketOpts(CreateBucketRequest.Builder cbr) {
        return cbr.bucket(BUCKET_NAME)
                .acl(ACL);
    }

    private static ListObjectsRequest.Builder listObjects(ListObjectsRequest.Builder lor) {
        return lor.maxKeys(1).bucket(BUCKET_NAME);
    }

    private static GetObjectRequest.Builder getImg(GetObjectRequest.Builder gor) {
        return gor.key(IMG_NAME).bucket(BUCKET_NAME);
    }

    private S3AsyncClient s3(Context context) throws Exception {
        final URI s3Uri = new URI(LocalstackDocker.INSTANCE.getEndpointS3());
        final S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .serviceConfiguration(sc ->
                        sc.checksumValidationEnabled(false)
                            .pathStyleAccessEnabled(true) // from localstack documentation
                )
                .credentialsProvider(credentialsProvider)
                .endpointOverride(s3Uri)
                .region(Region.EU_WEST_1);
        return withVertx(builder, context).build();
    }

}
