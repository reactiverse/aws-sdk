package io.reactiverse.awssdk;

import io.vertx.core.Context;
import io.vertx.core.http.HttpClientOptions;
import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;

public interface VertxSdkClient {

  static<C extends SdkClient, B extends AwsAsyncClientBuilder<B, C>> B withVertx(B builder, Context context) {
    return builder
      .httpClient(new VertxNioAsyncHttpClient(context))
      .asyncConfiguration(conf ->
        conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
      );
  }

  static<C extends SdkClient, B extends AwsAsyncClientBuilder<B, C>> B withVertx(B builder, HttpClientOptions clientOptions, Context context) {
    return builder
      .httpClient(new VertxNioAsyncHttpClient(context, clientOptions))
      .asyncConfiguration(conf ->
        conf.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, new VertxExecutor(context))
      );
  }

}
