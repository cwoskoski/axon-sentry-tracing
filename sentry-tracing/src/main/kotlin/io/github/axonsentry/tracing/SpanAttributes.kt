package io.github.axonsentry.tracing

/**
 * Standard OpenTelemetry span attribute keys for Axon Framework tracing.
 *
 * Follows semantic conventions where applicable and defines Axon-specific
 * attributes for detailed tracing.
 *
 * @since 1.0.0
 */
object SpanAttributes {
    // OpenTelemetry semantic conventions

    /**
     * The messaging system identifier (always "axon" for Axon Framework).
     */
    const val MESSAGING_SYSTEM = "messaging.system"

    /**
     * The type of messaging operation (send, receive, process).
     */
    const val MESSAGING_OPERATION = "messaging.operation"

    /**
     * Unique identifier for the message.
     */
    const val MESSAGING_MESSAGE_ID = "messaging.message.id"

    /**
     * The destination name for the message (e.g., aggregate type, query name).
     */
    const val MESSAGING_DESTINATION = "messaging.destination.name"

    // Axon Framework specific

    /**
     * The type of Axon message (command, event, query).
     */
    const val AXON_MESSAGE_TYPE = "axon.message.type"

    /**
     * The fully qualified name of the message class.
     */
    const val AXON_MESSAGE_NAME = "axon.message.name"

    /**
     * The unique message identifier.
     */
    const val AXON_MESSAGE_ID = "axon.message.id"

    /**
     * The aggregate identifier for the message.
     */
    const val AXON_AGGREGATE_ID = "axon.aggregate.id"

    /**
     * The type of aggregate being targeted or modified.
     */
    const val AXON_AGGREGATE_TYPE = "axon.aggregate.type"

    /**
     * The sequence number for domain events.
     */
    const val AXON_SEQUENCE_NUMBER = "axon.sequence_number"

    /**
     * The routing key used for message distribution.
     */
    const val AXON_ROUTING_KEY = "axon.routing_key"

    // Command specific

    /**
     * The name of the command being dispatched.
     */
    const val AXON_COMMAND_NAME = "axon.command.name"

    /**
     * The expected result type for the command.
     */
    const val AXON_COMMAND_RESULT_TYPE = "axon.command.result_type"

    // Event specific

    /**
     * The type of domain event.
     */
    const val AXON_EVENT_TYPE = "axon.event.type"

    /**
     * The timestamp when the event occurred.
     */
    const val AXON_EVENT_TIMESTAMP = "axon.event.timestamp"

    // Query specific

    /**
     * The name of the query being executed.
     */
    const val AXON_QUERY_NAME = "axon.query.name"

    /**
     * The expected response type for the query.
     */
    const val AXON_QUERY_RESPONSE_TYPE = "axon.query.response_type"

    // Handler specific

    /**
     * The class that handles the message.
     */
    const val AXON_HANDLER_CLASS = "axon.handler.class"

    /**
     * The method that handles the message.
     */
    const val AXON_HANDLER_METHOD = "axon.handler.method"

    // Processing context

    /**
     * The event processing group name.
     */
    const val AXON_PROCESSING_GROUP = "axon.processing_group"

    /**
     * The segment ID for parallel event processing.
     */
    const val AXON_SEGMENT_ID = "axon.segment.id"

    // Error tracking

    /**
     * Indicates whether an error occurred (true/false).
     */
    const val ERROR = "error"

    /**
     * The type/class of error that occurred.
     */
    const val ERROR_TYPE = "error.type"

    /**
     * The error message.
     */
    const val ERROR_MESSAGE = "error.message"

    /**
     * The error stacktrace.
     */
    const val ERROR_STACKTRACE = "error.stacktrace"

    // Constant values for message types

    /**
     * Value for AXON_MESSAGE_TYPE indicating a command.
     */
    const val MESSAGE_TYPE_COMMAND = "command"

    /**
     * Value for AXON_MESSAGE_TYPE indicating an event.
     */
    const val MESSAGE_TYPE_EVENT = "event"

    /**
     * Value for AXON_MESSAGE_TYPE indicating a query.
     */
    const val MESSAGE_TYPE_QUERY = "query"

    // Constant values for operations

    /**
     * Value for MESSAGING_OPERATION indicating message dispatch/send.
     */
    const val OPERATION_SEND = "send"

    /**
     * Value for MESSAGING_OPERATION indicating message receipt.
     */
    const val OPERATION_RECEIVE = "receive"

    /**
     * Value for MESSAGING_OPERATION indicating message processing.
     */
    const val OPERATION_PROCESS = "process"
}
