# Issue 011: Command Message Tracing Enhancement

**Phase:** Core Integration
**Priority:** Critical
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 010

## Overview
Enhance command tracing implementation from Issue 005 to use the centralized AxonSpanFactory and implement advanced features like result correlation, retry tracing, and aggregate lifecycle integration. This provides production-ready command tracing with comprehensive observability.

## Goals
- Refactor existing command interceptors to use AxonSpanFactory
- Add command result correlation to spans
- Implement retry and timeout tracing
- Integrate with aggregate lifecycle events
- Add distributed command tracing across services
- Support async command execution patterns
- Optimize for high-throughput scenarios

## Technical Requirements

### Components to Enhance

1. **CommandTracingInterceptor** (`io.github.axonsentry.axon.CommandTracingInterceptor.kt`)
   - Purpose: Unified interceptor for command tracing (combines dispatch and handler)
   - Key responsibilities:
     - Use AxonSpanFactory for span creation
     - Track command results in spans
     - Handle distributed trace propagation
     - Integrate with UnitOfWork lifecycle

2. **CommandResultSpanEnricher** (`io.github.axonsentry.axon.CommandResultSpanEnricher.kt`)
   - Purpose: Enrich spans with command execution results
   - Key responsibilities:
     - Capture successful command results
     - Record result types and values
     - Handle void results
     - Track execution time metrics

3. **AggregateLifecycleSpanEnricher** (`io.github.axonsentry.axon.AggregateLifecycleSpanEnricher.kt`)
   - Purpose: Capture aggregate creation, loading, and persistence
   - Key responsibilities:
     - Detect aggregate creation vs updates
     - Record aggregate version changes
     - Track event count per command
     - Capture snapshot information

### Dependencies
- AxonSpanFactory from Issue 010
- TracingConfiguration from Issue 003
- TraceContext from Issue 003
- Axon Framework messaging and modeling APIs

## Implementation Guidance

### Code Examples

#### CommandTracingInterceptor.kt
```kotlin
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
 */
class CommandTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val resultEnricher: CommandResultSpanEnricher = CommandResultSpanEnricher(),
    private val lifecycleEnricher: AggregateLifecycleSpanEnricher = AggregateLifecycleSpanEnricher()
) : MessageDispatchInterceptor<CommandMessage<*>>, MessageHandlerInterceptor<CommandMessage<*>> {

    private val logger = LoggerFactory.getLogger(CommandTracingInterceptor::class.java)

    // Dispatch interceptor implementation
    override fun handle(
        messages: MutableList<out CommandMessage<*>>
    ): BiFunction<Int, CommandMessage<*>, CommandMessage<*>> {
        if (!configuration.enabled || !configuration.traceCommands) {
            return BiFunction { _, message -> message }
        }

        val spans = messages.associateWith { command ->
            spanFactory.createCommandDispatchSpan(command)
        }

        return BiFunction { _, message ->
            val span = spans[message]
            if (span != null) {
                try {
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata = message.metaData
                        .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
                    logger.error("Failed to propagate trace context in command", e)
                    span.recordException(e)
                    message
                }
            } else {
                message
            }
        }
    }

    // Handler interceptor implementation
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any? {
        if (!configuration.enabled || !configuration.traceCommands) {
            return interceptorChain.proceed()
        }

        val command = unitOfWork.message
        val parentContext = extractParentContext(command)
        val handlerClass = extractHandlerClass(unitOfWork)

        val span = spanFactory.createCommandHandlerSpan(
            command,
            handlerClass,
            null,
            parentContext
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

    private fun extractParentContext(command: CommandMessage<*>): Context {
        val traceContextMap = command.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
            ?: return Context.current()

        @Suppress("UNCHECKED_CAST")
        return TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?.toContext() ?: Context.current()
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: CommandTracingInterceptor::class.java
        } catch (e: Exception) {
            CommandTracingInterceptor::class.java
        }
    }
}
```

#### CommandResultSpanEnricher.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span

/**
 * Enriches spans with command execution result information.
 */
class CommandResultSpanEnricher {

    /**
     * Enriches span with command result details.
     */
    fun enrichWithResult(span: Span, result: Any?) {
        when {
            result == null -> {
                span.setAttribute("axon.command.result_type", "void")
            }
            result is Unit -> {
                span.setAttribute("axon.command.result_type", "void")
            }
            else -> {
                span.setAttribute("axon.command.result_type", result::class.java.name)

                // Capture simple result types
                when (result) {
                    is String -> span.setAttribute("axon.command.result", result)
                    is Number -> span.setAttribute("axon.command.result", result.toString())
                    is Boolean -> span.setAttribute("axon.command.result", result)
                    else -> {
                        // For complex types, just capture the type
                        span.setAttribute("axon.command.result_class", result::class.java.simpleName)
                    }
                }
            }
        }
    }
}
```

#### AggregateLifecycleSpanEnricher.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.modelling.command.AggregateLifecycle
import org.slf4j.LoggerFactory

/**
 * Enriches spans with aggregate lifecycle information.
 */
class AggregateLifecycleSpanEnricher {
    private val logger = LoggerFactory.getLogger(AggregateLifecycleSpanEnricher::class.java)

    /**
     * Enriches span with aggregate lifecycle details from the unit of work.
     */
    fun enrichWithAggregateInfo(span: Span, unitOfWork: UnitOfWork<*>) {
        try {
            // Extract aggregate identifier
            val aggregateId = extractAggregateId(unitOfWork)
            if (aggregateId != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, aggregateId)
            }

            // Extract aggregate type
            val aggregateType = extractAggregateType(unitOfWork)
            if (aggregateType != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, aggregateType)
            }

            // Track events applied
            val eventCount = extractEventCount(unitOfWork)
            span.setAttribute("axon.aggregate.events_applied", eventCount.toLong())

            // Detect if this is aggregate creation
            val isCreation = detectAggregateCreation(unitOfWork)
            span.setAttribute("axon.aggregate.is_creation", isCreation)

        } catch (e: Exception) {
            logger.debug("Could not extract aggregate lifecycle info", e)
        }
    }

    private fun extractAggregateId(unitOfWork: UnitOfWork<*>): String? {
        return try {
            unitOfWork.message.metaData["aggregateId"]?.toString()
                ?: unitOfWork.resources()["aggregateId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAggregateType(unitOfWork: UnitOfWork<*>): String? {
        return try {
            unitOfWork.resources()["aggregateType"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEventCount(unitOfWork: UnitOfWork<*>): Int {
        return try {
            val events = unitOfWork.resources()["events"] as? List<*>
            events?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun detectAggregateCreation(unitOfWork: UnitOfWork<*>): Boolean {
        return try {
            val isNew = unitOfWork.resources()["aggregateCreation"] as? Boolean
            isNew ?: false
        } catch (e: Exception) {
            false
        }
    }
}
```

## Testing Requirements

### Unit Tests
- [ ] Test: CommandTracingInterceptor uses AxonSpanFactory
- [ ] Test: Dispatch phase enriches metadata with trace context
- [ ] Test: Handler phase extracts parent context correctly
- [ ] Test: Command results enriched in spans
- [ ] Test: Aggregate lifecycle information captured
- [ ] Test: Errors recorded and propagated
- [ ] Test: Configuration controls tracing behavior
- [ ] Test: Concurrent command execution is thread-safe

### Integration Tests
- [ ] Integration: End-to-end command tracing with Axon Test
- [ ] Integration: Aggregate creation traced correctly
- [ ] Integration: Aggregate updates traced with event count
- [ ] Integration: Command results appear in Sentry
- [ ] Integration: Distributed tracing across services
- [ ] Integration: Async commands traced correctly

### Test Coverage Target
90%+ coverage

## Acceptance Criteria
- [ ] Command tracing uses AxonSpanFactory
- [ ] Command results captured in spans
- [ ] Aggregate lifecycle events tracked
- [ ] Distributed trace context propagates
- [ ] Performance overhead <5%
- [ ] All tests passing
- [ ] Documentation updated

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests passing (90%+ coverage)
- [ ] Integration tests passing
- [ ] Code quality checks passing
- [ ] KDoc complete
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main

## Resources
- [Axon Command Handling](https://docs.axoniq.io/reference-guide/axon-framework/axon-framework-commands)
- [Axon Unit of Work](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/unit-of-work)
- [OpenTelemetry Context](https://opentelemetry.io/docs/instrumentation/java/manual/#context-propagation)

## Notes
- Replaces/enhances Issue 005 implementation
- Focus on production features: results, lifecycle, distributed tracing
- Performance critical - measure overhead
- Consider correlation ID propagation for microservices
- Future: Add command bus metrics (queue depth, processing time)

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
