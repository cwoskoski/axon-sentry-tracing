# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

**axon-sentry-tracing** is a Kotlin library that integrates Sentry tracing and error monitoring with Axon Framework via OpenTelemetry. It provides distributed tracing for event-sourced, CQRS applications with minimal configuration.

## Project Status

**Current Phase:** Phase 0 - Foundation & Setup (80% Complete)
**Implementation Status:** 4 of 5 foundation issues complete, ready for Issue 005
**Next Task:** Issue 005 - Sentry Axon SpanFactory

This repository contains a buildable multi-module project with core domain model and Sentry integration. Issues 001-004 are complete. The project is organized into 41 detailed implementation issues in `docs/issues/` that provide step-by-step guidance for building the library.

## Architecture Overview

The library operates through Axon message interceptors that:
1. Intercept commands, events, and queries
2. Create OpenTelemetry spans with Axon-specific attributes
3. Propagate trace context through message metadata
4. Export spans to Sentry for visualization

**Key Module Structure:**
- `sentry-tracing` - Core library with interceptors and OTel-Sentry bridge
- `sentry-tracing-spring-boot-autoconfigure` - Spring Boot auto-configuration
- `sentry-tracing-spring-boot-starter` - Aggregated starter dependency
- `sentry-tracing-example` - Demo application

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run all quality checks
./gradlew detekt ktlint

# Run specific module tests
./gradlew :sentry-tracing:test

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Run example application (once implemented)
./gradlew :sentry-tracing-example:bootRun
```

## Technology Stack

- **Language:** Kotlin 1.9.22+, targeting JVM 17
- **Build:** Gradle 8.5+ with Kotlin DSL
- **Key Dependencies:** Axon Framework 4.9.x, Sentry SDK 7.x, OpenTelemetry 1.33.x, Spring Boot 3.2.x (optional)
- **Testing:** JUnit 5, Mockk, AssertJ, Axon Test

## Code Standards

- **Kotlin style:** Follow official Kotlin coding conventions
- **Quality gates:** Zero detekt/ktlint warnings required
- **Test coverage:** Minimum 85% for core components
- **Documentation:** All public APIs must have KDoc
- **Immutability:** Prefer immutable data structures, leverage Kotlin's null safety

## Implementation Guidance

Detailed implementation documentation is in `docs/issues/` organized into phases:

- **Phase 0 (001-005):** Foundation & Setup
  - 001-004: Complete ✅ (Project setup, Gradle, domain model, Sentry integration)
  - 005: **Sentry Axon SpanFactory** ← NEXT TO IMPLEMENT

- **Phase 1 (006-017):** Core Integration ⭐ MVP
  - 006-008: Command, Event, Query message tracing
  - 009: Comprehensive Spring Boot auto-configuration
  - 010-017: Context propagation, sampling, testing, example app

- **Phase 2 (018-022):** Enhanced Spring Boot features
- **Phase 3 (023-032):** Advanced features (sagas, sampling strategies)
- **Phase 4 (033-040):** Production readiness (security, performance, testing)
- **Phase 5 (041-045):** Documentation and release preparation

**Total Issues:** 41 (was 52, restructured 2025-11-19)

Each issue includes:
- Technical requirements with dependencies
- Implementation guidance with code examples
- Testing requirements
- Acceptance criteria and definition of done

**Current Focus:** Complete Issue 005 to enable Phase 1 interceptor implementation.

## Issue Tracking

**IMPORTANT:** When working on issues, you MUST keep `docs/issues/STATUS.md` updated:

1. **When Starting an Issue:**
   - Update the issue status from "🔴 Not Started" to "🟡 In Progress"
   - Update the "Current Sprint" section to list the active issue
   - Update the overall progress metrics

2. **When Completing an Issue:**
   - Update the issue status from "🟡 In Progress" to "🟢 Completed"
   - Move the issue to "Completed This Sprint" section
   - Update all progress metrics (overall, phase, priority, complexity)
   - Update progress bars and completion percentages
   - Add entry to "Recent Activity" section with completion date

3. **When Blocked:**
   - Update issue status to "🔴 Blocked"
   - Add details to "Blockers & Risks" section
   - Document blocker details and required resolution

4. **After Each Work Session:**
   - Review and update the "Last Updated" date
   - Verify all metrics are accurate
   - Commit STATUS.md changes along with implementation changes

The STATUS.md file is the single source of truth for project progress tracking. Keeping it updated ensures accurate visibility into project health and completion status.

## Common Development Patterns

### Adding a New Interceptor

1. Create factory class in `io.github.axonsentry.axon`
2. Implement dispatch and handler interceptor methods
3. Add span attributes to `SpanAttributes` object
4. Register in Spring auto-configuration
5. Add configuration properties
6. Write unit and integration tests

### Span Creation Pattern

```kotlin
// Spans follow OpenTelemetry conventions
val span = tracer.spanBuilder("Command: ${commandName}")
    .setSpanKind(SpanKind.CLIENT)
    .setAttribute("axon.message.type", "command")
    .setAttribute("axon.message.id", messageId)
    .startSpan()

try {
    return span.makeCurrent().use { scope ->
        // Execute message handling
    }
} catch (e: Exception) {
    span.recordException(e)
    span.setStatus(StatusCode.ERROR)
    throw e
} finally {
    span.end()
}
```

## Testing Strategy

- **Unit tests:** Test components in isolation with Mockk
- **Integration tests:** Test Axon + OpenTelemetry + Sentry integration
- **Spring Boot tests:** Test auto-configuration and properties binding
- **Example app:** Serves as integration test and documentation

## OpenTelemetry Best Practices

- Use appropriate SpanKind (CLIENT, SERVER, PRODUCER, CONSUMER, INTERNAL)
- Follow semantic conventions where applicable
- Add Axon-specific attributes with `axon.` prefix
- Always end spans (use try-finally or `.use {}`)
- Propagate context through metadata, not ThreadLocals
- Batch span exports for efficiency

## Performance Considerations

- Span creation: ~10-50μs per span
- Async batched export prevents blocking
- Sampling reduces overhead in production
- Payload capture is opt-in (can be large/sensitive)

## Debugging

- Enable debug logging: `io.github.axonsentry=DEBUG`
- Verify Sentry DSN configuration
- Check message metadata for trace context
- Review OpenTelemetry span export logs

## Git Workflow

1. Create feature branch from `main`
2. Make atomic commits with clear messages
3. Run all tests and quality checks before pushing
4. Create PR with description referencing issue
5. Squash merge to main after approval

**Commit message format:**
```
feat: Add saga tracing support (#009)

- Implement SagaDispatchInterceptor
- Add saga-specific span attributes
- Update Spring auto-configuration
```

## Resources

- [Axon Framework Docs](https://docs.axoniq.io/reference-guide/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Sentry Java SDK](https://docs.sentry.io/platforms/java/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)

For comprehensive implementation guidance, see `docs/issues/README.md`.
