# Issue 003: Core Domain Model and Tracing Context

**Phase:** Foundation
**Priority:** Critical
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 002

## Overview
Define the core domain model for tracing integration, including span context management, trace propagation, and Axon message metadata integration. This establishes the foundational data structures and contracts that all tracing operations will depend on.

## Goals
- Create tracing context model to bridge Axon messages and OpenTelemetry spans
- Define message metadata keys for trace propagation
- Establish span attribute conventions for Axon-specific data
- Create configuration model for tracing behavior
- Design clean abstractions that don't leak implementation details

## Technical Requirements

### Components to Create

1. **TraceContext** (`io.github.axonsentry/tracing/TraceContext.kt`)
   - Purpose: Immutable representation of trace state
   - Key responsibilities:
     - Hold trace ID, span ID, parent span ID
     - Provide serialization for metadata storage
     - Support context propagation across message boundaries

2. **SpanAttributes** (`io.github.axonsentry/tracing/SpanAttributes.kt`)
   - Purpose: Define standard attribute keys for Axon events
   - Key responsibilities:
     - Semantic naming for message types
     - Aggregate identifiers
     - Command/event/query specific attributes
     - Custom tag support

3. **TracingConfiguration** (`io.github.axonsentry/config/TracingConfiguration.kt`)
   - Purpose: Configuration model for tracing behavior
   - Key responsibilities:
     - Enable/disable tracing per message type
     - Sampling configuration
     - Custom attribute providers
     - Sentry DSN configuration

4. **MessageMetadataKeys** (`io.github.axonsentry/tracing/MessageMetadataKeys.kt`)
   - Purpose: Define metadata keys for trace propagation
   - Key responsibilities:
     - Standardize key names
     - Prevent collisions with other metadata

### Dependencies
Already defined in Issue 002:
- OpenTelemetry API (core abstractions)
- Kotlin standard library (data classes)
- Axon Messaging (Message, MetaData types)

### Configuration
No external configuration files needed; this is internal modeling.

## Implementation Guidance

### Step-by-Step Approach

1. **Create Package Structure**
   - Create `io.github.axonsentry.tracing` package
   - Create `io.github.axonsentry.config` package

2. **Implement TraceContext**
   - Define as immutable data class
   - Add companion object for creation and deserialization
   - Implement toMap()/fromMap() for metadata storage

3. **Define SpanAttributes**
   - Create object with const string properties
   - Follow OpenTelemetry semantic conventions
   - Add Axon-specific attributes

4. **Create TracingConfiguration**
   - Use data class with sensible defaults
   - Support builder pattern for fluent configuration
   - Validate configuration on construction

5. **Define MessageMetadataKeys**
   - Create object with const keys
   - Document each key's purpose

### Code Examples

#### TraceContext.kt
```kotlin
package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState

/**
 * Immutable trace context for propagating trace information through Axon messages.
 *
 * This context can be serialized to/from message metadata to maintain trace continuity
 * across async boundaries and distributed systems.
 */
data class TraceContext(
    val traceId: String,
    val spanId: String,
    val traceFlags: Byte = TraceFlags.getDefault(),
    val traceState: Map<String, String> = emptyMap(),
    val baggage: Map<String, String> = emptyMap()
) {
    /**
     * Converts this context to a map suitable for storing in Axon message metadata.
     */
    fun toMetadataMap(): Map<String, Any> = buildMap {
        put(TRACE_ID_KEY, traceId)
        put(SPAN_ID_KEY, spanId)
        put(TRACE_FLAGS_KEY, traceFlags)
        if (traceState.isNotEmpty()) {
            put(TRACE_STATE_KEY, traceState)
        }
        if (baggage.isNotEmpty()) {
            put(BAGGAGE_KEY, baggage)
        }
    }

    /**
     * Converts to OpenTelemetry SpanContext for creating child spans.
     */
    fun toSpanContext(): SpanContext = SpanContext.createFromRemoteParent(
        traceId,
        spanId,
        TraceFlags.fromByte(traceFlags),
        TraceState.builder().apply {
            traceState.forEach { (key, value) -> put(key, value) }
        }.build()
    )

    companion object {
        private const val TRACE_ID_KEY = "_trace_id"
        private const val SPAN_ID_KEY = "_span_id"
        private const val TRACE_FLAGS_KEY = "_trace_flags"
        private const val TRACE_STATE_KEY = "_trace_state"
        private const val BAGGAGE_KEY = "_baggage"

        /**
         * Creates TraceContext from OpenTelemetry SpanContext.
         */
        fun fromSpanContext(spanContext: SpanContext, baggage: Map<String, String> = emptyMap()): TraceContext {
            return TraceContext(
                traceId = spanContext.traceId,
                spanId = spanContext.spanId,
                traceFlags = spanContext.traceFlags.asByte(),
                traceState = spanContext.traceState.asMap(),
                baggage = baggage
            )
        }

        /**
         * Extracts TraceContext from Axon message metadata.
         * Returns null if no trace context is present.
         */
        fun fromMetadata(metadata: Map<String, Any>): TraceContext? {
            val traceId = metadata[TRACE_ID_KEY] as? String ?: return null
            val spanId = metadata[SPAN_ID_KEY] as? String ?: return null
            val traceFlags = (metadata[TRACE_FLAGS_KEY] as? Number)?.toByte() ?: TraceFlags.getDefault()

            @Suppress("UNCHECKED_CAST")
            val traceState = metadata[TRACE_STATE_KEY] as? Map<String, String> ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val baggage = metadata[BAGGAGE_KEY] as? Map<String, String> ?: emptyMap()

            return TraceContext(traceId, spanId, traceFlags, traceState, baggage)
        }
    }
}
```

#### SpanAttributes.kt
```kotlin
package io.github.axonsentry.tracing

/**
 * Standard OpenTelemetry span attribute keys for Axon Framework tracing.
 *
 * Follows semantic conventions where applicable and defines Axon-specific
 * attributes for detailed tracing.
 */
object SpanAttributes {
    // OpenTelemetry semantic conventions
    const val MESSAGING_SYSTEM = "messaging.system"
    const val MESSAGING_OPERATION = "messaging.operation"
    const val MESSAGING_MESSAGE_ID = "messaging.message.id"
    const val MESSAGING_DESTINATION = "messaging.destination.name"

    // Axon Framework specific
    const val AXON_MESSAGE_TYPE = "axon.message.type"
    const val AXON_MESSAGE_NAME = "axon.message.name"
    const val AXON_MESSAGE_ID = "axon.message.id"
    const val AXON_AGGREGATE_ID = "axon.aggregate.id"
    const val AXON_AGGREGATE_TYPE = "axon.aggregate.type"
    const val AXON_SEQUENCE_NUMBER = "axon.sequence_number"
    const val AXON_ROUTING_KEY = "axon.routing_key"

    // Command specific
    const val AXON_COMMAND_NAME = "axon.command.name"
    const val AXON_COMMAND_RESULT_TYPE = "axon.command.result_type"

    // Event specific
    const val AXON_EVENT_TYPE = "axon.event.type"
    const val AXON_EVENT_TIMESTAMP = "axon.event.timestamp"

    // Query specific
    const val AXON_QUERY_NAME = "axon.query.name"
    const val AXON_QUERY_RESPONSE_TYPE = "axon.query.response_type"

    // Handler specific
    const val AXON_HANDLER_CLASS = "axon.handler.class"
    const val AXON_HANDLER_METHOD = "axon.handler.method"

    // Processing context
    const val AXON_PROCESSING_GROUP = "axon.processing_group"
    const val AXON_SEGMENT_ID = "axon.segment.id"

    // Error tracking
    const val ERROR = "error"
    const val ERROR_TYPE = "error.type"
    const val ERROR_MESSAGE = "error.message"
    const val ERROR_STACKTRACE = "error.stacktrace"

    // Values
    const val MESSAGE_TYPE_COMMAND = "command"
    const val MESSAGE_TYPE_EVENT = "event"
    const val MESSAGE_TYPE_QUERY = "query"
    const val OPERATION_SEND = "send"
    const val OPERATION_RECEIVE = "receive"
    const val OPERATION_PROCESS = "process"
}
```

#### TracingConfiguration.kt
```kotlin
package io.github.axonsentry.config

import io.sentry.SentryOptions

/**
 * Configuration for Axon-Sentry tracing integration.
 *
 * @property enabled Master switch for all tracing functionality
 * @property traceCommands Enable command tracing
 * @property traceEvents Enable event tracing
 * @property traceQueries Enable query tracing
 * @property traceEventProcessors Enable event processor tracing
 * @property traceSagas Enable saga tracing
 * @property captureCommandPayloads Include command payloads in spans (be cautious with sensitive data)
 * @property captureEventPayloads Include event payloads in spans
 * @property captureQueryPayloads Include query payloads in spans
 * @property sentryDsn Sentry Data Source Name for error reporting
 * @property environment Deployment environment (dev, staging, production)
 * @property tracesSampleRate Sample rate for traces (0.0 to 1.0)
 * @property attachStacktrace Include stacktraces in Sentry events
 */
data class TracingConfiguration(
    val enabled: Boolean = true,
    val traceCommands: Boolean = true,
    val traceEvents: Boolean = true,
    val traceQueries: Boolean = true,
    val traceEventProcessors: Boolean = true,
    val traceSagas: Boolean = true,
    val captureCommandPayloads: Boolean = false,
    val captureEventPayloads: Boolean = false,
    val captureQueryPayloads: Boolean = false,
    val sentryDsn: String? = null,
    val environment: String = "development",
    val tracesSampleRate: Double = 1.0,
    val attachStacktrace: Boolean = true,
    val customAttributeProviders: List<CustomAttributeProvider> = emptyList()
) {
    init {
        require(tracesSampleRate in 0.0..1.0) {
            "tracesSampleRate must be between 0.0 and 1.0, got $tracesSampleRate"
        }
    }

    /**
     * Creates a builder for fluent configuration.
     */
    fun toBuilder(): Builder = Builder(this)

    class Builder(config: TracingConfiguration = TracingConfiguration()) {
        var enabled: Boolean = config.enabled
        var traceCommands: Boolean = config.traceCommands
        var traceEvents: Boolean = config.traceEvents
        var traceQueries: Boolean = config.traceQueries
        var traceEventProcessors: Boolean = config.traceEventProcessors
        var traceSagas: Boolean = config.traceSagas
        var captureCommandPayloads: Boolean = config.captureCommandPayloads
        var captureEventPayloads: Boolean = config.captureEventPayloads
        var captureQueryPayloads: Boolean = config.captureQueryPayloads
        var sentryDsn: String? = config.sentryDsn
        var environment: String = config.environment
        var tracesSampleRate: Double = config.tracesSampleRate
        var attachStacktrace: Boolean = config.attachStacktrace
        private val customAttributeProviders: MutableList<CustomAttributeProvider> =
            config.customAttributeProviders.toMutableList()

        fun addAttributeProvider(provider: CustomAttributeProvider) = apply {
            customAttributeProviders.add(provider)
        }

        fun build(): TracingConfiguration = TracingConfiguration(
            enabled = enabled,
            traceCommands = traceCommands,
            traceEvents = traceEvents,
            traceQueries = traceQueries,
            traceEventProcessors = traceEventProcessors,
            traceSagas = traceSagas,
            captureCommandPayloads = captureCommandPayloads,
            captureEventPayloads = captureEventPayloads,
            captureQueryPayloads = captureQueryPayloads,
            sentryDsn = sentryDsn,
            environment = environment,
            tracesSampleRate = tracesSampleRate,
            attachStacktrace = attachStacktrace,
            customAttributeProviders = customAttributeProviders.toList()
        )
    }

    companion object {
        /**
         * Creates a configuration builder.
         */
        fun builder(): Builder = Builder()

        /**
         * Default configuration with all tracing enabled.
         */
        fun default(): TracingConfiguration = TracingConfiguration()

        /**
         * Minimal configuration with only errors tracked.
         */
        fun errorsOnly(): TracingConfiguration = TracingConfiguration(
            traceCommands = false,
            traceEvents = false,
            traceQueries = false,
            traceEventProcessors = false,
            traceSagas = false
        )
    }
}

/**
 * Functional interface for providing custom span attributes.
 */
fun interface CustomAttributeProvider {
    /**
     * Provides custom attributes for a given message.
     *
     * @param message The Axon message being traced
     * @return Map of attribute key-value pairs to add to the span
     */
    fun provideAttributes(message: Any): Map<String, String>
}
```

#### MessageMetadataKeys.kt
```kotlin
package io.github.axonsentry.tracing

/**
 * Standard metadata keys for trace propagation in Axon messages.
 *
 * These keys are used to store and retrieve trace context from message metadata,
 * enabling distributed tracing across async boundaries.
 */
object MessageMetadataKeys {
    /**
     * Prefix for all tracing-related metadata to avoid collisions.
     */
    private const val PREFIX = "axon.sentry.tracing"

    /**
     * Parent trace context serialized for propagation.
     */
    const val TRACE_CONTEXT = "$PREFIX.context"

    /**
     * Indicates whether this message should be traced.
     */
    const val TRACE_ENABLED = "$PREFIX.enabled"

    /**
     * Custom tags to be added to the span for this message.
     */
    const val CUSTOM_TAGS = "$PREFIX.tags"

    /**
     * Span kind for this message operation.
     */
    const val SPAN_KIND = "$PREFIX.span_kind"

    /**
     * Transaction name override (for Sentry).
     */
    const val TRANSACTION_NAME = "$PREFIX.transaction_name"
}
```

### Integration Points
- TraceContext bridges Axon MetaData and OpenTelemetry SpanContext
- SpanAttributes used by all span creation operations
- TracingConfiguration consumed by all tracing components
- MessageMetadataKeys used by interceptors for context propagation

## Testing Requirements

### Unit Tests

**TraceContext Tests** (`TraceContextTest.kt`)
- [ ] Test: toMetadataMap() produces correct key-value pairs
- [ ] Test: fromMetadata() reconstructs TraceContext accurately
- [ ] Test: fromMetadata() returns null for invalid/missing data
- [ ] Test: toSpanContext() creates valid OpenTelemetry SpanContext
- [ ] Test: fromSpanContext() captures all context information
- [ ] Test: Baggage is preserved through serialization round-trip

**TracingConfiguration Tests** (`TracingConfigurationTest.kt`)
- [ ] Test: Builder pattern produces correct configuration
- [ ] Test: Validation rejects invalid sample rates (<0 or >1)
- [ ] Test: Default configuration has sensible values
- [ ] Test: errorsOnly() disables all tracing flags
- [ ] Test: Custom attribute providers are preserved

**SpanAttributes Tests** (`SpanAttributesTest.kt`)
- [ ] Test: All constants are non-null and follow naming convention
- [ ] Test: No duplicate values across attributes
- [ ] Test: Axon-specific attributes have proper prefix

### Test Coverage Target
95%+ coverage for domain models (these are critical contracts)

## Acceptance Criteria
- [ ] TraceContext data class created with serialization methods
- [ ] SpanAttributes object defines all standard attribute keys
- [ ] TracingConfiguration supports all required options
- [ ] MessageMetadataKeys defines propagation keys
- [ ] All components are immutable or effectively immutable
- [ ] Comprehensive unit tests written and passing
- [ ] KDoc documentation complete for all public APIs
- [ ] No external dependencies beyond OpenTelemetry API and Axon
- [ ] Code passes detekt and ktlint checks

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (95%+ coverage)
- [ ] Integration with OpenTelemetry verified
- [ ] Code meets quality standards (detekt, ktlint)
- [ ] KDoc reviewed for clarity and completeness
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OpenTelemetry Trace API](https://opentelemetry.io/docs/instrumentation/java/manual/#traces)
- [Axon Message Metadata](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/message-intercepting#message-metadata)
- [Sentry Tracing Concepts](https://docs.sentry.io/product/sentry-basics/tracing/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)

## Notes
- Keep TraceContext serialization format stable for backwards compatibility
- Consider using sealed classes if TraceContext variants are needed in the future
- SpanAttributes should align with OpenTelemetry semantic conventions where possible
- TracingConfiguration should not depend on Sentry or OpenTelemetry implementation classes
- MessageMetadataKeys prefix prevents collisions with user metadata
- All string constants should be compile-time constants for performance
- Consider impact on message size when storing trace context in metadata
- Document any privacy considerations for payload capture settings

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
