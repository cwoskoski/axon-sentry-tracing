package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.providers.CorrelationIdAttributeProvider
import io.github.axonsentry.providers.MetadataAttributeProvider
import io.github.axonsentry.spi.AttributeProvider
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.CompositeAttributeProvider
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.Message
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("AxonSpanFactory with AttributeProvider Integration")
class AxonSpanFactoryAttributeProviderIntegrationTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var configuration: TracingConfiguration
    private lateinit var spanFactory: AxonSpanFactory

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        configuration =
            TracingConfiguration(
                enabled = true,
                captureCommandPayloads = false,
                captureEventPayloads = false,
                captureQueryPayloads = false,
            )
    }

    @Test
    @DisplayName("should apply custom attribute providers to command dispatch spans")
    fun `applies custom providers to command spans`() {
        // Given
        val providers =
            listOf(
                CorrelationIdAttributeProvider(),
                MetadataAttributeProvider(prefix = "ctx"),
            )

        val compositeProvider = CompositeAttributeProvider(providers)
        val attributeApplier =
            AttributeApplier(
                configuration = configuration,
                attributeProvider = compositeProvider,
            )

        spanFactory =
            AxonSpanFactory(
                tracer = tracer,
                configuration = configuration,
                spanNameGenerator = SpanNameGenerator(),
                spanKindResolver = SpanKindResolver(),
                attributeApplier = attributeApplier,
            )

        val metadata =
            MetaData.with("correlationId", "corr-12345")
                .and("userId", "user-123")
                .and("tenantId", "tenant-789")

        val command =
            GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))
                .withMetaData(metadata)

        // When
        val span = spanFactory.createCommandDispatchSpan(command, Context.current())
        span.end()

        // Then
        val spanData = otelTesting.spans.single()

        // Should have custom attributes from providers
        assertThat(spanData.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("correlation.id"),
            "corr-12345",
        )
        assertThat(spanData.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("ctx.userId"),
            "user-123",
        )
        assertThat(spanData.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("ctx.tenantId"),
            "tenant-789",
        )
    }

    @Test
    @DisplayName("should apply high-priority providers first")
    fun `applies providers in priority order`() {
        // Given
        val highPriorityProvider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> =
                    mapOf(
                        "priority.test" to "high",
                    )

                override fun priority(): Int = 1000
            }

        val lowPriorityProvider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> =
                    mapOf(
                        "priority.test" to "low",
                    )

                override fun priority(): Int = 10
            }

        val compositeProvider = CompositeAttributeProvider(listOf(lowPriorityProvider, highPriorityProvider))
        val attributeApplier =
            AttributeApplier(
                configuration = configuration,
                attributeProvider = compositeProvider,
            )

        spanFactory =
            AxonSpanFactory(
                tracer = tracer,
                configuration = configuration,
                spanNameGenerator = SpanNameGenerator(),
                spanKindResolver = SpanKindResolver(),
                attributeApplier = attributeApplier,
            )

        val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))

        // When
        val span = spanFactory.createCommandDispatchSpan(command, Context.current())
        span.end()

        // Then
        val spanData = otelTesting.spans.single()

        // Last provider wins (low priority overwrites high)
        assertThat(spanData.attributes.asMap()).containsEntry(
            AttributeKey.stringKey("priority.test"),
            "low",
        )
    }

    @Test
    @DisplayName("should work without custom attribute providers")
    fun `works without custom providers`() {
        // Given - No custom providers
        val attributeApplier = AttributeApplier(configuration = configuration)

        spanFactory =
            AxonSpanFactory(
                tracer = tracer,
                configuration = configuration,
                spanNameGenerator = SpanNameGenerator(),
                spanKindResolver = SpanKindResolver(),
                attributeApplier = attributeApplier,
            )

        val command = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))

        // When
        val span = spanFactory.createCommandDispatchSpan(command, Context.current())
        span.end()

        // Then - Should still work and have standard attributes
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap()).containsKey(
            AttributeKey.stringKey("axon.message.id"),
        )
    }

    // Test domain objects
    data class TestCommand(val id: String)
}
