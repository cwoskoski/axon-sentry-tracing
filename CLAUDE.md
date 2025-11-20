# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

**axon-sentry-tracing** is a Kotlin library that integrates Sentry tracing and error monitoring with Axon Framework via OpenTelemetry. It provides distributed tracing for event-sourced, CQRS applications with minimal configuration.

## Project Status

**Current Phase:** Phase 1 - Core Integration (41.7% Complete)
**Implementation Status:** 10 of 41 issues complete
**Latest Completed:** Issue 010 - Trace Context Propagation
**Overall Progress:** 24.4%

This repository contains a production-ready multi-module project with:
- ✅ Phase 0 Complete: Foundation, domain model, Sentry integration, SpanFactory
- ✅ Issues 006-010 Complete: Command/Event/Query tracing, Spring Boot auto-config, W3C trace context propagation
- 🔄 Phase 1 In Progress: 5 of 12 issues complete (MVP path)

The project is organized into 41 detailed implementation issues in `docs/issues/` that provide step-by-step guidance for building the library.

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

- **Phase 0 (001-005):** Foundation & Setup ✅ COMPLETE
  - 001-005: All complete (Project setup, Gradle, domain model, Sentry integration, SpanFactory)

- **Phase 1 (006-017):** Core Integration ⭐ MVP (41.7% complete)
  - 006-010: Complete ✅ (Command/Event/Query tracing, Spring Boot auto-config, W3C trace context)
  - 011-017: Remaining (Attribute providers, sampling, error correlation, tests, docs, example app)

- **Phase 2 (018-022):** Advanced Tracing Features
- **Phase 3 (023-032):** Enterprise Features (sagas, sampling strategies, performance)
- **Phase 4 (033-040):** Production Readiness (security, performance, testing)
- **Phase 5 (041-045):** Documentation and Release Preparation

**Total Issues:** 41 (restructured from 52 on 2025-11-19)

Each issue includes:
- Technical requirements with dependencies
- Implementation guidance with code examples
- Testing requirements
- Acceptance criteria and definition of done

**Current Focus:** Phase 1 MVP completion - Issues 011-017 remaining.

## Issue Tracking & Commit Requirements

**CRITICAL:** When working on issues, you MUST follow these requirements:

### 1. STATUS.md Updates (Required for Every Issue)

Keep `docs/issues/STATUS.md` updated throughout the development lifecycle:

**When Starting an Issue:**
- Update issue status: "🔴 Not Started" → "🟡 In Progress"
- Update phase progress percentages
- Update overall progress metrics

**When Completing an Issue:**
- Update issue status: "🟡 In Progress" → "🟢 Completed"
- Update all progress metrics:
  - Overall progress (Completed count, percentage)
  - Phase progress (issue count, percentage)
- Verify assignee is set correctly
- Commit STATUS.md with implementation changes

**When Blocked:**
- Update issue status to "🔴 Blocked"
- Document blocker details in STATUS.md

### 2. Commit Message Requirements (Required for Every Commit)

**IMPORTANT:** Every commit related to an issue MUST reference the issue number.

**Format:**
```
<type>: <description> (#<issue-number>)

<detailed explanation>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

**Examples:**
```bash
# Good - References issue
feat: implement command tracing interceptor (#006)

# Good - References issue in docs
docs: update STATUS.md - Issue 010 complete

# Bad - Missing issue reference
feat: add command tracing

# Bad - Wrong format
feat: add command tracing (Issue 006)
```

**Commit Types:**
- `feat:` - New feature implementation
- `fix:` - Bug fix
- `docs:` - Documentation updates (including STATUS.md)
- `test:` - Test additions/modifications
- `refactor:` - Code refactoring
- `chore:` - Build/tooling changes

**Rules:**
1. ALWAYS include issue number in format `(#NNN)` in the first line
2. Use three-digit issue numbers: `#006`, not `#6`
3. STATUS.md updates should reference the issue in the commit message
4. Multi-issue commits should list all issues: `(#006, #007, #008)`
5. Always include Claude Code attribution footer

### 3. Work Session Checklist

At the end of each work session:
- [ ] All issue-related code changes committed with issue references
- [ ] STATUS.md updated with current progress
- [ ] STATUS.md changes committed with descriptive message
- [ ] Overall project progress percentage verified
- [ ] Phase progress percentage verified

### Why This Matters

- **Traceability:** Links commits to requirements and implementation decisions
- **Progress Visibility:** Stakeholders can track progress via STATUS.md
- **Documentation:** Git history becomes searchable by issue number
- **CI/CD Integration:** Issue references enable automated workflows
- **Project Management:** Clear connection between work done and issues

The STATUS.md file is the **single source of truth** for project progress tracking. Issue references in commits provide **end-to-end traceability** from requirement to implementation.

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
