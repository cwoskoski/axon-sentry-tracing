package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory

/**
 * Enriches spans with aggregate lifecycle information.
 *
 * This enricher extracts aggregate-related metadata from the Unit of Work to provide
 * visibility into aggregate operations during command handling:
 * - Aggregate identification (ID and type)
 * - Event count (number of events applied)
 * - Aggregate creation detection
 *
 * ## Usage Example
 * ```kotlin
 * val enricher = AggregateLifecycleSpanEnricher()
 * unitOfWork.onPrepareCommit { uow ->
 *     enricher.enrichWithAggregateInfo(span, uow)
 * }
 * ```
 *
 * @since 1.0.0
 */
class AggregateLifecycleSpanEnricher {
    private val logger = LoggerFactory.getLogger(AggregateLifecycleSpanEnricher::class.java)

    /**
     * Enriches span with aggregate lifecycle details from the unit of work.
     *
     * Attempts to extract:
     * - Aggregate ID (from metadata or resources)
     * - Aggregate type (from resources)
     * - Event count (from resources)
     * - Creation flag (from resources)
     *
     * Errors during extraction are logged but do not fail the enrichment process.
     *
     * @param span The span to enrich with aggregate information
     * @param unitOfWork The unit of work containing aggregate lifecycle data
     */
    @Suppress("TooGenericExceptionCaught")
    fun enrichWithAggregateInfo(
        span: Span,
        unitOfWork: UnitOfWork<*>,
    ) {
        try {
            // Extract aggregate identifier
            val aggregateId = extractAggregateId(unitOfWork)
            if (aggregateId != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, aggregateId)
            }

            // Extract aggregate type
            val aggregateType = extractAggregateType(unitOfWork)
            if (aggregateType != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, aggregateType)
            }

            // Track events applied
            val eventCount = extractEventCount(unitOfWork)
            span.setAttribute("axon.aggregate.events_applied", eventCount.toLong())

            // Detect if this is aggregate creation
            val isCreation = detectAggregateCreation(unitOfWork)
            span.setAttribute("axon.aggregate.is_creation", isCreation)
        } catch (e: Exception) {
            logger.debug("Could not extract aggregate lifecycle info", e)
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractAggregateId(unitOfWork: UnitOfWork<*>): String? {
        return try {
            // Try metadata first
            unitOfWork.message.metaData["aggregateId"]?.toString()
                ?: unitOfWork.resources()["aggregateId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractAggregateType(unitOfWork: UnitOfWork<*>): String? {
        return try {
            unitOfWork.resources()["aggregateType"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractEventCount(unitOfWork: UnitOfWork<*>): Int {
        return try {
            val events = unitOfWork.resources()["events"] as? List<*>
            events?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun detectAggregateCreation(unitOfWork: UnitOfWork<*>): Boolean {
        return try {
            val isNew = unitOfWork.resources()["aggregateCreation"] as? Boolean
            isNew ?: false
        } catch (e: Exception) {
            false
        }
    }
}
