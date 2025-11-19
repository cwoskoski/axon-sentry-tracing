package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanKind

/**
 * Resolves the appropriate OpenTelemetry [SpanKind] for different Axon message operations.
 *
 * This class provides semantic meaning to spans by mapping Axon operations to OpenTelemetry's
 * distributed tracing semantics:
 * - **CLIENT**: Command/query dispatch operations (client initiating a request)
 * - **PRODUCER**: Event publishing operations (producing messages for consumers)
 * - **CONSUMER**: Message handler operations (consuming and processing messages)
 * - **INTERNAL**: Internal operations within the same service
 *
 * @see <a href="https://opentelemetry.io/docs/specs/otel/trace/api/#spankind">OpenTelemetry SpanKind Specification</a>
 */
class SpanKindResolver {
    /**
     * Resolves the span kind for command/query dispatch operations.
     *
     * Returns [SpanKind.CLIENT] because dispatching a command or query is semantically
     * equivalent to making a client request - the dispatcher initiates the operation
     * and expects a response or acknowledgment.
     *
     * @return [SpanKind.CLIENT] for dispatch operations
     */
    fun resolveDispatchKind(): SpanKind = SpanKind.CLIENT

    /**
     * Resolves the span kind for event publishing operations.
     *
     * Returns [SpanKind.PRODUCER] because publishing events is semantically
     * equivalent to producing messages for consumption by handlers - it's a
     * fire-and-forget operation that produces work for consumers.
     *
     * @return [SpanKind.PRODUCER] for publish operations
     */
    fun resolvePublishKind(): SpanKind = SpanKind.PRODUCER

    /**
     * Resolves the span kind for message handler operations.
     *
     * Returns [SpanKind.CONSUMER] because handling a message (command, event, or query)
     * is semantically equivalent to consuming a message - the handler processes work
     * that was initiated elsewhere.
     *
     * @return [SpanKind.CONSUMER] for handler operations
     */
    fun resolveHandlerKind(): SpanKind = SpanKind.CONSUMER

    /**
     * Resolves the span kind for internal operations.
     *
     * Returns [SpanKind.INTERNAL] for operations that don't cross service boundaries,
     * such as aggregate state reconstruction or projection updates within the same service.
     *
     * @return [SpanKind.INTERNAL] for internal operations
     */
    fun resolveInternalKind(): SpanKind = SpanKind.INTERNAL
}
