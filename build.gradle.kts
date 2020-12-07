val vertxVersion = "4.0.0.CR2"
val awsSdkVersion = "2.15.40"
val junit5Version = "5.7.0"
val logbackVersion = "1.2.3"
val integrationOption = "tests.integration"

group = "io.reactiverse"
version = "0.7.0"

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("com.jfrog.bintray") version "1.8.5"
    id("com.jaredsburrows.license") version "0.8.42"
    id("org.sonarqube") version "3.0"
    id("com.github.ben-manes.versions") version "0.34.0"
}

// In order to publish SNAPSHOTs to Sonatype Snapshots repository => the CI should define such `ossrhUsername` and `ossrhPassword` properties
if (!project.hasProperty("ossrhUsername")) {
    logger.warn("No ossrhUsername property defined in your Gradle properties file to deploy to Sonatype Snapshots, using 'foo' to make the build pass")
    project.extra["ossrhUsername"] = "foo"
}
if (!project.hasProperty("ossrhPassword")) {
    logger.warn("No ossrhPassword property defined in your Gradle properties file to deploy to Sonatype Snapshots, using 'bar' to make the build pass")
    project.extra["ossrhPassword"] = "bar"
}

// Releases are published to Bintray under the Reactiverse organization
// Then manually synced to Central
bintray {
    user    = System.getenv("BINTRAY_USER")
    key     = System.getenv("BINTRAY_KEY")
    with(pkg) {
        userOrg = "reactiverse"
        repo = "releases"
        name = project.name
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/reactiverse/aws-sdk"
        setLabels("vertx", "vert.x", "aws-sdk", "amazon web services")
        publicDownloadNumbers = true
        with(version) {
            name = project.version.toString()
            description = "${project.description}. Version: ${project.version}"
        }
        setPublications("mavenJava")
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

dependencies {
    api("io.vertx:vertx-core:$vertxVersion")
    api("software.amazon.awssdk:aws-core:$awsSdkVersion")

    testImplementation("io.vertx:vertx-junit5:$vertxVersion")
    testImplementation("io.vertx:vertx-rx-java2:$vertxVersion")
    testImplementation("cloud.localstack:localstack-utils:0.2.5")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
    testImplementation("ch.qos.logback:logback-core:$logbackVersion")
    testImplementation("software.amazon.awssdk:aws-sdk-java:$awsSdkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

jacoco {
    toolVersion = "0.8.5"
}

tasks {
    named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
      rejectVersionIf {
        isNonStable(candidate.version)
      }
    }

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
        options {
            source("8")
        }
        (options as StandardJavadocDocletOptions).links(
            "https://docs.oracle.com/javase/8/docs/api/",
            "https://sdk.amazonaws.com/java/api/latest/",
            "https://vertx.io/docs/${vertxVersion}/apidocs/",
            "http://www.reactive-streams.org/reactive-streams-1.0.0-javadoc/",
            "https://netty.io/4.1/api/"
        )
    }

    withType<Wrapper> {
        gradleVersion = "6.5"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            setVersion(project.version)
            pom {
                name.set(project.name)
                description.set("Reactiverse AWS SDK v2 with Vert.x")
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
            repositories {
                // To locally check out the poms
                maven {
                    name = "BuildDir"
                    url = uri("$buildDir/repos/snapshots")
                }
                // Snapshots are published to Sonatype's repository directly
                maven {
                    name = "SonatypeOSS"
                    url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
                    credentials {
                        val ossrhUsername: String by project
                        val ossrhPassword: String by project
                        username = ossrhUsername
                        password = ossrhPassword
                    }
                }
            }
        }
    }
}
