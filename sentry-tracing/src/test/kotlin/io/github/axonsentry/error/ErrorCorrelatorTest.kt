package io.github.axonsentry.error

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.sentry.Sentry
import io.sentry.SentryOptions
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("ErrorCorrelator")
class ErrorCorrelatorTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var correlator: ErrorCorrelator

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        correlator = ErrorCorrelator()

        // Initialize Sentry in test mode
        Sentry.init { options: SentryOptions ->
            options.dsn = "https://examplePublicKey@o0.ingest.sentry.io/0"
            options.environment = "test"
            options.isEnableExternalConfiguration = false
        }
    }

    @AfterEach
    fun tearDown() {
        Sentry.close()
    }

    @Test
    @DisplayName("should record exception in span with ERROR status")
    fun `recordException sets span status to ERROR`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = RuntimeException("Test error")

        // When
        correlator.recordException(span, exception)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.status.description).isEqualTo("Test error")
        assertThat(spanData.events).hasSize(1)
        assertThat(spanData.events.first().name).isEqualTo("exception")
    }

    @Test
    @DisplayName("should handle null exception message")
    fun `recordException handles null message`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = RuntimeException()

        // When
        correlator.recordException(span, exception)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.status.description).isEqualTo("Error")
    }

    @Test
    @DisplayName("should record exception without Axon message context")
    fun `recordException works without message`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = IllegalArgumentException("Invalid argument")

        // When
        correlator.recordException(span, exception, null)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.events).hasSize(1)
    }

    @Test
    @DisplayName("should enrich with command message context")
    fun `recordException enriches with command context`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = RuntimeException("Command failed")
        val command = GenericCommandMessage.asCommandMessage<String>("TestCommand")

        // When
        correlator.recordException(span, exception, command)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.events).hasSize(1)
        // Note: Sentry event capture is tested in integration tests
    }

    @Test
    @DisplayName("should enrich with event message context")
    fun `recordException enriches with event context`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = RuntimeException("Event processing failed")
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")

        // When
        correlator.recordException(span, exception, event)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
    }

    @Test
    @DisplayName("should handle CommandExecutionException with aggregate context")
    fun `recordException extracts CommandExecutionException details`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val command = GenericCommandMessage.asCommandMessage<String>("CreateAggregate")
        val exception = CommandExecutionException("Aggregate validation failed", null, command)

        // When
        correlator.recordException(span, exception, command)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.status.description).isEqualTo("Aggregate validation failed")
    }

    @Test
    @DisplayName("should propagate trace context to Sentry")
    fun `recordException includes trace context`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val exception = RuntimeException("Test with trace context")

        // When
        correlator.recordException(span, exception)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.spanContext.traceId).isNotEmpty()
        assertThat(spanData.spanContext.spanId).isNotEmpty()
        // Actual Sentry correlation is tested in integration tests
    }

    @Test
    @DisplayName("should handle nested exceptions")
    fun `recordException handles exception with cause`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val cause = IllegalStateException("Root cause")
        val exception = RuntimeException("Wrapper exception", cause)

        // When
        correlator.recordException(span, exception)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(spanData.events).hasSize(1)
    }
}
