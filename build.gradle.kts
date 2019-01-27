val vertxVersion = "3.6.2"
val awsSdkVersion = "2.3.7"
val junit5Version = "5.3.1"
val logbackVersion = "1.2.3"
val integrationOption = "tests.integration"


plugins {
    java
    jacoco
    `maven-publish`
    `java-library`
    signing
    id("org.sonarqube") version "2.6"
}

group = "io.reactiverse"
version = "0.0.1-SNAPSHOT"
val isRelease = !version.toString().endsWith("SNAPSHOT")

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
}

repositories {
    mavenCentral()
}

dependencies {
    api("io.vertx:vertx-core:$vertxVersion")
    api("software.amazon.awssdk:aws-core:$awsSdkVersion")

    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation("io.vertx:vertx-rx-java2:$vertxVersion")
    testImplementation("cloud.localstack:localstack-utils:0.1.15")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("ch.qos.logback:logback-core:$logbackVersion")
    testImplementation("software.amazon.awssdk:aws-sdk-java:$awsSdkVersion")

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

/**
 * Maven Publication
 **/
if (!project.hasProperty("ossrhUsername")) {
    extra["ossrhUsername"] = "foo"
}

if (!project.hasProperty("ossrhPassword")) {
    extra["ossrhPassword"] = "bar"
}

tasks.withType<Sign> {
    onlyIf { isRelease }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Reactive AWS SDK")
                url.set("https://github.com/reactiverse/aws-sdk")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("aesteve")
                        name.set("Arnaud Esteve")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:reactiverse/aws-sdk.git")
                    developerConnection.set("scm:git:git@github.com:reactiverse/aws-sdk.git")
                    url.set("https://github.com/reactiverse/aws-sdk")
                }
            }
        }
    }
    repositories {
        maven {
            name = "SonatypeOSS"
            url = if (isRelease) {
                uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            } else {
                uri("https://oss.sonatype.org/content/repositories/snapshots/")
            }
            credentials {
                val ossrhUsername: String by project
                val ossrhPassword: String by project
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}
