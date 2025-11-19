package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * Tests for [DomainEventSpanEnricher].
 *
 * Validates that domain event metadata is correctly added to spans
 * for both publish and handler phases.
 */
class DomainEventSpanEnricherTest {
    @JvmField
    @RegisterExtension
    val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()

    private lateinit var enricher: DomainEventSpanEnricher
    private lateinit var span: Span

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        enricher = DomainEventSpanEnricher()

        val tracer = otelTesting.openTelemetry.getTracer("test")
        span = tracer.spanBuilder("test-span").startSpan()
    }

    @Test
    fun `enrichPublishSpan should add aggregate ID`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
            )

        // When
        enricher.enrichPublishSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "aggregate-123",
        )
    }

    @Test
    fun `enrichPublishSpan should add aggregate type`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "OrderAggregate",
                sequenceNumber = 5L,
            )

        // When
        enricher.enrichPublishSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "OrderAggregate",
        )
    }

    @Test
    fun `enrichPublishSpan should add sequence number`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 42L,
            )

        // When
        enricher.enrichPublishSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            42L,
        )
    }

    @Test
    fun `enrichPublishSpan should add timestamp`() {
        // Given
        val timestamp = Instant.parse("2025-11-19T10:00:00Z")
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
                timestamp = timestamp,
            )

        // When
        enricher.enrichPublishSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event.timestamp"),
            timestamp.toString(),
        )
    }

    @Test
    fun `enrichPublishSpan should handle all domain event attributes together`() {
        // Given
        val timestamp = Instant.parse("2025-11-19T12:30:00Z")
        val event =
            createDomainEvent(
                aggregateId = "order-456",
                aggregateType = "OrderAggregate",
                sequenceNumber = 10L,
                timestamp = timestamp,
            )

        // When
        enricher.enrichPublishSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "order-456",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "OrderAggregate",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            10L,
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event.timestamp"),
            timestamp.toString(),
        )
    }

    @Test
    fun `enrichHandlerSpan should add aggregate ID`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-789",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
            )

        // When
        enricher.enrichHandlerSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "aggregate-789",
        )
    }

    @Test
    fun `enrichHandlerSpan should add aggregate type`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "InvoiceAggregate",
                sequenceNumber = 1L,
            )

        // When
        enricher.enrichHandlerSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "InvoiceAggregate",
        )
    }

    @Test
    fun `enrichHandlerSpan should add sequence number`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 99L,
            )

        // When
        enricher.enrichHandlerSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            99L,
        )
    }

    @Test
    fun `enrichHandlerSpan should not add timestamp`() {
        // Given
        val timestamp = Instant.parse("2025-11-19T14:00:00Z")
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
                timestamp = timestamp,
            )

        // When
        enricher.enrichHandlerSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event.timestamp"),
        )
    }

    @Test
    fun `enrichHandlerSpan should handle all handler attributes together`() {
        // Given
        val event =
            createDomainEvent(
                aggregateId = "payment-999",
                aggregateType = "PaymentAggregate",
                sequenceNumber = 25L,
            )

        // When
        enricher.enrichHandlerSpan(span, event)
        span.end()

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "payment-999",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "PaymentAggregate",
        )
        assertThat(exportedSpan.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            25L,
        )
        assertThat(exportedSpan.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event.timestamp"),
        )
    }

    @Test
    fun `enrichPublishSpan should handle span that is not recording`() {
        // Given
        span.end() // End the span to make it not recording
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
            )

        // When - should not throw
        enricher.enrichPublishSpan(span, event)

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.status.statusCode).isEqualTo(StatusCode.UNSET)
    }

    @Test
    fun `enrichHandlerSpan should handle span that is not recording`() {
        // Given
        span.end() // End the span to make it not recording
        val event =
            createDomainEvent(
                aggregateId = "aggregate-123",
                aggregateType = "TestAggregate",
                sequenceNumber = 1L,
            )

        // When - should not throw
        enricher.enrichHandlerSpan(span, event)

        // Then
        val exportedSpan = otelTesting.spans.single()
        assertThat(exportedSpan.status.statusCode).isEqualTo(StatusCode.UNSET)
    }

    private fun createDomainEvent(
        aggregateId: String,
        aggregateType: String,
        sequenceNumber: Long,
        timestamp: Instant = Instant.now(),
    ): DomainEventMessage<TestEvent> {
        return GenericDomainEventMessage(
            aggregateType,
            aggregateId,
            sequenceNumber,
            TestEvent("test-data"),
            emptyMap<String, Any>(),
            java.util.UUID.randomUUID().toString(),
            timestamp,
        )
    }

    private data class TestEvent(val data: String)
}
