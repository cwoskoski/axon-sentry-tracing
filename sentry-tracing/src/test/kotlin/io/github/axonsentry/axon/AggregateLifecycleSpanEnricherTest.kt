package io.github.axonsentry.axon

import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MetaData
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("AggregateLifecycleSpanEnricher")
class AggregateLifecycleSpanEnricherTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var enricher: AggregateLifecycleSpanEnricher

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        enricher = AggregateLifecycleSpanEnricher()
    }

    @Test
    @DisplayName("should extract aggregate ID from metadata")
    fun `enrichWithAggregateInfo extracts aggregate ID`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()
        val metaData = MetaData.with("aggregateId", "aggregate-123")

        every { unitOfWork.message } returns message
        every { message.metaData } returns metaData
        every { unitOfWork.resources() } returns emptyMap()

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.aggregate.id"), "aggregate-123")
    }

    @Test
    @DisplayName("should extract aggregate ID from resources")
    fun `enrichWithAggregateInfo extracts aggregate ID from resources`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns mapOf("aggregateId" to "resource-aggregate-456")

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.aggregate.id"), "resource-aggregate-456")
    }

    @Test
    @DisplayName("should extract aggregate type")
    fun `enrichWithAggregateInfo extracts aggregate type`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns mapOf("aggregateType" to "OrderAggregate")

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.aggregate.type"), "OrderAggregate")
    }

    @Test
    @DisplayName("should track event count")
    fun `enrichWithAggregateInfo tracks event count`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()
        val events = listOf("Event1", "Event2", "Event3")

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns mapOf("events" to events)

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.longKey("axon.aggregate.events_applied"), 3L)
    }

    @Test
    @DisplayName("should detect aggregate creation")
    fun `enrichWithAggregateInfo detects creation`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns mapOf("aggregateCreation" to true)

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.booleanKey("axon.aggregate.is_creation"), true)
    }

    @Test
    @DisplayName("should default to zero events when no events in resources")
    fun `enrichWithAggregateInfo defaults to zero events`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns emptyMap()

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.longKey("axon.aggregate.events_applied"), 0L)
    }

    @Test
    @DisplayName("should default to false for aggregate creation when not specified")
    fun `enrichWithAggregateInfo defaults to false for creation`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()
        val message = mockk<CommandMessage<*>>()

        every { unitOfWork.message } returns message
        every { message.metaData } returns MetaData.emptyInstance()
        every { unitOfWork.resources() } returns emptyMap()

        // When
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.booleanKey("axon.aggregate.is_creation"), false)
    }

    @Test
    @DisplayName("should handle exceptions gracefully")
    fun `enrichWithAggregateInfo handles exceptions gracefully`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val unitOfWork = mockk<UnitOfWork<*>>()

        every { unitOfWork.message } throws RuntimeException("Test exception")
        every { unitOfWork.resources() } returns emptyMap()

        // When - should not throw
        enricher.enrichWithAggregateInfo(span, unitOfWork)
        span.end()

        // Then - span should still be created
        val spanData = otelTesting.spans.single()
        assertThat(spanData).isNotNull
    }
}
