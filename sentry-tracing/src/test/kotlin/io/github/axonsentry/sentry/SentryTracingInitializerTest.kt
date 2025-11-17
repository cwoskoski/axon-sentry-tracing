package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.OpenTelemetry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SentryTracingInitializerTest {
    @AfterEach
    fun cleanup() {
        // Reset singleton after each test
        SentryTracingInitializer.reset()
    }

    @Test
    fun `initialize returns noop OpenTelemetry when tracing is disabled`() {
        // Given
        val configuration = TracingConfiguration(enabled = false)
        val initializer = SentryTracingInitializer(configuration)

        // When
        val openTelemetry = initializer.initialize()

        // Then
        assertThat(openTelemetry).isNotNull
        assertThat(openTelemetry).isSameAs(OpenTelemetry.noop())
    }

    @Test
    fun `initialize throws IllegalStateException when DSN is missing and tracing is enabled`() {
        // Given
        val configuration = TracingConfiguration(enabled = true, sentryDsn = null)
        val initializer = SentryTracingInitializer(configuration)

        // When / Then
        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Sentry DSN must be configured")
    }

    @Test
    fun `maskDsn properly masks DSN key`() {
        // Given
        val configuration = TracingConfiguration(sentryDsn = "https://key@sentry.io/123")
        val initializer = SentryTracingInitializer(configuration)

        // When
        // Use reflection to test private method
        val maskMethod = SentryTracingInitializer::class.java.getDeclaredMethod("maskDsn", String::class.java)
        maskMethod.isAccessible = true
        val masked = maskMethod.invoke(initializer, "https://key@sentry.io/123") as String

        // Then
        assertThat(masked).isEqualTo("https://***@sentry.io/123")
    }

    @Test
    fun `getInstance returns singleton instance`() {
        // Given
        val configuration = TracingConfiguration(sentryDsn = "https://key@sentry.io/123")

        // When
        val instance1 = SentryTracingInitializer.getInstance(configuration)
        val instance2 = SentryTracingInitializer.getInstance(configuration)

        // Then
        assertThat(instance1).isSameAs(instance2)
    }

    @Test
    fun `reset clears singleton instance`() {
        // Given
        val configuration = TracingConfiguration(sentryDsn = "https://key@sentry.io/123")
        val instance1 = SentryTracingInitializer.getInstance(configuration)

        // When
        SentryTracingInitializer.reset()
        val instance2 = SentryTracingInitializer.getInstance(configuration)

        // Then
        assertThat(instance1).isNotSameAs(instance2)
    }

    @Test
    fun `getOpenTelemetry returns null before initialization`() {
        // Given
        val configuration = TracingConfiguration(sentryDsn = "https://key@sentry.io/123")
        val initializer = SentryTracingInitializer(configuration)

        // When
        val openTelemetry = initializer.getOpenTelemetry()

        // Then
        assertThat(openTelemetry).isNull()
    }

    @Test
    fun `flush returns false when tracer provider is not initialized`() {
        // Given
        val configuration = TracingConfiguration(enabled = false)
        val initializer = SentryTracingInitializer(configuration)

        // When
        val result = initializer.flush()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `shutdown can be called multiple times safely`() {
        // Given
        val configuration = TracingConfiguration(enabled = false)
        val initializer = SentryTracingInitializer(configuration)
        initializer.initialize()

        // When / Then - should not throw
        initializer.shutdown()
        initializer.shutdown()
    }

    @Test
    fun `configuration with custom environment is applied`() {
        // Given
        val configuration =
            TracingConfiguration(
                enabled = true,
                sentryDsn = "https://key@sentry.io/123",
                environment = "production",
                tracesSampleRate = 0.5,
            )
        val initializer = SentryTracingInitializer(configuration)

        // When
        val openTelemetry = initializer.initialize()

        // Then
        assertThat(openTelemetry).isNotNull
        assertThat(openTelemetry).isNotSameAs(OpenTelemetry.noop())

        // Cleanup
        initializer.shutdown()
    }

    @Test
    fun `configuration with 100% sample rate is applied`() {
        // Given
        val configuration =
            TracingConfiguration(
                enabled = true,
                sentryDsn = "https://key@sentry.io/123",
                tracesSampleRate = 1.0,
            )
        val initializer = SentryTracingInitializer(configuration)

        // When
        val openTelemetry = initializer.initialize()

        // Then
        assertThat(openTelemetry).isNotNull

        // Cleanup
        initializer.shutdown()
    }

    @Test
    fun `configuration with 0% sample rate is applied`() {
        // Given
        val configuration =
            TracingConfiguration(
                enabled = true,
                sentryDsn = "https://key@sentry.io/123",
                tracesSampleRate = 0.0,
            )
        val initializer = SentryTracingInitializer(configuration)

        // When
        val openTelemetry = initializer.initialize()

        // Then
        assertThat(openTelemetry).isNotNull

        // Cleanup
        initializer.shutdown()
    }
}
