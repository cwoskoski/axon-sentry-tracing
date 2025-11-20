package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.error.ErrorCorrelator
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.queryhandling.QueryMessage
import org.axonframework.queryhandling.SubscriptionQueryMessage
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Unified query tracing interceptor that handles both dispatch and handler phases.
 *
 * This interceptor integrates with Axon Framework's query bus to provide distributed
 * tracing for query messages using OpenTelemetry and Sentry. It supports both
 * regular queries and subscription queries.
 *
 * Features:
 * - Creates dispatch spans when queries are sent
 * - Creates handler spans when queries are processed
 * - Propagates trace context through message metadata
 * - Enriches spans with query results
 * - Handles subscription queries with lifecycle tracking
 * - Handles errors and exceptions with proper span status
 *
 * @property spanFactory Factory for creating OpenTelemetry spans
 * @property configuration Tracing configuration controlling behavior
 * @property resultEnricher Enricher for query result information
 * @property subscriptionEnricher Enricher for subscription query lifecycle
 *
 * @since 1.0.0
 */
class QueryTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val resultEnricher: QueryResultSpanEnricher = QueryResultSpanEnricher(),
    private val subscriptionEnricher: SubscriptionQuerySpanEnricher = SubscriptionQuerySpanEnricher(),
    private val errorCorrelator: ErrorCorrelator? = null,
) : MessageDispatchInterceptor<QueryMessage<*, *>>,
    MessageHandlerInterceptor<QueryMessage<*, *>> {
    private val logger = LoggerFactory.getLogger(QueryTracingInterceptor::class.java)

    /**
     * Intercepts query dispatch to create client-side spans and propagate trace context.
     *
     * For each query being dispatched:
     * 1. Creates a dispatch span using AxonSpanFactory
     * 2. Extracts trace context from the span
     * 3. Adds trace context to query metadata for propagation
     * 4. For subscription queries, marks the span appropriately
     *
     * @param messages The list of queries being dispatched
     * @return Function that enriches each query with trace metadata
     */
    @Suppress("MaxLineLength") // Axon interface signature is long
    override fun handle(messages: MutableList<out QueryMessage<*, *>>): BiFunction<Int, QueryMessage<*, *>, QueryMessage<*, *>> {
        if (!configuration.enabled || !configuration.traceQueries) {
            return BiFunction { _, message -> message }
        }

        // Create dispatch spans for all messages
        val spans =
            messages.associateWith { query ->
                spanFactory.createQueryDispatchSpan(query, Context.current())
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
                    logger.error("Failed to propagate trace context in query", e)
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
     * Intercepts query handling to create server-side spans and track execution.
     *
     * For each query being handled:
     * 1. Extracts parent trace context from query metadata
     * 2. Creates a handler span as child of dispatch span
     * 3. Tracks execution duration and results
     * 4. Enriches with subscription query lifecycle if applicable
     * 5. Records errors and exceptions
     *
     * @param unitOfWork The unit of work for query execution
     * @param interceptorChain The interceptor chain to continue processing
     * @return The query execution result
     */
    @Suppress("TooGenericExceptionCaught")
    override fun handle(
        unitOfWork: UnitOfWork<out QueryMessage<*, *>>,
        interceptorChain: InterceptorChain,
    ): Any? {
        if (!configuration.enabled || !configuration.traceQueries) {
            return interceptorChain.proceed()
        }

        val query = unitOfWork.message
        val parentContext = extractParentContext(query)
        val handlerClass = extractHandlerClass(unitOfWork)

        // Handler method name not available from UnitOfWork
        val span =
            spanFactory.createQueryHandlerSpan(
                query,
                handlerClass,
                "",
                parentContext,
            )

        return Context.current().with(span).makeCurrent().use {
            try {
                // Mark as subscription query if applicable
                if (query is SubscriptionQueryMessage<*, *, *>) {
                    subscriptionEnricher.enrichWithSubscriptionStart(span, query)
                }

                // Execute query
                val startTime = System.nanoTime()
                val result = interceptorChain.proceed()
                val duration = System.nanoTime() - startTime

                // Record success
                span.setStatus(StatusCode.OK)
                span.setAttribute("axon.query.handler_duration_ns", duration)

                // Enrich with result
                resultEnricher.enrichWithResult(span, result)

                // For subscription queries, enrich with initial result
                if (query is SubscriptionQueryMessage<*, *, *>) {
                    subscriptionEnricher.enrichWithInitialResult(span, result)
                }

                result
            } catch (e: Exception) {
                // Use ErrorCorrelator for comprehensive error handling if available
                if (errorCorrelator != null) {
                    errorCorrelator.recordException(span, e, query)
                } else {
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR, e.message ?: "Query failed")
                }
                throw e
            } finally {
                span.end()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractParentContext(query: QueryMessage<*, *>): Context {
        return try {
            val traceContextMap =
                query.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
                    ?: return Context.current()

            @Suppress("UNCHECKED_CAST")
            TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
                ?.toContext() ?: Context.current()
        } catch (e: Exception) {
            logger.debug("Could not extract trace context from query metadata", e)
            Context.current()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: QueryTracingInterceptor::class.java
        } catch (e: Exception) {
            QueryTracingInterceptor::class.java
        }
    }
}
