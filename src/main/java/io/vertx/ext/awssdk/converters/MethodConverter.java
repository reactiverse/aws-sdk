package io.vertx.ext.awssdk.converters;

import io.vertx.core.http.HttpMethod;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class MethodConverter {

    private MethodConverter() {}
    private static Map<SdkHttpMethod, HttpMethod> sdkToVertx = new EnumMap<>(SdkHttpMethod.class);
    static {
        sdkToVertx.put(SdkHttpMethod.GET, HttpMethod.GET);
        sdkToVertx.put(SdkHttpMethod.POST, HttpMethod.POST);
        sdkToVertx.put(SdkHttpMethod.PUT, HttpMethod.PUT);
        sdkToVertx.put(SdkHttpMethod.DELETE, HttpMethod.DELETE);
        sdkToVertx.put(SdkHttpMethod.HEAD, HttpMethod.HEAD);
        sdkToVertx.put(SdkHttpMethod.PATCH, HttpMethod.PATCH);
        sdkToVertx.put(SdkHttpMethod.OPTIONS, HttpMethod.OPTIONS);
    }

    public static HttpMethod awsToVertx(SdkHttpMethod method) {
        return sdkToVertx.get(method);
    }

}
