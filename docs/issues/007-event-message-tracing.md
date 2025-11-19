# Issue 007: Event Message Tracing

**Phase:** Core Tracing (Phase 1)
**Priority:** Critical
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 005

## Overview
Enhance event tracing implementation from Issue 006 to use the centralized AxonSpanFactory and add advanced features like event sourcing integration, event stream tracking, and handler group observability. This provides production-ready event tracing with comprehensive observability for event-driven architectures.

## Goals
- Refactor existing event interceptors to use AxonSpanFactory
- Add domain event sourcing metadata to spans
- Track event handler groups and processing order
- Implement event stream position tracking
- Support tracking event processors and subscriptions
- Add replay detection and tracking
- Optimize for high-throughput event streams

## Technical Requirements

### Components to Enhance

1. **EventTracingInterceptor** (`io.github.axonsentry.axon.EventTracingInterceptor.kt`)
   - Purpose: Unified interceptor for event tracing
   - Key responsibilities:
     - Use AxonSpanFactory for span creation
     - Track domain event metadata
     - Handle event processor context
     - Support replay mode detection

2. **EventProcessorSpanEnricher** (`io.github.axonsentry.axon.EventProcessorSpanEnricher.kt`)
   - Purpose: Enrich spans with event processor information
   - Key responsibilities:
     - Capture processor name and type
     - Track token position in event stream
     - Record handler group information
     - Detect replay vs live processing

3. **DomainEventSpanEnricher** (`io.github.axonsentry.axon.DomainEventSpanEnricher.kt`)
   - Purpose: Enrich spans with domain event specific data
   - Key responsibilities:
     - Capture aggregate context
     - Record sequence numbers
     - Track event timestamps
     - Add event metadata

### Dependencies
- AxonSpanFactory from Issue 010
- TracingConfiguration from Issue 003
- Axon Framework event handling APIs
- Axon EventProcessor APIs

## Implementation Guidance

### Code Examples

#### EventTracingInterceptor.kt
```kotlin
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
 */
class EventTracingInterceptor(
    private val spanFactory: AxonSpanFactory,
    private val configuration: TracingConfiguration,
    private val processorEnricher: EventProcessorSpanEnricher = EventProcessorSpanEnricher(),
    private val domainEventEnricher: DomainEventSpanEnricher = DomainEventSpanEnricher()
) : MessageDispatchInterceptor<EventMessage<*>>, MessageHandlerInterceptor<EventMessage<*>> {

    private val logger = LoggerFactory.getLogger(EventTracingInterceptor::class.java)

    // Dispatch (publish) interceptor
    override fun handle(
        messages: MutableList<out EventMessage<*>>
    ): BiFunction<Int, EventMessage<*>, EventMessage<*>> {
        if (!configuration.enabled || !configuration.traceEvents) {
            return BiFunction { _, message -> message }
        }

        val spans = messages.associateWith { event ->
            spanFactory.createEventPublishSpan(event)
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
                    val enrichedMetadata = message.metaData
                        .and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    span.end()
                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
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
        interceptorChain: InterceptorChain
    ): Any? {
        if (!configuration.enabled || !configuration.traceEvents) {
            return interceptorChain.proceed()
        }

        val event = unitOfWork.message
        val parentContext = extractParentContext(event)
        val handlerClass = extractHandlerClass(unitOfWork)

        val span = spanFactory.createEventHandlerSpan(
            event,
            handlerClass,
            null,
            parentContext
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
            } catch (e: Exception) {
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Event handling failed")
                throw e
            } finally {
                span.end()
            }
        }
    }

    private fun extractParentContext(event: EventMessage<*>): Context {
        val traceContextMap = event.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
            ?: return Context.current()

        @Suppress("UNCHECKED_CAST")
        return TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?.toContext() ?: Context.current()
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: EventTracingInterceptor::class.java
        } catch (e: Exception) {
            EventTracingInterceptor::class.java
        }
    }
}
```

#### EventProcessorSpanEnricher.kt
```kotlin
package io.github.axonsentry.axon

import io.opentelemetry.api.trace.Span
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory

/**
 * Enriches spans with event processor context information.
 */
class EventProcessorSpanEnricher {
    private val logger = LoggerFactory.getLogger(EventProcessorSpanEnricher::class.java)

    /**
     * Enriches handler span with event processor details.
     */
    fun enrichHandlerSpan(span: Span, unitOfWork: UnitOfWork<out EventMessage<*>>) {
        try {
            // Processor name
            val processorName = extractProcessorName(unitOfWork)
            if (processorName != null) {
                span.setAttribute("axon.event_processor.name", processorName)
            }

            // Processor type (tracking, subscribing, pooled)
            val processorType = extractProcessorType(unitOfWork)
            if (processorType != null) {
                span.setAttribute("axon.event_processor.type", processorType)
            }

            // Tracking token position (if tracking processor)
            val tokenPosition = extractTokenPosition(unitOfWork)
            if (tokenPosition != null) {
                span.setAttribute("axon.event_processor.token_position", tokenPosition)
            }

            // Detect replay mode
            val isReplaying = detectReplayMode(unitOfWork)
            span.setAttribute("axon.event_processor.is_replaying", isReplaying)

            // Handler group
            val handlerGroup = extractHandlerGroup(unitOfWork)
            if (handlerGroup != null) {
                span.setAttribute("axon.event_handler.group", handlerGroup)
            }

        } catch (e: Exception) {
            logger.debug("Could not extract event processor info", e)
        }
    }

    private fun extractProcessorName(unitOfWork: UnitOfWork<*>): String? {
        return unitOfWork.resources()["processorName"]?.toString()
    }

    private fun extractProcessorType(unitOfWork: UnitOfWork<*>): String? {
        return unitOfWork.resources()["processorType"]?.toString()
    }

    private fun extractTokenPosition(unitOfWork: UnitOfWork<*>): String? {
        val token = unitOfWork.resources()["trackingToken"] as? TrackingToken
        return token?.position()?.toString()
    }

    private fun detectReplayMode(unitOfWork: UnitOfWork<*>): Boolean {
        return unitOfWork.resources()["isReplaying"] as? Boolean ?: false
    }

    private fun extractHandlerGroup(unitOfWork: UnitOfWork<*>): String? {
        return unitOfWork.resources()["handlerGroup"]?.toString()
    }
}
```

#### DomainEventSpanEnricher.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import org.axonframework.eventhandling.DomainEventMessage

/**
 * Enriches spans with domain event specific information.
 */
class DomainEventSpanEnricher {

    /**
     * Enriches publish span with domain event metadata.
     */
    fun enrichPublishSpan(span: Span, event: DomainEventMessage<*>) {
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
        span.setAttribute("axon.event.sequence_number", event.sequenceNumber)
        span.setAttribute("axon.event.timestamp", event.timestamp.toString())
    }

    /**
     * Enriches handler span with domain event metadata.
     */
    fun enrichHandlerSpan(span: Span, event: DomainEventMessage<*>) {
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
        span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
        span.setAttribute("axon.event.sequence_number", event.sequenceNumber)
    }
}
```

## Testing Requirements

### Unit Tests
- [ ] Test: EventTracingInterceptor uses AxonSpanFactory
- [ ] Test: Domain event attributes captured
- [ ] Test: Event processor context enriched
- [ ] Test: Replay mode detected correctly
- [ ] Test: Tracking token position recorded
- [ ] Test: Handler group information captured
- [ ] Test: Errors recorded and propagated
- [ ] Test: Configuration controls behavior

### Integration Tests
- [ ] Integration: Event publication traced end-to-end
- [ ] Integration: Event handlers traced with processor context
- [ ] Integration: Tracking event processor traces include token
- [ ] Integration: Subscribing event processor traces correctly
- [ ] Integration: Replay mode visible in Sentry
- [ ] Integration: Event streams traced across handlers

### Test Coverage Target
90%+ coverage

## Acceptance Criteria
- [ ] Event tracing uses AxonSpanFactory
- [ ] Domain event metadata captured
- [ ] Event processor context enriched
- [ ] Replay mode detection works
- [ ] Handler groups tracked
- [ ] Performance overhead <5%
- [ ] All tests passing

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
- [Axon Event Handling](https://docs.axoniq.io/reference-guide/axon-framework/events)
- [Axon Event Processors](https://docs.axoniq.io/reference-guide/axon-framework/events/event-processors)
- [OpenTelemetry Messaging](https://opentelemetry.io/docs/specs/semconv/messaging/)

## Notes
- Replaces/enhances Issue 006 implementation
- Event processor context critical for debugging
- Replay detection helps filter out non-production traces
- Token position useful for tracking processing lag
- Consider sampling during replay to reduce overhead

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
