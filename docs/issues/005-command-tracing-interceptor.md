# Issue 005: Command Tracing Interceptor

**Phase:** Core Tracing
**Priority:** High
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 003, 004

## Overview
Implement message dispatch and handler interceptors for Axon command bus that create and manage OpenTelemetry spans for command execution. This enables end-to-end tracing of command processing from dispatch through handling, including aggregate loading and persistence.

## Goals
- Create spans for command dispatch operations
- Create spans for command handler execution
- Propagate trace context through command metadata
- Capture command details as span attributes
- Handle exceptions and mark spans as errors
- Support both synchronous and async command handling
- Integrate with aggregate lifecycle for context

## Technical Requirements

### Components to Create

1. **CommandDispatchInterceptor** (`io.github.axonsentry/axon/CommandDispatchInterceptor.kt`)
   - Purpose: Intercept commands at dispatch time and create parent spans
   - Key responsibilities:
     - Create or continue trace spans
     - Add trace context to command metadata
     - Capture command attributes
     - Handle span lifecycle

2. **CommandHandlerInterceptor** (`io.github.axonsentry/axon/CommandHandlerInterceptor.kt`)
   - Purpose: Intercept commands at handler execution and create child spans
   - Key responsibilities:
     - Extract parent trace context from metadata
     - Create child span for handler execution
     - Capture handler details (class, method)
     - Mark errors on exceptions
     - Complete span after handling

3. **CommandSpanFactory** (`io.github.axonsentry/axon/CommandSpanFactory.kt`)
   - Purpose: Factory for creating command-related spans with proper attributes
   - Key responsibilities:
     - Generate appropriate span names
     - Add command-specific attributes
     - Apply configuration settings
     - Support custom attribute providers

4. **CommandMetadataEnricher** (`io.github.axonsentry/axon/CommandMetadataEnricher.kt`)
   - Purpose: Enrich command metadata with tracing information
   - Key responsibilities:
     - Add trace context to metadata
     - Add custom tags if configured
     - Preserve existing metadata

### Dependencies
All dependencies already configured in previous issues:
- Axon Framework messaging API
- OpenTelemetry API
- TracingConfiguration and TraceContext from Issue 003

### Configuration
Uses TracingConfiguration to control:
- Whether command tracing is enabled
- Whether command payloads are captured
- Custom attribute providers
- Sampling decisions

## Implementation Guidance

### Step-by-Step Approach

1. **Implement CommandSpanFactory**
   - Create span with proper attributes
   - Apply naming conventions
   - Add Axon-specific metadata

2. **Create CommandDispatchInterceptor**
   - Implement MessageDispatchInterceptor interface
   - Start new span or continue existing trace
   - Enrich metadata with trace context
   - Return BiFunction that completes span

3. **Create CommandHandlerInterceptor**
   - Implement MessageHandlerInterceptor interface
   - Extract trace context from metadata
   - Create child span for handler
   - Wrap handler invocation
   - Complete span and handle errors

4. **Implement CommandMetadataEnricher**
   - Extract active span context
   - Serialize to metadata
   - Merge with existing metadata

5. **Integration Testing**
   - Test with Axon Test fixture
   - Verify span hierarchy
   - Test error scenarios

### Code Examples

#### CommandSpanFactory.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.CommandMessage

/**
 * Factory for creating OpenTelemetry spans for Axon command operations.
 */
class CommandSpanFactory(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) {
    /**
     * Creates a span for command dispatch operation.
     */
    fun createDispatchSpan(
        command: CommandMessage<*>,
        parentContext: Context = Context.current()
    ): Span {
        val spanName = "Command: ${command.commandName}"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_SEND)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_COMMAND)
            .setAttribute(SpanAttributes.AXON_COMMAND_NAME, command.commandName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, command.identifier)
            .apply {
                // Add routing key if present
                command.metaData[CommandMessage.ROUTING_KEY]?.let {
                    setAttribute(SpanAttributes.AXON_ROUTING_KEY, it.toString())
                }

                // Add payload class name
                setAttribute("axon.command.payload_type", command.payloadType.name)

                // Optionally capture payload
                if (configuration.captureCommandPayloads) {
                    setAttribute("axon.command.payload", command.payload.toString())
                }

                // Apply custom attribute providers
                configuration.customAttributeProviders.forEach { provider ->
                    provider.provideAttributes(command).forEach { (key, value) ->
                        setAttribute(key, value)
                    }
                }
            }
            .startSpan()

        return span
    }

    /**
     * Creates a span for command handler execution.
     */
    fun createHandlerSpan(
        command: CommandMessage<*>,
        handlerClass: Class<*>,
        handlerMethod: String? = null,
        parentContext: Context
    ): Span {
        val spanName = "Handle: ${command.commandName}"

        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(SpanAttributes.MESSAGING_SYSTEM, "axon")
            .setAttribute(SpanAttributes.MESSAGING_OPERATION, SpanAttributes.OPERATION_PROCESS)
            .setAttribute(SpanAttributes.AXON_MESSAGE_TYPE, SpanAttributes.MESSAGE_TYPE_COMMAND)
            .setAttribute(SpanAttributes.AXON_COMMAND_NAME, command.commandName)
            .setAttribute(SpanAttributes.AXON_MESSAGE_ID, command.identifier)
            .setAttribute(SpanAttributes.AXON_HANDLER_CLASS, handlerClass.name)
            .apply {
                handlerMethod?.let {
                    setAttribute(SpanAttributes.AXON_HANDLER_METHOD, it)
                }

                // Add routing key if present
                command.metaData[CommandMessage.ROUTING_KEY]?.let {
                    setAttribute(SpanAttributes.AXON_ROUTING_KEY, it.toString())
                }
            }
            .startSpan()

        return span
    }
}
```

#### CommandDispatchInterceptor.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MessageDispatchInterceptor
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Dispatch interceptor that creates tracing spans for outgoing commands.
 *
 * This interceptor:
 * - Creates a CLIENT span for command dispatch
 * - Enriches command metadata with trace context for propagation
 * - Completes the span when command handling finishes
 */
class CommandDispatchInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageDispatchInterceptor<CommandMessage<*>> {

    private val logger = LoggerFactory.getLogger(CommandDispatchInterceptor::class.java)
    private val spanFactory = CommandSpanFactory(tracer, configuration)

    override fun handle(messages: MutableList<out CommandMessage<*>>): BiFunction<Int, CommandMessage<*>, CommandMessage<*>> {
        if (!configuration.enabled || !configuration.traceCommands) {
            return BiFunction { _, message -> message }
        }

        // Create spans for each command
        val spans = messages.map { command ->
            val span = spanFactory.createDispatchSpan(command)
            command.identifier to span
        }.toMap()

        return BiFunction { index, message ->
            val span = spans[message.identifier]

            if (span != null) {
                try {
                    // Enrich metadata with trace context
                    val traceContext = TraceContext.fromSpanContext(span.spanContext)
                    val enrichedMetadata = message.metaData.and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())

                    message.andMetaData(enrichedMetadata)
                } catch (e: Exception) {
                    logger.error("Failed to enrich command metadata with trace context", e)
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR, "Failed to propagate trace context")
                    message
                }
            } else {
                message
            }
        }
    }

    /**
     * Completes the dispatch span when result is available.
     * Should be called by result handling logic.
     */
    fun completeSpan(commandId: String, result: Any?, error: Throwable?) {
        // This will be called from a CommandCallback or similar mechanism
        // Implementation depends on how Axon tracks command completion
    }
}
```

#### CommandHandlerInterceptor.kt
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
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory

/**
 * Handler interceptor that creates tracing spans for command handling.
 *
 * This interceptor:
 * - Extracts parent trace context from command metadata
 * - Creates a CONSUMER span for command handler execution
 * - Records handler details and execution results
 * - Marks span as error on exceptions
 */
class CommandHandlerInterceptor(
    private val tracer: Tracer,
    private val configuration: TracingConfiguration
) : MessageHandlerInterceptor<CommandMessage<*>> {

    private val logger = LoggerFactory.getLogger(CommandHandlerInterceptor::class.java)
    private val spanFactory = CommandSpanFactory(tracer, configuration)

    override fun handle(unitOfWork: UnitOfWork<out CommandMessage<*>>, interceptorChain: InterceptorChain): Any? {
        if (!configuration.enabled || !configuration.traceCommands) {
            return interceptorChain.proceed()
        }

        val command = unitOfWork.message

        // Extract parent trace context from metadata
        val parentContext = extractParentContext(command)

        // Create handler span
        val handlerClass = extractHandlerClass(unitOfWork)
        val span = spanFactory.createHandlerSpan(command, handlerClass, null, parentContext)

        return Context.current().with(span).makeCurrent().use { scope ->
            try {
                // Add unit of work phase listeners to track aggregate operations
                unitOfWork.onPrepareCommit { uow ->
                    recordAggregateInfo(span, uow)
                }

                // Proceed with command handling
                val result = interceptorChain.proceed()

                // Record success
                span.setStatus(StatusCode.OK)
                if (result != null) {
                    span.setAttribute(SpanAttributes.AXON_COMMAND_RESULT_TYPE, result::class.java.name)
                }

                result
            } catch (e: Exception) {
                // Record exception
                span.recordException(e)
                span.setStatus(StatusCode.ERROR, e.message ?: "Command handling failed")
                span.setAttribute(SpanAttributes.ERROR, true)
                span.setAttribute(SpanAttributes.ERROR_TYPE, e::class.java.name)
                span.setAttribute(SpanAttributes.ERROR_MESSAGE, e.message ?: "")

                throw e
            } finally {
                span.end()
            }
        }
    }

    /**
     * Extracts parent trace context from command metadata.
     */
    private fun extractParentContext(command: CommandMessage<*>): Context {
        val traceContextMap = command.metaData[MessageMetadataKeys.TRACE_CONTEXT] as? Map<*, *>
            ?: return Context.current()

        @Suppress("UNCHECKED_CAST")
        val traceContext = TraceContext.fromMetadata(traceContextMap as Map<String, Any>)
            ?: return Context.current()

        // Create remote context from trace context
        return Context.current().with(Span.wrap(traceContext.toSpanContext()))
    }

    /**
     * Extracts handler class from unit of work.
     */
    private fun extractHandlerClass(unitOfWork: UnitOfWork<*>): Class<*> {
        // Try to get from execution result
        return try {
            val handler = unitOfWork.resources()["handlerClass"]
            if (handler is Class<*>) {
                handler
            } else {
                CommandHandlerInterceptor::class.java
            }
        } catch (e: Exception) {
            CommandHandlerInterceptor::class.java
        }
    }

    /**
     * Records aggregate information in the span.
     */
    private fun recordAggregateInfo(span: Span, unitOfWork: UnitOfWork<*>) {
        try {
            // Extract aggregate identifier if available
            val aggregateId = unitOfWork.message.metaData["aggregateId"]
            if (aggregateId != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_ID, aggregateId.toString())
            }

            // Try to get aggregate type from unit of work resources
            val aggregateType = unitOfWork.resources()["aggregateType"]
            if (aggregateType != null) {
                span.setAttribute(SpanAttributes.AXON_AGGREGATE_TYPE, aggregateType.toString())
            }
        } catch (e: Exception) {
            logger.debug("Could not extract aggregate information", e)
        }
    }
}
```

#### CommandMetadataEnricher.kt
```kotlin
package io.github.axonsentry.axon

import io.github.axonsentry.tracing.MessageMetadataKeys
import io.github.axonsentry.tracing.TraceContext
import io.opentelemetry.api.trace.Span
import org.axonframework.messaging.MetaData

/**
 * Enriches command metadata with tracing information.
 */
class CommandMetadataEnricher {

    /**
     * Enriches metadata with current trace context.
     */
    fun enrichWithTraceContext(metadata: MetaData): MetaData {
        val currentSpan = Span.current()

        if (!currentSpan.spanContext.isValid) {
            return metadata
        }

        val traceContext = TraceContext.fromSpanContext(currentSpan.spanContext)
        return metadata.and(MessageMetadataKeys.TRACE_CONTEXT, traceContext.toMetadataMap())
    }

    /**
     * Enriches metadata with custom tags.
     */
    fun enrichWithTags(metadata: MetaData, tags: Map<String, String>): MetaData {
        return if (tags.isNotEmpty()) {
            metadata.and(MessageMetadataKeys.CUSTOM_TAGS, tags)
        } else {
            metadata
        }
    }
}
```

### Integration Points
- Registers with Axon CommandBus as dispatch and handler interceptor
- Uses TraceContext from Issue 003 for propagation
- Uses SpanAttributes from Issue 003 for attribute naming
- Integrates with OpenTelemetry Context API
- Interacts with Axon UnitOfWork for aggregate information

## Testing Requirements

### Unit Tests

**CommandSpanFactoryTest**
- [ ] Test: Creates dispatch span with correct attributes
- [ ] Test: Creates handler span with correct attributes
- [ ] Test: Applies custom attribute providers
- [ ] Test: Captures payload when configured
- [ ] Test: Omits payload when not configured

**CommandDispatchInterceptorTest**
- [ ] Test: Creates span for dispatched command
- [ ] Test: Enriches metadata with trace context
- [ ] Test: Skips tracing when disabled in configuration
- [ ] Test: Handles errors gracefully
- [ ] Test: Preserves existing metadata

**CommandHandlerInterceptorTest**
- [ ] Test: Creates handler span with parent context
- [ ] Test: Extracts parent context from metadata
- [ ] Test: Records exceptions in span
- [ ] Test: Marks span OK on success
- [ ] Test: Records aggregate information
- [ ] Test: Skips tracing when disabled

### Integration Tests
- [ ] Integration: Commands traced end-to-end with Axon Test
- [ ] Integration: Parent-child span relationships maintained
- [ ] Integration: Aggregate information captured
- [ ] Integration: Exceptions propagate correctly
- [ ] Integration: Async command handling works

### Test Coverage Target
90%+ coverage (critical path for all command tracing)

## Acceptance Criteria
- [ ] CommandDispatchInterceptor creates CLIENT spans
- [ ] CommandHandlerInterceptor creates CONSUMER spans
- [ ] Trace context propagates through metadata
- [ ] Span attributes include all relevant command details
- [ ] Exceptions are recorded and spans marked as errors
- [ ] Configuration controls tracing behavior
- [ ] Aggregate information is captured when available
- [ ] All tests passing
- [ ] Integration with Axon verified

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (90%+ coverage)
- [ ] Integration tests with Axon Test passing
- [ ] Code meets quality standards (detekt, ktlint)
- [ ] KDoc complete for public APIs
- [ ] PR reviewed and approved
- [ ] Documentation updated with usage examples
- [ ] Changes committed to main branch

## Resources
- [Axon Message Intercepting](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/message-intercepting)
- [Axon Command Handling](https://docs.axoniq.io/reference-guide/axon-framework/axon-framework-commands)
- [OpenTelemetry Context Propagation](https://opentelemetry.io/docs/instrumentation/java/manual/#context-propagation)
- [OpenTelemetry Span](https://opentelemetry.io/docs/instrumentation/java/manual/#span)
- [Axon Unit of Work](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/unit-of-work)

## Notes
- Command dispatch and handling create separate spans (CLIENT and CONSUMER)
- Trace context must be serialized to metadata for async propagation
- Aggregate information may not always be available in handler interceptor
- Consider performance impact of creating spans for every command
- Payload capture should be opt-in due to privacy/size concerns
- Unit of work phases can be used to capture aggregate lifecycle events
- Test with both @CommandHandler and aggregate command handler methods
- Ensure spans are completed even when exceptions occur
- Consider correlation with event spans for full command lifecycle

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
