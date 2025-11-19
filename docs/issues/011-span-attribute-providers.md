# Issue 015: Span Attribute Providers

**Phase:** Core Integration
**Priority:** High
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 010

## Overview
Implement an extensible attribute provider system that allows users to add custom attributes to spans without modifying core library code. This enables domain-specific metadata, business context, and custom tags to enrich traces.

## Goals
- Define attribute provider SPI
- Implement built-in providers for common use cases
- Support provider ordering and prioritization
- Enable conditional attribute application
- Provide type-safe attribute APIs
- Document extension patterns

## Technical Requirements

### Components to Create

1. **AttributeProvider** interface (`io.github.axonsentry.spi.AttributeProvider.kt`)
2. **CompositeAttributeProvider** (`io.github.axonsentry.tracing.CompositeAttributeProvider.kt`)
3. **MetadataAttributeProvider** (`io.github.axonsentry.providers.MetadataAttributeProvider.kt`)
4. **CorrelationIdAttributeProvider** (`io.github.axonsentry.providers.CorrelationIdAttributeProvider.kt`)

### Implementation Example

```kotlin
package io.github.axonsentry.spi

import org.axonframework.messaging.Message

/**
 * SPI for providing custom attributes to spans.
 */
interface AttributeProvider {
    /**
     * Provides attributes for the given message.
     * Returns a map of attribute keys to values.
     */
    fun provideAttributes(message: Message<*>): Map<String, Any>

    /**
     * Priority for applying this provider (higher = earlier).
     */
    fun priority(): Int = 0
}
```

## Testing Requirements

- [ ] Test: Custom attribute providers work
- [ ] Test: Provider ordering respected
- [ ] Test: Multiple providers compose correctly

## Acceptance Criteria
- [ ] SPI defined and documented
- [ ] Built-in providers implemented
- [ ] Extension example provided
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
