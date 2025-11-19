package io.github.axonsentry.tracing

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.MetaData
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.GenericQueryMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("AttributeApplier")
class AttributeApplierTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var configuration: TracingConfiguration
    private lateinit var applier: AttributeApplier

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans() // Clear spans from previous tests
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        configuration =
            TracingConfiguration(
                enabled = true,
                captureCommandPayloads = true,
                captureEventPayloads = true,
                captureQueryPayloads = true,
            )
        applier = AttributeApplier(configuration, maxPayloadLength = 1000)
    }

    @Nested
    @DisplayName("Command Attributes")
    inner class CommandAttributes {
        @Test
        @DisplayName("should apply command attributes with operation")
        fun `applyCommandAttributes adds dispatch attributes`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyCommandAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE),
                SpanAttributes.MESSAGE_TYPE_COMMAND,
            )
            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.MESSAGING_OPERATION),
                SpanAttributes.OPERATION_SEND,
            )
            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_ID),
            )
            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey(SpanAttributes.AXON_COMMAND_NAME),
            )
        }

        @Test
        @DisplayName("should capture payload when enabled")
        fun `applyCommandAttributes captures payload when configured`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyCommandAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey("axon.message.payload"),
            )
        }

        @Test
        @DisplayName("should not capture payload when disabled")
        fun `applyCommandAttributes skips payload when disabled`() {
            // Given
            val noPayloadConfig =
                TracingConfiguration(
                    enabled = true,
                    captureCommandPayloads = false,
                )
            val noPayloadApplier = AttributeApplier(noPayloadConfig)
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            noPayloadApplier.applyCommandAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).doesNotContainKey(
                AttributeKey.stringKey("axon.message.payload"),
            )
        }
    }

    @Nested
    @DisplayName("Event Attributes")
    inner class EventAttributes {
        @Test
        @DisplayName("should apply event attributes with operation")
        fun `applyEventAttributes adds publish attributes`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(TestEvent("event-123"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyEventAttributes(spanBuilder, event, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE),
                SpanAttributes.MESSAGE_TYPE_EVENT,
            )
            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey(SpanAttributes.AXON_EVENT_TYPE),
            )
        }

        @Test
        @DisplayName("should capture event timestamp")
        fun `applyEventAttributes captures timestamp`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(TestEvent("event-123"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyEventAttributes(spanBuilder, event, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.longKey(SpanAttributes.AXON_EVENT_TIMESTAMP),
            )
        }
    }

    @Nested
    @DisplayName("Query Attributes")
    inner class QueryAttributes {
        @Test
        @DisplayName("should apply query attributes with operation")
        fun `applyQueryAttributes adds dispatch attributes`() {
            // Given
            val query =
                GenericQueryMessage(
                    TestQuery("query-123"),
                    ResponseTypes.instanceOf(String::class.java),
                )
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyQueryAttributes(spanBuilder, query, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE),
                SpanAttributes.MESSAGE_TYPE_QUERY,
            )
            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey(SpanAttributes.AXON_QUERY_NAME),
            )
        }

        @Test
        @DisplayName("should capture response type")
        fun `applyQueryAttributes captures response type`() {
            // Given
            val query =
                GenericQueryMessage(
                    TestQuery("query-123"),
                    ResponseTypes.instanceOf(String::class.java),
                )
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyQueryAttributes(spanBuilder, query, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey(SpanAttributes.AXON_QUERY_RESPONSE_TYPE),
            )
        }
    }

    @Nested
    @DisplayName("Handler Attributes")
    inner class HandlerAttributes {
        @Test
        @DisplayName("should apply handler class and method attributes")
        fun `applyHandlerAttributes adds handler information`() {
            // Given
            val spanBuilder = tracer.spanBuilder("test-span")
            val handlerClass = TestHandler::class.java
            val handlerMethod = "handleCommand"

            // When
            applier.applyHandlerAttributes(spanBuilder, handlerClass, handlerMethod)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.AXON_HANDLER_CLASS),
                "TestHandler",
            )
            assertThat(spanData.attributes.asMap()).containsEntry(
                AttributeKey.stringKey(SpanAttributes.AXON_HANDLER_METHOD),
                "handleCommand",
            )
        }
    }

    @Nested
    @DisplayName("Metadata Capture")
    inner class MetadataCapture {
        @Test
        @DisplayName("should capture metadata when present")
        fun `applyGenericMessageAttributes captures metadata`() {
            // Given
            val metadata = MetaData.with("userId", "user-123").and("correlationId", "corr-456")
            val command =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyGenericMessageAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).containsKey(
                AttributeKey.stringKey("axon.message.metadata"),
            )
        }

        @Test
        @DisplayName("should not add metadata attribute when empty")
        fun `applyGenericMessageAttributes skips empty metadata`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyGenericMessageAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()

            assertThat(spanData.attributes.asMap()).doesNotContainKey(
                AttributeKey.stringKey("axon.message.metadata"),
            )
        }
    }

    @Nested
    @DisplayName("Payload Sanitization")
    inner class PayloadSanitization {
        @Test
        @DisplayName("should truncate large payloads")
        fun `sanitizes large payload by truncating`() {
            // Given
            val shortApplier = AttributeApplier(configuration, maxPayloadLength = 20)
            val longCommand = TestCommand("x".repeat(100))
            val command = GenericCommandMessage.asCommandMessage<Any>(longCommand)
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            shortApplier.applyCommandAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then
            span.end()
            val spanData = otelTesting.spans.single()
            val payload = spanData.attributes.asMap()[AttributeKey.stringKey("axon.message.payload")] as? String

            assertThat(payload).isNotNull
            assertThat(payload).hasSizeLessThanOrEqualTo(23) // 20 + "..."
            assertThat(payload).endsWith("...")
        }

        @Test
        @DisplayName("should handle empty string payloads gracefully")
        fun `handles empty payload without error`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>("")
            val spanBuilder = tracer.spanBuilder("test-span")

            // When
            applier.applyCommandAttributes(spanBuilder, command, SpanAttributes.OPERATION_SEND)
            val span = spanBuilder.startSpan()

            // Then - Should not throw exception
            span.end()
            assertThat(otelTesting.spans).hasSize(1)
        }
    }

    // Test domain objects
    data class TestCommand(val id: String)

    data class TestEvent(val id: String)

    data class TestQuery(val id: String)

    class TestHandler {
        @Suppress("EmptyFunctionBlock", "UnusedParameter")
        fun handleCommand(command: TestCommand) {
            // Empty handler for testing
        }
    }
}
