package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("DistributedTraceCorrelator")
class DistributedTraceCorrelatorTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var correlator: DistributedTraceCorrelator

    @BeforeEach
    fun setup() {
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        correlator = DistributedTraceCorrelator()
    }

    @Nested
    @DisplayName("CorrelationId")
    inner class CorrelationIdTest {
        @Test
        fun `should generate unique correlation IDs`() {
            // When
            val id1 = CorrelationId.generate()
            val id2 = CorrelationId.generate()

            // Then
            assertThat(id1.value).isNotEqualTo(id2.value)
            assertThat(id1).isNotEqualTo(id2)
        }

        @Test
        fun `should create correlation ID from string`() {
            // Given
            val value = "corr-123-456"

            // When
            val id = CorrelationId.of(value)

            // Then
            assertThat(id.value).isEqualTo(value)
        }

        @Test
        fun `should extract correlation ID from message`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(mapOf(DistributedTraceCorrelator.CORRELATION_ID_KEY to "corr-123"))

            // When
            val id = CorrelationId.fromMessage(message)

            // Then
            assertThat(id).isNotNull()
            assertThat(id?.value).isEqualTo("corr-123")
        }

        @Test
        fun `should return null when correlation ID not in message`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When
            val id = CorrelationId.fromMessage(message)

            // Then
            assertThat(id).isNull()
        }

        @Test
        fun `should convert to string`() {
            // Given
            val id = CorrelationId.of("test-id")

            // When
            val string = id.toString()

            // Then
            assertThat(string).isEqualTo("test-id")
        }

        @Test
        fun `should be a value class`() {
            // Given
            val id1 = CorrelationId.of("same-value")
            val id2 = CorrelationId.of("same-value")

            // Then
            assertThat(id1).isEqualTo(id2)
            assertThat(id1.value).isEqualTo(id2.value)
        }
    }

    @Nested
    @DisplayName("TransactionId")
    inner class TransactionIdTest {
        @Test
        fun `should generate unique transaction IDs`() {
            // When
            val id1 = TransactionId.generate()
            val id2 = TransactionId.generate()

            // Then
            assertThat(id1.value).isNotEqualTo(id2.value)
            assertThat(id1).isNotEqualTo(id2)
        }

        @Test
        fun `should create transaction ID from string`() {
            // Given
            val value = "txn-789"

            // When
            val id = TransactionId.of(value)

            // Then
            assertThat(id.value).isEqualTo(value)
        }

        @Test
        fun `should create transaction ID from trace ID`() {
            // Given
            val traceId = "0af7651916cd43dd8448eb211c80319c"

            // When
            val id = TransactionId.fromTraceId(traceId)

            // Then
            assertThat(id.value).isEqualTo(traceId)
        }

        @Test
        fun `should extract transaction ID from message`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(mapOf(DistributedTraceCorrelator.TRANSACTION_ID_KEY to "txn-456"))

            // When
            val id = TransactionId.fromMessage(message)

            // Then
            assertThat(id).isNotNull()
            assertThat(id?.value).isEqualTo("txn-456")
        }

        @Test
        fun `should return null when transaction ID not in message`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When
            val id = TransactionId.fromMessage(message)

            // Then
            assertThat(id).isNull()
        }

        @Test
        fun `should convert to string`() {
            // Given
            val id = TransactionId.of("test-txn-id")

            // When
            val string = id.toString()

            // Then
            assertThat(string).isEqualTo("test-txn-id")
        }
    }

    @Nested
    @DisplayName("withCorrelationId")
    inner class WithCorrelationIdTest {
        @Test
        fun `should attach correlation ID to message`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val correlationId = CorrelationId.of("corr-123")

            // When
            val enriched = correlator.withCorrelationId(message, correlationId)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY])
                .isEqualTo("corr-123")
        }

        @Test
        fun `should use extension function to attach correlation ID`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val correlationId = CorrelationId.of("corr-456")

            // When
            val enriched = message.withCorrelationId(correlationId)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY])
                .isEqualTo("corr-456")
        }

        @Test
        fun `should preserve existing metadata`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test-command")
                    .andMetaData(mapOf("custom-key" to "custom-value"))
            val correlationId = CorrelationId.of("corr-789")

            // When
            val enriched = correlator.withCorrelationId(message, correlationId)

            // Then
            assertThat(enriched.metaData["custom-key"]).isEqualTo("custom-value")
            assertThat(enriched.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY])
                .isEqualTo("corr-789")
        }
    }

    @Nested
    @DisplayName("withTransactionId")
    inner class WithTransactionIdTest {
        @Test
        fun `should attach transaction ID to message`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val transactionId = TransactionId.of("txn-123")

            // When
            val enriched = correlator.withTransactionId(message, transactionId)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.TRANSACTION_ID_KEY])
                .isEqualTo("txn-123")
        }

        @Test
        fun `should use extension function to attach transaction ID`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val transactionId = TransactionId.of("txn-456")

            // When
            val enriched = message.withTransactionId(transactionId)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.TRANSACTION_ID_KEY])
                .isEqualTo("txn-456")
        }
    }

    @Nested
    @DisplayName("withCorrelationContext")
    inner class WithCorrelationContextTest {
        @Test
        fun `should attach both correlation and transaction IDs`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val correlationId = CorrelationId.of("corr-123")
            val transactionId = TransactionId.of("txn-456")

            // When
            val enriched = correlator.withCorrelationContext(message, correlationId, transactionId)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY])
                .isEqualTo("corr-123")
            assertThat(enriched.metaData[DistributedTraceCorrelator.TRANSACTION_ID_KEY])
                .isEqualTo("txn-456")
        }

        @Test
        fun `should use extension function to attach correlation context`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val context =
                CorrelationContext(
                    correlationId = CorrelationId.of("corr-789"),
                    transactionId = TransactionId.of("txn-012"),
                )

            // When
            val enriched = message.withCorrelationContext(context)

            // Then
            assertThat(enriched.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY])
                .isEqualTo("corr-789")
            assertThat(enriched.metaData[DistributedTraceCorrelator.TRANSACTION_ID_KEY])
                .isEqualTo("txn-012")
        }

        @Test
        fun `should handle empty correlation context`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test-command")
            val context = CorrelationContext()

            // When
            val enriched = message.withCorrelationContext(context)

            // Then
            assertThat(enriched.metaData).doesNotContainKey(DistributedTraceCorrelator.CORRELATION_ID_KEY)
            assertThat(enriched.metaData).doesNotContainKey(DistributedTraceCorrelator.TRANSACTION_ID_KEY)
        }
    }

    @Nested
    @DisplayName("extractCorrelationContext")
    inner class ExtractCorrelationContextTest {
        @Test
        fun `should extract correlation context from message`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(
                        mapOf(
                            DistributedTraceCorrelator.CORRELATION_ID_KEY to "corr-123",
                            DistributedTraceCorrelator.TRANSACTION_ID_KEY to "txn-456",
                        ),
                    )

            // When
            val context = correlator.extractCorrelationContext(message)

            // Then
            assertThat(context.correlationId?.value).isEqualTo("corr-123")
            assertThat(context.transactionId?.value).isEqualTo("txn-456")
        }

        @Test
        fun `should extract partial correlation context`() {
            // Given
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(mapOf(DistributedTraceCorrelator.CORRELATION_ID_KEY to "corr-only"))

            // When
            val context = correlator.extractCorrelationContext(message)

            // Then
            assertThat(context.correlationId?.value).isEqualTo("corr-only")
            assertThat(context.transactionId).isNull()
        }

        @Test
        fun `should return empty context when no correlation info in message`() {
            // Given
            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When
            val context = correlator.extractCorrelationContext(message)

            // Then
            assertThat(context.correlationId).isNull()
            assertThat(context.transactionId).isNull()
        }

        @Test
        fun `should extract trace ID from W3C traceparent`() {
            // Given
            val traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(mapOf("traceparent" to traceparent))

            // When
            val context = correlator.extractCorrelationContext(message)

            // Then
            assertThat(context.traceId).isEqualTo("0af7651916cd43dd8448eb211c80319c")
        }

        @Test
        fun `should extract trace ID from active span when no traceparent`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When
            val context =
                Context.current().with(span).makeCurrent().use {
                    correlator.extractCorrelationContext(message)
                }

            // Then
            assertThat(context.traceId).isEqualTo(span.spanContext.traceId)

            span.end()
        }
    }

    @Nested
    @DisplayName("linkSpanToCorrelationContext")
    inner class LinkSpanToCorrelationContextTest {
        @Test
        fun `should add correlation attributes to span`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message =
                GenericCommandMessage.asCommandMessage<String>("test")
                    .andMetaData(
                        mapOf(
                            DistributedTraceCorrelator.CORRELATION_ID_KEY to "corr-123",
                            DistributedTraceCorrelator.TRANSACTION_ID_KEY to "txn-456",
                        ),
                    )

            // When
            correlator.linkSpanToCorrelationContext(span, message)
            span.end()

            // Then
            val finishedSpan = otelTesting.getSpans().first()
            assertThat(
                finishedSpan.attributes.asMap()[
                    io.opentelemetry.api.common.AttributeKey.stringKey(
                        DistributedTraceCorrelator.CORRELATION_ID_ATTRIBUTE,
                    ),
                ],
            ).isEqualTo("corr-123")
            assertThat(
                finishedSpan.attributes.asMap()[
                    io.opentelemetry.api.common.AttributeKey.stringKey(
                        DistributedTraceCorrelator.TRANSACTION_ID_ATTRIBUTE,
                    ),
                ],
            ).isEqualTo("txn-456")
        }

        @Test
        fun `should handle message without correlation context`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val message = GenericCommandMessage.asCommandMessage<String>("test")

            // When / Then - should not throw
            correlator.linkSpanToCorrelationContext(span, message)
            span.end()
        }
    }

    @Nested
    @DisplayName("generateCorrelationContext")
    inner class GenerateCorrelationContextTest {
        @Test
        fun `should generate new correlation context`() {
            // When
            val context = correlator.generateCorrelationContext()

            // Then
            assertThat(context.correlationId).isNotNull()
            assertThat(context.transactionId).isNotNull()
        }

        @Test
        fun `should use trace ID as transaction ID when useTraceIdAsTransactionId is true`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()

            // When
            val context =
                Context.current().with(span).makeCurrent().use {
                    correlator.generateCorrelationContext(useTraceIdAsTransactionId = true)
                }

            // Then
            assertThat(context.transactionId?.value).isEqualTo(span.spanContext.traceId)

            span.end()
        }

        @Test
        fun `should generate random transaction ID when useTraceIdAsTransactionId is false`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()

            // When
            val context =
                Context.current().with(span).makeCurrent().use {
                    correlator.generateCorrelationContext(useTraceIdAsTransactionId = false)
                }

            // Then
            assertThat(context.transactionId?.value).isNotEqualTo(span.spanContext.traceId)

            span.end()
        }

        @Test
        fun `should generate unique correlation contexts`() {
            // When
            val context1 = correlator.generateCorrelationContext()
            val context2 = correlator.generateCorrelationContext()

            // Then
            assertThat(context1.correlationId).isNotEqualTo(context2.correlationId)
        }
    }

    @Nested
    @DisplayName("createChildContext")
    inner class CreateChildContextTest {
        @Test
        fun `should create child context with new correlation ID but same transaction ID`() {
            // Given
            val parent =
                CorrelationContext(
                    correlationId = CorrelationId.of("parent-corr"),
                    transactionId = TransactionId.of("shared-txn"),
                )

            // When
            val child = correlator.createChildContext(parent)

            // Then
            assertThat(child.correlationId).isNotEqualTo(parent.correlationId)
            assertThat(child.transactionId).isEqualTo(parent.transactionId)
        }

        @Test
        fun `should generate transaction ID if parent has none`() {
            // Given
            val parent = CorrelationContext(correlationId = CorrelationId.of("parent-corr"))

            // When
            val child = correlator.createChildContext(parent)

            // Then
            assertThat(child.transactionId).isNotNull()
        }

        @Test
        fun `should capture current trace ID in child context`() {
            // Given
            val span = tracer.spanBuilder("test-span").startSpan()
            val parent = CorrelationContext(transactionId = TransactionId.of("txn-123"))

            // When
            val child =
                Context.current().with(span).makeCurrent().use {
                    correlator.createChildContext(parent)
                }

            // Then
            assertThat(child.traceId).isEqualTo(span.spanContext.traceId)

            span.end()
        }
    }

    @Nested
    @DisplayName("CorrelationContext")
    inner class CorrelationContextTest {
        @Test
        fun `should report has correlation when IDs present`() {
            // Given
            val context = CorrelationContext(correlationId = CorrelationId.of("corr-123"))

            // When / Then
            assertThat(context.hasCorrelation()).isTrue()
        }

        @Test
        fun `should report no correlation when all IDs null`() {
            // Given
            val context = CorrelationContext()

            // When / Then
            assertThat(context.hasCorrelation()).isFalse()
        }

        @Test
        fun `should convert to metadata map`() {
            // Given
            val context =
                CorrelationContext(
                    correlationId = CorrelationId.of("corr-123"),
                    transactionId = TransactionId.of("txn-456"),
                )

            // When
            val map = context.toMetadataMap()

            // Then
            assertThat(map).containsEntry(DistributedTraceCorrelator.CORRELATION_ID_KEY, "corr-123")
            assertThat(map).containsEntry(DistributedTraceCorrelator.TRANSACTION_ID_KEY, "txn-456")
        }

        @Test
        fun `should convert to metadata map with only present IDs`() {
            // Given
            val context = CorrelationContext(correlationId = CorrelationId.of("corr-only"))

            // When
            val map = context.toMetadataMap()

            // Then
            assertThat(map).containsEntry(DistributedTraceCorrelator.CORRELATION_ID_KEY, "corr-only")
            assertThat(map).doesNotContainKey(DistributedTraceCorrelator.TRANSACTION_ID_KEY)
        }

        @Test
        fun `should convert to empty map when no IDs present`() {
            // Given
            val context = CorrelationContext()

            // When
            val map = context.toMetadataMap()

            // Then
            assertThat(map).isEmpty()
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    inner class IntegrationScenariosTest {
        @Test
        fun `should support command-event correlation pattern`() {
            // Given - Command handler creates correlation context
            val commandContext = correlator.generateCorrelationContext()
            val command =
                GenericCommandMessage.asCommandMessage<String>("CreateOrder")
                    .withCorrelationContext(commandContext)

            // When - Event handler extracts and creates child context
            val extractedContext = correlator.extractCorrelationContext(command)
            val eventContext = correlator.createChildContext(extractedContext)
            val event =
                GenericCommandMessage.asCommandMessage<String>("OrderCreated")
                    .withCorrelationContext(eventContext)

            // Then - Both share transaction ID but have different correlation IDs
            val finalContext = correlator.extractCorrelationContext(event)
            assertThat(finalContext.transactionId).isEqualTo(commandContext.transactionId)
            assertThat(finalContext.correlationId).isNotEqualTo(commandContext.correlationId)
        }

        @Test
        fun `should support distributed transaction across services`() {
            // Service 1: Initiate transaction
            val service1Context = correlator.generateCorrelationContext()
            val message1 =
                GenericCommandMessage.asCommandMessage<String>("Step1")
                    .withCorrelationContext(service1Context)

            // Service 2: Continue transaction
            val service2ExtractedContext = correlator.extractCorrelationContext(message1)
            val service2Context = correlator.createChildContext(service2ExtractedContext)
            val message2 =
                GenericCommandMessage.asCommandMessage<String>("Step2")
                    .withCorrelationContext(service2Context)

            // Service 3: Complete transaction
            val service3ExtractedContext = correlator.extractCorrelationContext(message2)
            val service3Context = correlator.createChildContext(service3ExtractedContext)

            // Then - All share same transaction ID
            assertThat(service3Context.transactionId).isEqualTo(service1Context.transactionId)
            assertThat(service3Context.transactionId).isEqualTo(service2Context.transactionId)

            // But have different correlation IDs
            assertThat(service1Context.correlationId).isNotEqualTo(service2Context.correlationId)
            assertThat(service2Context.correlationId).isNotEqualTo(service3Context.correlationId)
        }
    }
}
