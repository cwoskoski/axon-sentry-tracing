package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Determines which spans should be exported to Sentry.
 *
 * Implementations can apply custom filtering logic based on span attributes,
 * configuration settings, or other criteria.
 *
 * @since 1.0.0
 */
fun interface SpanFilter {
    /**
     * Returns true if the span should be exported to Sentry.
     *
     * @param span The span data to evaluate
     * @return true if the span should be exported, false otherwise
     */
    fun shouldExport(span: SpanData): Boolean
}

/**
 * Configuration-based span filter that respects TracingConfiguration settings.
 *
 * This filter implements filtering based on message types (command, event, query)
 * according to the tracing configuration.
 *
 * @property configuration The tracing configuration to apply
 * @since 1.0.0
 */
class ConfigurationBasedSpanFilter(
    private val configuration: TracingConfiguration,
) : SpanFilter {
    override fun shouldExport(span: SpanData): Boolean {
        if (!configuration.enabled) {
            return false
        }

        val messageType =
            span.attributes.get(
                AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE),
            )

        return when (messageType) {
            SpanAttributes.MESSAGE_TYPE_COMMAND -> configuration.traceCommands
            SpanAttributes.MESSAGE_TYPE_EVENT -> configuration.traceEvents
            SpanAttributes.MESSAGE_TYPE_QUERY -> configuration.traceQueries
            else -> true // Export unknown types by default
        }
    }
}

/**
 * Composite filter that requires all child filters to pass.
 *
 * This filter implements an AND logic - all filters must return true
 * for the span to be exported.
 *
 * @property filters List of filters to evaluate
 * @since 1.0.0
 */
class CompositeSpanFilter(
    private val filters: List<SpanFilter>,
) : SpanFilter {
    override fun shouldExport(span: SpanData): Boolean {
        return filters.all { it.shouldExport(span) }
    }
}
