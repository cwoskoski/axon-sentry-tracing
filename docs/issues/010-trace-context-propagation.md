# Issue 014: Trace Context Propagation

**Phase:** Core Integration
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 010, 011, 012, 013

## Overview
Implement robust trace context propagation across Axon message boundaries, service boundaries, and asynchronous processing. This ensures distributed traces remain connected across commands, events, queries, sagas, and microservices.

## Goals
- Implement W3C Trace Context standard for interoperability
- Propagate context through Axon message metadata
- Support distributed tracing across services
- Handle async message processing
- Support saga correlation
- Implement baggage propagation for custom data
- Handle context inheritance in event upcasting

## Technical Requirements

### Components to Create

1. **TraceContextPropagator** (`io.github.axonsentry.tracing.TraceContextPropagator.kt`)
   - Purpose: Inject and extract trace context from message metadata
   - Implement W3C Trace Context format
   - Handle OpenTelemetry baggage
   - Support custom propagation headers

2. **MessageMetadataCarrier** (`io.github.axonsentry.tracing.MessageMetadataCarrier.kt`)
   - Purpose: Adapter for OpenTelemetry context propagation
   - Bridge Axon MetaData to OTel TextMapGetter/Setter
   - Handle type conversions

3. **DistributedTraceCorrelator** (`io.github.axonsentry.tracing.DistributedTraceCorrelator.kt`)
   - Purpose: Correlate traces across service boundaries
   - Generate correlation IDs
   - Link distributed transactions
   - Handle service mesh integration

### Implementation Example

```kotlin
package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import org.axonframework.messaging.Message
import org.axonframework.messaging.MetaData

/**
 * Propagates trace context through Axon message metadata using W3C Trace Context format.
 */
class TraceContextPropagator(
    private val propagator: TextMapPropagator
) {
    private val metadataGetter = MetaDataGetter()
    private val metadataSetter = MetaDataSetter()

    /**
     * Injects current trace context into message metadata.
     */
    fun <T> inject(message: Message<T>): Message<T> {
        val currentContext = Context.current()
        val mutableMetadata = message.metaData.toMutableMap()

        propagator.inject(currentContext, mutableMetadata, metadataSetter)

        return message.andMetaData(MetaData.from(mutableMetadata))
    }

    /**
     * Extracts trace context from message metadata.
     */
    fun <T> extract(message: Message<T>): Context {
        return propagator.extract(
            Context.current(),
            message.metaData,
            metadataGetter
        )
    }

    /**
     * Creates a remote span context from message metadata.
     */
    fun <T> extractSpanContext(message: Message<T>): SpanContext? {
        val context = extract(message)
        return Span.fromContext(context).spanContext.takeIf { it.isValid }
    }

    private class MetaDataGetter : TextMapGetter<MetaData> {
        override fun keys(carrier: MetaData): Iterable<String> {
            return carrier.keys
        }

        override fun get(carrier: MetaData?, key: String): String? {
            return carrier?.get(key)?.toString()
        }
    }

    private class MetaDataSetter : TextMapSetter<MutableMap<String, Any>> {
        override fun set(carrier: MutableMap<String, Any>?, key: String, value: String) {
            carrier?.put(key, value)
        }
    }
}
```

## Testing Requirements

### Unit Tests
- [ ] Test: Inject trace context into metadata
- [ ] Test: Extract trace context from metadata
- [ ] Test: W3C format compatibility
- [ ] Test: Baggage propagation
- [ ] Test: Handle missing context gracefully
- [ ] Test: Multiple propagation formats

### Integration Tests
- [ ] Integration: Context propagates across commands
- [ ] Integration: Context propagates across events
- [ ] Integration: Context propagates across queries
- [ ] Integration: Distributed tracing across services
- [ ] Integration: Async processing maintains context
- [ ] Integration: Saga correlation

### Test Coverage Target
95%+ coverage (critical for distributed tracing)

## Acceptance Criteria
- [ ] W3C Trace Context implemented
- [ ] Context propagates across all message types
- [ ] Distributed tracing works across services
- [ ] Baggage propagation supported
- [ ] All tests passing
- [ ] Interoperability verified

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing (95%+ coverage)
- [ ] W3C compliance verified
- [ ] Documentation complete
- [ ] Changes committed

## Resources
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [OpenTelemetry Context Propagation](https://opentelemetry.io/docs/instrumentation/java/manual/#context-propagation)
- [Axon Metadata](https://docs.axoniq.io/reference-guide/axon-framework/messaging-concepts/messages#message-metadata)

## Notes
- Critical for distributed tracing
- Must maintain backwards compatibility
- Consider service mesh integration
- Performance critical - optimize metadata serialization

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
