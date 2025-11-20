package io.github.axonsentry.error

import io.sentry.SentryEvent
import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.Message
import org.axonframework.queryhandling.QueryMessage
import org.slf4j.LoggerFactory

/**
 * Enriches Sentry error events with Axon Framework-specific context.
 *
 * This enricher adds detailed information about Axon messages to Sentry error reports,
 * making it easier to diagnose issues in CQRS/Event Sourcing applications:
 *
 * - Message type (command, event, query)
 * - Message identifier and payload type
 * - Aggregate information (for domain events and command exceptions)
 * - Sequence numbers (for domain events)
 * - Command/query names
 * - Message metadata as context
 *
 * The enricher is designed to handle all Axon message types gracefully,
 * extracting as much relevant context as possible without failing if
 * certain information is unavailable.
 *
 * @since 1.0.0
 */
class AxonExceptionEnricher {
    private val logger = LoggerFactory.getLogger(AxonExceptionEnricher::class.java)

    /**
     * Enriches a Sentry event with Axon message context.
     *
     * This method inspects the message type and extracts relevant information:
     * - CommandMessage: command name, aggregate context
     * - EventMessage: event type, timestamp, aggregate context (if DomainEventMessage)
     * - QueryMessage: query name, response type
     *
     * All messages have their identifier, payload type, and metadata added.
     *
     * @param event The Sentry event to enrich
     * @param message The Axon message providing context (can be null)
     * @param hint Additional hint data for Sentry processors
     */
    fun enrich(
        event: SentryEvent,
        message: Message<*>?,
        @Suppress("UNUSED_PARAMETER") hint: MutableMap<String, Any>,
    ) {
        if (message == null) {
            return
        }

        try {
            // Add base message information
            event.setTag("axon.message_id", message.identifier)
            event.setTag("axon.message_name", message.payloadType.name)

            // Add message type-specific context
            when (message) {
                is CommandMessage<*> -> enrichWithCommandContext(event, message)
                is DomainEventMessage<*> -> enrichWithDomainEventContext(event, message)
                is EventMessage<*> -> enrichWithEventContext(event, message)
                is QueryMessage<*, *> -> enrichWithQueryContext(event, message)
                else -> event.setTag("axon.message_type", "message")
            }

            // Add metadata as context
            enrichWithMetadata(event, message)

            // Extract CommandExecutionException details if present
            if (event.throwable is CommandExecutionException) {
                enrichWithCommandException(event, event.throwable as CommandExecutionException)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Failed to enrich Sentry event with Axon context", e)
        }
    }

    private fun enrichWithCommandContext(
        event: SentryEvent,
        command: CommandMessage<*>,
    ) {
        event.setTag("axon.message_type", "command")
        event.setTag("axon.command_name", command.commandName)
    }

    private fun enrichWithDomainEventContext(
        event: SentryEvent,
        domainEvent: DomainEventMessage<*>,
    ) {
        event.setTag("axon.message_type", "event")
        event.setTag("axon.aggregate_type", domainEvent.type)
        event.setTag("axon.aggregate_id", domainEvent.aggregateIdentifier)
        event.setTag("axon.sequence_number", domainEvent.sequenceNumber.toString())
        event.setTag("axon.event_timestamp", domainEvent.timestamp.toString())
    }

    private fun enrichWithEventContext(
        event: SentryEvent,
        eventMessage: EventMessage<*>,
    ) {
        event.setTag("axon.message_type", "event")
        event.setTag("axon.event_timestamp", eventMessage.timestamp.toString())
    }

    private fun enrichWithQueryContext(
        event: SentryEvent,
        query: QueryMessage<*, *>,
    ) {
        event.setTag("axon.message_type", "query")
        event.setTag("axon.query_name", query.queryName)
        event.setTag("axon.query_response_type", query.responseType.toString())
    }

    private fun enrichWithMetadata(
        event: SentryEvent,
        message: Message<*>,
    ) {
        if (message.metaData.isEmpty()) {
            return
        }

        try {
            val metadataContext = mutableMapOf<String, Any?>()
            message.metaData.forEach { (key, value) ->
                when (value) {
                    is String, is Number, is Boolean -> metadataContext[key] = value
                    else -> metadataContext[key] = value?.toString()
                }
            }

            if (metadataContext.isNotEmpty()) {
                event.setExtra("axon.metadata", metadataContext)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.debug("Failed to enrich with metadata", e)
        }
    }

    private fun enrichWithCommandException(
        event: SentryEvent,
        @Suppress("UNUSED_PARAMETER") exception: CommandExecutionException,
    ) {
        try {
            // CommandExecutionException already tagged with message context
            // Additional context extracted from message if available
            event.setTag("axon.exception_type", "CommandExecutionException")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.debug("Failed to extract CommandExecutionException details", e)
        }
    }
}
