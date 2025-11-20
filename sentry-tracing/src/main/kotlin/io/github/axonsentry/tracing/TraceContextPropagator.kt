package io.github.axonsentry.tracing

import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapPropagator
import org.axonframework.messaging.Message
import org.axonframework.messaging.MetaData
import org.slf4j.LoggerFactory

/**
 * Propagates W3C Trace Context through Axon message metadata using OpenTelemetry's standard propagation.
 *
 * This class enables distributed tracing across Axon Framework messages by injecting and extracting
 * trace context using the W3C Trace Context standard (traceparent/tracestate headers). It supports
 * baggage propagation for custom metadata and provides methods for creating remote span contexts.
 *
 * Key features:
 * - W3C Trace Context standard compliance (traceparent, tracestate)
 * - Baggage propagation for custom trace metadata
 * - Thread-safe context extraction and injection
 * - Interoperability with other OpenTelemetry-instrumented services
 *
 * Usage:
 * ```kotlin
 * val propagator = TraceContextPropagator(W3CTraceContextPropagator.getInstance())
 *
 * // On message dispatch:
 * val enrichedMessage = propagator.inject(command)
 *
 * // On message handling:
 * val parentContext = propagator.extract(command)
 * val span = tracer.spanBuilder("HandleCommand")
 *     .setParent(parentContext)
 *     .startSpan()
 * ```
 *
 * @property propagator The OpenTelemetry TextMapPropagator (typically W3CTraceContextPropagator)
 *
 * @since 1.0.0
 */
class TraceContextPropagator(
    private val propagator: TextMapPropagator,
) {
    private val logger = LoggerFactory.getLogger(TraceContextPropagator::class.java)

    /**
     * Injects the current trace context into message metadata.
     *
     * This method takes the current OpenTelemetry context (from [Context.current]) and injects
     * trace headers (traceparent, tracestate, baggage) into the message metadata. The original
     * message is not modified; a new message with enriched metadata is returned.
     *
     * If injection fails, the original message is returned unchanged and an error is logged.
     *
     * @param T The message payload type
     * @param message The Axon message to enrich with trace context
     * @return A new message with trace context injected into metadata, or the original message if injection fails
     */
    fun <T> inject(message: Message<T>): Message<T> {
        return try {
            val currentContext = Context.current()
            val mutableMetadata = message.metaData.toMutableMap()

            // Inject trace context using W3C propagator
            propagator.inject(currentContext, mutableMetadata, MetaDataSetter)

            // Return message with enriched metadata
            message.andMetaData(MetaData.from(mutableMetadata))
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Failed to inject trace context into message metadata", e)
            message
        }
    }

    /**
     * Extracts trace context from message metadata.
     *
     * This method extracts W3C trace context headers (traceparent, tracestate, baggage) from
     * the message metadata and creates an OpenTelemetry [Context] that can be used as a parent
     * for new spans.
     *
     * If extraction fails or no trace context is present, the current context is returned.
     *
     * @param T The message payload type
     * @param message The Axon message containing trace context in metadata
     * @return OpenTelemetry Context with extracted trace context, or current context if extraction fails
     */
    fun <T> extract(message: Message<T>): Context {
        return try {
            propagator.extract(Context.current(), message.metaData, MetaDataGetter)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.debug("Failed to extract trace context from message metadata", e)
            Context.current()
        }
    }

    /**
     * Extracts a remote SpanContext from message metadata.
     *
     * This is a convenience method that extracts the trace context and returns just the
     * [SpanContext] portion, which is useful for creating child spans or linking spans.
     *
     * Returns null if no valid trace context is present in the message metadata.
     *
     * @param T The message payload type
     * @param message The Axon message containing trace context in metadata
     * @return SpanContext if a valid trace context is present, null otherwise
     */
    fun <T> extractSpanContext(message: Message<T>): SpanContext? {
        val context = extract(message)
        val spanContext = Span.fromContext(context).spanContext
        return if (spanContext.isValid) spanContext else null
    }

    /**
     * Extracts baggage from message metadata.
     *
     * Baggage is custom key-value metadata that propagates alongside trace context.
     * This method extracts baggage from the message and returns it as a map.
     *
     * @param T The message payload type
     * @param message The Axon message containing baggage in metadata
     * @return Map of baggage key-value pairs, empty if no baggage present
     */
    fun <T> extractBaggage(message: Message<T>): Map<String, String> {
        val context = extract(message)
        val baggage = Baggage.fromContext(context)

        return buildMap {
            baggage.forEach { key, entry ->
                put(key, entry.value)
            }
        }
    }

    /**
     * Injects both trace context and custom baggage into message metadata.
     *
     * This method extends [inject] by also adding custom baggage key-value pairs to the context
     * before injection. This is useful for propagating application-specific metadata alongside
     * the trace context.
     *
     * @param T The message payload type
     * @param message The Axon message to enrich
     * @param baggage Custom key-value pairs to propagate with the trace
     * @return A new message with trace context and baggage injected into metadata
     */
    fun <T> injectWithBaggage(
        message: Message<T>,
        baggage: Map<String, String>,
    ): Message<T> {
        if (baggage.isEmpty()) {
            return inject(message)
        }

        return try {
            // Build context with baggage
            var currentContext = Context.current()
            var baggageBuilder = Baggage.fromContext(currentContext).toBuilder()

            baggage.forEach { (key, value) ->
                baggageBuilder = baggageBuilder.put(key, value)
            }

            currentContext = currentContext.with(baggageBuilder.build())

            // Inject with baggage-enriched context
            val mutableMetadata = message.metaData.toMutableMap()
            propagator.inject(currentContext, mutableMetadata, MetaDataSetter)

            message.andMetaData(MetaData.from(mutableMetadata))
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Failed to inject trace context with baggage into message metadata", e)
            message
        }
    }

    companion object {
        /**
         * Creates a TraceContextPropagator with the default W3C Trace Context propagator.
         *
         * This factory method provides a convenient way to create a propagator with standard
         * W3C Trace Context support, which is the recommended configuration for most use cases.
         *
         * @return TraceContextPropagator configured with W3C standard propagation
         */
        fun withW3CDefaults(): TraceContextPropagator {
            return TraceContextPropagator(
                io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance(),
            )
        }

        /**
         * Creates a TraceContextPropagator with composite propagator supporting multiple formats.
         *
         * This includes W3C Trace Context, W3C Baggage, and other standard propagators.
         * Use this when you need maximum interoperability with different tracing systems.
         *
         * @return TraceContextPropagator configured with composite propagation
         */
        fun withCompositePropagator(): TraceContextPropagator {
            return TraceContextPropagator(
                io.opentelemetry.context.propagation.ContextPropagators.create(
                    TextMapPropagator.composite(
                        io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance(),
                        io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator.getInstance(),
                    ),
                ).textMapPropagator,
            )
        }
    }
}

/**
 * Extension function to inject trace context into an Axon message.
 *
 * This provides a more Kotlin-idiomatic API for trace context injection.
 *
 * @receiver The message to enrich with trace context
 * @param propagator The TraceContextPropagator to use for injection
 * @return A new message with trace context injected into metadata
 */
fun <T> Message<T>.withTraceContext(propagator: TraceContextPropagator): Message<T> =
    propagator.inject(this)

/**
 * Extension function to inject trace context and baggage into an Axon message.
 *
 * This provides a more Kotlin-idiomatic API for trace context and baggage injection.
 *
 * @receiver The message to enrich with trace context and baggage
 * @param propagator The TraceContextPropagator to use for injection
 * @param baggage Custom key-value pairs to propagate with the trace
 * @return A new message with trace context and baggage injected into metadata
 */
fun <T> Message<T>.withTraceContextAndBaggage(
    propagator: TraceContextPropagator,
    baggage: Map<String, String>,
): Message<T> = propagator.injectWithBaggage(this, baggage)
