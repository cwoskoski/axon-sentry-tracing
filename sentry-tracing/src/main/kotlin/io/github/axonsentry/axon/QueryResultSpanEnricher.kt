package io.github.axonsentry.axon

import io.opentelemetry.api.trace.Span
import java.util.Optional

/**
 * Enriches spans with query execution result information.
 *
 * This enricher adds result type, count, and value information to query handler spans,
 * allowing comprehensive result tracking in distributed traces. It handles various result types:
 * - Void/null results
 * - Simple types (String, Number, Boolean)
 * - Optional results (empty or present)
 * - Collection types (List, Set, Collection) with counts
 * - Array types with counts
 * - Complex types (only type name is captured)
 *
 * ## Usage Example
 * ```kotlin
 * val enricher = QueryResultSpanEnricher()
 * enricher.enrichWithResult(span, queryResult)
 * ```
 *
 * @since 1.0.0
 */
class QueryResultSpanEnricher {
    /**
     * Enriches span with query result details.
     *
     * For void/null results, sets result_type to "void".
     * For simple types (String, Number, Boolean), captures both type and value.
     * For Optional results, captures presence and value type.
     * For Collection/List/Set results, captures type and count.
     * For complex types, captures only type information to avoid sensitive data leakage.
     *
     * @param span The span to enrich with result information
     * @param result The query execution result (can be null)
     */
    fun enrichWithResult(
        span: Span,
        result: Any?,
    ) {
        when {
            result == null -> {
                span.setAttribute("axon.query.result_type", "void")
            }
            result is Unit -> {
                span.setAttribute("axon.query.result_type", "void")
            }
            result is Optional<*> -> {
                enrichWithOptional(span, result)
            }
            result is List<*> -> {
                span.setAttribute("axon.query.result_type", "List")
                span.setAttribute("axon.query.result_count", result.size.toLong())
                span.setAttribute("axon.query.result_class", result::class.java.simpleName)
            }
            result is Set<*> -> {
                span.setAttribute("axon.query.result_type", "Set")
                span.setAttribute("axon.query.result_count", result.size.toLong())
                span.setAttribute("axon.query.result_class", result::class.java.simpleName)
            }
            result is Collection<*> -> {
                // Handle general Collection interface
                span.setAttribute("axon.query.result_type", result::class.java.simpleName)
                span.setAttribute("axon.query.result_count", result.size.toLong())
                span.setAttribute("axon.query.result_class", result::class.java.simpleName)
            }
            result is Array<*> -> {
                span.setAttribute("axon.query.result_type", "Array")
                span.setAttribute("axon.query.result_count", result.size.toLong())
                span.setAttribute("axon.query.result_class", result::class.java.simpleName)
            }
            else -> {
                enrichWithSingleValue(span, result)
            }
        }
    }

    private fun enrichWithOptional(
        span: Span,
        optional: Optional<*>,
    ) {
        span.setAttribute("axon.query.result_type", "Optional")
        span.setAttribute("axon.query.result_present", optional.isPresent)

        if (optional.isPresent) {
            val value = optional.get()
            span.setAttribute("axon.query.result_value_type", value::class.java.name)
        }
    }

    private fun enrichWithSingleValue(
        span: Span,
        result: Any,
    ) {
        // Always capture the result type
        span.setAttribute("axon.query.result_type", result::class.java.name)

        // Capture simple result types with their values
        when (result) {
            is String -> span.setAttribute("axon.query.result", result)
            is Number -> span.setAttribute("axon.query.result", result.toString())
            is Boolean -> span.setAttribute("axon.query.result", result)
            else -> {
                // For complex types, just capture the simple class name
                span.setAttribute("axon.query.result_class", result::class.java.simpleName)
            }
        }
    }
}
