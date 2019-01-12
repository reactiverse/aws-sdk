val vertxVersion = "3.6.2"
val awsSdkVersion = "2.2.0"
val junit5Version = "5.3.1"
val logbackVersion = "1.2.3"
val integrationOption = "tests.integration"

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("com.jaredsburrows:gradle-license-plugin:0.8.42")
    }
}

plugins {
    java
    jacoco
    maven
    `java-library`
    id("com.jaredsburrows.license") version("0.8.42")
    id("org.sonarqube") version "2.6"
}

group = "io.reactiverse"
version = "0.0.1-SNAPSHOT"

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
}

repositories {
    mavenCentral()
}

dependencies {
    compile("io.vertx:vertx-core:$vertxVersion")
    compile("software.amazon.awssdk:aws-sdk-java:$awsSdkVersion")

    testCompile("io.vertx:vertx-junit5:$vertxVersion")
    testCompile("io.vertx:vertx-rx-java2:$vertxVersion")
    testCompile("cloud.localstack:localstack-utils:0.1.15")
    testCompile("ch.qos.logback:logback-classic:$logbackVersion")
    testCompile("ch.qos.logback:logback-core:$logbackVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperties[integrationOption] = System.getProperty(integrationOption)
}

jacoco {
    toolVersion = "0.8.2"
}

tasks.jacocoTestReport {
    dependsOn(":test")
    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.destination = file("$buildDir/jacocoHtml")
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.1.1"
}
