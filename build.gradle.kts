val vertxVersion = "3.8.0"
val awsSdkVersion = "2.7.8"
val junit5Version = "5.4.0"
val logbackVersion = "1.2.3"
val integrationOption = "tests.integration"

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    id("com.jaredsburrows.license") version "0.8.42"
    id("org.sonarqube") version "2.6"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

group = "io.reactiverse"
version = "0.0.1-SNAPSHOT"

dependencies {
    api("io.vertx:vertx-core:$vertxVersion")
    api("software.amazon.awssdk:aws-core:$awsSdkVersion")

    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation("io.vertx:vertx-rx-java2:$vertxVersion")
    testImplementation("cloud.localstack:localstack-utils:0.1.22")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("ch.qos.logback:logback-core:$logbackVersion")
    testImplementation("software.amazon.awssdk:aws-sdk-java:$awsSdkVersion")

    testCompile("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

jacoco {
    toolVersion = "0.8.2"
}

tasks {

    jacocoTestReport {
        dependsOn(":test")
        reports {
            xml.isEnabled = true
            csv.isEnabled = false
            html.destination = file("$buildDir/jacocoHtml")
        }
    }

    withType<Test> {
        useJUnitPlatform()
        systemProperties[integrationOption] = System.getProperty(integrationOption)
        maxParallelForks = 1
    }

    withType<Wrapper> {
        gradleVersion = "5.4.1"
    }
}

