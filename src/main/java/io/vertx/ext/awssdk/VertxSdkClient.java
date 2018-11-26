package io.vertx.ext.awssdk;

import io.vertx.core.Context;
import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;

public interface VertxSdkClient {

    static<C extends SdkClient, B extends AwsAsyncClientBuilder<B, C>> B withVertx(B builder, Context context) {
        return builder
                .httpClient(new VertxNioAsyncHttpClient(context.owner()))
                .asyncConfiguration(conf ->
                        conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
                );
    }

}
