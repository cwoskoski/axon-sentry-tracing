package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.SpanAttributes
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Unified command tracing interceptor that handles both dispatch and handler phases.
 *
 * This interceptor integrates with Axon Framework's command bus to provide distributed
 * tracing for command messages using OpenTelemetry and Sentry.
 *
 * Features:
 * - Creates dispatch spans when commands are sent
 * - Creates handler spans when commands are processed
 * - Propagates trace context through message metadata
 * - Enriches spans with command results and aggregate lifecycle information
 * - Handles errors and exceptions with proper span status
 *
 * @property spanFactory Factory for creating OpenTelemetry spans
 * @property configuration Tracing configuration controlling behavior
 * @property resultEnricher Enricher for command result information
 * @property lifecycleEnricher Enricher for aggregate lifecycle information
 *
 * @since 1.0.0
 */
class CommandTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val resultEnricher: CommandResultSpanEnricher = CommandResultSpanEnricher(),
    private val lifecycleEnricher: AggregateLifecycleSpanEnricher = AggregateLifecycleSpanEnricher(),
) : MessageDispatchInterceptor<CommandMessage<*>>,
    MessageHandlerInterceptor<CommandMessage<*>> {
    private val logger = LoggerFactory.getLogger(CommandTracingInterceptor::class.java)

    /**
     * Intercepts command dispatch to create client-side spans and propagate trace context.
     *
     * For each command being dispatched:
     * 1. Creates a dispatch span using AxonSpanFactory
     * 2. Extracts trace context from the span
     * 3. Adds trace context to command metadata for propagation
     *
     * @param messages The list of commands being dispatched
     * @return Function that enriches each command with trace metadata
     */
    override fun handle(
        messages: MutableList<out CommandMessage<*>>,
    ): BiFunction<Int, CommandMessage<*>, CommandMessage<*>> {
        if (!configuration.enabled || !configuration.traceCommands) {
            return BiFunction { _, message -> message }
        }

        // Create dispatch spans for all messages
        val spans =
            messages.associateWith { command ->
                spanFactory.createCommandDispatchSpan(command, Context.current())
            }

        return BiFunction { _, message ->
            val span = spans[message]
            if (span != null) {
                try {
                    // Extract trace context and add to metadata
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata =
                        message.metaData
                            .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    // End dispatch span
                    span.end()

                    message.andMetaData(enrichedMetadata)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.error("Failed to propagate trace context in command", e)
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR)
                    span.end()
                    message
                }
            } else {
                message
            }
        }
    }

    /**
     * Intercepts command handling to create server-side spans and track execution.
     *
     * For each command being handled:
     * 1. Extracts parent trace context from command metadata
     * 2. Creates a handler span as child of dispatch span
     * 3. Registers lifecycle enrichers with UnitOfWork
     * 4. Tracks execution duration and results
     * 5. Records errors and exceptions
     *
     * @param unitOfWork The unit of work for command execution
     * @param interceptorChain The interceptor chain to continue processing
     * @return The command execution result
     */
    @Suppress("TooGenericExceptionCaught")
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any? {
        if (!configuration.enabled || !configuration.traceCommands) {
            return interceptorChain.proceed()
        }

        val command = unitOfWork.message
        val parentContext = extractParentContext(command)
        val handlerClass = extractHandlerClass(unitOfWork)

        // Handler method name not available from UnitOfWork
        val span =
            spanFactory.createCommandHandlerSpan(
                command,
                handlerClass,
                "",
                parentContext,
            )

        return Context.current().with(span).makeCurrent().use {
            try {
                // Register lifecycle listeners
                unitOfWork.onPrepareCommit { uow ->
                    lifecycleEnricher.enrichWithAggregateInfo(span, uow)
                }

                // Execute command
                val startTime = System.nanoTime()
                val result = interceptorChain.proceed()
                val duration = System.nanoTime() - startTime

                // Record success
                span.setStatus(StatusCode.OK)
                span.setAttribute("axon.command.duration_ns", duration)
                resultEnricher.enrichWithResult(span, result)

                result
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Command failed")
                span.setAttribute(SpanAttributes.ERROR, true)
                throw e
            } finally {
                span.end()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractParentContext(command: CommandMessage<*>): Context {
        return try {
            val traceContextMap =
                command.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
                    ?: return Context.current()

            @Suppress("UNCHECKED_CAST")
            TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
                ?.toContext() ?: Context.current()
        } catch (e: Exception) {
            logger.debug("Could not extract trace context from command metadata", e)
            Context.current()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: CommandTracingInterceptor::class.java
        } catch (e: Exception) {
            CommandTracingInterceptor::class.java
        }
    }
}
