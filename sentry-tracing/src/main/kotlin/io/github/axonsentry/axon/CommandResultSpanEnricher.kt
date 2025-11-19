package io.github.axonsentry.axon

import io.opentelemetry.api.trace.Span

/**
 * Enriches spans with command execution result information.
 *
 * This enricher adds result type and value information to command handler spans,
 * allowing result tracking in distributed traces. It handles various result types:
 * - Void/null results
 * - Simple types (String, Number, Boolean)
 * - Complex types (only type name is captured)
 *
 * ## Usage Example
 * ```kotlin
 * val enricher = CommandResultSpanEnricher()
 * enricher.enrichWithResult(span, commandResult)
 * ```
 *
 * @since 1.0.0
 */
class CommandResultSpanEnricher {
    /**
     * Enriches span with command result details.
     *
     * For void/null results, sets result_type to "void".
     * For simple types (String, Number, Boolean), captures both type and value.
     * For complex types, captures only type information to avoid sensitive data leakage.
     *
     * @param span The span to enrich with result information
     * @param result The command execution result (can be null)
     */
    fun enrichWithResult(
        span: Span,
        result: Any?,
    ) {
        when {
            result == null -> {
                span.setAttribute("axon.command.result_type", "void")
            }
            result is Unit -> {
                span.setAttribute("axon.command.result_type", "void")
            }
            else -> {
                // Always capture the result type
                span.setAttribute("axon.command.result_type", result::class.java.name)

                // Capture simple result types with their values
                when (result) {
                    is String -> span.setAttribute("axon.command.result", result)
                    is Number -> span.setAttribute("axon.command.result", result.toString())
                    is Boolean -> span.setAttribute("axon.command.result", result)
                    else -> {
                        // For complex types, just capture the simple class name
                        span.setAttribute("axon.command.result_class", result::class.java.simpleName)
                    }
                }
            }
        }
    }
}
