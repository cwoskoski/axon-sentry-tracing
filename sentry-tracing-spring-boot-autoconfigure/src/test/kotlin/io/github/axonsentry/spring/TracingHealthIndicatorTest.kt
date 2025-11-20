package io.github.axonsentry.spring

import io.github.axonsentry.config.TracingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

/**
 * Unit tests for TracingHealthIndicator.
 */
class TracingHealthIndicatorTest {
    @Test
    fun `should report UP when tracing is enabled`() {
        val config =
            TracingConfiguration(
                enabled = true,
                sentryDsn = "https://test@sentry.io/123",
            )
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["enabled"]).isEqualTo(true)
        assertThat(health.details["sentryConfigured"]).isEqualTo(true)
    }

    @Test
    fun `should report DISABLED when tracing is disabled`() {
        val config = TracingConfiguration(enabled = false)
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status("DISABLED"))
        assertThat(health.details["enabled"]).isEqualTo(false)
    }

    @Test
    fun `should include all tracing configuration details`() {
        val config =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                traceEvents = false,
                traceQueries = true,
                traceEventProcessors = false,
                traceSagas = true,
                environment = "staging",
                tracesSampleRate = 0.5,
                sentryDsn = "https://test@sentry.io/123",
            )
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.details["enabled"]).isEqualTo(true)
        assertThat(health.details["commands"]).isEqualTo(true)
        assertThat(health.details["events"]).isEqualTo(false)
        assertThat(health.details["queries"]).isEqualTo(true)
        assertThat(health.details["eventProcessors"]).isEqualTo(false)
        assertThat(health.details["sagas"]).isEqualTo(true)
        assertThat(health.details["environment"]).isEqualTo("staging")
        assertThat(health.details["sampleRate"]).isEqualTo(0.5)
        assertThat(health.details["sentryConfigured"]).isEqualTo(true)
    }

    @Test
    fun `should report when Sentry DSN is not configured`() {
        val config =
            TracingConfiguration(
                enabled = true,
                sentryDsn = null,
            )
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.details["sentryConfigured"]).isEqualTo(false)
    }

    @Test
    fun `should handle missing Sentry DSN gracefully`() {
        val config = TracingConfiguration(enabled = false, sentryDsn = null)
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status("DISABLED"))
        assertThat(health.details["sentryConfigured"]).isEqualTo(false)
    }

    @Test
    fun `should report full tracing configuration`() {
        val config =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                traceEvents = true,
                traceQueries = true,
                traceEventProcessors = true,
                traceSagas = true,
                environment = "production",
                tracesSampleRate = 0.1,
                sentryDsn = "https://prod@sentry.io/456",
            )
        val indicator = TracingHealthIndicator(config)

        val health = indicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["enabled"]).isEqualTo(true)
        assertThat(health.details["commands"]).isEqualTo(true)
        assertThat(health.details["events"]).isEqualTo(true)
        assertThat(health.details["queries"]).isEqualTo(true)
        assertThat(health.details["eventProcessors"]).isEqualTo(true)
        assertThat(health.details["sagas"]).isEqualTo(true)
        assertThat(health.details["environment"]).isEqualTo("production")
        assertThat(health.details["sampleRate"]).isEqualTo(0.1)
        assertThat(health.details["sentryConfigured"]).isEqualTo(true)
    }
}
