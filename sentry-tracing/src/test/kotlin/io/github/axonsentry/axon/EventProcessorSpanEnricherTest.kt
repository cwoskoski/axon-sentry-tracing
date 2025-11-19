package io.github.axonsentry.axon

import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.eventhandling.GlobalSequenceTrackingToken
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for [EventProcessorSpanEnricher].
 *
 * Validates that event processor context is correctly extracted and added to spans.
 */
class EventProcessorSpanEnricherTest {
    @JvmField
    @RegisterExtension
    val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()

    private lateinit var enricher: EventProcessorSpanEnricher
    private lateinit var span: Span
    private lateinit var unitOfWork: UnitOfWork<EventMessage<*>>

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        enricher = EventProcessorSpanEnricher()

        val tracer = otelTesting.openTelemetry.getTracer("test")
        span = tracer.spanBuilder("test-span").startSpan()

        unitOfWork = mockk(relaxed = true)
        every { unitOfWork.message } returns GenericEventMessage.asEventMessage<Any>("test-event")
    }

    @Test
    fun `enrichHandlerSpan should add processor name`() {
        // Given
        val resources =
            mutableMapOf<String, Any>(
                "processorName" to "OrderEventProcessor",
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.name"),
            "OrderEventProcessor",
        )
    }

    @Test
    fun `enrichHandlerSpan should add processor type`() {
        // Given
        val resources =
            mutableMapOf<String, Any>(
                "processorType" to "TrackingEventProcessor",
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.type"),
            "TrackingEventProcessor",
        )
    }

    @Test
    fun `enrichHandlerSpan should add tracking token position`() {
        // Given
        val token = GlobalSequenceTrackingToken(12345L)
        val resources =
            mutableMapOf<String, Any>(
                "trackingToken" to token,
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.token_position"),
            "12345",
        )
    }

    @Test
    fun `enrichHandlerSpan should add replay mode when true`() {
        // Given
        val resources =
            mutableMapOf<String, Any>(
                "isReplaying" to true,
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.booleanKey("axon.event_processor.is_replaying"),
            true,
        )
    }

    @Test
    fun `enrichHandlerSpan should default replay mode to false when not present`() {
        // Given
        val resources = mutableMapOf<String, Any>()
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.booleanKey("axon.event_processor.is_replaying"),
            false,
        )
    }

    @Test
    fun `enrichHandlerSpan should add handler group`() {
        // Given
        val resources =
            mutableMapOf<String, Any>(
                "handlerGroup" to "order-handlers",
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_handler.group"),
            "order-handlers",
        )
    }

    @Test
    fun `enrichHandlerSpan should handle all processor attributes together`() {
        // Given
        val token = GlobalSequenceTrackingToken(42L)
        val resources =
            mutableMapOf<String, Any>(
                "processorName" to "PaymentProcessor",
                "processorType" to "TrackingEventProcessor",
                "trackingToken" to token,
                "isReplaying" to true,
                "handlerGroup" to "payment-group",
            )
        every { unitOfWork.resources() } returns resources

        // When
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.name"),
            "PaymentProcessor",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.type"),
            "TrackingEventProcessor",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.token_position"),
            "42",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.booleanKey("axon.event_processor.is_replaying"),
            true,
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_handler.group"),
            "payment-group",
        )
    }

    @Test
    fun `enrichHandlerSpan should handle empty resources`() {
        // Given
        val resources = mutableMapOf<String, Any>()
        every { unitOfWork.resources() } returns resources

        // When - should not throw
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event_processor.name"),
        )
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event_processor.type"),
        )
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event_processor.token_position"),
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.booleanKey("axon.event_processor.is_replaying"),
            false,
        )
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event_handler.group"),
        )
    }

    @Test
    fun `enrichHandlerSpan should handle resources access exception`() {
        // Given
        every { unitOfWork.resources() } throws RuntimeException("Resource access failed")

        // When - should not throw
        enricher.enrichHandlerSpan(span, unitOfWork)
        span.end()

        // Then - span should be created without processor attributes
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event_processor.name"),
        )
    }
}
