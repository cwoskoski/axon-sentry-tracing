package io.github.axonsentry.tracing

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.queryhandling.QueryMessage

/**
 * Generates human-readable span names for Axon messages that are optimized for Sentry's UI.
 *
 * This class creates consistent, hierarchical span names that make distributed traces
 * easy to understand in Sentry. The naming convention follows the pattern:
 * - **Commands**: "Command: {CommandName}" / "Handle: {CommandName}"
 * - **Events**: "Event: {EventName}" / "Handle: {EventName}"
 * - **Queries**: "Query: {QueryName}" / "Handle: {QueryName}"
 *
 * The generator handles edge cases like CGLIB proxies, anonymous classes, and null message names
 * by falling back to payload type information.
 *
 * ## Example Span Names
 * ```
 * Command: CreateOrderCommand
 * Handle: CreateOrderCommand
 * Event: OrderCreatedEvent
 * Handle: OrderCreatedEvent
 * Query: FindOrderQuery
 * Handle: FindOrderQuery
 * ```
 *
 * These names create clear parent-child relationships in Sentry's trace waterfall view.
 */
class SpanNameGenerator {
    /**
     * Generates a span name for command dispatch operations.
     *
     * @param message The command message being dispatched
     * @return Span name in format "Command: {CommandName}"
     */
    fun generateCommandName(message: CommandMessage<*>): String {
        val messageName = extractMessageName(message.commandName, message.payloadType)
        return "Command: $messageName"
    }

    /**
     * Generates a span name for command handler operations.
     *
     * @param message The command message being handled
     * @return Span name in format "Handle: {CommandName}"
     */
    fun generateCommandHandlerName(message: CommandMessage<*>): String {
        val messageName = extractMessageName(message.commandName, message.payloadType)
        return "Handle: $messageName"
    }

    /**
     * Generates a span name for event publishing operations.
     *
     * @param message The event message being published
     * @return Span name in format "Event: {EventName}"
     */
    fun generateEventName(message: EventMessage<*>): String {
        val messageName = extractMessageName(message.payloadType.simpleName, message.payloadType)
        return "Event: $messageName"
    }

    /**
     * Generates a span name for event handler operations.
     *
     * @param message The event message being handled
     * @return Span name in format "Handle: {EventName}"
     */
    fun generateEventHandlerName(message: EventMessage<*>): String {
        val messageName = extractMessageName(message.payloadType.simpleName, message.payloadType)
        return "Handle: $messageName"
    }

    /**
     * Generates a span name for query dispatch operations.
     *
     * @param message The query message being dispatched
     * @return Span name in format "Query: {QueryName}"
     */
    fun generateQueryName(message: QueryMessage<*, *>): String {
        val messageName = extractMessageName(message.queryName, message.payloadType)
        return "Query: $messageName"
    }

    /**
     * Generates a span name for query handler operations.
     *
     * @param message The query message being handled
     * @return Span name in format "Handle: {QueryName}"
     */
    fun generateQueryHandlerName(message: QueryMessage<*, *>): String {
        val messageName = extractMessageName(message.queryName, message.payloadType)
        return "Handle: $messageName"
    }

    /**
     * Extracts a clean message name from either the explicit message name or payload type.
     *
     * This method handles several edge cases:
     * - **Null message name**: Falls back to payload type's simple name
     * - **Fully qualified names**: Extracts just the simple class name
     * - **Inner classes**: Extracts the innermost class name (e.g., "Outer$Inner" -> "Inner")
     * - **CGLIB proxies**: Removes "$$EnhancerByCGLIB$$..." suffix
     * - **Anonymous classes**: Removes "$1", "$2" etc. numeric suffixes
     * - **Null payload type**: Returns "Unknown"
     *
     * @param messageName The explicit message name (may be null, may be fully qualified)
     * @param payloadType The payload class type (may be null)
     * @return A clean, human-readable message name
     */
    fun extractMessageName(
        messageName: String?,
        payloadType: Class<*>?,
    ): String {
        // Determine the raw name to process
        val rawName =
            when {
                !messageName.isNullOrBlank() -> messageName
                payloadType != null -> payloadType.simpleName ?: payloadType.name
                else -> return "Unknown"
            }

        // Extract simple name if fully qualified (e.g., "com.example.MyClass" -> "MyClass")
        val simpleName = rawName.substringAfterLast('.')

        // Remove CGLIB proxy enhancer suffix (e.g., "MyClass$$EnhancerByCGLIB$$12345678" -> "MyClass")
        val withoutCGLIB = simpleName.substringBefore("$$")

        // For inner/nested classes, extract the innermost class name (e.g., "Outer$Inner" -> "Inner")
        // For anonymous classes, this also removes numeric suffixes (e.g., "MyClass$1" -> "MyClass")
        val parts = withoutCGLIB.split('$')
        val cleaned =
            parts.lastOrNull { it.isNotBlank() && !it.all { char -> char.isDigit() } }
                ?: parts.firstOrNull()
                ?: "Unknown"

        return cleaned.ifBlank { "Unknown" }
    }
}
