package io.github.axonsentry.tracing

import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.axonframework.messaging.MetaData

/**
 * OpenTelemetry TextMapGetter implementation for extracting trace context from Axon MetaData.
 *
 * This adapter bridges the gap between Axon Framework's immutable [MetaData] and
 * OpenTelemetry's [TextMapGetter] interface for trace context propagation.
 *
 * Thread-safe and immutable.
 *
 * @since 1.0.0
 */
object MetaDataGetter : TextMapGetter<MetaData> {
    /**
     * Returns all metadata keys for iteration.
     *
     * @param carrier The Axon MetaData containing trace context headers
     * @return Iterable of all metadata keys
     */
    override fun keys(carrier: MetaData): Iterable<String> = carrier.keys

    /**
     * Retrieves a header value from metadata, converting to String if needed.
     *
     * Handles various value types by converting them to strings. Returns null
     * if the key is not present or the value cannot be converted.
     *
     * @param carrier The Axon MetaData containing trace context headers
     * @param key The header key to retrieve (e.g., "traceparent", "tracestate")
     * @return The header value as a string, or null if not present
     */
    override fun get(
        carrier: MetaData?,
        key: String,
    ): String? {
        if (carrier == null) return null

        return when (val value = carrier[key]) {
            null -> null
            is String -> value
            else -> value.toString()
        }
    }
}

/**
 * OpenTelemetry TextMapSetter implementation for injecting trace context into mutable metadata maps.
 *
 * This adapter bridges the gap between OpenTelemetry's [TextMapSetter] interface and
 * mutable metadata maps used for building new Axon messages.
 *
 * Thread-safe.
 *
 * @since 1.0.0
 */
object MetaDataSetter : TextMapSetter<MutableMap<String, Any>> {
    /**
     * Sets a header value in the mutable metadata map.
     *
     * If the carrier is null, this operation is a no-op. The value is stored as-is
     * (String type) in the metadata map.
     *
     * @param carrier The mutable metadata map to inject headers into
     * @param key The header key (e.g., "traceparent", "tracestate", "baggage")
     * @param value The header value to set
     */
    override fun set(
        carrier: MutableMap<String, Any>?,
        key: String,
        value: String,
    ) {
        carrier?.put(key, value)
    }
}
