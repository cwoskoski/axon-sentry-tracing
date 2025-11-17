package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TraceContextTest {
    @Test
    fun `toMetadataMap produces correct key-value pairs`() {
        // Given
        val traceContext =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "b9c7c989f97918e1",
                traceFlags = TraceFlags.getSampled().asByte(),
                traceState = mapOf("vendor" to "value"),
                baggage = mapOf("userId" to "123"),
            )

        // When
        val metadataMap = traceContext.toMetadataMap()

        // Then
        assertThat(metadataMap).containsEntry("_trace_id", "0af7651916cd43dd8448eb211c80319c")
        assertThat(metadataMap).containsEntry("_span_id", "b9c7c989f97918e1")
        assertThat(metadataMap).containsEntry("_trace_flags", TraceFlags.getSampled().asByte())
        assertThat(metadataMap).containsKey("_trace_state")
        assertThat(metadataMap).containsKey("_baggage")
    }

    @Test
    fun `toMetadataMap omits empty trace state and baggage`() {
        // Given
        val traceContext =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "b9c7c989f97918e1",
            )

        // When
        val metadataMap = traceContext.toMetadataMap()

        // Then
        assertThat(metadataMap).doesNotContainKey("_trace_state")
        assertThat(metadataMap).doesNotContainKey("_baggage")
        assertThat(metadataMap).hasSize(3) // Only traceId, spanId, traceFlags
    }

    @Test
    fun `fromMetadata reconstructs TraceContext accurately`() {
        // Given
        val metadata =
            mapOf<String, Any>(
                "_trace_id" to "0af7651916cd43dd8448eb211c80319c",
                "_span_id" to "b9c7c989f97918e1",
                "_trace_flags" to 1.toByte(),
                "_trace_state" to mapOf("vendor" to "value"),
                "_baggage" to mapOf("userId" to "123"),
            )

        // When
        val traceContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(traceContext).isNotNull
        assertThat(traceContext!!.traceId).isEqualTo("0af7651916cd43dd8448eb211c80319c")
        assertThat(traceContext.spanId).isEqualTo("b9c7c989f97918e1")
        assertThat(traceContext.traceFlags).isEqualTo(1.toByte())
        assertThat(traceContext.traceState).containsEntry("vendor", "value")
        assertThat(traceContext.baggage).containsEntry("userId", "123")
    }

    @Test
    fun `fromMetadata returns null for missing traceId`() {
        // Given
        val metadata =
            mapOf<String, Any>(
                "_span_id" to "b9c7c989f97918e1",
            )

        // When
        val traceContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(traceContext).isNull()
    }

    @Test
    fun `fromMetadata returns null for missing spanId`() {
        // Given
        val metadata =
            mapOf<String, Any>(
                "_trace_id" to "0af7651916cd43dd8448eb211c80319c",
            )

        // When
        val traceContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(traceContext).isNull()
    }

    @Test
    fun `fromMetadata handles missing optional fields`() {
        // Given
        val metadata =
            mapOf<String, Any>(
                "_trace_id" to "0af7651916cd43dd8448eb211c80319c",
                "_span_id" to "b9c7c989f97918e1",
            )

        // When
        val traceContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(traceContext).isNotNull
        assertThat(traceContext!!.traceFlags).isEqualTo(TraceFlags.getDefault().asByte())
        assertThat(traceContext.traceState).isEmpty()
        assertThat(traceContext.baggage).isEmpty()
    }

    @Test
    fun `toSpanContext creates valid OpenTelemetry SpanContext`() {
        // Given
        val traceContext =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "b9c7c989f97918e1",
                traceFlags = TraceFlags.getSampled().asByte(),
                traceState = mapOf("vendor" to "value"),
            )

        // When
        val spanContext = traceContext.toSpanContext()

        // Then
        assertThat(spanContext.traceId).isEqualTo("0af7651916cd43dd8448eb211c80319c")
        assertThat(spanContext.spanId).isEqualTo("b9c7c989f97918e1")
        assertThat(spanContext.traceFlags.asByte()).isEqualTo(TraceFlags.getSampled().asByte())
        assertThat(spanContext.traceState.get("vendor")).isEqualTo("value")
        assertThat(spanContext.isValid).isTrue()
        assertThat(spanContext.isRemote).isTrue()
    }

    @Test
    fun `fromSpanContext captures all context information`() {
        // Given
        val spanContext =
            SpanContext.createFromRemoteParent(
                "0af7651916cd43dd8448eb211c80319c",
                "b9c7c989f97918e1",
                TraceFlags.getSampled(),
                TraceState.builder().put("vendor", "value").build(),
            )
        val baggage = mapOf("userId" to "123")

        // When
        val traceContext = TraceContext.fromSpanContext(spanContext, baggage)

        // Then
        assertThat(traceContext.traceId).isEqualTo("0af7651916cd43dd8448eb211c80319c")
        assertThat(traceContext.spanId).isEqualTo("b9c7c989f97918e1")
        assertThat(traceContext.traceFlags).isEqualTo(TraceFlags.getSampled().asByte())
        assertThat(traceContext.traceState).containsEntry("vendor", "value")
        assertThat(traceContext.baggage).containsEntry("userId", "123")
    }

    @Test
    fun `baggage is preserved through serialization round-trip`() {
        // Given
        val originalContext =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "b9c7c989f97918e1",
                baggage = mapOf("userId" to "123", "sessionId" to "xyz"),
            )

        // When
        val metadata = originalContext.toMetadataMap()
        val reconstructedContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(reconstructedContext).isNotNull
        assertThat(reconstructedContext!!.baggage).isEqualTo(originalContext.baggage)
        assertThat(reconstructedContext.baggage).containsEntry("userId", "123")
        assertThat(reconstructedContext.baggage).containsEntry("sessionId", "xyz")
    }

    @Test
    fun `trace state is preserved through serialization round-trip`() {
        // Given
        val originalContext =
            TraceContext(
                traceId = "0af7651916cd43dd8448eb211c80319c",
                spanId = "b9c7c989f97918e1",
                traceState = mapOf("vendor1" to "value1", "vendor2" to "value2"),
            )

        // When
        val metadata = originalContext.toMetadataMap()
        val reconstructedContext = TraceContext.fromMetadata(metadata)

        // Then
        assertThat(reconstructedContext).isNotNull
        assertThat(reconstructedContext!!.traceState).isEqualTo(originalContext.traceState)
    }

    @Test
    fun `fromSpanContext handles empty baggage`() {
        // Given
        val spanContext =
            SpanContext.createFromRemoteParent(
                "0af7651916cd43dd8448eb211c80319c",
                "b9c7c989f97918e1",
                TraceFlags.getSampled(),
                TraceState.getDefault(),
            )

        // When
        val traceContext = TraceContext.fromSpanContext(spanContext)

        // Then
        assertThat(traceContext.baggage).isEmpty()
    }
}
