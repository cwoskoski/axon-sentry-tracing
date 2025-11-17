# Issue 030: Custom Annotations (@SentryTraced)

**Phase:** Advanced Features
**Priority:** Medium
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 028

## Overview
Implement custom annotations that allow developers to explicitly mark methods for tracing, add custom span names, and control tracing behavior declaratively.

## Goals
- Create @SentryTraced annotation
- Support custom span names
- Enable/disable tracing per method
- Add custom attributes via annotations
- Support AOP-based tracing

## Implementation Example

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SentryTraced(
    val spanName: String = "",
    val enabled: Boolean = true,
    val captureArguments: Boolean = false,
    val captureResult: Boolean = false
)

@Component
class OrderService {
    @SentryTraced(spanName = "ProcessVIPOrder", captureResult = true)
    fun processVipOrder(order: Order): Result {
        // Automatically traced with custom span name
    }
}
```

## Acceptance Criteria
- [ ] Annotation defined
- [ ] AOP processor implemented
- [ ] Custom span names work
- [ ] Argument capture supported

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing
- [ ] Changes committed

---
**Created:** 2025-11-17
