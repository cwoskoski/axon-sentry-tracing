plugins {
    `java-library`
    `maven-publish`
}

description = "Spring Boot starter for Axon Sentry Tracing"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Auto-configuration module
    api(project(":sentry-tracing-spring-boot-autoconfigure"))

    // No additional dependencies - this is a pure aggregator module
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "axon-sentry-tracing-spring-boot-starter"

            pom {
                name.set("Axon Sentry Tracing Spring Boot Starter")
                description.set("Spring Boot starter for Axon Sentry Tracing")
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
