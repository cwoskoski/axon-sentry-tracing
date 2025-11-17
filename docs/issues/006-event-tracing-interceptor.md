# Issue 006: Event Tracing Interceptor

**Phase:** Core Tracing
**Priority:** High
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 003, 004

## Overview
Implement dispatch and handler interceptors for Axon event bus to create OpenTelemetry spans for event publication and processing. This enables distributed tracing across event-driven workflows, including event processors, sagas, and projections.

## Goals
- Trace event publication from aggregates
- Trace event processing in event handlers
- Support tracking processor groups and segments
- Propagate trace context through event metadata
- Handle both domain events and integration events
- Support event sourcing replay scenarios
- Capture event store operations

## Technical Requirements

### Components to Create

1. **EventDispatchInterceptor** (`io.github.axonsentry/axon/EventDispatchInterceptor.kt`)
   - Purpose: Create spans for published events
   - Key responsibilities:
     - Create PRODUCER spans for event publication
     - Enrich event metadata with trace context
     - Link events to parent command spans
     - Capture event details and timestamps

2. **EventHandlerInterceptor** (`io.github.axonsentry/axon/EventHandlerInterceptor.kt`)
   - Purpose: Create spans for event handler execution
   - Key responsibilities:
     - Extract parent trace from event metadata
     - Create CONSUMER spans for handlers
     - Capture processor group and segment info
     - Record handler execution details
     - Support saga and projection handlers

3. **EventSpanFactory** (`io.github.axonsentry/axon/EventSpanFactory.kt`)
   - Purpose: Factory for event-specific spans
   - Key responsibilities:
     - Create publication spans
     - Create processing spans
     - Add event-specific attributes
     - Support different event types

4. **EventProcessorSpanDecorator** (`io.github.axonsentry/axon/EventProcessorSpanDecorator.kt`)
   - Purpose: Decorate spans with event processor details
   - Key responsibilities:
     - Add processor group name
     - Add segment information
     - Add token position
     - Track batch processing

### Dependencies
All dependencies from previous issues, plus:
- Axon EventBus and EventProcessor APIs
- OpenTelemetry API for span management

### Configuration
Controlled by TracingConfiguration:
- `traceEvents`: Enable/disable event tracing
- `traceEventProcessors`: Enable processor-level tracing
- `captureEventPayloads`: Include event payloads
- Event-specific custom attributes

## Implementation Guidance

### Step-by-Step Approach

1. **Create EventSpanFactory**
   - Implement publication span creation
   - Implement processing span creation
   - Add event-specific attributes
   - Support aggregate event metadata

2. **Implement EventDispatchInterceptor**
   - Intercept published events
   - Create PRODUCER spans
   - Link to active command span if available
   - Enrich metadata with trace context

3. **Implement EventHandlerInterceptor**
   - Extract trace context from metadata
   - Create CONSUMER spans
   - Capture handler details
   - Complete spans with results

4. **Create EventProcessorSpanDecorator**
   - Extract processor metadata
   - Add processor-specific attributes
   - Handle batch vs. single event processing

5. **Test with Event Sourcing**
   - Verify replay doesn't duplicate spans
   - Test with different processor types
   - Validate saga tracing

### Code Examples

#### EventSpanFactory.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.EventMessage
import java.time.Instant

/**
 * Factory for creating OpenTelemetry spans for Axon event operations.
 */
class EventSpanFactory(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) {
    /**
     * Creates a span for event publication.
     */
    fun createPublicationSpan(
        event: EventMessage<*>,
        parentContext: Context = Context.current()
    ): Span {
        val eventName = event.payloadType.simpleName
        val spanName = "Publish: $eventName"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_SEND)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_EVENT)
            .setAttribute(SpanAttributes.AXON_EVENT_TYPE, eventName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, event.identifier)
            .apply {
                // Add timestamp
                setAttribute(
                    SpanAttributes.AXON_EVENT_TIMESTAMP,
                    event.timestamp.toString()
                )

                // Add domain event specific attributes
                if (event is DomainEventMessage<*>) {
                    setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
                    setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
                    setAttribute(SpanAttributes.AXON_SEQUENCE_NUMBER, event.sequenceNumber)
                }

                // Optionally capture payload
                if (configuration.captureEventPayloads) {
                    setAttribute("axon.event.payload", event.payload.toString())
                }

                // Apply custom attribute providers
                configuration.customAttributeProviders.forEach { provider ->
                    provider.provideAttributes(event).forEach { (key, value) ->
                        setAttribute(key, value)
                    }
                }
            }
            .startSpan()

        return span
    }

    /**
     * Creates a span for event handler execution.
     */
    fun createHandlerSpan(
        event: EventMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        processingGroup: String? = null,
        segmentId: Int? = null,
        parentContext: Context
    ): Span {
        val eventName = event.payloadType.simpleName
        val spanName = "Handle: $eventName"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_PROCESS)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_EVENT)
            .setAttribute(SpanAttributes.AXON_EVENT_TYPE, eventName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, event.identifier)
            .setAttribute(SpanAttributes.AXON_HANDLER_CLASS, handlerClass.name)
            .apply {
                handlerMethod?.let {
                    setAttribute(SpanAttributes.AXON_HANDLER_METHOD, it)
                }

                processingGroup?.let {
                    setAttribute(SpanAttributes.AXON_PROCESSING_GROUP, it)
                }

                segmentId?.let {
                    setAttribute(SpanAttributes.AXON_SEGMENT_ID, it.toLong())
                }

                // Add domain event attributes
                if (event is DomainEventMessage<*>) {
                    setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
                    setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
                    setAttribute(SpanAttributes.AXON_SEQUENCE_NUMBER, event.sequenceNumber)
                }
            }
            .startSpan()

        return span
    }

    /**
     * Creates a span for event processor batch processing.
     */
    fun createBatchProcessingSpan(
        processingGroup: String,
        batchSize: Int,
        segmentId: Int,
        parentContext: Context = Context.current()
    ): Span {
        val spanName = "Process Batch: $processingGroup"

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(SpanAttributes.AXON_PROCESSING_GROUP, processingGroup)
            .setAttribute(SpanAttributes.AXON_SEGMENT_ID, segmentId.toLong())
            .setAttribute("axon.batch.size", batchSize.toLong())
            .startSpan()
    }
}
```

#### EventDispatchInterceptor.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.MessageDispatchInterceptor
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Dispatch interceptor that creates tracing spans for published events.
 *
 * This interceptor:
 * - Creates PRODUCER spans for event publication
 * - Links events to parent command spans when applicable
 * - Enriches event metadata with trace context
 */
class EventDispatchInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageDispatchInterceptor<EventMessage<*>> {

    private val logger = LoggerFactory.getLogger(EventDispatchInterceptor::class.java)
    private val spanFactory = EventSpanFactory(tracer, configuration)

    override fun handle(messages: MutableList<out EventMessage<*>>): BiFunction<Int, EventMessage<*>, EventMessage<*>> {
        if (!configuration.enabled || !configuration.traceEvents) {
            return BiFunction { _, message -> message }
        }

        // Get current context (likely from command handler)
        val parentContext = Context.current()

        // Create spans for each event
        val spans = messages.associateBy(
            keySelector = { it.identifier },
            valueTransform = { event ->
                spanFactory.createPublicationSpan(event, parentContext)
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

                    // Complete the span immediately after dispatch
                    // Events are fire-and-forget from the publisher's perspective
                    span.setStatus(StatusCode.OK)
                    span.end()

                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
                    logger.error("Failed to enrich event metadata with trace context", e)
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR, "Failed to propagate trace context")
                    span.end()
                    message
                }
            } else {
                message
            }
        }
    }
}
```

#### EventHandlerInterceptor.kt
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
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory

/**
 * Handler interceptor that creates tracing spans for event processing.
 *
 * This interceptor:
 * - Extracts parent trace context from event metadata
 * - Creates CONSUMER spans for event handler execution
 * - Records processing group and segment information
 * - Handles saga and projection handlers
 */
class EventHandlerInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageHandlerInterceptor<EventMessage<*>> {

    private val logger = LoggerFactory.getLogger(EventHandlerInterceptor::class.java)
    private val spanFactory = EventSpanFactory(tracer, configuration)

    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any? {
        if (!configuration.enabled || !configuration.traceEventProcessors) {
            return interceptorChain.proceed()
        }

        val event = unitOfWork.message

        // Extract parent trace context
        val parentContext = extractParentContext(event)

        // Extract handler and processor information
        val handlerClass = extractHandlerClass(unitOfWork)
        val processingGroup = extractProcessingGroup(unitOfWork)
        val segmentId = extractSegmentId(unitOfWork)

        // Create handler span
        val span = spanFactory.createHandlerSpan(
            event,
            handlerClass,
            null,
            processingGroup,
            segmentId,
            parentContext
        )

        return Context.current().with(span).makeCurrent().use { _ ->
            try {
                // Proceed with event handling
                val result = interceptorChain.proceed()

                // Record success
                span.setStatus(StatusCode.OK)

                result
            } catch (e: Exception) {
                // Record exception
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Event handling failed")
                span.setAttribute(SpanAttributes.ERROR, true)
                span.setAttribute(SpanAttributes.ERROR_TYPE, e::class.java.name)
                span.setAttribute(SpanAttributes.ERROR_MESSAGE, e.message ?: "")

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
        val traceContext = TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?: return Context.current()

        return Context.current().with(Span.wrap(traceContext.toSpanContext()))
    }

    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        return try {
            unitOfWork.resources()["handlerClass"] as? Class<*>
                ?: EventHandlerInterceptor::class.java
        } catch (e: Exception) {
            EventHandlerInterceptor::class.java
        }
    }

    private fun extractProcessingGroup(unitOfWork: UnitOfWork<*>): String? {
        return try {
            unitOfWork.resources()["processingGroup"] as? String
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSegmentId(unitOfWork: UnitOfWork<*>): Int? {
        return try {
            (unitOfWork.resources()["segmentId"] as? Number)?.toInt()
        } catch (e: Exception) {
            null
        }
    }
}
```

#### EventProcessorSpanDecorator.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.Span
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.Segment

/**
 * Decorates spans with event processor-specific information.
 */
class EventProcessorSpanDecorator {

    /**
     * Adds processor information to an active span.
     */
    fun decorateWithProcessorInfo(
        span: Span,
        processorName: String,
        segment: Segment? = null
    ) {
        span.setAttribute(SpanAttributes.AXON_PROCESSING_GROUP, processorName)

        segment?.let {
            span.setAttribute(SpanAttributes.AXON_SEGMENT_ID, it.segmentId.toLong())
            span.setAttribute("axon.segment.mask", it.mask.toLong())
        }
    }

    /**
     * Adds batch processing information to a span.
     */
    fun decorateWithBatchInfo(
        span: Span,
        events: List<EventMessage<*>>,
        currentPosition: Long? = null
    ) {
        span.setAttribute("axon.batch.size", events.size.toLong())

        currentPosition?.let {
            span.setAttribute("axon.token.position", it)
        }

        // Add first and last event IDs
        if (events.isNotEmpty()) {
            span.setAttribute("axon.batch.first_event_id", events.first().identifier)
            span.setAttribute("axon.batch.last_event_id", events.last().identifier)
        }
    }

    /**
     * Adds saga-specific information to a span.
     */
    fun decorateWithSagaInfo(
        span: Span,
        sagaType: Class<*>,
        sagaIdentifier: String? = null,
        associationValue: Any? = null
    ) {
        span.setAttribute("axon.saga.type", sagaType.name)

        sagaIdentifier?.let {
            span.setAttribute("axon.saga.id", it)
        }

        associationValue?.let {
            span.setAttribute("axon.saga.association_value", it.toString())
        }
    }
}
```

### Integration Points
- Registers with Axon EventBus and EventProcessors
- Uses TraceContext for context propagation
- Links to command spans when events are published from command handlers
- Integrates with Axon's processor lifecycle
- Supports tracking processor, saga, and projection handlers

## Testing Requirements

### Unit Tests
- [ ] Test: EventSpanFactory creates publication spans
- [ ] Test: EventSpanFactory creates handler spans
- [ ] Test: EventDispatchInterceptor enriches metadata
- [ ] Test: EventHandlerInterceptor extracts parent context
- [ ] Test: EventHandlerInterceptor records exceptions
- [ ] Test: EventProcessorSpanDecorator adds processor info
- [ ] Test: Saga information is captured
- [ ] Test: Batch processing information is captured
- [ ] Test: Configuration controls event tracing

### Integration Tests
- [ ] Integration: Events traced from command to handler
- [ ] Integration: Multiple handlers create separate spans
- [ ] Integration: Saga handlers are traced
- [ ] Integration: Projection handlers are traced
- [ ] Integration: Event replay doesn't create duplicate spans
- [ ] Integration: Processor segment information is captured

### Test Coverage Target
90%+ coverage

## Acceptance Criteria
- [ ] EventDispatchInterceptor creates PRODUCER spans
- [ ] EventHandlerInterceptor creates CONSUMER spans
- [ ] Trace context propagates through event metadata
- [ ] Processing group and segment info captured
- [ ] Saga handlers are traced correctly
- [ ] Batch processing is supported
- [ ] Domain events link to aggregate information
- [ ] Configuration controls behavior
- [ ] All tests passing

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (90%+ coverage)
- [ ] Integration tests passing
- [ ] Saga and projection tracing verified
- [ ] Code meets quality standards
- [ ] KDoc complete
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [Axon Event Handling](https://docs.axoniq.io/reference-guide/axon-framework/events)
- [Axon Event Processors](https://docs.axoniq.io/reference-guide/axon-framework/events/event-processors)
- [Axon Sagas](https://docs.axoniq.io/reference-guide/axon-framework/sagas)
- [OpenTelemetry Messaging](https://opentelemetry.io/docs/specs/semconv/messaging/)

## Notes
- Event publication spans complete immediately (fire-and-forget)
- Handler spans are created per handler invocation
- Multiple handlers for same event create separate spans
- Event sourcing replay should be detectable to avoid duplicate spans
- Consider performance impact with high event volumes
- Saga correlation information is valuable for debugging
- Batch processing should create parent span with child handler spans
- Test with both tracking and subscribing processors

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
