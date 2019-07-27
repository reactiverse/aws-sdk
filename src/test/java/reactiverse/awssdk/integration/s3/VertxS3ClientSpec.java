package reactiverse.awssdk.integration.s3;

import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.reactiverse.awssdk.reactivestreams.ReadStreamPublisher;
import io.reactivex.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.file.AsyncFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { "s3" }, randomizePorts = true)
@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Unfortunately causing docker issues")
class VertxS3ClientSpec extends LocalStackBaseSpec {

    private final static String BUCKET_NAME = "my-vertx-bucket";
    private final static String IMG_FOLDER = "src/test/resources/";
    private final static String RESOURCE_PATH = "s3/cairn_little.jpg";
    private final static String IMG_LOCAL_PATH = IMG_FOLDER + RESOURCE_PATH;
    private final static String IMG_S3_NAME = "my-image";
    private final static String ACL = "public-read-write";
    private final static OpenOptions READ_ONLY = new OpenOptions().setRead(true);

    private long fileSize;

    @BeforeEach
    public void fileSize() throws Exception {
        fileSize = ClassLoader.getSystemResource(RESOURCE_PATH).openConnection().getContentLength();
    }

    @Test
    @Order(1)
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    public void createS3Bucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.createBucket(VertxS3ClientSpec::createBucketReq))
                .subscribe(createRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Order(2)
    public void listBuckets(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.listBuckets())
                .subscribe(bucketList -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertEquals(1, bucketList.buckets().size());
                    final Bucket bucket = bucketList.buckets().get(0);
                    assertEquals(BUCKET_NAME, bucket.name());
                    ctx.completeNow();
                }, ctx::failNow
        );
    }

    @Test
    @Order(3)
    public void publishImageToBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        readFileFromDisk(vertx)
                .flatMap(file -> {
                    final AsyncRequestBody body = AsyncRequestBody.fromPublisher(new ReadStreamPublisher<>(file.getDelegate()));
                    return single(s3.putObject(VertxS3ClientSpec::uploadImgReq, body));
                })
                .subscribe(putFileRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertNotNull(putFileRes.eTag());
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Order(4)
    public void getImageFromBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.listObjects(VertxS3ClientSpec::listObjectsReq))
                .subscribe(listRes -> {
                    assertEquals(originalContext, vertx.getOrCreateContext());
                    assertEquals(1, listRes.contents().size());
                    final S3Object myImg = listRes.contents().get(0);
                    assertNotNull(myImg);
                    assertEquals(IMG_S3_NAME, myImg.key());
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Order(5)
    public void downloadImageFromBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.getObject(VertxS3ClientSpec::downloadImgReq, AsyncResponseTransformer.toBytes()))
            .subscribe(getRes -> {
                assertEquals(originalContext, vertx.getOrCreateContext());
                byte[] bytes = getRes.asByteArray();
                assertEquals(fileSize, bytes.length); // We've sent, then received the whole file
                ctx.completeNow();
            }, ctx::failNow);
    }

    /* Utility methods */
    private static Single<AsyncFile> readFileFromDisk(Vertx vertx) {
        final io.vertx.reactivex.core.Vertx rxVertx = new io.vertx.reactivex.core.Vertx(vertx);
        return rxVertx.fileSystem().rxOpen(IMG_LOCAL_PATH, READ_ONLY);
    }

    private static PutObjectRequest.Builder uploadImgReq(PutObjectRequest.Builder por) {
        return por.bucket(BUCKET_NAME)
                .key(IMG_S3_NAME)
                .contentType(Mimetype.MIMETYPE_OCTET_STREAM);
    }

    private static CreateBucketRequest.Builder createBucketReq(CreateBucketRequest.Builder cbr) {
        return cbr.bucket(BUCKET_NAME)
                .acl(ACL);
    }

    private static ListObjectsRequest.Builder listObjectsReq(ListObjectsRequest.Builder lor) {
        return lor.maxKeys(1).bucket(BUCKET_NAME);
    }

    private static GetObjectRequest.Builder downloadImgReq(GetObjectRequest.Builder gor) {
        return gor.key(IMG_S3_NAME).bucket(BUCKET_NAME);
    }

}
