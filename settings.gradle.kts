rootProject.name = "axon-sentry-tracing"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "sentry-tracing",
    "sentry-tracing-spring-boot-autoconfigure",
    "sentry-tracing-spring-boot-starter",
    "sentry-tracing-example"
)
