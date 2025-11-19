package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Unified event tracing interceptor for publish and handler phases.
 *
 * This interceptor creates spans for event publication and handling,
 * enriches them with domain event and processor metadata, and propagates
 * trace context through event metadata.
 */
class EventTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val processorEnricher: EventProcessorSpanEnricher = EventProcessorSpanEnricher(),
    private val domainEventEnricher: DomainEventSpanEnricher = DomainEventSpanEnricher(),
) : MessageDispatchInterceptor<EventMessage<*>>, MessageHandlerInterceptor<EventMessage<*>> {
    private val logger = LoggerFactory.getLogger(EventTracingInterceptor::class.java)

    // Dispatch (publish) interceptor
    override fun handle(messages: MutableList<out EventMessage<*>>): BiFunction<Int, EventMessage<*>, EventMessage<*>> {
        if (!configuration.enabled || !configuration.traceEvents) {
            return BiFunction { _, message -> message }
        }

        val spans =
            messages.associateWith { event ->
                spanFactory.createEventPublishSpan(event, Context.current())
            }

        return BiFunction { _, message ->
            val span = spans[message]
            if (span != null) {
                try {
                    // Enrich with domain event info
                    if (message is DomainEventMessage<*>) {
                        domainEventEnricher.enrichPublishSpan(span, message)
                    }

                    // Propagate trace context
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata =
                        message.metaData
                            .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    span.end()
                    message.andMetaData(enrichedMetadata)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.error("Failed to trace event publication", e)
                    span.recordException(e)
                    span.end()
                    message
                }
            } else {
                message
            }
        }
    }

    // Handler interceptor
    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any? {
        if (!configuration.enabled || !configuration.traceEvents) {
            return interceptorChain.proceed()
        }

        val event = unitOfWork.message
        val parentContext = extractParentContext(event)
        val handlerClass = extractHandlerClass(unitOfWork)

        // Handler method name - not available at interceptor level
        val span =
            spanFactory.createEventHandlerSpan(
                event,
                handlerClass,
                "",
                parentContext,
            )

        return Context.current().with(span).makeCurrent().use {
            try {
                // Enrich with processor information
                processorEnricher.enrichHandlerSpan(span, unitOfWork)

                // Enrich with domain event info
                if (event is DomainEventMessage<*>) {
                    domainEventEnricher.enrichHandlerSpan(span, event)
                }

                // Execute handler
                val startTime = System.nanoTime()
                val result = interceptorChain.proceed()
                val duration = System.nanoTime() - startTime

                // Record success
                span.setStatus(StatusCode.OK)
                span.setAttribute("axon.event.handler_duration_ns", duration)

                result
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Event handling failed")
                throw e
            } finally {
                span.end()
            }
        }
    }

    private fun extractParentContext(event: EventMessage<*>): Context {
        val traceContextMap =
            event.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
                ?: return Context.current()

        @Suppress("UNCHECKED_CAST")
        return TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?.toContext() ?: Context.current()
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: EventTracingInterceptor::class.java
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            EventTracingInterceptor::class.java
        }
    }
}
