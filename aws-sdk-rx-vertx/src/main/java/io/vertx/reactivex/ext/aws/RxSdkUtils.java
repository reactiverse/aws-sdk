package io.vertx.reactivex.ext.aws;

import io.reactivex.Single;

import java.util.concurrent.CompletableFuture;

public class RxSdkUtils {

    public static <T> Single<T> single(CompletableFuture<T> future) {
        return Single.create(emitter -> {
            future.whenComplete((result, error) -> {
                if (error != null) {
                    emitter.onError(error);
                } else {
                    emitter.onSuccess(result);
                }
            });
        });
    }

}
