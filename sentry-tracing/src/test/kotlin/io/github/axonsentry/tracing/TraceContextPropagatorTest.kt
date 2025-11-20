package io.github.axonsentry.tracing

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.Message
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("TraceContextPropagator")
class TraceContextPropagatorTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var propagator: TraceContextPropagator

    @BeforeEach
    fun setup() {
        // Get tracer from the SDK, not from the OpenTelemetry instance
        // This ensures we get a properly configured tracer that creates valid spans
        tracer = otelTesting.openTelemetry.tracerProvider.get("test-tracer")
        propagator = TraceContextPropagator.withW3CDefaults()
    }

    @Nested
    @DisplayName("inject")
    inner class InjectTest {
        @Test
        fun `should inject current trace context into message metadata`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // Verify span is valid for testing
            assertThat(span.spanContext.isValid).isTrue()

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    propagator.inject(message)
                }

            // Then
            assertThat(enrichedMessage.metaData).containsKey("traceparent")
            val traceparent = enrichedMessage.metaData["traceparent"] as String
            assertThat(traceparent).startsWith("00-")
            assertThat(traceparent).contains(span.spanContext.traceId)
            assertThat(traceparent).contains(span.spanContext.spanId)

            span.end()
        }

        @Test
        fun `should inject trace context with valid W3C format`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    propagator.inject(message)
                }

            // Then
            val traceparent = enrichedMessage.metaData["traceparent"] as String
            val parts = traceparent.split("-")

            assertThat(parts).hasSize(4)
            assertThat(parts[0]).isEqualTo("00") // version
            assertThat(parts[1]).hasSize(32) // trace-id (32 hex chars)
            assertThat(parts[2]).hasSize(16) // span-id (16 hex chars)
            assertThat(parts[3]).isIn("00", "01") // trace-flags

            span.end()
        }

        @Test
        fun `should preserve existing metadata when injecting`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(mapOf("custom-key" to "custom-value"))

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    propagator.inject(message)
                }

            // Then
            assertThat(enrichedMessage.metaData["custom-key"]).isEqualTo("custom-value")
            assertThat(enrichedMessage.metaData).containsKey("traceparent")

            span.end()
        }

        @Test
        fun `should handle injection when no active span`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val enrichedMessage = propagator.inject(message)

            // Then
            // Should still inject, but with invalid/empty trace context
            assertThat(enrichedMessage).isNotNull()
        }

        @Test
        fun `should use extension function for injection`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    message.withTraceContext(propagator)
                }

            // Then
            assertThat(enrichedMessage.metaData).containsKey("traceparent")

            span.end()
        }
    }

    @Nested
    @DisplayName("extract")
    inner class ExtractTest {
        @Test
        fun `should extract trace context from message metadata`() {
            // Given
            val originalSpan = tracer.spanBuilder("original-span").startSpan()
            val message =
                Context.current().with(originalSpan).makeCurrent().use {
                    propagator.inject(GenericCommandMessage.asCommandMessage<String>("test-command"))
                }

            // When
            val extractedContext = propagator.extract(message)
            val extractedSpan = Span.fromContext(extractedContext)

            // Then
            assertThat(extractedSpan.spanContext.traceId).isEqualTo(originalSpan.spanContext.traceId)
            assertThat(extractedSpan.spanContext.spanId).isEqualTo(originalSpan.spanContext.spanId)

            originalSpan.end()
        }

        @Test
        fun `should extract valid span context from W3C traceparent`() {
            // Given
            val traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(mapOf("traceparent" to traceparent))

            // When
            val extractedContext = propagator.extract(message)
            val extractedSpan = Span.fromContext(extractedContext)

            // Then
            assertThat(extractedSpan.spanContext.isValid).isTrue()
            assertThat(extractedSpan.spanContext.traceId).isEqualTo("0af7651916cd43dd8448eb211c80319c")
            assertThat(extractedSpan.spanContext.spanId).isEqualTo("b7ad6b7169203331")
        }

        @Test
        fun `should return current context when no trace context in metadata`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val extractedContext = propagator.extract(message)

            // Then
            assertThat(extractedContext).isNotNull()
            // Should be current context (which may or may not have a span)
        }

        @Test
        fun `should handle invalid traceparent gracefully`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(mapOf("traceparent" to "invalid-format"))

            // When
            val extractedContext = propagator.extract(message)

            // Then
            assertThat(extractedContext).isNotNull()
            val span = Span.fromContext(extractedContext)
            assertThat(span.spanContext.isValid).isFalse()
        }
    }

    @Nested
    @DisplayName("extractSpanContext")
    inner class ExtractSpanContextTest {
        @Test
        fun `should extract span context from message`() {
            // Given
            val originalSpan = tracer.spanBuilder("original-span").startSpan()
            val message =
                Context.current().with(originalSpan).makeCurrent().use {
                    propagator.inject(GenericCommandMessage.asCommandMessage<String>("test-command"))
                }

            // When
            val spanContext = propagator.extractSpanContext(message)

            // Then
            assertThat(spanContext).isNotNull()
            assertThat(spanContext?.isValid).isTrue()
            assertThat(spanContext?.traceId).isEqualTo(originalSpan.spanContext.traceId)

            originalSpan.end()
        }

        @Test
        fun `should return null when no valid trace context`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val spanContext = propagator.extractSpanContext(message)

            // Then
            assertThat(spanContext).isNull()
        }

        @Test
        fun `should return null for invalid span context`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(mapOf("traceparent" to "invalid"))

            // When
            val spanContext = propagator.extractSpanContext(message)

            // Then
            assertThat(spanContext).isNull()
        }
    }

    @Nested
    @DisplayName("Baggage Propagation")
    inner class BaggagePropagationTest {
        private lateinit var compositePropagator: TraceContextPropagator

        @BeforeEach
        fun setupComposite() {
            compositePropagator = TraceContextPropagator.withCompositePropagator()
        }

        @Test
        fun `should inject and extract baggage`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val baggage =
                Baggage.builder()
                    .put("userId", "alice")
                    .put("sessionId", "session-123")
                    .build()

            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val enrichedMessage =
                Context.current()
                    .with(span)
                    .with(baggage)
                    .makeCurrent().use {
                        compositePropagator.inject(message)
                    }

            val extractedBaggage = compositePropagator.extractBaggage(enrichedMessage)

            // Then
            assertThat(extractedBaggage).containsEntry("userId", "alice")
            assertThat(extractedBaggage).containsEntry("sessionId", "session-123")

            span.end()
        }

        @Test
        fun `should extract empty map when no baggage present`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val extractedBaggage = compositePropagator.extractBaggage(message)

            // Then
            assertThat(extractedBaggage).isEmpty()
        }

        @Test
        fun `should inject with custom baggage using injectWithBaggage`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val customBaggage =
                mapOf(
                    "correlationId" to "corr-123",
                    "tenantId" to "tenant-456",
                )

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    compositePropagator.injectWithBaggage(message, customBaggage)
                }

            val extractedBaggage = compositePropagator.extractBaggage(enrichedMessage)

            // Then
            assertThat(extractedBaggage).containsEntry("correlationId", "corr-123")
            assertThat(extractedBaggage).containsEntry("tenantId", "tenant-456")

            span.end()
        }

        @Test
        fun `should use extension function for baggage injection`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val baggage = mapOf("key" to "value")

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    message.withTraceContextAndBaggage(compositePropagator, baggage)
                }

            // Then
            val extractedBaggage = compositePropagator.extractBaggage(enrichedMessage)
            assertThat(extractedBaggage).containsEntry("key", "value")

            span.end()
        }

        @Test
        fun `should handle empty baggage map`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When
            val enrichedMessage =
                Context.current().with(span).makeCurrent().use {
                    compositePropagator.injectWithBaggage(message, emptyMap())
                }

            // Then
            assertThat(enrichedMessage.metaData).containsKey("traceparent")

            span.end()
        }
    }

    @Nested
    @DisplayName("Round-trip Propagation")
    inner class RoundTripTest {
        @Test
        fun `should maintain trace continuity across inject and extract`() {
            // Given
            val parentSpan = tracer.spanBuilder("parent-span").startSpan()
            val command = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When - Dispatch side: inject context
            val enrichedCommand =
                Context.current().with(parentSpan).makeCurrent().use {
                    propagator.inject(command)
                }

            // When - Handler side: extract context and create child span
            val parentContext = propagator.extract(enrichedCommand)
            val childSpan =
                tracer.spanBuilder("child-span")
                    .setParent(parentContext)
                    .startSpan()

            // Then - Verify trace continuity
            assertThat(childSpan.spanContext.traceId).isEqualTo(parentSpan.spanContext.traceId)
            assertThat(childSpan.spanContext.spanId).isNotEqualTo(parentSpan.spanContext.spanId)

            childSpan.end()
            parentSpan.end()
        }

        @Test
        fun `should support multi-hop propagation`() {
            // Given
            val span1 = tracer.spanBuilder("span-1").startSpan()

            // Service 1: Dispatch command
            val message1 =
                Context.current().with(span1).makeCurrent().use {
                    propagator.inject(GenericCommandMessage.asCommandMessage<String>("command-1"))
                }

            // Service 2: Handle command, dispatch event
            val context2 = propagator.extract(message1)
            val span2 = tracer.spanBuilder("span-2").setParent(context2).startSpan()
            val message2 =
                Context.current().with(span2).makeCurrent().use {
                    propagator.inject(GenericCommandMessage.asCommandMessage<String>("event-1"))
                }

            // Service 3: Handle event
            val context3 = propagator.extract(message2)
            val span3 = tracer.spanBuilder("span-3").setParent(context3).startSpan()

            // Then - All spans share the same trace ID
            assertThat(span1.spanContext.traceId).isEqualTo(span2.spanContext.traceId)
            assertThat(span2.spanContext.traceId).isEqualTo(span3.spanContext.traceId)

            // Each span has unique span ID
            assertThat(span1.spanContext.spanId).isNotEqualTo(span2.spanContext.spanId)
            assertThat(span2.spanContext.spanId).isNotEqualTo(span3.spanContext.spanId)

            span3.end()
            span2.end()
            span1.end()
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    inner class FactoryMethodsTest {
        @Test
        fun `should create propagator with W3C defaults`() {
            // When
            val propagator = TraceContextPropagator.withW3CDefaults()

            // Then
            assertThat(propagator).isNotNull()
        }

        @Test
        fun `should create propagator with composite propagator`() {
            // When
            val propagator = TraceContextPropagator.withCompositePropagator()

            // Then
            assertThat(propagator).isNotNull()
        }

        @Test
        fun `composite propagator should support baggage`() {
            // Given
            val propagator = TraceContextPropagator.withCompositePropagator()
            val span = tracer.spanBuilder("test-span").startSpan()
            val baggage = Baggage.builder().put("key", "value").build()

            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When
            val enriched =
                Context.current()
                    .with(span)
                    .with(baggage)
                    .makeCurrent().use {
                        propagator.inject(message)
                    }

            // Then
            assertThat(enriched.metaData).containsKey("baggage")

            span.end()
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTest {
        @Test
        fun `should handle null metadata gracefully during extraction`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")

            // When / Then - should not throw
            val context = propagator.extract(message)
            assertThat(context).isNotNull()
        }

        @Test
        fun `should handle malformed metadata gracefully`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(
                        mapOf(
                            "traceparent" to 12345, // Wrong type
                        ),
                    )

            // When / Then - should not throw
            val context = propagator.extract(message)
            assertThat(context).isNotNull()
        }
    }
}
