# Issue 022: Configuration Properties

**Phase:** Spring Boot Integration
**Priority:** High
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 021

## Overview
Define comprehensive Spring Boot configuration properties for all tracing features with sensible defaults, validation, and IDE auto-completion support.

## Goals
- Define all configuration properties
- Provide sensible defaults
- Add property validation
- Enable IDE auto-completion
- Document all properties
- Support environment-specific configs

## Implementation Example

```kotlin
package io.github.axonsentry.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

@ConfigurationProperties(prefix = "axon.sentry.tracing")
@Validated
data class SentryTracingProperties(
    /**
     * Enable/disable tracing globally.
     */
    var enabled: Boolean = true,

    /**
     * Sentry DSN for sending traces.
     */
    @NotBlank
    var dsn: String? = null,

    /**
     * Enable command tracing.
     */
    var traceCommands: Boolean = true,

    /**
     * Enable event tracing.
     */
    var traceEvents: Boolean = true,

    /**
     * Enable query tracing.
     */
    var traceQueries: Boolean = true,

    /**
     * Payload capture settings.
     */
    @NestedConfigurationProperty
    var capturePayloads: PayloadCaptureProperties = PayloadCaptureProperties(),

    /**
     * Sampling configuration.
     */
    @NestedConfigurationProperty
    var sampling: SamplingProperties = SamplingProperties(),

    /**
     * Performance settings.
     */
    @NestedConfigurationProperty
    var performance: PerformanceProperties = PerformanceProperties()
)

data class PayloadCaptureProperties(
    var commands: Boolean = false,
    var events: Boolean = false,
    var queries: Boolean = false,
    var maxLength: Int = 1000
)

data class SamplingProperties(
    @Min(0)
    @Max(1)
    var rate: Double = 1.0,
    var enableRateLimiting: Boolean = false,
    var maxTracesPerSecond: Int = 100
)

data class PerformanceProperties(
    var enableAsync: Boolean = true,
    var batchSize: Int = 100,
    var flushIntervalMs: Long = 5000
)
```

### application.yml Example
```yaml
axon:
  sentry:
    tracing:
      enabled: true
      dsn: ${SENTRY_DSN}
      trace-commands: true
      trace-events: true
      trace-queries: true
      capture-payloads:
        commands: false
        events: false
        queries: false
        max-length: 1000
      sampling:
        rate: 0.1
        enable-rate-limiting: true
        max-traces-per-second: 100
      performance:
        enable-async: true
        batch-size: 100
        flush-interval-ms: 5000
```

## Acceptance Criteria
- [ ] All properties defined
- [ ] Validation configured
- [ ] IDE auto-completion works
- [ ] Documentation complete

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing
- [ ] Documentation updated
- [ ] Changes committed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
