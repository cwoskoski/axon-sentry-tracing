# Issue 016: Basic Sampling Strategy

**Phase:** Core Integration
**Priority:** High
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 010

## Overview
Implement basic sampling strategies to control which traces are sent to Sentry, reducing costs and performance overhead in production while maintaining observability for important transactions.

## Goals
- Implement probability-based sampling
- Add rate-limiting sampler
- Support parent-based sampling for consistency
- Enable configuration-driven sampling rules
- Provide sampling decision logging
- Optimize sampling performance

## Technical Requirements

### Components to Create

1. **SamplingStra tegy** interface (`io.github.axonsentry.sampling.SamplingStrategy.kt`)
2. **ProbabilitySampler** (`io.github.axonsentry.sampling.ProbabilitySampler.kt`)
3. **RateLimitingSampler** (`io.github.axonsentry.sampling.RateLimitingSampler.kt`)
4. **CompositeSampler** (`io.github.axonsentry.sampling.CompositeSampler.kt`)

### Implementation Example

```kotlin
package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult

/**
 * Probability-based sampler that samples a fixed percentage of traces.
 */
class ProbabilitySampler(
    private val probability: Double
) : Sampler {

    init {
        require(probability in 0.0..1.0) { "Probability must be between 0.0 and 1.0" }
    }

    override fun shouldSample(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: MutableList<LinkData>
    ): SamplingResult {
        val hash = Math.abs(traceId.hashCode())
        val sample = (hash % 10000) / 10000.0 < probability

        return if (sample) {
            SamplingResult.recordAndSample()
        } else {
            SamplingResult.drop()
        }
    }

    override fun getDescription(): String = "ProbabilitySampler{$probability}"
}
```

## Testing Requirements

- [ ] Test: Probability sampler respects rate
- [ ] Test: Rate limiter enforces limits
- [ ] Test: Parent-based sampling consistent
- [ ] Test: Configuration applies correctly

## Acceptance Criteria
- [ ] Basic sampling strategies implemented
- [ ] Configuration-driven sampling
- [ ] Performance overhead minimal
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
