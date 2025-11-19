package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.GenericQueryMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("AxonSpanFactory")
class AxonSpanFactoryTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var factory: AxonSpanFactory

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans() // Clear spans from previous tests
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        val configuration = TracingConfiguration()
        val spanNameGenerator = SpanNameGenerator()
        val spanKindResolver = SpanKindResolver()
        val attributeApplier = AttributeApplier(configuration)

        factory =
            AxonSpanFactory(
                tracer = tracer,
                configuration = configuration,
                spanNameGenerator = spanNameGenerator,
                spanKindResolver = spanKindResolver,
                attributeApplier = attributeApplier,
            )
    }

    @Nested
    @DisplayName("Command Spans")
    inner class CommandSpans {
        @Test
        @DisplayName("should create command dispatch span with CLIENT kind")
        fun `createCommandDispatchSpan creates CLIENT span`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("cmd-123"))

            // When
            val span = factory.createCommandDispatchSpan(command, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Command:")
            assertThat(spanData.kind).isEqualTo(SpanKind.CLIENT)
        }

        @Test
        @DisplayName("should create command handler span with CONSUMER kind")
        fun `createCommandHandlerSpan creates CONSUMER span`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("cmd-123"))
            val handlerClass = TestHandler::class.java
            val handlerMethod = "handleCommand"

            // When
            val span = factory.createCommandHandlerSpan(command, handlerClass, handlerMethod, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Handle:")
            assertThat(spanData.kind).isEqualTo(SpanKind.CONSUMER)
        }
    }

    @Nested
    @DisplayName("Event Spans")
    inner class EventSpans {
        @Test
        @DisplayName("should create event publish span with PRODUCER kind")
        fun `createEventPublishSpan creates PRODUCER span`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(TestEvent("evt-123"))

            // When
            val span = factory.createEventPublishSpan(event, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Event:")
            assertThat(spanData.kind).isEqualTo(SpanKind.PRODUCER)
        }

        @Test
        @DisplayName("should create event handler span with CONSUMER kind")
        fun `createEventHandlerSpan creates CONSUMER span`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(TestEvent("evt-123"))
            val handlerClass = TestHandler::class.java
            val handlerMethod = "on"

            // When
            val span = factory.createEventHandlerSpan(event, handlerClass, handlerMethod, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Handle:")
            assertThat(spanData.kind).isEqualTo(SpanKind.CONSUMER)
        }
    }

    @Nested
    @DisplayName("Query Spans")
    inner class QuerySpans {
        @Test
        @DisplayName("should create query dispatch span with CLIENT kind")
        fun `createQueryDispatchSpan creates CLIENT span`() {
            // Given
            val query =
                GenericQueryMessage(
                    TestQuery("qry-123"),
                    ResponseTypes.instanceOf(String::class.java),
                )

            // When
            val span = factory.createQueryDispatchSpan(query, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Query:")
            assertThat(spanData.kind).isEqualTo(SpanKind.CLIENT)
        }

        @Test
        @DisplayName("should create query handler span with CONSUMER kind")
        fun `createQueryHandlerSpan creates CONSUMER span`() {
            // Given
            val query =
                GenericQueryMessage(
                    TestQuery("qry-123"),
                    ResponseTypes.instanceOf(String::class.java),
                )
            val handlerClass = TestHandler::class.java
            val handlerMethod = "handleQuery"

            // When
            val span = factory.createQueryHandlerSpan(query, handlerClass, handlerMethod, Context.root())

            // Then
            assertThat(span).isNotNull
            span.end()

            val spanData = otelTesting.spans.single()
            assertThat(spanData.name).startsWith("Handle:")
            assertThat(spanData.kind).isEqualTo(SpanKind.CONSUMER)
        }
    }

    // Test domain objects
    data class TestCommand(val id: String)

    data class TestEvent(val id: String)

    data class TestQuery(val id: String)

    @Suppress("EmptyFunctionBlock", "UnusedParameter", "FunctionOnlyReturningConstant")
    class TestHandler {
        fun handleCommand(command: TestCommand) {
            // Empty handler for testing
        }

        fun on(event: TestEvent) {
            // Empty handler for testing
        }

        fun handleQuery(query: TestQuery): String = "result"
    }
}
