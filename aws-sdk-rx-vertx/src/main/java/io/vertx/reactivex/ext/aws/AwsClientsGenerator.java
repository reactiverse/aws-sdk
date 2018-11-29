package io.vertx.reactivex.ext.aws;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkClient;

import java.util.ArrayList;
import java.util.List;

public class AwsClientsGenerator {

    final static String AWS_CLIENTS_PKG = "software.amazon.awssdk.services";
    final static List<String> AWS_MODULES = new ArrayList<>();
    static {
        AWS_MODULES.add("acm");
        AWS_MODULES.add("apigateway");
        AWS_MODULES.add("applicationautoscaling");
        AWS_MODULES.add("applicationdiscovery");
        AWS_MODULES.add("appstream");
        AWS_MODULES.add("appsync");
        AWS_MODULES.add("athena");
        AWS_MODULES.add("autoscaling");
        AWS_MODULES.add("batch");
        AWS_MODULES.add("budgets");
        AWS_MODULES.add("clouddirectory");
        AWS_MODULES.add("cloudformation");
        AWS_MODULES.add("cloudfront");
        AWS_MODULES.add("cloudhsm");
        AWS_MODULES.add("cloudsearch");
        AWS_MODULES.add("cloudtrail");
        AWS_MODULES.add("cloudwatch");
        AWS_MODULES.add("cloudwatchevents");
        AWS_MODULES.add("cloudwatchlogs");
        AWS_MODULES.add("dynamodb");
        AWS_MODULES.add("kinesis");
        AWS_MODULES.add("s3");
    }

    public static void main(String... args){
        try(
            ScanResult scan = new ClassGraph()
                    .whitelistPackages(AWS_CLIENTS_PKG)
                    .enableMethodInfo()
                    .scan()
        ) {
            ClassInfoList asyncClients = scan.getAllInterfaces().filter(ci -> {
                return ci.implementsInterface(SdkClient.class.getName()) && ci.getSimpleName().contains("Async");
            });
            asyncClients.forEach(asyncClient -> {
                    new VertxAsyncClientGenerator(asyncClient).generate();
            });
        }
    }


}
