plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Example Spring Boot application demonstrating Axon Sentry Tracing"

dependencies {
    // Our starter
    implementation(project(":sentry-tracing-spring-boot-starter"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Axon Framework with Spring Boot
    implementation("org.axonframework:axon-spring-boot-starter:${property("axonVersion")}")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.axonframework:axon-test:${property("axonVersion")}")
}

tasks.bootJar {
    archiveFileName.set("axon-sentry-tracing-example.jar")
}

tasks.bootRun {
    jvmArgs =
        listOf(
            "-Dspring.profiles.active=dev",
        )
}
