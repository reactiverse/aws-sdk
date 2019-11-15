val vertxVersion = "3.8.3"
val awsSdkVersion = "2.10.16"
val junit5Version = "5.4.0"
val logbackVersion = "1.2.3"
val integrationOption = "tests.integration"
val githubURL = "https://github.com/reactiverse/aws-sdk"

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    id("com.jaredsburrows.license") version "0.8.42"
    id("org.sonarqube") version "2.6"
    id("com.jfrog.bintray") version "1.8.4"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

group = "io.reactiverse"
version = "0.4.0"

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
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
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
        gradleVersion = "6.0"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("Reactiverse AWS SDK 2 with Vert.x")
                url.set(githubURL)
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

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")
    pkg.apply {
        repo = "releases"
        name = "aws-sdk"
        userOrg = "reactiverse"
        setLicenses("Apache-2.0")
        vcsUrl = githubURL
    }
    setPublications(publishing.publications["mavenJava"].name)
}

if (project.hasProperty("signing.keyId")) {
    plugins.apply("signing")
    signing {
        sign(publishing.publications["mavenJava"])
    }
}
