package io.github.axonsentry.error

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.SentryId
import org.axonframework.messaging.Message
import org.slf4j.LoggerFactory

/**
 * Correlates exceptions with OpenTelemetry spans and Sentry error tracking.
 *
 * This component is responsible for:
 * - Recording exceptions in OpenTelemetry spans with proper status codes
 * - Capturing exceptions to Sentry with trace context correlation
 * - Enriching Sentry events with Axon-specific message context
 *
 * The correlator ensures that errors are linked to their distributed traces,
 * making it easier to diagnose issues in event-sourced, CQRS applications.
 *
 * Usage:
 * ```kotlin
 * val span = tracer.spanBuilder("command-handler").startSpan()
 * try {
 *     // Execute command
 * } catch (e: Exception) {
 *     errorCorrelator.recordException(span, e, commandMessage)
 *     throw e
 * } finally {
 *     span.end()
 * }
 * ```
 *
 * @since 1.0.0
 */
class ErrorCorrelator {
    private val logger = LoggerFactory.getLogger(ErrorCorrelator::class.java)
    private val exceptionEnricher = AxonExceptionEnricher()
    private val fingerprintGenerator = ErrorFingerprintGenerator()

    /**
     * Records an exception in both the OpenTelemetry span and Sentry.
     *
     * This method:
     * 1. Records the exception as a span event in OpenTelemetry
     * 2. Sets the span status to ERROR
     * 3. Creates a Sentry event with trace context correlation
     * 4. Enriches the Sentry event with Axon message context
     * 5. Applies error fingerprinting for intelligent grouping
     * 6. Captures the event to Sentry
     *
     * @param span The current OpenTelemetry span
     * @param exception The exception that occurred
     * @param message The Axon message being processed (optional)
     */
    fun recordException(
        span: Span,
        exception: Throwable,
        message: Message<*>? = null,
    ) {
        try {
            // Record in OpenTelemetry span
            span.recordException(exception)
            span.setStatus(StatusCode.ERROR, exception.message ?: "Error")

            // Create Sentry event
            val sentryEvent = SentryEvent(exception)

            // Add trace context correlation
            addTraceContext(sentryEvent, span)

            // Enrich with Axon-specific context
            val hint = mutableMapOf<String, Any>()
            exceptionEnricher.enrich(sentryEvent, message, hint)

            // Apply error fingerprinting
            val fingerprint = fingerprintGenerator.generateFingerprint(exception)
            sentryEvent.fingerprints = fingerprint

            // Capture to Sentry (only if Sentry is enabled/configured)
            if (Sentry.isEnabled()) {
                val eventId: SentryId = Sentry.captureEvent(sentryEvent)
                logger.debug("Captured exception to Sentry: eventId={}, traceId={}", eventId, span.spanContext.traceId)
            } else {
                logger.debug("Sentry not enabled, skipping event capture for trace={}", span.spanContext.traceId)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Don't let error correlation failures break the application
            logger.error("Failed to correlate exception with Sentry", e)
        }
    }

    /**
     * Adds OpenTelemetry trace context to the Sentry event.
     *
     * This enables correlation between Sentry errors and distributed traces,
     * allowing navigation from error reports to trace visualizations.
     */
    private fun addTraceContext(
        sentryEvent: SentryEvent,
        span: Span,
    ) {
        val spanContext = span.spanContext
        if (spanContext.isValid) {
            // Add trace context as tags for correlation with OpenTelemetry
            sentryEvent.setTag("trace_id", spanContext.traceId)
            sentryEvent.setTag("span_id", spanContext.spanId)
            sentryEvent.setTag("trace_sampled", spanContext.isSampled.toString())
        }
    }
}
