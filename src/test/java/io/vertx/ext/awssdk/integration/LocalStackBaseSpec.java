package io.vertx.ext.awssdk.integration;

import cloud.localstack.docker.LocalstackDockerExtension;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.CompletableFuture;

@ExtendWith(VertxExtension.class)
@ExtendWith(LocalstackDockerExtension.class)
public abstract class LocalStackBaseSpec {

    protected static final AwsCredentialsProvider credentialsProvider = () -> new AwsCredentials() {
        @Override
        public String accessKeyId() {
            return "a";
        }

        @Override
        public String secretAccessKey() {
            return "a";
        }
    };


    protected static <T> Single<T> single(CompletableFuture<T> future) {
        final SingleOnSubscribe<T> sos = emitter ->
                future.handle((result, error) -> {
                    if (error != null) {
                        emitter.onError(error);
                    } else {
                        emitter.onSuccess(result);
                    }
                    return future;
                });
        return Single.create(sos);
    }

}
