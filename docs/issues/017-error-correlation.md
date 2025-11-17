# Issue 017: Error Correlation

**Phase:** Core Integration
**Priority:** High
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 010, 011, 012, 013

## Overview
Implement automatic error correlation between Axon exceptions and Sentry error tracking, ensuring that errors are linked to their traces and enriched with Axon-specific context.

## Goals
- Automatically capture exceptions in spans
- Correlate Sentry errors with traces
- Add Axon-specific error context
- Support error fingerprinting
- Capture aggregate state on errors
- Track error recovery attempts

## Technical Requirements

### Components to Create

1. **ErrorCorrelator** (`io.github.axonsentry.error.ErrorCorrelator.kt`)
2. **AxonExceptionEnricher** (`io.github.axonsentry.error.AxonExceptionEnricher.kt`)
3. **ErrorFingerprintGenerator** (`io.github.axonsentry.error.ErrorFingerprintGenerator.kt`)

### Implementation Example

```kotlin
package io.github.axonsentry.error

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.messaging.Message

/**
 * Correlates exceptions with OpenTelemetry spans and Sentry errors.
 */
class ErrorCorrelator {

    /**
     * Records an exception in both the span and Sentry.
     */
    fun recordException(
        span: Span,
        exception: Throwable,
        message: Message<*>? = null
    ) {
        // Record in span
        span.recordException(exception)
        span.setStatus(StatusCode.ERROR, exception.message ?: "Error")

        // Capture in Sentry with correlation
        val event = SentryEvent(exception).apply {
            contexts.setTrace(createTraceContext(span))

            if (message != null) {
                setTag("axon.message_type", message.payloadType.simpleName)
                setTag("axon.message_id", message.identifier)
            }

            // Add Axon-specific context
            if (exception is CommandExecutionException) {
                setTag("axon.command_name", exception.commandMessage?.commandName)
                setTag("axon.aggregate_id", exception.aggregateIdentifier?.toString())
            }
        }

        Sentry.captureEvent(event)
    }

    private fun createTraceContext(span: Span): io.sentry.protocol.SentryTrace {
        val spanContext = span.spanContext
        return io.sentry.protocol.SentryTrace(
            spanContext.traceId,
            spanContext.spanId,
            null,
            "true"
        )
    }
}
```

## Testing Requirements

- [ ] Test: Exceptions recorded in spans
- [ ] Test: Sentry errors correlated with traces
- [ ] Test: Axon context added to errors
- [ ] Test: Error fingerprinting works

## Acceptance Criteria
- [ ] Errors automatically correlated
- [ ] Axon context enriches errors
- [ ] Trace context links errors to spans
- [ ] All tests passing

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing
- [ ] Documentation complete
- [ ] Changes committed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
