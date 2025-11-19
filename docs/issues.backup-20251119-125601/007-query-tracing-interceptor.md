# Issue 007: Query Tracing Interceptor

**Phase:** Core Tracing
**Priority:** Medium
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 003, 004

## Overview
Implement dispatch and handler interceptors for Axon query bus to create OpenTelemetry spans for query execution. This enables performance monitoring of read operations and helps identify slow queries in the system.

## Goals
- Trace query dispatch and execution
- Support scatter-gather queries
- Support subscription queries
- Capture query response types and sizes
- Link queries to parent operations when applicable
- Track query handler performance
- Support streaming query responses

## Technical Requirements

### Components to Create

1. **QueryDispatchInterceptor** (`io.github.axonsentry/axon/QueryDispatchInterceptor.kt`)
   - Purpose: Create spans for dispatched queries
   - Key responsibilities:
     - Create CLIENT spans for query dispatch
     - Handle point-to-point queries
     - Handle scatter-gather queries
     - Enrich metadata with trace context
     - Track query timeout

2. **QueryHandlerInterceptor** (`io.github.axonsentry/axon/QueryHandlerInterceptor.kt`)
   - Purpose: Create spans for query handler execution
   - Key responsibilities:
     - Create SERVER spans for query handling
     - Extract parent trace context
     - Capture response type and size
     - Record handler performance
     - Handle streaming responses

3. **QuerySpanFactory** (`io.github.axonsentry/axon/QuerySpanFactory.kt`)
   - Purpose: Factory for query-specific spans
   - Key responsibilities:
     - Create dispatch spans
     - Create handler spans
     - Add query-specific attributes
     - Support different query types

4. **SubscriptionQuerySpanManager** (`io.github.axonsentry/axon/SubscriptionQuerySpanManager.kt`)
   - Purpose: Manage spans for subscription queries
   - Key responsibilities:
     - Create parent span for subscription
     - Create child spans for initial result
     - Create child spans for updates
     - Handle subscription lifecycle

### Dependencies
All dependencies from previous issues.

### Configuration
Controlled by TracingConfiguration:
- `traceQueries`: Enable/disable query tracing
- `captureQueryPayloads`: Include query payloads
- Query-specific sampling rates

## Implementation Guidance

### Step-by-Step Approach

1. **Create QuerySpanFactory**
   - Implement dispatch span creation
   - Implement handler span creation
   - Add query-specific attributes
   - Support scatter-gather metadata

2. **Implement QueryDispatchInterceptor**
   - Create CLIENT spans for queries
   - Enrich query metadata
   - Handle different query types
   - Track response futures

3. **Implement QueryHandlerInterceptor**
   - Extract trace context
   - Create SERVER spans
   - Capture response metadata
   - Handle streaming responses

4. **Create SubscriptionQuerySpanManager**
   - Manage subscription lifecycle
   - Create spans for updates
   - Handle backpressure scenarios

### Code Examples

#### QuerySpanFactory.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.messaging.responsetypes.ResponseType
import org.axonframework.queryhandling.QueryMessage

/**
 * Factory for creating OpenTelemetry spans for Axon query operations.
 */
class QuerySpanFactory(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) {
    /**
     * Creates a span for query dispatch.
     */
    fun createDispatchSpan(
        query: QueryMessage<*, *>,
        parentContext: Context = Context.current()
    ): Span {
        val queryName = query.queryName
        val spanName = "Query: $queryName"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_SEND)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_QUERY)
            .setAttribute(SpanAttributes.AXON_QUERY_NAME, queryName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, query.identifier)
            .apply {
                // Add response type
                setAttribute(
                    SpanAttributes.AXON_QUERY_RESPONSE_TYPE,
                    query.responseType.responseMessagePayloadType.name
                )

                // Add payload type
                setAttribute("axon.query.payload_type", query.payloadType.name)

                // Optionally capture payload
                if (configuration.captureQueryPayloads) {
                    setAttribute("axon.query.payload", query.payload.toString())
                }

                // Apply custom attribute providers
                configuration.customAttributeProviders.forEach { provider ->
                    provider.provideAttributes(query).forEach { (key, value) ->
                        setAttribute(key, value)
                    }
                }
            }
            .startSpan()

        return span
    }

    /**
     * Creates a span for query handler execution.
     */
    fun createHandlerSpan(
        query: QueryMessage<*, *>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        parentContext: Context
    ): Span {
        val queryName = query.queryName
        val spanName = "Handle Query: $queryName"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_PROCESS)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_QUERY)
            .setAttribute(SpanAttributes.AXON_QUERY_NAME, queryName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, query.identifier)
            .setAttribute(SpanAttributes.AXON_HANDLER_CLASS, handlerClass.name)
            .apply {
                handlerMethod?.let {
                    setAttribute(SpanAttributes.AXON_HANDLER_METHOD, it)
                }

                setAttribute(
                    SpanAttributes.AXON_QUERY_RESPONSE_TYPE,
                    query.responseType.responseMessagePayloadType.name
                )
            }
            .startSpan()

        return span
    }

    /**
     * Creates a span for scatter-gather query dispatch.
     */
    fun createScatterGatherSpan(
        query: QueryMessage<*, *>,
        parentContext: Context = Context.current()
    ): Span {
        val queryName = query.queryName
        val spanName = "Scatter-Gather: $queryName"

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_QUERY)
            .setAttribute(SpanAttributes.AXON_QUERY_NAME, queryName)
            .setAttribute("axon.query.type", "scatter-gather")
            .startSpan()
    }

    /**
     * Creates a span for subscription query.
     */
    fun createSubscriptionSpan(
        query: QueryMessage<*, *>,
        parentContext: Context = Context.current()
    ): Span {
        val queryName = query.queryName
        val spanName = "Subscribe: $queryName"

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_QUERY)
            .setAttribute(SpanAttributes.AXON_QUERY_NAME, queryName)
            .setAttribute("axon.query.type", "subscription")
            .startSpan()
    }
}
```

#### QueryDispatchInterceptor.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.queryhandling.QueryMessage
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Dispatch interceptor that creates tracing spans for outgoing queries.
 */
class QueryDispatchInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageDispatchInterceptor<QueryMessage<*, *>> {

    private val logger = LoggerFactory.getLogger(QueryDispatchInterceptor::class.java)
    private val spanFactory = QuerySpanFactory(tracer, configuration)

    override fun handle(
        messages: MutableList<out QueryMessage<*, *>>
    ): BiFunction<Int, QueryMessage<*, *>, QueryMessage<*, *>> {
        if (!configuration.enabled || !configuration.traceQueries) {
            return BiFunction { _, message -> message }
        }

        // Create spans for each query
        val spans = messages.associateBy(
            keySelector = { it.identifier },
            valueTransform = { query ->
                spanFactory.createDispatchSpan(query)
            }
        )

        return BiFunction { _, message ->
            val span = spans[message.identifier]

            if (span != null) {
                try {
                    // Enrich metadata with trace context
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata = message.metaData
                        .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
                    logger.error("Failed to enrich query metadata with trace context", e)
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR)
                    message
                }
            } else {
                message
            }
        }
    }

    /**
     * Completes the dispatch span when query result is received.
     */
    fun completeQuerySpan(queryId: String, result: Any?, error: Throwable?, span: Span) {
        try {
            if (error != null) {
                span.recordException(error)
                span.setStatus(StatusCode.ERROR, error.message ?: "Query failed")
            } else {
                span.setStatus(StatusCode.OK)

                // Record result metadata
                if (result != null) {
                    span.setAttribute("axon.query.result_type", result::class.java.name)

                    // If result is a collection, record size
                    if (result is Collection<*>) {
                        span.setAttribute("axon.query.result_count", result.size.toLong())
                    }
                }
            }
        } finally {
            span.end()
        }
    }
}
```

#### QueryHandlerInterceptor.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.SpanAttributes
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.queryhandling.QueryMessage
import org.slf4j.LoggerFactory

/**
 * Handler interceptor that creates tracing spans for query handling.
 */
class QueryHandlerInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageHandlerInterceptor<QueryMessage<*, *>> {

    private val logger = LoggerFactory.getLogger(QueryHandlerInterceptor::class.java)
    private val spanFactory = QuerySpanFactory(tracer, configuration)

    override fun handle(
        unitOfWork: UnitOfWork<out QueryMessage<*, *>>,
        interceptorChain: InterceptorChain
    ): Any? {
        if (!configuration.enabled || !configuration.traceQueries) {
            return interceptorChain.proceed()
        }

        val query = unitOfWork.message

        // Extract parent trace context
        val parentContext = extractParentContext(query)

        // Extract handler information
        val handlerClass = extractHandlerClass(unitOfWork)

        // Create handler span
        val span = spanFactory.createHandlerSpan(
            query,
            handlerClass,
            null,
            parentContext
        )

        return Context.current().with(span).makeCurrent().use { _ ->
            try {
                // Proceed with query handling
                val result = interceptorChain.proceed()

                // Record result metadata
                if (result != null) {
                    span.setAttribute("axon.query.result_type", result::class.java.name)

                    // Record collection size if applicable
                    when (result) {
                        is Collection<*> -> {
                            span.setAttribute("axon.query.result_count", result.size.toLong())
                        }
                        is Array<*> -> {
                            span.setAttribute("axon.query.result_count", result.size.toLong())
                        }
                    }
                }

                span.setStatus(StatusCode.OK)
                result
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Query handling failed")
                span.setAttribute(SpanAttributes.ERROR, true)
                span.setAttribute(SpanAttributes.ERROR_TYPE, e::class.java.name)
                span.setAttribute(SpanAttributes.ERROR_MESSAGE, e.message ?: "")

                throw e
            } finally {
                span.end()
            }
        }
    }

    private fun extractParentContext(query: QueryMessage<*, *>): Context {
        val traceContextMap = query.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
            ?: return Context.current()

        @Suppress("UNCHECKED_CAST")
        val traceContext = TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?: return Context.current()

        return Context.current().with(Span.wrap(traceContext.toSpanContext()))
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: QueryHandlerInterceptor::class.java
        } catch (e: Exception) {
            QueryHandlerInterceptor::class.java
        }
    }
}
```

#### SubscriptionQuerySpanManager.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.queryhandling.QueryMessage
import org.axonframework.queryhandling.SubscriptionQueryResult
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages spans for subscription queries.
 *
 * Subscription queries have a parent span for the subscription lifetime,
 * with child spans for initial result and each update.
 */
class SubscriptionQuerySpanManager(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) {
    private val logger = LoggerFactory.getLogger(SubscriptionQuerySpanManager::class.java)
    private val spanFactory = QuerySpanFactory(tracer, configuration)

    // Track active subscription spans
    private val activeSubscriptions = ConcurrentHashMap<String, Span>()

    /**
     * Wraps a subscription query result with tracing.
     */
    fun <I, U> wrapSubscriptionQuery(
        query: QueryMessage<*, *>,
        result: SubscriptionQueryResult<I, U>
    ): SubscriptionQueryResult<I, U> {
        if (!configuration.enabled || !configuration.traceQueries) {
            return result
        }

        val subscriptionSpan = spanFactory.createSubscriptionSpan(query)
        activeSubscriptions[query.identifier] = subscriptionSpan

        // Wrap initial result
        val tracedInitialResult = result.initialResult()
            .doOnNext { initialValue ->
                createUpdateSpan(query, "initial", subscriptionSpan.spanContext)
                    .setStatus(StatusCode.OK)
                    .end()
            }
            .doOnError { error ->
                val errorSpan = createUpdateSpan(query, "initial", subscriptionSpan.spanContext)
                errorSpan.recordException(error)
                errorSpan.setStatus(StatusCode.ERROR)
                errorSpan.end()
            }

        // Wrap updates
        val tracedUpdates = result.updates()
            .doOnNext { update ->
                createUpdateSpan(query, "update", subscriptionSpan.spanContext)
                    .setStatus(StatusCode.OK)
                    .end()
            }
            .doOnError { error ->
                val errorSpan = createUpdateSpan(query, "update", subscriptionSpan.spanContext)
                errorSpan.recordException(error)
                errorSpan.setStatus(StatusCode.ERROR)
                errorSpan.end()
            }
            .doOnComplete {
                completeSubscription(query.identifier)
            }
            .doOnCancel {
                cancelSubscription(query.identifier)
            }

        return object : SubscriptionQueryResult<I, U> {
            override fun initialResult(): Mono<I> = tracedInitialResult
            override fun updates(): Flux<U> = tracedUpdates
            override fun cancel(): Boolean = result.cancel()
        }
    }

    private fun createUpdateSpan(
        query: QueryMessage<*, *>,
        updateType: String,
        parentSpanContext: io.opentelemetry.api.trace.SpanContext
    ): Span {
        val parentContext = Context.current().with(Span.wrap(parentSpanContext))

        return tracer.spanBuilder("Query Update: ${query.queryName}")
            .setParent(parentContext)
            .setAttribute("axon.query.update_type", updateType)
            .startSpan()
    }

    private fun completeSubscription(queryId: String) {
        activeSubscriptions.remove(queryId)?.let { span ->
            span.setStatus(StatusCode.OK)
            span.end()
        }
    }

    private fun cancelSubscription(queryId: String) {
        activeSubscriptions.remove(queryId)?.let { span ->
            span.setAttribute("axon.subscription.cancelled", true)
            span.setStatus(StatusCode.OK)
            span.end()
        }
    }
}
```

### Integration Points
- Registers with Axon QueryBus
- Uses TraceContext for propagation
- Links queries to parent operations
- Integrates with reactive streams for subscription queries

## Testing Requirements

### Unit Tests
- [ ] Test: QuerySpanFactory creates dispatch spans
- [ ] Test: QuerySpanFactory creates handler spans
- [ ] Test: QueryDispatchInterceptor enriches metadata
- [ ] Test: QueryHandlerInterceptor extracts parent context
- [ ] Test: Response metadata is captured
- [ ] Test: Collection sizes are recorded
- [ ] Test: Subscription query spans are created
- [ ] Test: Subscription updates create child spans

### Integration Tests
- [ ] Integration: Point-to-point queries traced
- [ ] Integration: Scatter-gather queries traced
- [ ] Integration: Subscription queries traced
- [ ] Integration: Query errors recorded
- [ ] Integration: Large result sets handled

### Test Coverage Target
85%+ coverage

## Acceptance Criteria
- [ ] QueryDispatchInterceptor creates CLIENT spans
- [ ] QueryHandlerInterceptor creates SERVER spans
- [ ] Trace context propagates through metadata
- [ ] Response types and sizes captured
- [ ] Subscription queries fully traced
- [ ] Scatter-gather queries supported
- [ ] Configuration controls behavior
- [ ] All tests passing

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (85%+ coverage)
- [ ] Integration tests passing
- [ ] Subscription query tracing verified
- [ ] Code meets quality standards
- [ ] KDoc complete
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [Axon Query Handling](https://docs.axoniq.io/reference-guide/axon-framework/queries)
- [Axon Subscription Queries](https://docs.axoniq.io/reference-guide/axon-framework/queries/subscription-queries)
- [Project Reactor Tracing](https://projectreactor.io/docs/core/release/reference/#context)

## Notes
- Query spans should complete when result is received
- Subscription queries need special handling for updates
- Consider performance impact on hot query paths
- Scatter-gather queries may create multiple handler spans
- Streaming results should be handled efficiently
- Test with different response types (single, optional, list, stream)

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
