plugins {
    `java-library`
    `maven-publish`
    kotlin("plugin.spring")
}

description = "Spring Boot auto-configuration for Axon Sentry Tracing"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Core library
    api(project(":sentry-tracing"))

    // Spring Boot
    api("org.springframework.boot:spring-boot-starter:${property("springBootVersion")}")
    api("org.springframework.boot:spring-boot-autoconfigure:${property("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${property("springBootVersion")}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${property("springBootVersion")}")

    // Spring Boot Actuator (optional)
    compileOnly("org.springframework.boot:spring-boot-starter-actuator:${property("springBootVersion")}")

    // Jakarta Validation for @DecimalMin/@DecimalMax
    api("jakarta.validation:jakarta.validation-api:3.0.2")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:${property("springBootVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator:${property("springBootVersion")}")
    testImplementation("org.springframework.boot:spring-boot-starter-validation:${property("springBootVersion")}")
    testImplementation("org.axonframework:axon-test:${property("axonVersion")}")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "axon-sentry-tracing-spring-boot-autoconfigure"

            pom {
                name.set("Axon Sentry Tracing Spring Boot Auto-Configuration")
                description.set("Spring Boot auto-configuration for Axon Sentry Tracing")
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
