package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState

/**
 * Immutable trace context for propagating trace information through Axon messages.
 *
 * This context can be serialized to/from message metadata to maintain trace continuity
 * across async boundaries and distributed systems.
 *
 * @property traceId The W3C trace ID (32 hex characters)
 * @property spanId The W3C span ID (16 hex characters)
 * @property traceFlags Trace flags indicating sampling decisions
 * @property traceState Additional vendor-specific trace state
 * @property baggage Custom key-value pairs propagated with the trace
 *
 * @since 1.0.0
 */
data class TraceContext(
    val traceId: String,
    val spanId: String,
    val traceFlags: Byte = TraceFlags.getDefault().asByte(),
    val traceState: Map<String, String> = emptyMap(),
    val baggage: Map<String, String> = emptyMap(),
) {
    /**
     * Converts this context to a map suitable for storing in Axon message metadata.
     *
     * @return Map containing trace context data with internal keys
     */
    fun toMetadataMap(): Map<String, Any> =
        buildMap {
            put(TRACE_ID_KEY, traceId)
            put(SPAN_ID_KEY, spanId)
            put(TRACE_FLAGS_KEY, traceFlags)
            if (traceState.isNotEmpty()) {
                put(TRACE_STATE_KEY, traceState)
            }
            if (baggage.isNotEmpty()) {
                put(BAGGAGE_KEY, baggage)
            }
        }

    /**
     * Converts to OpenTelemetry SpanContext for creating child spans.
     *
     * @return SpanContext representing this trace context
     */
    fun toSpanContext(): SpanContext =
        SpanContext.createFromRemoteParent(
            traceId,
            spanId,
            TraceFlags.fromByte(traceFlags),
            TraceState.builder().apply {
                traceState.forEach { (key, value) -> put(key, value) }
            }.build(),
        )

    /**
     * Converts to OpenTelemetry Context with this trace context as parent.
     *
     * @return OpenTelemetry Context with this trace as parent
     */
    fun toContext(): io.opentelemetry.context.Context =
        io.opentelemetry.context.Context.root().with(io.opentelemetry.api.trace.Span.wrap(toSpanContext()))

    companion object {
        private const val TRACE_ID_KEY = "_trace_id"
        private const val SPAN_ID_KEY = "_span_id"
        private const val TRACE_FLAGS_KEY = "_trace_flags"
        private const val TRACE_STATE_KEY = "_trace_state"
        private const val BAGGAGE_KEY = "_baggage"

        /**
         * Creates TraceContext from OpenTelemetry SpanContext.
         *
         * @param spanContext The OpenTelemetry span context
         * @param baggage Optional baggage to include
         * @return TraceContext instance
         */
        fun fromSpanContext(
            spanContext: SpanContext,
            baggage: Map<String, String> = emptyMap(),
        ): TraceContext {
            return TraceContext(
                traceId = spanContext.traceId,
                spanId = spanContext.spanId,
                traceFlags = spanContext.traceFlags.asByte(),
                traceState = spanContext.traceState.asMap(),
                baggage = baggage,
            )
        }

        /**
         * Extracts TraceContext from Axon message metadata.
         * Returns null if no trace context is present.
         *
         * @param metadata The message metadata map
         * @return TraceContext if present, null otherwise
         */
        @Suppress("ReturnCount")
        fun fromMetadata(metadata: Map<String, Any>): TraceContext? {
            val traceId = metadata[TRACE_ID_KEY] as? String ?: return null
            val spanId = metadata[SPAN_ID_KEY] as? String ?: return null
            val traceFlags = (metadata[TRACE_FLAGS_KEY] as? Number)?.toByte() ?: TraceFlags.getDefault().asByte()

            @Suppress("UNCHECKED_CAST")
            val traceState = metadata[TRACE_STATE_KEY] as? Map<String, String> ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val baggage = metadata[BAGGAGE_KEY] as? Map<String, String> ?: emptyMap()

            return TraceContext(traceId, spanId, traceFlags, traceState, baggage)
        }
    }
}
