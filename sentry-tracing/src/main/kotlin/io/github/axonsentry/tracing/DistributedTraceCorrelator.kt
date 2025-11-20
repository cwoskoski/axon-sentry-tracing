package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceId
import org.axonframework.messaging.Message
import java.util.UUID

/**
 * Correlation ID for distributed transactions across service boundaries.
 *
 * This value class provides a type-safe wrapper around correlation IDs used for
 * linking related traces in distributed systems. The ID is immutable and can be
 * used as a baggage value or custom attribute.
 *
 * @property value The correlation ID string (typically a UUID)
 *
 * @since 1.0.0
 */
@JvmInline
value class CorrelationId(val value: String) {
    companion object {
        /**
         * Generates a new random correlation ID using UUID.
         *
         * @return A new CorrelationId with a random UUID value
         */
        fun generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString())

        /**
         * Creates a CorrelationId from an existing string value.
         *
         * @param value The correlation ID string
         * @return CorrelationId wrapping the provided value
         */
        fun of(value: String): CorrelationId = CorrelationId(value)

        /**
         * Extracts correlation ID from message metadata.
         *
         * Looks for the correlation ID in the standard metadata key.
         *
         * @param message The Axon message to extract from
         * @return CorrelationId if present in metadata, null otherwise
         */
        fun <T> fromMessage(message: Message<T>): CorrelationId? {
            val value = message.metaData[DistributedTraceCorrelator.CORRELATION_ID_KEY] as? String
            return value?.let { CorrelationId(it) }
        }
    }

    override fun toString(): String = value
}

/**
 * Transaction ID for grouping related operations across a distributed transaction.
 *
 * This value class provides a type-safe wrapper around transaction IDs used for
 * correlating all operations within a business transaction, even across multiple
 * traces and services.
 *
 * @property value The transaction ID string
 *
 * @since 1.0.0
 */
@JvmInline
value class TransactionId(val value: String) {
    companion object {
        /**
         * Generates a new random transaction ID using UUID.
         *
         * @return A new TransactionId with a random UUID value
         */
        fun generate(): TransactionId = TransactionId(UUID.randomUUID().toString())

        /**
         * Creates a TransactionId from an existing string value.
         *
         * @param value The transaction ID string
         * @return TransactionId wrapping the provided value
         */
        fun of(value: String): TransactionId = TransactionId(value)

        /**
         * Derives a TransactionId from an OpenTelemetry trace ID.
         *
         * This is useful for using the trace ID as the transaction ID,
         * ensuring all spans in a trace share the same transaction ID.
         *
         * @param traceId The OpenTelemetry trace ID
         * @return TransactionId derived from the trace ID
         */
        fun fromTraceId(traceId: String): TransactionId = TransactionId(traceId)

        /**
         * Extracts transaction ID from message metadata.
         *
         * Looks for the transaction ID in the standard metadata key.
         *
         * @param message The Axon message to extract from
         * @return TransactionId if present in metadata, null otherwise
         */
        fun <T> fromMessage(message: Message<T>): TransactionId? {
            val value = message.metaData[DistributedTraceCorrelator.TRANSACTION_ID_KEY] as? String
            return value?.let { TransactionId(it) }
        }
    }

    override fun toString(): String = value
}

/**
 * Correlates distributed traces across Axon messages, service boundaries, and async operations.
 *
 * This class provides utilities for generating and propagating correlation IDs and transaction IDs
 * that link related traces in distributed systems. It supports:
 *
 * - Correlation IDs for linking related messages
 * - Transaction IDs for grouping business transactions
 * - Service mesh integration patterns
 * - Baggage propagation for correlation metadata
 *
 * Correlation patterns:
 * - **CorrelationId**: Links related messages (e.g., command -> events -> queries)
 * - **TransactionId**: Links entire business transaction across services
 * - **TraceId**: Links spans within a single distributed trace (OpenTelemetry standard)
 *
 * Usage:
 * ```kotlin
 * val correlator = DistributedTraceCorrelator()
 *
 * // Generate and attach correlation ID
 * val correlationId = CorrelationId.generate()
 * val enrichedCommand = correlator.withCorrelationId(command, correlationId)
 *
 * // Extract correlation context
 * val context = correlator.extractCorrelationContext(event)
 * println("Correlation ID: ${context.correlationId}")
 * println("Transaction ID: ${context.transactionId}")
 * ```
 *
 * @since 1.0.0
 */
class DistributedTraceCorrelator {
    /**
     * Attaches a correlation ID to an Axon message.
     *
     * The correlation ID is added to message metadata and can be extracted on the
     * receiving side to correlate related messages.
     *
     * @param T The message payload type
     * @param message The message to enrich
     * @param correlationId The correlation ID to attach
     * @return A new message with the correlation ID in metadata
     */
    fun <T> withCorrelationId(
        message: Message<T>,
        correlationId: CorrelationId,
    ): Message<T> =
        message.andMetaData(
            mapOf(CORRELATION_ID_KEY to correlationId.value),
        )

    /**
     * Attaches a transaction ID to an Axon message.
     *
     * The transaction ID is added to message metadata and can be extracted on the
     * receiving side to correlate all operations in a business transaction.
     *
     * @param T The message payload type
     * @param message The message to enrich
     * @param transactionId The transaction ID to attach
     * @return A new message with the transaction ID in metadata
     */
    fun <T> withTransactionId(
        message: Message<T>,
        transactionId: TransactionId,
    ): Message<T> =
        message.andMetaData(
            mapOf(TRANSACTION_ID_KEY to transactionId.value),
        )

    /**
     * Attaches both correlation and transaction IDs to an Axon message.
     *
     * @param T The message payload type
     * @param message The message to enrich
     * @param correlationId The correlation ID to attach
     * @param transactionId The transaction ID to attach
     * @return A new message with both IDs in metadata
     */
    fun <T> withCorrelationContext(
        message: Message<T>,
        correlationId: CorrelationId,
        transactionId: TransactionId,
    ): Message<T> =
        message.andMetaData(
            mapOf(
                CORRELATION_ID_KEY to correlationId.value,
                TRANSACTION_ID_KEY to transactionId.value,
            ),
        )

    /**
     * Extracts correlation context (correlation ID and transaction ID) from a message.
     *
     * @param T The message payload type
     * @param message The message to extract from
     * @return CorrelationContext containing extracted IDs (may have null values if not present)
     */
    fun <T> extractCorrelationContext(message: Message<T>): CorrelationContext {
        val correlationId = CorrelationId.fromMessage(message)
        val transactionId = TransactionId.fromMessage(message)
        val traceId = extractTraceId(message)

        return CorrelationContext(
            correlationId = correlationId,
            transactionId = transactionId,
            traceId = traceId,
        )
    }

    /**
     * Links a new span to a correlation context from a message.
     *
     * This adds correlation and transaction IDs as span attributes, making them
     * searchable in Sentry and other observability platforms.
     *
     * @param span The span to enrich with correlation attributes
     * @param message The message containing correlation context
     */
    fun <T> linkSpanToCorrelationContext(
        span: Span,
        message: Message<T>,
    ) {
        val context = extractCorrelationContext(message)

        context.correlationId?.let {
            span.setAttribute(CORRELATION_ID_ATTRIBUTE, it.value)
        }

        context.transactionId?.let {
            span.setAttribute(TRANSACTION_ID_ATTRIBUTE, it.value)
        }
    }

    /**
     * Generates a new correlation context for initiating a distributed transaction.
     *
     * This creates a new correlation ID and derives a transaction ID from the current
     * trace ID (if available) or generates a new one.
     *
     * @param useTraceIdAsTransactionId If true, use current trace ID as transaction ID
     * @return A new CorrelationContext with generated IDs
     */
    fun generateCorrelationContext(useTraceIdAsTransactionId: Boolean = true): CorrelationContext {
        val correlationId = CorrelationId.generate()
        val currentSpan = Span.current()
        val traceId = if (currentSpan.spanContext.isValid) currentSpan.spanContext.traceId else null

        val transactionId =
            if (useTraceIdAsTransactionId && traceId != null) {
                TransactionId.fromTraceId(traceId)
            } else {
                TransactionId.generate()
            }

        return CorrelationContext(
            correlationId = correlationId,
            transactionId = transactionId,
            traceId = traceId,
        )
    }

    /**
     * Creates a child correlation context, preserving transaction ID but generating new correlation ID.
     *
     * This is useful for correlated child operations (e.g., events from a command) that should
     * share the same transaction but have unique correlation IDs.
     *
     * @param parent The parent correlation context
     * @return A new CorrelationContext with new correlation ID but same transaction ID
     */
    fun createChildContext(parent: CorrelationContext): CorrelationContext {
        return CorrelationContext(
            correlationId = CorrelationId.generate(),
            transactionId = parent.transactionId ?: TransactionId.generate(),
            traceId = Span.current().spanContext.traceId.takeIf { it.isNotEmpty() },
        )
    }

    private fun <T> extractTraceId(message: Message<T>): String? {
        // Try to extract from W3C trace context in metadata
        val metadataGetter = MetaDataGetter
        val traceparent = metadataGetter.get(message.metaData, "traceparent")

        if (traceparent != null) {
            // W3C traceparent format: 00-{trace-id}-{span-id}-{flags}
            val parts = traceparent.split("-")
            if (parts.size >= 2 && TraceId.isValid(parts[1])) {
                return parts[1]
            }
        }

        // Fallback to current span
        val currentSpan = Span.current()
        return if (currentSpan.spanContext.isValid) {
            currentSpan.spanContext.traceId
        } else {
            null
        }
    }

    companion object {
        /**
         * Metadata key for correlation ID.
         */
        const val CORRELATION_ID_KEY = "axon.correlation.id"

        /**
         * Metadata key for transaction ID.
         */
        const val TRANSACTION_ID_KEY = "axon.transaction.id"

        /**
         * Span attribute key for correlation ID.
         */
        const val CORRELATION_ID_ATTRIBUTE = "correlation.id"

        /**
         * Span attribute key for transaction ID.
         */
        const val TRANSACTION_ID_ATTRIBUTE = "transaction.id"
    }
}

/**
 * Correlation context containing IDs for distributed transaction tracking.
 *
 * This data class holds all correlation information for a distributed operation:
 * - **correlationId**: Links related messages within a transaction
 * - **transactionId**: Groups all operations in a business transaction
 * - **traceId**: OpenTelemetry trace ID for the current trace
 *
 * @property correlationId Optional correlation ID for message correlation
 * @property transactionId Optional transaction ID for business transaction grouping
 * @property traceId Optional OpenTelemetry trace ID
 *
 * @since 1.0.0
 */
data class CorrelationContext(
    val correlationId: CorrelationId? = null,
    val transactionId: TransactionId? = null,
    val traceId: String? = null,
) {
    /**
     * Checks if this context has any correlation information.
     *
     * @return true if at least one ID is present, false otherwise
     */
    fun hasCorrelation(): Boolean =
        correlationId != null || transactionId != null || traceId != null

    /**
     * Converts this context to a metadata map for message enrichment.
     *
     * @return Map of metadata keys to ID values (only non-null IDs included)
     */
    fun toMetadataMap(): Map<String, String> =
        buildMap {
            correlationId?.let { put(DistributedTraceCorrelator.CORRELATION_ID_KEY, it.value) }
            transactionId?.let { put(DistributedTraceCorrelator.TRANSACTION_ID_KEY, it.value) }
        }
}

/**
 * Extension function to attach a correlation ID to an Axon message.
 *
 * @receiver The message to enrich
 * @param correlationId The correlation ID to attach
 * @return A new message with the correlation ID in metadata
 */
fun <T> Message<T>.withCorrelationId(correlationId: CorrelationId): Message<T> =
    DistributedTraceCorrelator().withCorrelationId(this, correlationId)

/**
 * Extension function to attach a transaction ID to an Axon message.
 *
 * @receiver The message to enrich
 * @param transactionId The transaction ID to attach
 * @return A new message with the transaction ID in metadata
 */
fun <T> Message<T>.withTransactionId(transactionId: TransactionId): Message<T> =
    DistributedTraceCorrelator().withTransactionId(this, transactionId)

/**
 * Extension function to attach a complete correlation context to an Axon message.
 *
 * @receiver The message to enrich
 * @param context The correlation context to attach
 * @return A new message with the correlation context in metadata
 */
fun <T> Message<T>.withCorrelationContext(context: CorrelationContext): Message<T> {
    val metadata = context.toMetadataMap()
    return if (metadata.isNotEmpty()) {
        this.andMetaData(metadata)
    } else {
        this
    }
}
