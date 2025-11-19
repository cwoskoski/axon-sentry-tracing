package io.github.axonsentry.axon

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Optional

@DisplayName("QueryResultSpanEnricher")
class QueryResultSpanEnricherTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    private lateinit var tracer: Tracer
    private lateinit var enricher: QueryResultSpanEnricher

    @BeforeEach
    fun setUp() {
        otelTesting.clearSpans()
        tracer = otelTesting.openTelemetry.getTracer("test-tracer")
        enricher = QueryResultSpanEnricher()
    }

    @Test
    @DisplayName("should mark null result as void type")
    fun `enrichWithResult handles null result`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        enricher.enrichWithResult(span, null)
        span.end()
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "void")
    }

    @Test
    @DisplayName("should mark Unit result as void type")
    fun `enrichWithResult handles Unit result`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        enricher.enrichWithResult(span, Unit)
        span.end()
        val spanData = otelTesting.spans.single()
        assertThat(spanData.attributes.asMap())
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "void")
    }

    @Test
    @DisplayName("should capture single String result")
    fun `enrichWithResult captures single String result`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = "user-123"
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "java.lang.String")
            .containsEntry(AttributeKey.stringKey("axon.query.result"), "user-123")
    }

    @Test
    @DisplayName("should capture single Number result")
    fun `enrichWithResult captures single Number result`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = 42
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "java.lang.Integer")
            .containsEntry(AttributeKey.stringKey("axon.query.result"), "42")
    }

    @Test
    @DisplayName("should capture single Boolean result")
    fun `enrichWithResult captures single Boolean result`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = true
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "java.lang.Boolean")
            .containsEntry(AttributeKey.booleanKey("axon.query.result"), true)
    }

    @Test
    @DisplayName("should capture single complex type with type name only")
    fun `enrichWithResult handles single complex type`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = QueryResult("user-123", "John Doe")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(
                AttributeKey.stringKey("axon.query.result_type"),
                QueryResult::class.java.name,
            )
            .containsEntry(AttributeKey.stringKey("axon.query.result_class"), "QueryResult")
            .doesNotContainKey(AttributeKey.stringKey("axon.query.result"))
    }

    @Test
    @DisplayName("should capture empty Optional as empty")
    fun `enrichWithResult handles empty Optional`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = Optional.empty<String>()
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "Optional")
            .containsEntry(AttributeKey.booleanKey("axon.query.result_present"), false)
    }

    @Test
    @DisplayName("should capture present Optional with value type")
    fun `enrichWithResult handles present Optional`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = Optional.of("user-123")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "Optional")
            .containsEntry(AttributeKey.booleanKey("axon.query.result_present"), true)
            .containsEntry(AttributeKey.stringKey("axon.query.result_value_type"), "java.lang.String")
    }

    @Test
    @DisplayName("should capture List result with count")
    fun `enrichWithResult handles List results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = listOf("user-1", "user-2", "user-3")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "List")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 3L)
            .containsEntry(AttributeKey.stringKey("axon.query.result_class"), "ArrayList")
    }

    @Test
    @DisplayName("should capture empty List result")
    fun `enrichWithResult handles empty List`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = emptyList<String>()
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "List")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 0L)
    }

    @Test
    @DisplayName("should capture Set result with count")
    fun `enrichWithResult handles Set results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = setOf("user-1", "user-2")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "Set")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 2L)
    }

    @Test
    @DisplayName("should capture Collection result with count")
    fun `enrichWithResult handles Collection results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result: Collection<String> = listOf("item1", "item2", "item3", "item4")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "List")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 4L)
    }

    @Test
    @DisplayName("should handle Map results")
    fun `enrichWithResult handles Map results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = mapOf("key1" to "value1", "key2" to "value2")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsKey(AttributeKey.stringKey("axon.query.result_type"))
            .containsKey(AttributeKey.stringKey("axon.query.result_class"))
    }

    @Test
    @DisplayName("should handle Iterable results with count")
    fun `enrichWithResult handles Iterable results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result: Iterable<String> = listOf("a", "b", "c")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "List")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 3L)
    }

    @Test
    @DisplayName("should handle array results with count")
    fun `enrichWithResult handles array results`() {
        val span = tracer.spanBuilder("test-span").startSpan()
        val result = arrayOf("a", "b", "c")
        enricher.enrichWithResult(span, result)
        span.end()
        val spanData = otelTesting.spans.single()
        val attributes = spanData.attributes.asMap()
        assertThat(attributes)
            .containsEntry(AttributeKey.stringKey("axon.query.result_type"), "Array")
            .containsEntry(AttributeKey.longKey("axon.query.result_count"), 3L)
    }

    private data class QueryResult(val id: String, val name: String)
}
