# Issue 005: Sentry Axon SpanFactory Implementation

**Phase:** Foundation & Setup (Phase 0)
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 004

## Overview
Implement a comprehensive SpanFactory that creates OpenTelemetry spans optimized for Axon Framework messages and Sentry visualization. This factory centralizes span creation logic, ensures consistent attribute naming, and provides hooks for customization while maintaining high performance.

## Goals
- Create a unified factory for all Axon message span creation
- Implement intelligent span naming conventions for Sentry UI
- Apply consistent OpenTelemetry semantic conventions
- Support custom attribute providers for extensibility
- Optimize span creation performance (<50μs per span)
- Provide configuration-driven feature toggles
- Support all Axon message types (commands, events, queries, sagas)

## Technical Requirements

### Components to Create

1. **AxonSpanFactory** (`io.github.axonsentry.axon.AxonSpanFactory.kt`)
   - Purpose: Central factory for creating all Axon-related spans
   - Key responsibilities:
     - Create dispatch and handler spans for all message types
     - Apply OpenTelemetry semantic conventions
     - Generate Sentry-optimized span names
     - Add Axon-specific attributes
     - Support custom attribute providers
     - Handle parent context extraction

2. **SpanNameGenerator** (`io.github.axonsentry.tracing.SpanNameGenerator.kt`)
   - Purpose: Generate descriptive, Sentry-friendly span names
   - Key responsibilities:
     - Create transaction names from message names
     - Apply naming conventions (e.g., "Command: CreateOrder")
     - Support custom naming strategies
     - Handle edge cases (anonymous classes, proxies)

3. **AttributeApplier** (`io.github.axonsentry.tracing.AttributeApplier.kt`)
   - Purpose: Apply attributes to spans efficiently
   - Key responsibilities:
     - Apply standard OpenTelemetry attributes
     - Apply Axon-specific attributes
     - Execute custom attribute providers
     - Handle null/missing values gracefully
     - Batch attribute application for performance

4. **SpanKindResolver** (`io.github.axonsentry.tracing.SpanKindResolver.kt`)
   - Purpose: Determine appropriate SpanKind for operations
   - Key responsibilities:
     - Map dispatch operations to CLIENT spans
     - Map handler operations to CONSUMER/SERVER spans
     - Support saga and deadline operations
     - Allow custom resolution strategies

### Dependencies
- Axon Framework 4.9.x messaging API
- OpenTelemetry API 1.33.x
- TracingConfiguration from Issue 003
- SpanAttributes from Issue 003
- Sentry SDK 7.x (for optimization insights)

### Configuration
Uses TracingConfiguration to control:
- Span creation enabled/disabled
- Payload capture settings
- Custom attribute providers
- Naming strategies
- Performance optimizations

## Implementation Guidance

### Step-by-Step Approach

1. **Implement SpanNameGenerator**
   - Create naming rules for each message type
   - Handle edge cases (null names, proxies)
   - Add tests for naming consistency

2. **Create SpanKindResolver**
   - Define rules for each operation type
   - Support custom resolution logic
   - Document SpanKind rationale

3. **Implement AttributeApplier**
   - Create efficient attribute batching
   - Handle type conversions
   - Support custom providers

4. **Build AxonSpanFactory**
   - Integrate all components
   - Create factory methods for each message type
   - Add performance optimizations
   - Implement caching where beneficial

5. **Performance Testing**
   - Benchmark span creation time
   - Optimize hot paths
   - Validate <50μs target

### Code Examples

#### AxonSpanFactory.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.Message
import org.axonframework.queryhandling.QueryMessage
import org.slf4j.LoggerFactory

/**
 * Factory for creating OpenTelemetry spans for Axon Framework operations.
 *
 * This factory provides consistent span creation across all message types,
 * applies OpenTelemetry semantic conventions, and optimizes for Sentry visualization.
 *
 * Performance target: <50μs per span creation
 */
class AxonSpanFactory(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration,
    private val spanNameGenerator: SpanNameGenerator = SpanNameGenerator(),
    private val spanKindResolver: SpanKindResolver = SpanKindResolver(),
    private val attributeApplier: AttributeApplier = AttributeApplier(configuration)
) {
    private val logger = LoggerFactory.getLogger(AxonSpanFactory::class.java)

    /**
     * Creates a span for command dispatch.
     */
    fun createCommandDispatchSpan(
        command: CommandMessage<*>,
        parentContext: Context = Context.current()
    ): Span {
        val spanName = spanNameGenerator.generateCommandName(command)
        val spanKind = spanKindResolver.resolveDispatchKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyCommandAttributes(this, command, "send")
            }
            .startSpan()
    }

    /**
     * Creates a span for command handler execution.
     */
    fun createCommandHandlerSpan(
        command: CommandMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        parentContext: Context
    ): Span {
        val spanName = spanNameGenerator.generateCommandHandlerName(command)
        val spanKind = spanKindResolver.resolveHandlerKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyCommandAttributes(this, command, "process")
                attributeApplier.applyHandlerAttributes(this, handlerClass, handlerMethod)
            }
            .startSpan()
    }

    /**
     * Creates a span for event publication.
     */
    fun createEventPublishSpan(
        event: EventMessage<*>,
        parentContext: Context = Context.current()
    ): Span {
        val spanName = spanNameGenerator.generateEventName(event)
        val spanKind = spanKindResolver.resolvePublishKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyEventAttributes(this, event, "publish")
            }
            .startSpan()
    }

    /**
     * Creates a span for event handler execution.
     */
    fun createEventHandlerSpan(
        event: EventMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        parentContext: Context
    ): Span {
        val spanName = spanNameGenerator.generateEventHandlerName(event)
        val spanKind = spanKindResolver.resolveHandlerKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyEventAttributes(this, event, "process")
                attributeApplier.applyHandlerAttributes(this, handlerClass, handlerMethod)
            }
            .startSpan()
    }

    /**
     * Creates a span for query dispatch.
     */
    fun createQueryDispatchSpan(
        query: QueryMessage<*, *>,
        parentContext: Context = Context.current()
    ): Span {
        val spanName = spanNameGenerator.generateQueryName(query)
        val spanKind = spanKindResolver.resolveDispatchKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyQueryAttributes(this, query, "send")
            }
            .startSpan()
    }

    /**
     * Creates a span for query handler execution.
     */
    fun createQueryHandlerSpan(
        query: QueryMessage<*, *>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        parentContext: Context
    ): Span {
        val spanName = spanNameGenerator.generateQueryHandlerName(query)
        val spanKind = spanKindResolver.resolveHandlerKind()

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyQueryAttributes(this, query, "process")
                attributeApplier.applyHandlerAttributes(this, handlerClass, handlerMethod)
            }
            .startSpan()
    }

    /**
     * Creates a span for generic message processing.
     * Used for messages that don't fit standard command/event/query patterns.
     */
    fun createGenericMessageSpan(
        message: Message<*>,
        operation: String,
        parentContext: Context = Context.current(),
        spanKind: SpanKind = SpanKind.INTERNAL
    ): Span {
        val spanName = "$operation: ${message.payloadType.simpleName}"

        return tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(spanKind)
            .apply {
                attributeApplier.applyGenericMessageAttributes(this, message, operation)
            }
            .startSpan()
    }
}
```

#### SpanNameGenerator.kt
```kotlin
package io.github.axonsentry.tracing

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.queryhandling.QueryMessage

/**
 * Generates descriptive span names optimized for Sentry UI.
 *
 * Naming conventions:
 * - Commands: "Command: CreateOrder"
 * - Events: "Event: OrderCreated"
 * - Queries: "Query: FindOrderById"
 * - Handlers: "Handle: CreateOrder" or "Handle: OrderCreated"
 */
class SpanNameGenerator {

    fun generateCommandName(command: CommandMessage<*>): String {
        return "Command: ${extractMessageName(command.commandName, command.payloadType)}"
    }

    fun generateCommandHandlerName(command: CommandMessage<*>): String {
        return "Handle: ${extractMessageName(command.commandName, command.payloadType)}"
    }

    fun generateEventName(event: EventMessage<*>): String {
        return "Event: ${extractMessageName(event.payloadType.simpleName, event.payloadType)}"
    }

    fun generateEventHandlerName(event: EventMessage<*>): String {
        return "Handle: ${extractMessageName(event.payloadType.simpleName, event.payloadType)}"
    }

    fun generateQueryName(query: QueryMessage<*, *>): String {
        return "Query: ${extractMessageName(query.queryName, query.payloadType)}"
    }

    fun generateQueryHandlerName(query: QueryMessage<*, *>): String {
        return "Handle: ${extractMessageName(query.queryName, query.payloadType)}"
    }

    /**
     * Extracts a clean message name from the provided name or payload type.
     * Handles edge cases like null names, proxy classes, and anonymous classes.
     */
    private fun extractMessageName(name: String?, payloadType: Class<*>): String {
        return when {
            !name.isNullOrBlank() -> name
            payloadType.simpleName.isNotBlank() -> payloadType.simpleName
            payloadType.name.contains("$") -> {
                // Handle anonymous or inner classes
                payloadType.name.substringAfterLast('.').substringBefore('$')
            }
            else -> payloadType.name.substringAfterLast('.')
        }
    }
}
```

#### AttributeApplier.kt
```kotlin
package io.github.axonsentry.tracing

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanBuilder
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.Message
import org.axonframework.queryhandling.QueryMessage

/**
 * Applies attributes to OpenTelemetry spans efficiently.
 *
 * Handles:
 * - Standard OpenTelemetry semantic conventions
 * - Axon-specific attributes
 * - Custom attribute providers
 * - Null/missing value handling
 */
class AttributeApplier(
    private val configuration: TracingConfiguration
) {
    /**
     * Applies command-specific attributes to a span builder.
     */
    fun applyCommandAttributes(
        spanBuilder: SpanBuilder,
        command: CommandMessage<*>,
        operation: String
    ) {
        spanBuilder.apply {
            // Standard attributes
            setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            setAttribute(SpanAttributes.MESSAGING_OPERATION, operation)
            setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_COMMAND)
            setAttribute(SpanAttributes.AXON_MESSAGE_ID, command.identifier)
            setAttribute(SpanAttributes.AXON_COMMAND_NAME, command.commandName)
            setAttribute("axon.command.payload_type", command.payloadType.name)

            // Routing key if present
            command.metaData["routingKey"]?.let {
                setAttribute(SpanAttributes.AXON_ROUTING_KEY, it.toString())
            }

            // Payload capture (opt-in)
            if (configuration.captureCommandPayloads) {
                setAttribute("axon.command.payload", sanitizePayload(command.payload))
            }

            // Custom attributes
            applyCustomAttributes(spanBuilder, command)
        }
    }

    /**
     * Applies event-specific attributes to a span builder.
     */
    fun applyEventAttributes(
        spanBuilder: SpanBuilder,
        event: EventMessage<*>,
        operation: String
    ) {
        spanBuilder.apply {
            setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            setAttribute(SpanAttributes.MESSAGING_OPERATION, operation)
            setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_EVENT)
            setAttribute(SpanAttributes.AXON_MESSAGE_ID, event.identifier)
            setAttribute(SpanAttributes.AXON_EVENT_TYPE, event.payloadType.simpleName)
            setAttribute("axon.event.payload_type", event.payloadType.name)
            setAttribute("axon.event.timestamp", event.timestamp.toString())

            // Domain event specific attributes
            if (event is DomainEventMessage<*>) {
                setAttribute(SpanAttributes.AXON_AGGREGATE_ID, event.aggregateIdentifier)
                setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, event.type)
                setAttribute("axon.event.sequence_number", event.sequenceNumber)
            }

            // Payload capture (opt-in)
            if (configuration.captureEventPayloads) {
                setAttribute("axon.event.payload", sanitizePayload(event.payload))
            }

            // Custom attributes
            applyCustomAttributes(spanBuilder, event)
        }
    }

    /**
     * Applies query-specific attributes to a span builder.
     */
    fun applyQueryAttributes(
        spanBuilder: SpanBuilder,
        query: QueryMessage<*, *>,
        operation: String
    ) {
        spanBuilder.apply {
            setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            setAttribute(SpanAttributes.MESSAGING_OPERATION, operation)
            setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_QUERY)
            setAttribute(SpanAttributes.AXON_MESSAGE_ID, query.identifier)
            setAttribute(SpanAttributes.AXON_QUERY_NAME, query.queryName)
            setAttribute("axon.query.payload_type", query.payloadType.name)
            setAttribute("axon.query.response_type", query.responseType.name)

            // Payload capture (opt-in)
            if (configuration.captureQueryPayloads) {
                setAttribute("axon.query.payload", sanitizePayload(query.payload))
            }

            // Custom attributes
            applyCustomAttributes(spanBuilder, query)
        }
    }

    /**
     * Applies handler-specific attributes to a span builder.
     */
    fun applyHandlerAttributes(
        spanBuilder: SpanBuilder,
        handlerClass: Class<*>,
        handlerMethod: String?
    ) {
        spanBuilder.apply {
            setAttribute(SpanAttributes.AXON_HANDLER_CLASS, handlerClass.name)
            handlerMethod?.let {
                setAttribute(SpanAttributes.AXON_HANDLER_METHOD, it)
            }
        }
    }

    /**
     * Applies generic message attributes.
     */
    fun applyGenericMessageAttributes(
        spanBuilder: SpanBuilder,
        message: Message<*>,
        operation: String
    ) {
        spanBuilder.apply {
            setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            setAttribute(SpanAttributes.MESSAGING_OPERATION, operation)
            setAttribute(SpanAttributes.AXON_MESSAGE_ID, message.identifier)
            setAttribute("axon.message.payload_type", message.payloadType.name)
        }
    }

    /**
     * Applies custom attributes from configured providers.
     */
    private fun applyCustomAttributes(spanBuilder: SpanBuilder, message: Message<*>) {
        configuration.customAttributeProviders.forEach { provider ->
            try {
                provider.provideAttributes(message).forEach { (key, value) ->
                    when (value) {
                        is String -> spanBuilder.setAttribute(key, value)
                        is Long -> spanBuilder.setAttribute(key, value)
                        is Double -> spanBuilder.setAttribute(key, value)
                        is Boolean -> spanBuilder.setAttribute(key, value)
                        else -> spanBuilder.setAttribute(key, value.toString())
                    }
                }
            } catch (e: Exception) {
                // Log but don't fail span creation
                // logger.debug("Custom attribute provider failed", e)
            }
        }
    }

    /**
     * Sanitizes payload for safe attribute storage.
     * Limits size and removes sensitive data.
     */
    private fun sanitizePayload(payload: Any?): String {
        if (payload == null) return "null"

        val payloadString = payload.toString()
        val maxLength = configuration.maxPayloadLength ?: 1000

        return if (payloadString.length > maxLength) {
            "${payloadString.take(maxLength)}... (truncated)"
        } else {
            payloadString
        }
    }
}
```

#### SpanKindResolver.kt
```kotlin
package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanKind

/**
 * Resolves appropriate SpanKind for Axon operations.
 *
 * Mapping:
 * - Dispatch operations: CLIENT
 * - Handler operations: CONSUMER
 * - Internal operations: INTERNAL
 */
class SpanKindResolver {

    fun resolveDispatchKind(): SpanKind = SpanKind.CLIENT

    fun resolvePublishKind(): SpanKind = SpanKind.PRODUCER

    fun resolveHandlerKind(): SpanKind = SpanKind.CONSUMER

    fun resolveInternalKind(): SpanKind = SpanKind.INTERNAL
}
```

## Integration Points
- Used by all message interceptors (commands, events, queries)
- Integrates with TracingConfiguration for feature toggles
- Uses SpanAttributes for consistent naming
- Supports custom attribute providers
- Optimized for Sentry transaction visualization

## Testing Requirements

### Unit Tests

**AxonSpanFactoryTest**
- [ ] Test: Creates command dispatch span with correct attributes
- [ ] Test: Creates command handler span with parent context
- [ ] Test: Creates event publish span with domain event attributes
- [ ] Test: Creates event handler span with correct SpanKind
- [ ] Test: Creates query dispatch span with response type
- [ ] Test: Creates query handler span with handler details
- [ ] Test: Applies custom attribute providers
- [ ] Test: Respects configuration for payload capture
- [ ] Test: Handles null/missing values gracefully
- [ ] Test: Creates generic message spans

**SpanNameGeneratorTest**
- [ ] Test: Generates correct command names
- [ ] Test: Generates correct event names
- [ ] Test: Generates correct query names
- [ ] Test: Handles null message names
- [ ] Test: Handles proxy classes
- [ ] Test: Handles anonymous classes
- [ ] Test: Generates consistent handler names

**AttributeApplierTest**
- [ ] Test: Applies all command attributes
- [ ] Test: Applies domain event attributes
- [ ] Test: Applies query attributes with response type
- [ ] Test: Applies handler attributes
- [ ] Test: Executes custom attribute providers
- [ ] Test: Sanitizes long payloads
- [ ] Test: Handles custom provider exceptions
- [ ] Test: Respects payload capture configuration

**SpanKindResolverTest**
- [ ] Test: Returns CLIENT for dispatch operations
- [ ] Test: Returns PRODUCER for publish operations
- [ ] Test: Returns CONSUMER for handler operations
- [ ] Test: Returns INTERNAL for internal operations

### Performance Tests
- [ ] Performance: Span creation completes in <50μs
- [ ] Performance: Attribute application scales linearly
- [ ] Performance: No memory leaks with high span volume
- [ ] Performance: Thread-safe under concurrent load

### Integration Tests
- [ ] Integration: Spans created by factory integrate with Sentry
- [ ] Integration: Custom attributes appear in Sentry UI
- [ ] Integration: Span hierarchy maintained correctly
- [ ] Integration: All message types traced end-to-end

### Test Coverage Target
90%+ coverage (critical component for all tracing)

## Acceptance Criteria
- [ ] AxonSpanFactory creates spans for all message types
- [ ] Span names are descriptive and Sentry-optimized
- [ ] All OpenTelemetry semantic conventions applied
- [ ] Axon-specific attributes included
- [ ] Custom attribute providers supported
- [ ] Configuration controls feature toggles
- [ ] Performance target <50μs achieved
- [ ] Thread-safe under concurrent access
- [ ] All tests passing

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (90%+ coverage)
- [ ] Performance benchmarks meet <50μs target
- [ ] Integration tests verify Sentry visualization
- [ ] Code meets quality standards (detekt, ktlint)
- [ ] KDoc complete for all public APIs
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OpenTelemetry SpanKind](https://opentelemetry.io/docs/specs/otel/trace/api/#spankind)
- [Sentry Tracing UI](https://docs.sentry.io/product/performance/)
- [Axon Message API](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/messages)
- [Java Performance Best Practices](https://wiki.openjdk.org/display/HotSpot/PerformanceTechniques)

## Notes
- This is the core component that all interceptors depend on
- Span naming significantly impacts Sentry UI usefulness
- Performance is critical as this runs on every message
- Consider caching frequently used attribute keys
- Payload sanitization prevents PII leakage
- Custom attribute providers enable extensibility
- SpanKind affects how Sentry visualizes traces
- Thread safety essential for concurrent message processing
- Future: Consider span sampling strategies in factory
- Future: Add support for baggage propagation

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
