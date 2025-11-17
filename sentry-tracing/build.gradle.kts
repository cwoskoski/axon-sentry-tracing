plugins {
    `java-library`
    `maven-publish`
}

description = "Core tracing library for Axon Framework with Sentry integration via OpenTelemetry"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Axon Framework
    api("org.axonframework:axon-messaging:${property("axonVersion")}")

    // Sentry
    api("io.sentry:sentry:${property("sentryVersion")}")
    implementation("io.sentry:sentry-opentelemetry-core:${property("sentryVersion")}")

    // OpenTelemetry
    api("io.opentelemetry:opentelemetry-api:${property("openTelemetryVersion")}")
    implementation("io.opentelemetry:opentelemetry-sdk:${property("openTelemetryVersion")}")
    implementation("io.opentelemetry:opentelemetry-sdk-trace:${property("openTelemetryVersion")}")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:${property("openTelemetryVersion")}")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging (SLF4J API)
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Testing - Axon Test
    testImplementation("org.axonframework:axon-test:${property("axonVersion")}")

    // Testing - Logging implementation for tests
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "axon-sentry-tracing"

            pom {
                name.set("Axon Sentry Tracing")
                description.set("Sentry tracing integration for Axon Framework via OpenTelemetry")
                url.set("https://github.com/axonsentry/axon-sentry-tracing")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("axonsentry")
                        name.set("Axon Sentry Contributors")
                        email.set("contact@axonsentry.io")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/axonsentry/axon-sentry-tracing.git")
                    developerConnection.set("scm:git:ssh://github.com/axonsentry/axon-sentry-tracing.git")
                    url.set("https://github.com/axonsentry/axon-sentry-tracing")
                }
            }
        }
    }
}
