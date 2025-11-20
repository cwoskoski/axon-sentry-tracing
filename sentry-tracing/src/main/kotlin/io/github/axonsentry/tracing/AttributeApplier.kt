package io.github.axonsentry.tracing

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.spi.AttributeProvider
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanBuilder
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.Message
import org.axonframework.queryhandling.QueryMessage

/**
 * Applies OpenTelemetry attributes to span builders based on Axon messages and tracing configuration.
 *
 * This class is responsible for enriching spans with Axon-specific metadata, following
 * OpenTelemetry semantic conventions where applicable and using custom "axon.*" attributes
 * for Axon-specific data.
 *
 * The applier respects the [TracingConfiguration] settings for:
 * - **Payload capture**: Controls whether message payloads are serialized to spans (per message type)
 * - **Payload size limits**: Prevents spans from becoming too large with sanitization
 * - **Custom attributes**: Applies custom attributes from [AttributeProvider] implementations
 *
 * ## Attribute Categories
 * 1. **Generic Message Attributes**: message.id, message.type, operation
 * 2. **Command-Specific**: axon.command.name
 * 3. **Event-Specific**: axon.event.type, axon.event.timestamp
 * 4. **Query-Specific**: axon.query.name, axon.query.response_type
 * 5. **Handler Attributes**: axon.handler.class, axon.handler.method
 * 6. **Custom Attributes**: Provided by [AttributeProvider] implementations
 * 7. **Optional**: message.payload (if configured per message type)
 *
 * @property configuration The tracing configuration controlling capture behavior
 * @property attributeProvider Optional custom attribute provider for domain-specific attributes
 * @property maxPayloadLength Maximum length for payload capture (default: 1000 characters)
 */
class AttributeApplier(
    private val configuration: TracingConfiguration,
    private val attributeProvider: AttributeProvider? = null,
    private val maxPayloadLength: Int = 1000,
) {
    companion object {
        // Create AttributeKey instances for all attribute keys
        private val MESSAGE_ID_KEY = AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_ID)
        private val MESSAGE_NAME_KEY = AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_NAME)
        private val MESSAGE_TYPE_KEY = AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE)
        private val OPERATION_KEY = AttributeKey.stringKey(SpanAttributes.MESSAGING_OPERATION)

        private val COMMAND_NAME_KEY = AttributeKey.stringKey(SpanAttributes.AXON_COMMAND_NAME)
        private val EVENT_TYPE_KEY = AttributeKey.stringKey(SpanAttributes.AXON_EVENT_TYPE)
        private val EVENT_TIMESTAMP_KEY = AttributeKey.longKey(SpanAttributes.AXON_EVENT_TIMESTAMP)
        private val QUERY_NAME_KEY = AttributeKey.stringKey(SpanAttributes.AXON_QUERY_NAME)
        private val QUERY_RESPONSE_TYPE_KEY = AttributeKey.stringKey(SpanAttributes.AXON_QUERY_RESPONSE_TYPE)

        private val HANDLER_CLASS_KEY = AttributeKey.stringKey(SpanAttributes.AXON_HANDLER_CLASS)
        private val HANDLER_METHOD_KEY = AttributeKey.stringKey(SpanAttributes.AXON_HANDLER_METHOD)

        private val MESSAGING_SYSTEM_KEY = AttributeKey.stringKey(SpanAttributes.MESSAGING_SYSTEM)
        private const val AXON_SYSTEM = "axon"

        // Payload attribute (optional)
        private const val AXON_MESSAGE_PAYLOAD = "axon.message.payload"
        private val MESSAGE_PAYLOAD_KEY = AttributeKey.stringKey(AXON_MESSAGE_PAYLOAD)

        // Metadata attribute (optional)
        private const val AXON_MESSAGE_METADATA = "axon.message.metadata"
        private val MESSAGE_METADATA_KEY = AttributeKey.stringKey(AXON_MESSAGE_METADATA)
    }

    /**
     * Applies command-specific attributes to a span builder.
     *
     * @param spanBuilder The span builder to enrich
     * @param message The command message containing attributes
     * @param operation The operation type (send, receive, process)
     */
    fun applyCommandAttributes(
        spanBuilder: SpanBuilder,
        message: CommandMessage<*>,
        operation: String,
    ) {
        // Apply generic message attributes first
        applyGenericMessageAttributes(spanBuilder, message, operation)

        // Add command-specific attributes
        spanBuilder.setAttribute(MESSAGE_TYPE_KEY, SpanAttributes.MESSAGE_TYPE_COMMAND)
        spanBuilder.setAttribute(COMMAND_NAME_KEY, message.commandName)

        // Capture payload if enabled
        if (configuration.captureCommandPayloads) {
            applyPayloadAttribute(spanBuilder, message.payload)
        }
    }

    /**
     * Applies event-specific attributes to a span builder.
     *
     * @param spanBuilder The span builder to enrich
     * @param message The event message containing attributes
     * @param operation The operation type (send, receive, process)
     */
    fun applyEventAttributes(
        spanBuilder: SpanBuilder,
        message: EventMessage<*>,
        operation: String,
    ) {
        // Apply generic message attributes first
        applyGenericMessageAttributes(spanBuilder, message, operation)

        // Add event-specific attributes
        spanBuilder.setAttribute(MESSAGE_TYPE_KEY, SpanAttributes.MESSAGE_TYPE_EVENT)
        spanBuilder.setAttribute(EVENT_TYPE_KEY, message.payloadType.simpleName)
        spanBuilder.setAttribute(EVENT_TIMESTAMP_KEY, message.timestamp.toEpochMilli())

        // Capture payload if enabled
        if (configuration.captureEventPayloads) {
            applyPayloadAttribute(spanBuilder, message.payload)
        }
    }

    /**
     * Applies query-specific attributes to a span builder.
     *
     * @param spanBuilder The span builder to enrich
     * @param message The query message containing attributes
     * @param operation The operation type (send, receive, process)
     */
    fun applyQueryAttributes(
        spanBuilder: SpanBuilder,
        message: QueryMessage<*, *>,
        operation: String,
    ) {
        // Apply generic message attributes first
        applyGenericMessageAttributes(spanBuilder, message, operation)

        // Add query-specific attributes
        spanBuilder.setAttribute(MESSAGE_TYPE_KEY, SpanAttributes.MESSAGE_TYPE_QUERY)
        spanBuilder.setAttribute(QUERY_NAME_KEY, message.queryName)
        spanBuilder.setAttribute(QUERY_RESPONSE_TYPE_KEY, message.responseType.toString())

        // Capture payload if enabled
        if (configuration.captureQueryPayloads) {
            applyPayloadAttribute(spanBuilder, message.payload)
        }
    }

    /**
     * Applies handler-specific attributes to a span builder.
     *
     * @param spanBuilder The span builder to enrich
     * @param handlerClass The class containing the handler method
     * @param handlerMethod The name of the handler method
     */
    fun applyHandlerAttributes(
        spanBuilder: SpanBuilder,
        handlerClass: Class<*>,
        handlerMethod: String,
    ) {
        spanBuilder.setAttribute(HANDLER_CLASS_KEY, handlerClass.simpleName)
        spanBuilder.setAttribute(HANDLER_METHOD_KEY, handlerMethod)
    }

    /**
     * Applies generic message attributes common to all Axon messages.
     *
     * This includes:
     * - Message ID
     * - Message name (derived from payload type)
     * - Operation type (send, receive, process)
     * - Messaging system (always "axon")
     * - Custom attributes from attribute providers (if configured)
     *
     * @param spanBuilder The span builder to enrich
     * @param message The Axon message
     * @param operation The operation type
     */
    fun applyGenericMessageAttributes(
        spanBuilder: SpanBuilder,
        message: Message<*>,
        operation: String,
    ) {
        // Core message attributes
        spanBuilder.setAttribute(MESSAGE_ID_KEY, message.identifier)
        spanBuilder.setAttribute(MESSAGE_NAME_KEY, message.payloadType.simpleName)
        spanBuilder.setAttribute(OPERATION_KEY, operation)
        spanBuilder.setAttribute(MESSAGING_SYSTEM_KEY, AXON_SYSTEM)

        // Apply custom attributes from providers if configured
        if (attributeProvider != null) {
            applyCustomAttributes(spanBuilder, message)
        }

        // Capture metadata if present (always capture metadata for now)
        if (message.metaData.isNotEmpty()) {
            applyMetadataAttribute(spanBuilder, message.metaData)
        }
    }

    /**
     * Applies custom attributes from the configured [AttributeProvider].
     *
     * This method invokes the attribute provider to extract domain-specific
     * attributes and adds them to the span.
     *
     * @param spanBuilder The span builder to enrich
     * @param message The Axon message
     */
    private fun applyCustomAttributes(
        spanBuilder: SpanBuilder,
        message: Message<*>,
    ) {
        val customAttributes = attributeProvider?.provideAttributes(message) ?: return

        for ((key, value) in customAttributes) {
            when (value) {
                is String -> spanBuilder.setAttribute(AttributeKey.stringKey(key), value)
                is Long -> spanBuilder.setAttribute(AttributeKey.longKey(key), value)
                is Int -> spanBuilder.setAttribute(AttributeKey.longKey(key), value.toLong())
                is Double -> spanBuilder.setAttribute(AttributeKey.doubleKey(key), value)
                is Boolean -> spanBuilder.setAttribute(AttributeKey.booleanKey(key), value)
                else -> spanBuilder.setAttribute(AttributeKey.stringKey(key), value.toString())
            }
        }
    }

    /**
     * Applies payload attribute with sanitization.
     *
     * @param spanBuilder The span builder to enrich
     * @param payload The payload to capture
     */
    private fun applyPayloadAttribute(
        spanBuilder: SpanBuilder,
        payload: Any?,
    ) {
        val sanitizedPayload = sanitizePayload(payload)
        if (sanitizedPayload != null) {
            spanBuilder.setAttribute(MESSAGE_PAYLOAD_KEY, sanitizedPayload)
        }
    }

    /**
     * Applies metadata attribute as JSON-like string.
     *
     * @param spanBuilder The span builder to enrich
     * @param metadata The Axon metadata map
     */
    private fun applyMetadataAttribute(
        spanBuilder: SpanBuilder,
        metadata: Map<String, *>,
    ) {
        // Convert metadata to simple JSON-like string
        val metadataJson =
            metadata.entries.joinToString(",", "{", "}") { (key, value) ->
                "\"$key\":\"$value\""
            }
        spanBuilder.setAttribute(MESSAGE_METADATA_KEY, metadataJson)
    }

    /**
     * Sanitizes a payload for span capture by limiting its size.
     *
     * This prevents spans from becoming too large and impacting performance or
     * exceeding Sentry's span attribute limits.
     *
     * @param payload The payload to sanitize
     * @return The sanitized payload string, or null if payload is null
     */
    private fun sanitizePayload(payload: Any?): String? {
        if (payload == null) {
            return null
        }

        val payloadString = payload.toString()
        return if (payloadString.length > maxPayloadLength) {
            payloadString.substring(0, maxPayloadLength) + "..."
        } else {
            payloadString
        }
    }
}
