import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
    id("org.jlleitschuh.gradle.ktlint") version "12.0.3"
    id("org.springframework.boot") version "3.2.2" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = findProperty("group") as String? ?: "io.github.axonsentry"
    version = findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    dependencies {
        // Kotlin standard library
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        // Testing
        "testImplementation"("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        "testImplementation"("io.mockk:mockk:${property("mockkVersion")}")
        "testImplementation"("org.assertj:assertj-core:${property("assertjVersion")}")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs =
                listOf(
                    "-Xjsr305=strict",
                    "-Xjvm-default=all",
                )
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("${property("ktlintVersion")}")
        verbose.set(true)
        android.set(false)
    }
}

// Root project tasks
tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("test") {
    dependsOn(subprojects.map { it.tasks.named("test") })
}
