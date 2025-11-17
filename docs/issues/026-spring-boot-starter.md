# Issue 026: Spring Boot Starter

**Phase:** Spring Boot Integration
**Priority:** High
**Complexity:** Small
**Status:** Not Started
**Dependencies:** 021, 022, 023, 024, 025

## Overview
Create Spring Boot starter module that aggregates all auto-configuration and dependencies for one-line dependency setup.

## Goals
- Create starter module
- Aggregate all dependencies
- Provide single dependency entry
- Document starter usage

## build.gradle.kts Example
```kotlin
dependencies {
    api(project(":sentry-tracing"))
    api(project(":sentry-tracing-spring-boot-autoconfigure"))
    api("io.sentry:sentry-spring-boot-starter")
    api("io.opentelemetry:opentelemetry-api")
}
```

## Acceptance Criteria
- [ ] Starter module created
- [ ] All dependencies included
- [ ] Works with single dependency

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing
- [ ] Changes committed

---
**Created:** 2025-11-17
