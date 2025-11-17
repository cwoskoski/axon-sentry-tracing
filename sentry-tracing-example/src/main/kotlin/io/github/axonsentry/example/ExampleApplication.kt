package io.github.axonsentry.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Example Spring Boot application demonstrating Axon Sentry Tracing integration.
 *
 * This application will be implemented in Phase 1 (Issue 009).
 */
@SpringBootApplication
class ExampleApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ExampleApplication>(*args)
}
