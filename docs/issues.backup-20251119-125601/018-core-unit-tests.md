# Issue 018: Core Unit Tests

**Phase:** Core Integration
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 010, 011, 012, 013, 014, 015, 016, 017

## Overview
Implement comprehensive unit test suite for all core tracing components, ensuring 85%+ code coverage and validating all edge cases, error scenarios, and configuration options.

## Goals
- Achieve 85%+ code coverage for core modules
- Test all message interceptors thoroughly
- Validate span creation and attributes
- Test trace context propagation
- Verify error handling and recovery
- Test configuration options
- Validate thread safety

## Testing Requirements

### Test Structure
```
src/test/kotlin/
└── io/github/axonsentry/
    ├── axon/
    │   ├── AxonSpanFactoryTest.kt
    │   ├── CommandTracingInterceptorTest.kt
    │   ├── EventTracingInterceptorTest.kt
    │   └── QueryTracingInterceptorTest.kt
    ├── tracing/
    │   ├── TraceContextPropagatorTest.kt
    │   ├── SpanNameGeneratorTest.kt
    │   └── AttributeApplierTest.kt
    ├── sampling/
    │   └── ProbabilitySamplerTest.kt
    └── error/
        └── ErrorCorrelatorTest.kt
```

## Acceptance Criteria
- [ ] 85%+ code coverage achieved
- [ ] All core components tested
- [ ] Edge cases covered
- [ ] Thread safety verified

## Definition of Done
- [ ] All tests implemented
- [ ] Coverage target met
- [ ] CI passing
- [ ] Changes committed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
