package io.github.axonsentry.axon

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("CommandResultSpanEnricher")
class CommandResultSpanEnricherTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var enricher: CommandResultSpanEnricher

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        enricher = CommandResultSpanEnricher()
    }

    @Test
    @DisplayName("should mark null result as void type")
    fun `enrichWithResult handles null result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()

        // When
        enricher.enrichWithResult(span, null)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "void")
    }

    @Test
    @DisplayName("should mark Unit result as void type")
    fun `enrichWithResult handles Unit result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()

        // When
        enricher.enrichWithResult(span, Unit)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "void")
    }

    @Test
    @DisplayName("should capture String result")
    fun `enrichWithResult captures String result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = "success-id-123"

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "java.lang.String")
            .containsEntry(AttributeKey.stringKey("axon.command.result"), "success-id-123")
    }

    @Test
    @DisplayName("should capture Number result")
    fun `enrichWithResult captures Number result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = 42

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "java.lang.Integer")
            .containsEntry(AttributeKey.stringKey("axon.command.result"), "42")
    }

    @Test
    @DisplayName("should capture Boolean result")
    fun `enrichWithResult captures Boolean result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = true

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "java.lang.Boolean")
            .containsEntry(AttributeKey.booleanKey("axon.command.result"), true)
    }

    @Test
    @DisplayName("should capture Long result")
    fun `enrichWithResult captures Long result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = 1000000L

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "java.lang.Long")
            .containsEntry(AttributeKey.stringKey("axon.command.result"), "1000000")
    }

    @Test
    @DisplayName("should capture Double result")
    fun `enrichWithResult captures Double result`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = 3.14

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.command.result_type"), "java.lang.Double")
            .containsEntry(AttributeKey.stringKey("axon.command.result"), "3.14")
    }

    @Test
    @DisplayName("should capture complex type name but not value")
    fun `enrichWithResult handles complex types`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = ComplexResult("id-123", "data")

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(
                AttributeKey.stringKey("axon.command.result_type"),
                ComplexResult::class.java.name,
            )
            .containsEntry(AttributeKey.stringKey("axon.command.result_class"), "ComplexResult")
            .doesNotContainKey(AttributeKey.stringKey("axon.command.result"))
    }

    @Test
    @DisplayName("should handle List results")
    fun `enrichWithResult handles List results`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = listOf("item1", "item2")

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsKey(AttributeKey.stringKey("axon.command.result_type"))
            .containsEntry(AttributeKey.stringKey("axon.command.result_class"), "ArrayList")
    }

    @Test
    @DisplayName("should handle Map results")
    fun `enrichWithResult handles Map results`() {
        // Given
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = mapOf("key" to "value")

        // When
        enricher.enrichWithResult(span, result)
        span.end()

        // Then
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsKey(AttributeKey.stringKey("axon.command.result_type"))
            .containsKey(AttributeKey.stringKey("axon.command.result_class"))
    }

    // Test data class
    private data class ComplexResult(val id: String, val data: String)
}
