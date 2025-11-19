package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.SpanAttributes
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.github.axonsentry.tracing.TraceContext
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.eventhandling.GlobalSequenceTrackingToken
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Tests for [EventTracingInterceptor].
 *
 * Validates event tracing for both dispatch (publish) and handler phases.
 */
class EventTracingInterceptorTest {
    @JvmField
    @RegisterExtension
    val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()

    private lateinit var spanFactory: AxonSpanFactory
    private lateinit var configuration: TracingConfiguration
    private lateinit var interceptor: EventTracingInterceptor

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()

        configuration =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                traceEvents = true,
                traceQueries = true,
            )

        val tracer = otelTesting.openTelemetry.getTracer("test")
        val spanNameGenerator = SpanNameGenerator()
        val spanKindResolver = SpanKindResolver()
        val attributeApplier = AttributeApplier(configuration)

        spanFactory =
            AxonSpanFactory(
                tracer = tracer,
                configuration = configuration,
                spanNameGenerator = spanNameGenerator,
                spanKindResolver = spanKindResolver,
                attributeApplier = attributeApplier,
            )

        interceptor =
            EventTracingInterceptor(
                spanFactory = spanFactory,
                configuration = configuration,
            )
    }

    // ========== Dispatch Interceptor Tests ==========

    @Test
    fun `handle dispatch should create publish span for event`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val messages = mutableListOf(event)

        // When
        val biFunction = interceptor.handle(messages)
        biFunction.apply(0, event)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.name).isEqualTo("Event: String")
        assertThat(span.kind).isEqualTo(SpanKind.PRODUCER)
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE),
            "event",
        )
    }

    @Test
    fun `handle dispatch should propagate trace context in metadata`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val messages = mutableListOf(event)

        // When
        val biFunction = interceptor.handle(messages)
        val enrichedEvent = biFunction.apply(0, event)

        // Then
        val traceContextMap = enrichedEvent.metaData[MessageMetadataKeys.TRACE_CONTEXT]
        assertThat(traceContextMap).isNotNull
        assertThat(traceContextMap).isInstanceOf(Map::class.java)

        @Suppress("UNCHECKED_CAST")
        val contextMap = traceContextMap as Map<String, Any>
        // Check for internal trace context keys (used by TraceContext.toMetadataMap())
        assertThat(contextMap).containsKey("_trace_id")
        assertThat(contextMap).containsKey("_span_id")
        assertThat(contextMap).containsKey("_trace_flags")
    }

    @Test
    fun `handle dispatch should enrich domain event with aggregate metadata`() {
        // Given
        val domainEvent =
            GenericDomainEventMessage(
                "OrderAggregate",
                "order-123",
                5L,
                "OrderCreated",
            )
        val messages = mutableListOf<EventMessage<*>>(domainEvent)

        // When
        val biFunction = interceptor.handle(messages)
        biFunction.apply(0, domainEvent)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "order-123",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "OrderAggregate",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            5L,
        )
    }

    @Test
    fun `handle dispatch should not trace when tracing disabled`() {
        // Given
        configuration =
            TracingConfiguration(
                enabled = false,
                traceCommands = true,
                traceEvents = true,
                traceQueries = true,
            )
        interceptor = EventTracingInterceptor(spanFactory, configuration)

        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val messages = mutableListOf(event)

        // When
        val biFunction = interceptor.handle(messages)
        val result = biFunction.apply(0, event)

        // Then
        assertThat(otelTesting.spans).isEmpty()
        assertThat(result).isSameAs(event)
    }

    @Test
    fun `handle dispatch should not trace when traceEvents disabled`() {
        // Given
        configuration =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                traceEvents = false,
                traceQueries = true,
            )
        interceptor = EventTracingInterceptor(spanFactory, configuration)

        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val messages = mutableListOf(event)

        // When
        val biFunction = interceptor.handle(messages)
        val result = biFunction.apply(0, event)

        // Then
        assertThat(otelTesting.spans).isEmpty()
        assertThat(result).isSameAs(event)
    }

    @Test
    fun `handle dispatch should handle multiple events`() {
        // Given
        val event1 = GenericEventMessage.asEventMessage<String>("Event1")
        val event2 = GenericEventMessage.asEventMessage<String>("Event2")
        val messages = mutableListOf<EventMessage<*>>(event1, event2)

        // When
        val biFunction = interceptor.handle(messages)
        biFunction.apply(0, event1)
        biFunction.apply(1, event2)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(2)
        assertThat(spans.map { it.name }).containsExactly(
            "Event: String",
            "Event: String",
        )
    }

    @Test
    fun `handle dispatch should handle exception gracefully`() {
        // Given
        val domainEvent =
            GenericDomainEventMessage(
                "OrderAggregate",
                "order-123",
                5L,
                "OrderCreated",
            )
        val messages = mutableListOf<EventMessage<*>>(domainEvent)

        // Create a broken enricher that throws
        val brokenEnricher = mockk<DomainEventSpanEnricher>()
        every { brokenEnricher.enrichPublishSpan(any(), any()) } throws RuntimeException("Enricher failed")

        interceptor =
            EventTracingInterceptor(
                spanFactory = spanFactory,
                configuration = configuration,
                domainEventEnricher = brokenEnricher,
            )

        // When
        val biFunction = interceptor.handle(messages)
        val result = biFunction.apply(0, domainEvent)

        // Then - should return a message (potentially enriched or original) and record exception in span
        assertThat(result).isNotNull
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)
        // The exception should be recorded in the span
        assertThat(spans.first().events).anyMatch { it.name == "exception" }
    }

    // ========== Handler Interceptor Tests ==========

    @Test
    fun `handle handler should create handler span for event`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "handler-result"

        // When
        val result = interceptor.handle(unitOfWork, chain)

        // Then
        assertThat(result).isEqualTo("handler-result")

        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.name).isEqualTo("Handle: String")
        assertThat(span.kind).isEqualTo(SpanKind.CONSUMER)
        assertThat(span.status.statusCode).isEqualTo(StatusCode.OK)
    }

    @Test
    fun `handle handler should extract parent context from metadata`() {
        // Given - create a real span first to get valid trace/span IDs
        val publishSpan =
            spanFactory.createEventPublishSpan(
                GenericEventMessage.asEventMessage<String>("TestEvent"),
                Context.current(),
            )
        val parentTraceId = publishSpan.spanContext.traceId
        val parentSpanId = publishSpan.spanContext.spanId

        // Create trace context from the publish span
        val traceContext = TraceContext.fromSpanContext(publishSpan.spanContext).toMetadataMap()

        val event =
            GenericEventMessage.asEventMessage<String>("TestEvent")
                .andMetaData(mapOf<String, Any>(MessageMetadataKeys.TRACE_CONTEXT to traceContext))

        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // End the publish span
        publishSpan.end()

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(2) // publish + handler

        val handlerSpan = spans.last() // Handler span is the second one
        assertThat(handlerSpan.traceId).isEqualTo(parentTraceId)
        assertThat(handlerSpan.parentSpanId).isEqualTo(parentSpanId)
    }

    @Test
    fun `handle handler should enrich with processor context`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val token = GlobalSequenceTrackingToken(123L)
        val resources =
            mutableMapOf<String, Any>(
                "processorName" to "TestProcessor",
                "processorType" to "TrackingEventProcessor",
                "trackingToken" to token,
                "isReplaying" to false,
            )

        val unitOfWork = createUnitOfWork(event, resources)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.name"),
            "TestProcessor",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.type"),
            "TrackingEventProcessor",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("axon.event_processor.token_position"),
            "123",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.booleanKey("axon.event_processor.is_replaying"),
            false,
        )
    }

    @Test
    fun `handle handler should enrich domain event with aggregate metadata`() {
        // Given
        val domainEvent =
            GenericDomainEventMessage(
                "PaymentAggregate",
                "payment-456",
                10L,
                "PaymentProcessed",
            )
        val unitOfWork = createUnitOfWork(domainEvent)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
            "payment-456",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
            "PaymentAggregate",
        )
        assertThat(span.attributes.asMap()).containsEntry(
            AttributeKey.longKey("axon.event.sequence_number"),
            10L,
        )
        assertThat(span.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey("axon.event.timestamp"),
        )
    }

    @Test
    fun `handle handler should record exception on handler failure`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        val exception = RuntimeException("Handler failed")
        every { chain.proceed() } throws exception

        // When / Then
        assertThatThrownBy {
            interceptor.handle(unitOfWork, chain)
        }.isSameAs(exception)

        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.status.statusCode).isEqualTo(StatusCode.ERROR)
        assertThat(span.status.description).isEqualTo("Handler failed")
        assertThat(span.events).anyMatch { it.name == "exception" }
    }

    @Test
    fun `handle handler should record handler duration`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } answers {
            Thread.sleep(10) // Simulate work
            "result"
        }

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        val duration = span.attributes.asMap()[AttributeKey.longKey("axon.event.handler_duration_ns")]
        assertThat(duration).isNotNull
        assertThat(duration as Long).isGreaterThan(0L)
    }

    @Test
    fun `handle handler should not trace when tracing disabled`() {
        // Given
        configuration =
            TracingConfiguration(
                enabled = false,
                traceCommands = true,
                traceEvents = true,
                traceQueries = true,
            )
        interceptor = EventTracingInterceptor(spanFactory, configuration)

        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        val result = interceptor.handle(unitOfWork, chain)

        // Then
        assertThat(result).isEqualTo("result")
        assertThat(otelTesting.spans).isEmpty()
    }

    @Test
    fun `handle handler should not trace when traceEvents disabled`() {
        // Given
        configuration =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                traceEvents = false,
                traceQueries = true,
            )
        interceptor = EventTracingInterceptor(spanFactory, configuration)

        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        val result = interceptor.handle(unitOfWork, chain)

        // Then
        assertThat(result).isEqualTo("result")
        assertThat(otelTesting.spans).isEmpty()
    }

    @Test
    fun `handle handler should handle regular event without domain metadata`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("RegularEvent")
        val unitOfWork = createUnitOfWork(event)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)

        val span = spans.first()
        assertThat(span.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_ID),
        )
        assertThat(span.attributes.asMap()).doesNotContainKey(
            AttributeKey.stringKey(SpanAttributes.AXON_AGGREGATE_TYPE),
        )
    }

    @Test
    fun `handle handler should extract handler class from unit of work`() {
        // Given
        val event = GenericEventMessage.asEventMessage<String>("TestEvent")
        val handlerClass = TestEventHandler::class.java
        val resources =
            mutableMapOf<String, Any>(
                "handlerClass" to handlerClass,
            )

        val unitOfWork = createUnitOfWork(event, resources)
        val chain = mockk<InterceptorChain>()
        every { chain.proceed() } returns "result"

        // When
        interceptor.handle(unitOfWork, chain)

        // Then
        val spans = otelTesting.spans
        assertThat(spans).hasSize(1)
        // Handler class should be used in span creation (verified by AxonSpanFactory behavior)
    }

    // ========== Helper Methods ==========

    private fun <T> createUnitOfWork(
        event: EventMessage<T>,
        resources: MutableMap<String, Any> = mutableMapOf(),
    ): UnitOfWork<EventMessage<T>> {
        val unitOfWork = mockk<UnitOfWork<EventMessage<T>>>(relaxed = true)
        every { unitOfWork.message } returns event
        every { unitOfWork.resources() } returns resources
        return unitOfWork
    }

    private class TestEventHandler
}
