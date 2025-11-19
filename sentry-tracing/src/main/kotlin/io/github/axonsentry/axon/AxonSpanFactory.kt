package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.SpanAttributes
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.queryhandling.QueryMessage

/**
 * Factory for creating OpenTelemetry spans for Axon Framework message handling operations.
 *
 * This is the central component that ties together span naming, span kind resolution, and
 * attribute application to create properly configured spans for all Axon message types.
 *
 * The factory creates two types of spans for each message category:
 * 1. **Dispatch/Publish Spans**: Created when sending a message (CLIENT/PRODUCER kind)
 * 2. **Handler Spans**: Created when processing a message (CONSUMER kind)
 *
 * Each span is configured with:
 * - Appropriate span name (via [SpanNameGenerator])
 * - Correct span kind (via [SpanKindResolver])
 * - Axon-specific attributes (via [AttributeApplier])
 * - Parent context for distributed tracing
 *
 * ## Performance
 * Span creation is designed to complete in <50μs per span through:
 * - Efficient attribute application
 * - Minimal object allocation
 * - Direct OpenTelemetry API usage
 *
 * @property tracer The OpenTelemetry tracer for creating spans
 * @property configuration The tracing configuration controlling behavior
 * @property spanNameGenerator Generates human-readable span names
 * @property spanKindResolver Resolves appropriate span kinds
 * @property attributeApplier Applies Axon-specific attributes to spans
 */
class AxonSpanFactory(
    private val tracer: Tracer,
    @Suppress("UnusedPrivateProperty") // Reserved for future custom attribute provider support
    private val configuration: TracingConfiguration,
    private val spanNameGenerator: SpanNameGenerator,
    private val spanKindResolver: SpanKindResolver,
    private val attributeApplier: AttributeApplier,
) {
    /**
     * Creates a span for command dispatch operations.
     *
     * This span represents the client-side operation of sending a command to a command bus.
     * It has [io.opentelemetry.api.trace.SpanKind.CLIENT] kind.
     *
     * @param message The command message being dispatched
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the command dispatch operation
     */
    fun createCommandDispatchSpan(
        message: CommandMessage<*>,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateCommandName(message)
        val spanKind = spanKindResolver.resolveDispatchKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyCommandAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_SEND,
                    )
                }
                .startSpan()

        return span
    }

    /**
     * Creates a span for command handler operations.
     *
     * This span represents the server-side operation of handling a command.
     * It has [io.opentelemetry.api.trace.SpanKind.CONSUMER] kind.
     *
     * @param message The command message being handled
     * @param handlerClass The class containing the handler method
     * @param handlerMethod The name of the handler method
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the command handler operation
     */
    fun createCommandHandlerSpan(
        message: CommandMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateCommandHandlerName(message)
        val spanKind = spanKindResolver.resolveHandlerKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyCommandAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_PROCESS,
                    )
                    attributeApplier.applyHandlerAttributes(
                        spanBuilder,
                        handlerClass,
                        handlerMethod,
                    )
                }
                .startSpan()

        return span
    }

    /**
     * Creates a span for event publishing operations.
     *
     * This span represents publishing an event to the event bus.
     * It has [io.opentelemetry.api.trace.SpanKind.PRODUCER] kind.
     *
     * @param message The event message being published
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the event publish operation
     */
    fun createEventPublishSpan(
        message: EventMessage<*>,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateEventName(message)
        val spanKind = spanKindResolver.resolvePublishKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyEventAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_SEND,
                    )
                }
                .startSpan()

        return span
    }

    /**
     * Creates a span for event handler operations.
     *
     * This span represents handling an event in an event handler or projection.
     * It has [io.opentelemetry.api.trace.SpanKind.CONSUMER] kind.
     *
     * @param message The event message being handled
     * @param handlerClass The class containing the handler method
     * @param handlerMethod The name of the handler method
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the event handler operation
     */
    fun createEventHandlerSpan(
        message: EventMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateEventHandlerName(message)
        val spanKind = spanKindResolver.resolveHandlerKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyEventAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_PROCESS,
                    )
                    attributeApplier.applyHandlerAttributes(
                        spanBuilder,
                        handlerClass,
                        handlerMethod,
                    )
                }
                .startSpan()

        return span
    }

    /**
     * Creates a span for query dispatch operations.
     *
     * This span represents the client-side operation of dispatching a query.
     * It has [io.opentelemetry.api.trace.SpanKind.CLIENT] kind.
     *
     * @param message The query message being dispatched
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the query dispatch operation
     */
    fun createQueryDispatchSpan(
        message: QueryMessage<*, *>,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateQueryName(message)
        val spanKind = spanKindResolver.resolveDispatchKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyQueryAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_SEND,
                    )
                }
                .startSpan()

        return span
    }

    /**
     * Creates a span for query handler operations.
     *
     * This span represents the server-side operation of handling a query.
     * It has [io.opentelemetry.api.trace.SpanKind.CONSUMER] kind.
     *
     * @param message The query message being handled
     * @param handlerClass The class containing the handler method
     * @param handlerMethod The name of the handler method
     * @param parentContext The parent OpenTelemetry context for trace propagation
     * @return A started span for the query handler operation
     */
    fun createQueryHandlerSpan(
        message: QueryMessage<*, *>,
        handlerClass: Class<*>,
        handlerMethod: String,
        parentContext: Context,
    ): Span {
        val spanName = spanNameGenerator.generateQueryHandlerName(message)
        val spanKind = spanKindResolver.resolveHandlerKind()

        val span =
            tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(spanKind)
                .also { spanBuilder ->
                    attributeApplier.applyQueryAttributes(
                        spanBuilder,
                        message,
                        SpanAttributes.OPERATION_PROCESS,
                    )
                    attributeApplier.applyHandlerAttributes(
                        spanBuilder,
                        handlerClass,
                        handlerMethod,
                    )
                }
                .startSpan()

        return span
    }
}
