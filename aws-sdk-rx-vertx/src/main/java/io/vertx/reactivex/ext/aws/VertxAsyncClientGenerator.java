package io.vertx.reactivex.ext.aws;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodTypeSignature;
import io.github.classgraph.TypeParameter;
import io.reactivex.Single;
import io.vertx.core.Context;
import io.vertx.ext.awssdk.VertxSdkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class VertxAsyncClientGenerator {

    private final static Logger LOG = LoggerFactory.getLogger(VertxAsyncClientGenerator.class);

    private final static String VERTX_PKG = "io.vertx.reactivex.ext.aws";
    private final static String SRC_GENERATION_PATH = "aws-sdk-rx-vertx/generated-src/";

    private final static String DELEGATE_FIELD = "_delegate";
    private final static String UTILS_VERTX_CREATOR = "withVertx";
    private final static String SINGLE_METHOD = "single";


    private ClassInfo originalClient;

    public VertxAsyncClientGenerator(ClassInfo originalClient) {
        this.originalClient = originalClient;
    }

    public void generate() {
        Class<?> originalClientClass = originalClient.loadClass();
        MethodInfoList builderMethods = originalClient.getMethodInfo("builder");
        if (builderMethods.isEmpty()) {
            LOG.error("Could not find builder for class {}. Skipping code generation", originalClientClass);
            return;
        }
        MethodInfo builderMethod = builderMethods.getSingleMethod("builder");
        if (builderMethod == null) {
            LOG.error("Could not find builder for class {}. Skipping code generation", originalClientClass);
            return;
        }
        Class<?> builderClass = builderMethod.loadClassAndGetMethod().getReturnType();
        String newName = "Vertx" + originalClient.getSimpleName();
        TypeSpec.Builder newClientClass = TypeSpec.classBuilder(newName)
                .addModifiers(Modifier.PUBLIC);
        MethodSpec constructor = vertxContextConstructor(builderClass, newName);
        newClientClass.addMethod(constructor);
        newClientClass.addField(originalClient.loadClass(), DELEGATE_FIELD, Modifier.PRIVATE, Modifier.FINAL);
        originalClient.getMethodInfo()
                .stream()
                .filter(mi -> {
                    return !mi.isStatic();
                })
                .map(this::copyOrReplaceMethod)
                .forEach(newClientClass::addMethod);
        JavaFile vertxClientFile = JavaFile.builder(VERTX_PKG, newClientClass.build())
                .addStaticImport(VertxSdkClient.class, UTILS_VERTX_CREATOR)
                .addStaticImport(RxSdkUtils.class, SINGLE_METHOD)
                .build();

        try {
            LOG.info("Generating {} to {}", newName, SRC_GENERATION_PATH);
            vertxClientFile.writeTo(new File(SRC_GENERATION_PATH ));
        } catch (Exception ioe) {
            LOG.error("Could not create {}.{}", VERTX_PKG, newName, ioe);
        }
    }

    private MethodSpec copyOrReplaceMethod(MethodInfo info) {
        Method method = info.loadClassAndGetMethod();
        MethodSpec.Builder builder = MethodSpec.methodBuilder(info.getName())
                .addModifiers(Modifier.PUBLIC);

        if (info.getTypeSignature() != null) {
            try {
                Class<? extends MethodTypeSignature> clazz = info.getTypeSignature().getClass();
                Field f = clazz.getDeclaredField("typeParameters");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<TypeParameter> methodParams = (List<TypeParameter>) f.get(info.getTypeSignature());
                methodParams.forEach(methodParam -> {
                    builder.addTypeVariable(TypeVariableName.get(methodParam.getName()));
                });
            } catch (IllegalAccessException | NoSuchFieldException e) {
                LOG.error("Could not extract method's type parameter", e);
            }
        }
        List<String> paramNames = new ArrayList<>();
        int i = 0;
        for (Parameter parameter : method.getParameters()) {
            String paramName = decapitalize(parameter.getType().getSimpleName());
            if (paramNames.contains(paramName)) {
                paramName += ++i;
            }
            try {
                ParameterizedType paramType = (ParameterizedType) parameter.getParameterizedType();
                ParameterizedTypeName parameterizedTypeName = ParameterizedTypeName.get(parameter.getType(), paramType.getActualTypeArguments());
                builder.addParameter(
                        ParameterSpec
                                .builder(parameterizedTypeName, paramName)
                                .build()
                );
            } catch (ClassCastException cce) {
                builder.addParameter(ParameterSpec.builder(parameter.getType(), paramName).build());
            }
            paramNames.add(paramName);
        }

        ParameterizedType returnType;
        try {
            returnType = (ParameterizedType) method.getGenericReturnType();
        } catch(ClassCastException cce) {
            // method does not return a generic type
            return copyMethod(builder, paramNames, info.getName(), method.getReturnType());
        }
        if (!returnType.getRawType().equals(CompletableFuture.class)) {
            return copyMethod(builder, paramNames, info.getName(), method.getReturnType());
        }
        return replaceMethodByRx(builder, paramNames, info.getName(), returnType);
    }

    private MethodSpec copyMethod(MethodSpec.Builder builder, List<String> paramNames, String methodName, Type returnType) {
        String paramsLitterals = paramNames.stream().collect(Collectors.joining(", ", "(", ")"));
        String returns = returnType.equals(Void.TYPE) ? "" : "return ";
        builder.addStatement(returns + "$N.$N" + paramsLitterals, DELEGATE_FIELD, methodName);
        return builder
                .returns(returnType)
                .build();
    }

    private MethodSpec replaceMethodByRx(MethodSpec.Builder builder, List<String> paramNames, String methodName, ParameterizedType returnType) {
        String paramsLitterals = paramNames.stream().collect(Collectors.joining(", ", "(", ")"));
        builder.addStatement("return single($N.$N" + paramsLitterals + ")", DELEGATE_FIELD, methodName);
        return builder
                .returns(ParameterizedTypeName.get(Single.class, returnType.getActualTypeArguments()))
                .build();
    }


    private MethodSpec vertxContextConstructor(Class<?> clazz, String simpleName) {
        String delegateParam = "awsAsyncClient";
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(Context.class, "context").build())
                .addParameter(ParameterSpec.builder(clazz, delegateParam).build())
                .addStatement("this.$N = " + UTILS_VERTX_CREATOR + "($N, context).build()", DELEGATE_FIELD, delegateParam)
                .build();
    }

    private String decapitalize(String original) {
        return Character.toLowerCase(original.charAt(0)) + original.substring(1);
    }

}
