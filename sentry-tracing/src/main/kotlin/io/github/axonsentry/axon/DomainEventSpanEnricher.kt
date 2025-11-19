package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import org.axonframework.eventhandling.DomainEventMessage

/**
 * Enriches spans with domain event specific information.
 *
 * This enricher adds aggregate context and sequence information to spans
 * for domain events that originate from aggregates.
 */
class DomainEventSpanEnricher {
    /**
     * Enriches publish span with domain event metadata.
     *
     * Adds the following attributes:
     * - Aggregate ID
     * - Aggregate type
     * - Event sequence number
     * - Event timestamp
     *
     * @param span The span to enrich
     * @param event The domain event message
     */
    fun enrichPublishSpan(
        span: Span,
        event: DomainEventMessage<*>,
    ) {
        if (!span.isRecording) {
            return
        }

        span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
        span.setAttribute("axon.event.sequence_number", event.sequenceNumber)
        span.setAttribute("axon.event.timestamp", event.timestamp.toString())
    }

    /**
     * Enriches handler span with domain event metadata.
     *
     * Adds the following attributes:
     * - Aggregate ID
     * - Aggregate type
     * - Event sequence number
     *
     * Note: Timestamp is not included in handler spans to avoid duplication,
     * as it's already available from the parent publish span.
     *
     * @param span The span to enrich
     * @param event The domain event message
     */
    fun enrichHandlerSpan(
        span: Span,
        event: DomainEventMessage<*>,
    ) {
        if (!span.isRecording) {
            return
        }

        span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
        span.setAttribute("axon.event.sequence_number", event.sequenceNumber)
    }
}
