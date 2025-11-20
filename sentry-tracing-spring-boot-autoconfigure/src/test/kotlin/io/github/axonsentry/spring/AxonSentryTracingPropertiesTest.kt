package io.github.axonsentry.spring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for AxonSentryTracingProperties.
 */
class AxonSentryTracingPropertiesTest {
    @Test
    fun `should have sensible defaults`() {
        val properties = AxonSentryTracingProperties()

        assertThat(properties.enabled).isTrue()
        assertThat(properties.traceCommands).isTrue()
        assertThat(properties.traceEvents).isTrue()
        assertThat(properties.traceQueries).isTrue()
        assertThat(properties.traceEventProcessors).isTrue()
        assertThat(properties.traceSagas).isTrue()
        assertThat(properties.captureCommandPayloads).isFalse()
        assertThat(properties.captureEventPayloads).isFalse()
        assertThat(properties.captureQueryPayloads).isFalse()
        assertThat(properties.environment).isEqualTo("development")
        assertThat(properties.tracesSampleRate).isEqualTo(1.0)
        assertThat(properties.attachStacktrace).isTrue()
        assertThat(properties.tags).isEmpty()
    }

    @Test
    fun `should read SENTRY_DSN from environment`() {
        // Note: This test depends on whether SENTRY_DSN is set in the environment
        // It will pass either way, just verifying the behavior
        val properties = AxonSentryTracingProperties()

        // If SENTRY_DSN env var exists, properties.sentryDsn will be non-null
        // Otherwise, it will be null (which is the default)
        // Both are valid scenarios
        assertThat(properties.sentryDsn).isEqualTo(System.getenv("SENTRY_DSN"))
    }

    @Test
    fun `should convert to TracingConfiguration`() {
        val properties =
            AxonSentryTracingProperties().apply {
                enabled = false
                traceCommands = false
                traceEvents = false
                traceQueries = false
                traceEventProcessors = false
                traceSagas = false
                captureCommandPayloads = true
                captureEventPayloads = true
                captureQueryPayloads = true
                sentryDsn = "https://test@sentry.io/123"
                environment = "test"
                tracesSampleRate = 0.5
                attachStacktrace = false
                release = "1.0.0"
                tags = mapOf("team" to "platform")
            }

        val config = properties.toTracingConfiguration()

        assertThat(config.enabled).isFalse()
        assertThat(config.traceCommands).isFalse()
        assertThat(config.traceEvents).isFalse()
        assertThat(config.traceQueries).isFalse()
        assertThat(config.traceEventProcessors).isFalse()
        assertThat(config.traceSagas).isFalse()
        assertThat(config.captureCommandPayloads).isTrue()
        assertThat(config.captureEventPayloads).isTrue()
        assertThat(config.captureQueryPayloads).isTrue()
        assertThat(config.sentryDsn).isEqualTo("https://test@sentry.io/123")
        assertThat(config.environment).isEqualTo("test")
        assertThat(config.tracesSampleRate).isEqualTo(0.5)
        assertThat(config.attachStacktrace).isFalse()
    }

    @Test
    fun `should handle custom tags`() {
        val properties =
            AxonSentryTracingProperties().apply {
                tags =
                    mapOf(
                        "team" to "platform",
                        "service" to "order-service",
                        "region" to "us-east-1",
                    )
            }

        assertThat(properties.tags).hasSize(3)
        assertThat(properties.tags["team"]).isEqualTo("platform")
        assertThat(properties.tags["service"]).isEqualTo("order-service")
        assertThat(properties.tags["region"]).isEqualTo("us-east-1")
    }

    @Test
    fun `should allow partial tracing configuration`() {
        val properties =
            AxonSentryTracingProperties().apply {
                traceCommands = true
                traceEvents = false
                traceQueries = true
            }

        val config = properties.toTracingConfiguration()

        assertThat(config.traceCommands).isTrue()
        assertThat(config.traceEvents).isFalse()
        assertThat(config.traceQueries).isTrue()
    }

    @Test
    fun `should handle different sample rates`() {
        val properties1 = AxonSentryTracingProperties().apply { tracesSampleRate = 0.0 }
        val properties2 = AxonSentryTracingProperties().apply { tracesSampleRate = 0.1 }
        val properties3 = AxonSentryTracingProperties().apply { tracesSampleRate = 1.0 }

        assertThat(properties1.toTracingConfiguration().tracesSampleRate).isEqualTo(0.0)
        assertThat(properties2.toTracingConfiguration().tracesSampleRate).isEqualTo(0.1)
        assertThat(properties3.toTracingConfiguration().tracesSampleRate).isEqualTo(1.0)
    }

    @Test
    fun `should preserve release version`() {
        val properties = AxonSentryTracingProperties().apply { release = "1.2.3" }

        assertThat(properties.release).isEqualTo("1.2.3")
    }

    @Test
    fun `should handle null release version`() {
        val properties = AxonSentryTracingProperties().apply { release = null }

        assertThat(properties.release).isNull()
    }
}
