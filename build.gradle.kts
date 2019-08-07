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

project.extra["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT")

if (!project.hasProperty("ossrhUsername")) {
    logger.warn("No ossrhUsername property defined in your Gradle properties file to deploy to Maven Central, using 'foo' to make the build pass")
    project.extra["ossrhUsername"] = "foo"
}
if (!project.hasProperty("ossrhPassword")) {
    logger.warn("No ossrhPassword property defined in your Gradle properties file to deploy to Maven Central, using 'bar' to make the build pass")
    project.extra["ossrhPassword"] = "bar"
}

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

    create<Copy>("javadocToDocsFolder") {
        from(javadoc)
        into("docs/javadoc")
    }

    assemble {
        dependsOn("javadocToDocsFolder")
    }

    create<Jar>("sourcesJar") {
        from(sourceSets.main.get().allJava)
        archiveClassifier.set("sources")
    }

    create<Jar>("javadocJar") {
        from(javadoc)
        archiveClassifier.set("javadoc")
    }

    javadoc {
        if (JavaVersion.current().isJava9Compatible) {
            (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
        }
        (options as StandardJavadocDocletOptions).links(
            "http://docs.oracle.com/javase/8/docs/api/",
            "https://sdk.amazonaws.com/java/api/latest/",
            "http://vertx.io/docs/3.8.0/apidocs/",
            "http://www.reactive-streams.org/reactive-streams-1.0.0-javadoc/",
            "http://netty.io/4.1/api/"
        )
    }

    withType<Sign> {
        onlyIf { project.extra["isReleaseVersion"] as Boolean }
    }

    withType<Wrapper> {
        gradleVersion = "5.4.1"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                name.set(project.name)
                description.set("Reactiverse AWS SDK 2 with Vert.x")
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
                        email.set("arnaud.esteve@gmail.com")
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
        // To locally check out the poms
        maven {
            val releasesRepoUrl = uri("$buildDir/repos/releases")
            val snapshotsRepoUrl = uri("$buildDir/repos/snapshots")
            name = "BuildDir"
            url = if (project.extra["isReleaseVersion"] as Boolean) releasesRepoUrl else snapshotsRepoUrl
        }
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            name = "SonatypeOSS"
            url = if (project.extra["isReleaseVersion"] as Boolean) releasesRepoUrl else snapshotsRepoUrl
            credentials {
                val ossrhUsername: String by project
                val ossrhPassword: String by project
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

