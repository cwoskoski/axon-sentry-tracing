package io.github.axonsentry.axon

import io.opentelemetry.api.trace.Span
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory

/**
 * Enriches spans with event processor context information.
 *
 * This enricher extracts and adds metadata about the event processor
 * handling the event, including processor name, type, tracking token position,
 * and replay status.
 */
class EventProcessorSpanEnricher {
    private val logger = LoggerFactory.getLogger(EventProcessorSpanEnricher::class.java)

    /**
     * Enriches handler span with event processor details.
     *
     * Extracts the following from the UnitOfWork resources:
     * - Processor name
     * - Processor type (TrackingEventProcessor, SubscribingEventProcessor, etc.)
     * - Tracking token position (for tracking processors)
     * - Replay mode flag
     * - Handler group
     *
     * @param span The span to enrich
     * @param unitOfWork The unit of work containing processor context
     */
    fun enrichHandlerSpan(
        span: Span,
        unitOfWork: UnitOfWork<out EventMessage<*>>,
    ) {
        try {
            if (!span.isRecording) {
                return
            }

            val resources = unitOfWork.resources()

            // Processor name
            val processorName = extractProcessorName(resources)
            if (processorName != null) {
                span.setAttribute("axon.event_processor.name", processorName)
            }

            // Processor type (tracking, subscribing, pooled)
            val processorType = extractProcessorType(resources)
            if (processorType != null) {
                span.setAttribute("axon.event_processor.type", processorType)
            }

            // Tracking token position (if tracking processor)
            val tokenPosition = extractTokenPosition(resources)
            if (tokenPosition != null) {
                span.setAttribute("axon.event_processor.token_position", tokenPosition)
            }

            // Detect replay mode
            val isReplaying = detectReplayMode(resources)
            span.setAttribute("axon.event_processor.is_replaying", isReplaying)

            // Handler group
            val handlerGroup = extractHandlerGroup(resources)
            if (handlerGroup != null) {
                span.setAttribute("axon.event_handler.group", handlerGroup)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.debug("Could not extract event processor info", e)
        }
    }

    private fun extractProcessorName(resources: Map<String, Any>): String? {
        return resources["processorName"]?.toString()
    }

    private fun extractProcessorType(resources: Map<String, Any>): String? {
        return resources["processorType"]?.toString()
    }

    private fun extractTokenPosition(resources: Map<String, Any>): String? {
        val token = resources["trackingToken"] as? TrackingToken ?: return null
        val position = token.position()
        return if (position.isPresent) {
            position.asLong.toString()
        } else {
            // If position is not available, use the token's toString() representation
            token.toString()
        }
    }

    private fun detectReplayMode(resources: Map<String, Any>): Boolean {
        return resources["isReplaying"] as? Boolean ?: false
    }

    private fun extractHandlerGroup(resources: Map<String, Any>): String? {
        return resources["handlerGroup"]?.toString()
    }
}
