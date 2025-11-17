package io.github.axonsentry.tracing

/**
 * Standard metadata keys for trace propagation in Axon messages.
 *
 * These keys are used to store and retrieve trace context from message metadata,
 * enabling distributed tracing across async boundaries.
 *
 * @since 1.0.0
 */
object MessageMetadataKeys {
    /**
     * Prefix for all tracing-related metadata to avoid collisions.
     */
    private const val PREFIX = "axon.sentry.tracing"

    /**
     * Parent trace context serialized for propagation.
     * Contains the full trace context information needed to continue the trace.
     */
    const val TRACE_CONTEXT = "$PREFIX.context"

    /**
     * Indicates whether this message should be traced.
     * Can be used to selectively enable/disable tracing for specific messages.
     */
    const val TRACE_ENABLED = "$PREFIX.enabled"

    /**
     * Custom tags to be added to the span for this message.
     * Should be a Map<String, String> of tag key-value pairs.
     */
    const val CUSTOM_TAGS = "$PREFIX.tags"

    /**
     * Span kind for this message operation.
     * Should be one of: CLIENT, SERVER, PRODUCER, CONSUMER, INTERNAL
     */
    const val SPAN_KIND = "$PREFIX.span_kind"

    /**
     * Transaction name override (for Sentry).
     * Allows custom naming of transactions for better organization in Sentry UI.
     */
    const val TRANSACTION_NAME = "$PREFIX.transaction_name"
}
