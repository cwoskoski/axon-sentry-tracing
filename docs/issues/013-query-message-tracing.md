# Issue 013: Query Message Tracing Enhancement

**Phase:** Core Integration
**Priority:** Critical
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 010

## Overview
Enhance query tracing implementation from Issue 007 to use the centralized AxonSpanFactory and add advanced features like subscription query tracking, scatter-gather query support, and query result metrics. This provides production-ready query tracing with comprehensive observability.

## Goals
- Refactor existing query interceptors to use AxonSpanFactory
- Add subscription query lifecycle tracking
- Implement scatter-gather query tracing
- Track query response types and sizes
- Support streaming query results
- Add query timeout and retry tracking
- Optimize for high-throughput query patterns

## Technical Requirements

### Components to Enhance

1. **QueryTracingInterceptor** (`io.github.axonsentry.axon.QueryTracingInterceptor.kt`)
   - Purpose: Unified interceptor for query tracing
   - Use AxonSpanFactory for span creation
   - Track subscription query lifecycle
   - Support scatter-gather queries

2. **QueryResultSpanEnricher** (`io.github.axonsentry.axon.QueryResultSpanEnricher.kt`)
   - Purpose: Enrich spans with query result information
   - Capture result types and counts
   - Track result stream duration
   - Handle partial results from scatter-gather

3. **SubscriptionQuerySpanEnricher** (`io.github.axonsentry.axon.SubscriptionQuerySpanEnricher.kt`)
   - Purpose: Track subscription query lifecycle
   - Create spans for initial result and updates
   - Track subscription duration
   - Handle cancellation

### Implementation Example

```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.queryhandling.QueryMessage
import java.util.function.BiFunction

class QueryTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val resultEnricher: QueryResultSpanEnricher = QueryResultSpanEnricher()
) : MessageDispatchInterceptor<QueryMessage<*, *>>, MessageHandlerInterceptor<QueryMessage<*, *>> {

    override fun handle(
        messages: MutableList<out QueryMessage<*, *>>
    ): BiFunction<Int, QueryMessage<*, *>, QueryMessage<*, *>> {
        if (!configuration.enabled || !configuration.traceQueries) {
            return BiFunction { _, message -> message }
        }

        val spans = messages.associateWith { query ->
            spanFactory.createQueryDispatchSpan(query)
        }

        return BiFunction { _, message ->
            val span = spans[message]
            if (span != null) {
                try {
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata = message.metaData
                        .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    span.end()
                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
                    span.recordException(e)
                    span.end()
                    message
                }
            } else {
                message
            }
        }
    }

    override fun handle(
        unitOfWork: UnitOfWork<out QueryMessage<*, *>>,
        interceptorChain: InterceptorChain
    ): Any? {
        if (!configuration.enabled || !configuration.traceQueries) {
            return interceptorChain.proceed()
        }

        val query = unitOfWork.message
        val parentContext = extractParentContext(query)
        val handlerClass = extractHandlerClass(unitOfWork)

        val span = spanFactory.createQueryHandlerSpan(query, handlerClass, null, parentContext)

        return Context.current().with(span).makeCurrent().use {
            try {
                val startTime = System.nanoTime()
                val result = interceptorChain.proceed()
                val duration = System.nanoTime() - startTime

                span.setStatus(StatusCode.OK)
                span.setAttribute("axon.query.handler_duration_ns", duration)
                resultEnricher.enrichWithResult(span, result)

                result
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Query failed")
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
        return TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?.toContext() ?: Context.current()
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: QueryTracingInterceptor::class.java
        } catch (e: Exception) {
            QueryTracingInterceptor::class.java
        }
    }
}
```

## Testing Requirements

### Unit Tests
- [ ] Test: Query dispatch creates correct spans
- [ ] Test: Query handlers traced with results
- [ ] Test: Subscription queries tracked
- [ ] Test: Scatter-gather queries traced
- [ ] Test: Query results enriched
- [ ] Test: Streaming results tracked
- [ ] Test: Errors recorded correctly

### Integration Tests
- [ ] Integration: Point-to-point queries traced
- [ ] Integration: Scatter-gather queries with multiple handlers
- [ ] Integration: Subscription query lifecycle
- [ ] Integration: Query results appear in Sentry
- [ ] Integration: Timeout handling

### Test Coverage Target
90%+ coverage

## Acceptance Criteria
- [ ] Query tracing uses AxonSpanFactory
- [ ] All query types supported
- [ ] Query results captured
- [ ] Performance overhead <5%
- [ ] All tests passing

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing (90%+ coverage)
- [ ] Code quality checks passing
- [ ] Documentation updated
- [ ] Changes committed

## Resources
- [Axon Query Handling](https://docs.axoniq.io/reference-guide/axon-framework/queries)
- [Subscription Queries](https://docs.axoniq.io/reference-guide/axon-framework/queries/subscription-queries)

## Notes
- Replaces/enhances Issue 007 implementation
- Subscription queries need special handling for updates
- Scatter-gather creates multiple child spans

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
