# Issue 002: Gradle Build Configuration

**Phase:** Foundation
**Priority:** Critical
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 001

## Overview
Configure Gradle build system with Kotlin DSL, establishing dependency management, plugin configuration, and build tasks. This issue sets up the build infrastructure that will compile, test, and package the axon-sentry-tracing library.

## Goals
- Configure Gradle with Kotlin DSL for type-safe build scripts
- Set up dependency management for Axon, Sentry, and OpenTelemetry
- Configure Kotlin compiler with appropriate JVM target
- Establish code quality plugins (detekt, ktlint)
- Configure publishing for library distribution

## Technical Requirements

### Components to Create
1. **build.gradle.kts** (Root build file)
   - Purpose: Define project configuration, dependencies, and build tasks
   - Key responsibilities: Compile code, manage dependencies, run tests, publish artifacts

2. **settings.gradle.kts** (Settings file)
   - Purpose: Configure project name and module structure
   - Key responsibilities: Define root project and any subprojects

3. **gradle.properties** (Build properties)
   - Purpose: Store build configuration and version numbers
   - Key responsibilities: Centralize version management

4. **Gradle Wrapper** (gradlew, gradlew.bat)
   - Purpose: Ensure consistent Gradle version across environments
   - Key responsibilities: Bootstrap Gradle downloads

### Dependencies
- **Kotlin**: 1.9.22+ (Latest stable)
- **Axon Framework**: 4.9.x (Core event sourcing framework)
- **Sentry Java**: 7.x (Sentry SDK for JVM)
- **OpenTelemetry**: 1.33.x (Tracing instrumentation)
  - opentelemetry-api
  - opentelemetry-sdk
  - opentelemetry-extension-kotlin
- **Testing**:
  - JUnit 5 (Jupiter): 5.10.x
  - Mockk: 1.13.x
  - AssertJ: 3.24.x
  - Axon Test: 4.9.x

### Configuration
Build should support:
- Kotlin JVM target: 17
- Java compatibility: 17
- Kotlin compiler options: -Xjsr305=strict, -Xjvm-default=all
- Source and JavaDoc JAR generation
- Maven publication configuration

## Implementation Guidance

### Step-by-Step Approach
1. **Create settings.gradle.kts**
   - Define root project name
   - Configure plugin repositories

2. **Create gradle.properties**
   - Define version properties
   - Set Kotlin compiler options
   - Configure Gradle daemon settings

3. **Create build.gradle.kts**
   - Apply necessary plugins
   - Configure Kotlin JVM plugin
   - Define dependencies with proper scopes
   - Configure test tasks
   - Set up code quality plugins
   - Configure publishing

4. **Install Gradle Wrapper**
   - Run: `gradle wrapper --gradle-version 8.5`
   - Commit wrapper files

5. **Verify Build**
   - Run: `./gradlew build` (should compile successfully)
   - Run: `./gradlew dependencies` (verify dependency resolution)

### Code Examples

#### settings.gradle.kts
```kotlin
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
```

#### gradle.properties
```properties
# Project
group=io.github.yourusername
version=0.1.0-SNAPSHOT

# Kotlin
kotlin.code.style=official
kotlin.incremental=true

# Versions
kotlinVersion=1.9.22
axonVersion=4.9.3
sentryVersion=7.3.0
openTelemetryVersion=1.33.0
junitVersion=5.10.1
mockkVersion=1.13.9
assertjVersion=3.24.2

# Gradle
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.caching=true
org.gradle.parallel=true
```

#### build.gradle.kts (Core Structure)
```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    `maven-publish`
    `java-library`
}

group = findProperty("group") as String? ?: "io.github.yourusername"
version = findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Axon Framework
    api("org.axonframework:axon-messaging:${property("axonVersion")}")

    // Sentry
    api("io.sentry:sentry:${property("sentryVersion")}")

    // OpenTelemetry
    api("io.opentelemetry:opentelemetry-api:${property("openTelemetryVersion")}")
    implementation("io.opentelemetry:opentelemetry-sdk:${property("openTelemetryVersion")}")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin:${property("openTelemetryVersion")}")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("io.mockk:mockk:${property("mockkVersion")}")
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
    testImplementation("org.axonframework:axon-test:${property("axonVersion")}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xjvm-default=all"
        )
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$projectDir/config/detekt/detekt.yml")
}

ktlint {
    version.set("1.0.1")
    verbose.set(true)
    android.set(false)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Axon Sentry Tracing")
                description.set("Sentry tracing integration for Axon Framework via OpenTelemetry")
                url.set("https://github.com/yourusername/axon-sentry-tracing")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("yourusername")
                        name.set("Your Name")
                        email.set("your.email@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yourusername/axon-sentry-tracing.git")
                    developerConnection.set("scm:git:ssh://github.com/yourusername/axon-sentry-tracing.git")
                    url.set("https://github.com/yourusername/axon-sentry-tracing")
                }
            }
        }
    }
}
```

### Integration Points
- Gradle build system integrates with CI/CD pipelines
- Maven publication enables distribution to repositories
- Code quality plugins enforce standards during build
- Test framework supports comprehensive testing strategy

## Testing Requirements

### Build Validation
- [ ] Test: `./gradlew clean build` completes successfully
- [ ] Test: `./gradlew dependencies` shows correct dependency tree
- [ ] Test: `./gradlew tasks` lists all available tasks
- [ ] Test: Code compiles without warnings

### Dependency Verification
- [ ] Verify Axon Framework is available
- [ ] Verify Sentry SDK is available
- [ ] Verify OpenTelemetry dependencies resolve
- [ ] Check for dependency conflicts with `./gradlew dependencies`

### Test Coverage Target
Build configuration itself doesn't require test coverage, but it must enable 80%+ coverage for library code.

## Acceptance Criteria
- [ ] build.gradle.kts exists with all required dependencies
- [ ] settings.gradle.kts configures project correctly
- [ ] gradle.properties contains version management
- [ ] Gradle wrapper is installed (gradlew, gradlew.bat)
- [ ] `./gradlew build` completes successfully
- [ ] All dependencies resolve without conflicts
- [ ] Kotlin code compiles with JVM target 17
- [ ] detekt and ktlint plugins configured
- [ ] Maven publication configured for library distribution
- [ ] Source and JavaDoc JARs are generated

## Definition of Done
- [ ] Implementation complete
- [ ] Build executes successfully on clean checkout
- [ ] All dependencies verified and documented
- [ ] Code quality plugins active
- [ ] Publishing configuration tested (local Maven repository)
- [ ] Documentation updated with build commands
- [ ] Changes committed to main branch

## Resources
- [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Kotlin Gradle Plugin](https://kotlinlang.org/docs/gradle.html)
- [Axon Framework Maven Dependencies](https://docs.axoniq.io/reference-guide/axon-framework/getting-started/maven-dependencies)
- [Sentry Java SDK](https://docs.sentry.io/platforms/java/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Gradle Publishing](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Detekt Configuration](https://detekt.dev/docs/gettingstarted/gradle/)

## Notes
- Use Gradle Kotlin DSL for type safety and IDE support
- Pin dependency versions in gradle.properties for easy updates
- The `api` scope exposes dependencies to library consumers
- The `implementation` scope hides internal dependencies
- Consider using Gradle version catalogs in future for dependency management
- Ensure Gradle wrapper is committed to enable reproducible builds
- Test the build on a clean environment to catch missing dependencies
- Document any custom Gradle tasks in README.md

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
