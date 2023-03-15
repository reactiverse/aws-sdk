package io.reactiverse.awssdk.integration.s3;

import cloud.localstack.ServiceName;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.reactiverse.awssdk.converters.VertxAsyncResponseTransformer;
import io.reactiverse.awssdk.integration.LocalStackBaseSpec;
import io.reactiverse.awssdk.reactivestreams.ReadStreamPublisher;
import io.reactivex.Single;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.WriteStream;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.file.AsyncFile;
import org.junit.jupiter.api.BeforeEach;
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
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "tests.integration", matches = "localstack")
@LocalstackDockerProperties(services = { ServiceName.S3 }, imageTag = "1.4.0")
@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    void fileSize() throws Exception {
        fileSize = ClassLoader.getSystemResource(RESOURCE_PATH).openConnection().getContentLength();
    }

    @Test
    @Order(1)
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    void createS3Bucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.createBucket(VertxS3ClientSpec::createBucketReq))
                .subscribe(createRes -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.completeNow();
                }, ctx::failNow);
    }

    @Test
    @Order(2)
    void listBuckets(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.listBuckets())
                .subscribe(bucketList -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.verify(() -> {
                        assertEquals(1, bucketList.buckets().size());
                        final Bucket bucket = bucketList.buckets().get(0);
                        assertEquals(BUCKET_NAME, bucket.name());
                        ctx.completeNow();
                    });
                }, ctx::failNow
        );
    }

    @Test
    @Order(3)
    void publishImageToBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        readFileFromDisk(vertx)
                .flatMap(file -> {
                    final AsyncRequestBody body = AsyncRequestBody.fromPublisher(new ReadStreamPublisher<>(file.getDelegate()));
                    return single(s3.putObject(VertxS3ClientSpec::uploadImgReq, body));
                })
                .subscribe(putFileRes -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.verify(() -> {
                        assertNotNull(putFileRes.eTag());
                        ctx.completeNow();
                    });
                }, ctx::failNow);
    }

    @Test
    @Order(4)
    void getImageFromBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.listObjects(VertxS3ClientSpec::listObjectsReq))
                .subscribe(listRes -> {
                    assertContext(vertx, originalContext, ctx);
                    ctx.verify(() -> {
                        assertEquals(1, listRes.contents().size());
                        final S3Object myImg = listRes.contents().get(0);
                        assertNotNull(myImg);
                        assertEquals(IMG_S3_NAME, myImg.key());
                        ctx.completeNow();
                    });
                }, ctx::failNow);
    }

    @Test
    @Order(5)
    void downloadImageFromBucket(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        single(s3.getObject(VertxS3ClientSpec::downloadImgReq, AsyncResponseTransformer.toBytes()))
            .subscribe(getRes -> {
                assertContext(vertx, originalContext, ctx);
                byte[] bytes = getRes.asByteArray();
                ctx.verify(() -> {
                    assertEquals(fileSize, bytes.length); // We've sent, then received the whole file
                    ctx.completeNow();
                });
            }, ctx::failNow);
    }

    @Test
    @Order(6)
    void downloadImageFromBucketToPump(Vertx vertx, VertxTestContext ctx) throws Exception {
        final Context originalContext = vertx.getOrCreateContext();
        final S3AsyncClient s3 = s3(originalContext);
        Buffer received = Buffer.buffer();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        VertxAsyncResponseTransformer<GetObjectResponse> transformer = new VertxAsyncResponseTransformer<>(new WriteStream<Buffer>() {
          @Override
          public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return null;
          }

          @Override
          public Future<Void> write(Buffer data) {
            received.appendBuffer(data);
            return Future.succeededFuture();
          }

          @Override
          public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            received.appendBuffer(data);
            handler.handle(null);
          }

          @Override
          public void end(Handler<AsyncResult<Void>> handler) {
            assertTrue(handlerCalled.get(), "Response handler should have been called before first bytes are received");
            if (received.length() == fileSize) ctx.completeNow();
            handler.handle(null);
          }

          @Override
          public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return null;
          }

          @Override
          public boolean writeQueueFull() {
            return false;
          }

          @Override
          public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
            return null;
          }
        });
        transformer.setResponseHandler(resp -> {
            handlerCalled.set(true);
        });
        single(s3.getObject(VertxS3ClientSpec::downloadImgReq, transformer))
                .subscribe(getRes -> {}, ctx::failNow);
    }

    @Test
    @Order(7)
    void downloadImageFromBucketWithoutSettingResponseHandler(Vertx vertx, VertxTestContext ctx) throws Exception {
      final Context originalContext = vertx.getOrCreateContext();
      final S3AsyncClient s3 = s3(originalContext);
      final Buffer received = Buffer.buffer();
      AtomicBoolean handlerCalled = new AtomicBoolean(false);
      VertxAsyncResponseTransformer<GetObjectResponse> transformer = new VertxAsyncResponseTransformer<>(new WriteStream<Buffer>() {
          @Override
          public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
            return null;
          }

          @Override
          public Future<Void> write(Buffer data) {
            received.appendBuffer(data);
            return Future.succeededFuture();
          }

          @Override
          public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            received.appendBuffer(data);
            handler.handle(null);
          }

          @Override
          public void end(Handler<AsyncResult<Void>> handler) {
            assertTrue(handlerCalled.get(), "Response handler should have been called before first bytes are received");
            if (received.length() == fileSize) ctx.completeNow();
            handler.handle(null);
          }

          @Override
          public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return null;
          }

          @Override
          public boolean writeQueueFull() {
            return false;
          }

          @Override
          public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
            return null;
          }
        });
        transformer.setResponseHandler(resp -> {
          handlerCalled.set(true);
        });
        single(s3.getObject(VertxS3ClientSpec::downloadImgReq, transformer))
                .subscribe(getRes -> ctx.completeNow(), ctx::failNow);
    }

  @Test
  @Order(8)
  void listObjectsV2(Vertx vertx, VertxTestContext ctx) throws Exception {
    final Context originalContext = vertx.getOrCreateContext();
    final S3AsyncClient s3 = s3(originalContext);
    single(s3.putObject(b -> putObjectReq(b, "obj1"), AsyncRequestBody.fromString("hello")))
      .flatMap(putObjectResponse1 -> single(s3.putObject(b -> putObjectReq(b, "obj2"), AsyncRequestBody.fromString("hi"))))
      .flatMap(putObjectResponse2 -> single(s3.listObjectsV2(VertxS3ClientSpec::listObjectsV2Req))
        .flatMap(listObjectsV2Response1 -> single(s3.listObjectsV2(b -> listObjectsV2ReqWithContToken(b, listObjectsV2Response1.nextContinuationToken())))
          .map(listObjectsV2Response2 -> {
            List<S3Object> allObjects = new ArrayList<>(listObjectsV2Response1.contents());
            allObjects.addAll(listObjectsV2Response2.contents());
            return allObjects;
          })
        ))
      .subscribe(allObjects -> {
        ctx.verify(() -> {
          assertEquals(3, allObjects.size());
          ctx.completeNow();
        });
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

  private static ListObjectsV2Request.Builder listObjectsV2Req(ListObjectsV2Request.Builder lovr) {
    return  lovr.maxKeys(2).bucket(BUCKET_NAME);
  }

  private static ListObjectsV2Request.Builder listObjectsV2ReqWithContToken(ListObjectsV2Request.Builder lovr, String token) {
    return lovr.maxKeys(2).bucket(BUCKET_NAME).continuationToken(token);
  }

  private static PutObjectRequest.Builder putObjectReq(PutObjectRequest.Builder por, String key) {
    return por.bucket(BUCKET_NAME).key(key);
  }

}
