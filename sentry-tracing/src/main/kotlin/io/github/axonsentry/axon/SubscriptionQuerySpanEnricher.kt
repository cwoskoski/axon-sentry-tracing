package io.github.axonsentry.axon

import io.opentelemetry.api.trace.Span
import org.axonframework.queryhandling.SubscriptionQueryMessage

/**
 * Enriches spans with subscription query lifecycle information.
 *
 * This enricher tracks subscription query lifecycle events including:
 * - Subscription start
 * - Initial result delivery
 * - Update delivery (with count tracking)
 * - Subscription completion
 * - Subscription cancellation
 * - Subscription errors
 * - Subscription duration
 *
 * ## Usage Example
 * ```kotlin
 * val enricher = SubscriptionQuerySpanEnricher()
 * enricher.enrichWithSubscriptionStart(span, subscriptionQuery)
 * enricher.enrichWithInitialResult(span, initialResult)
 * enricher.enrichWithUpdate(span, update)
 * enricher.enrichWithCompletion(span)
 * enricher.enrichWithSubscriptionDuration(span, durationNanos)
 * ```
 *
 * @since 1.0.0
 */
class SubscriptionQuerySpanEnricher {
    private val updateCountKey = "axon.query.update_count"

    // Track update counts per span (using span context as key)
    private val updateCounts = mutableMapOf<String, Long>()

    /**
     * Marks the span as a subscription query.
     *
     * @param span The span to enrich
     * @param query The subscription query message
     */
    fun enrichWithSubscriptionStart(
        span: Span,
        @Suppress("UNUSED_PARAMETER") query: SubscriptionQueryMessage<*, *, *>,
    ) {
        span.setAttribute("axon.query.is_subscription", true)
    }

    /**
     * Enriches span with initial result information.
     *
     * @param span The span to enrich
     * @param result The initial query result (can be null)
     */
    fun enrichWithInitialResult(
        span: Span,
        result: Any?,
    ) {
        if (result == null) {
            span.setAttribute("axon.query.initial_result_type", "void")
        } else {
            span.setAttribute("axon.query.initial_result_type", result::class.java.name)
        }
    }

    /**
     * Enriches span with update information and increments update count.
     *
     * @param span The span to enrich
     * @param update The update value (can be null)
     */
    fun enrichWithUpdate(
        span: Span,
        update: Any?,
    ) {
        // Get span ID for tracking
        val spanId = span.spanContext.spanId

        // Increment update count
        val currentCount = updateCounts.getOrDefault(spanId, 0L)
        val newCount = currentCount + 1
        updateCounts[spanId] = newCount
        span.setAttribute(updateCountKey, newCount)

        // Track update type (last update wins)
        if (update != null) {
            span.setAttribute("axon.query.update_type", update::class.java.name)
        }
    }

    /**
     * Marks the subscription as completed successfully.
     *
     * @param span The span to enrich
     */
    fun enrichWithCompletion(span: Span) {
        span.setAttribute("axon.query.subscription_completed", true)
        // Clean up tracking state
        cleanupSpanState(span)
    }

    /**
     * Marks the subscription as cancelled.
     *
     * @param span The span to enrich
     */
    fun enrichWithCancellation(span: Span) {
        span.setAttribute("axon.query.subscription_cancelled", true)
        // Clean up tracking state
        cleanupSpanState(span)
    }

    /**
     * Records an error that occurred during subscription.
     *
     * @param span The span to enrich
     * @param error The error that occurred
     */
    fun enrichWithError(
        span: Span,
        error: Throwable,
    ) {
        span.recordException(error)
        span.setAttribute("axon.query.subscription_error", true)
    }

    /**
     * Records the total duration of the subscription.
     *
     * @param span The span to enrich
     * @param durationNanos The subscription duration in nanoseconds
     */
    fun enrichWithSubscriptionDuration(
        span: Span,
        durationNanos: Long,
    ) {
        span.setAttribute("axon.query.subscription_duration_ns", durationNanos)
    }

    /**
     * Cleans up internal tracking state for a span.
     *
     * @param span The span to clean up
     */
    private fun cleanupSpanState(span: Span) {
        val spanId = span.spanContext.spanId
        updateCounts.remove(spanId)
    }
}
