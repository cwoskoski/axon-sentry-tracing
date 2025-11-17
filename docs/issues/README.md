# Axon-Sentry-Tracing Implementation Issues

This directory contains comprehensive technical documentation for implementing the axon-sentry-tracing library. Each issue represents a discrete unit of work with detailed implementation guidance, code examples, testing requirements, and acceptance criteria.

## Project Overview

**axon-sentry-tracing** is a Kotlin library that integrates Sentry's tracing and error monitoring with Axon Framework via OpenTelemetry. It provides distributed tracing across commands, events, queries, sagas, and event processors, with seamless integration into Sentry's performance monitoring UI.

### Key Features
- OpenTelemetry-based distributed tracing
- Sentry integration for error tracking and performance monitoring
- Automatic trace propagation through Axon message metadata
- Support for commands, events, queries, sagas, and event processors
- Spring Boot auto-configuration for zero-config setup
- Configurable payload capture and sampling
- Health monitoring and diagnostics

## Implementation Phases

### Phase 1: Foundation (Issues 001-004)
Core infrastructure and integration setup.

- **[001: Project Setup](./001-project-setup.md)** - Repository structure, Git configuration, documentation framework
- **[002: Gradle Configuration](./002-gradle-configuration.md)** - Build system, dependencies, plugins, publishing
- **[003: Core Domain Model](./003-core-domain-model.md)** - TraceContext, SpanAttributes, TracingConfiguration, metadata keys
- **[004: OpenTelemetry-Sentry Integration](./004-opentelemetry-sentry-integration.md)** - SpanExporter, Sentry bridge, initialization

### Phase 2: Core Tracing (Issues 005-007)
Implement tracing for Axon message types.

- **[005: Command Tracing Interceptor](./005-command-tracing-interceptor.md)** - Command dispatch and handler tracing
- **[006: Event Tracing Interceptor](./006-event-tracing-interceptor.md)** - Event publication and processor tracing
- **[007: Query Tracing Interceptor](./007-query-tracing-interceptor.md)** - Query dispatch, handler, and subscription tracing

### Phase 3: Integration (Issue 008)
Framework integration and ease of use.

- **[008: Spring Boot Auto-Configuration](./008-spring-boot-autoconfiguration.md)** - Auto-config, properties, health indicators

### Phase 4: Advanced Features (Issues 009-012)
Enhanced capabilities and production readiness.

- **009: Saga Tracing** - Saga lifecycle and correlation tracing
- **010: Deadletter Queue Integration** - DLQ event tracking and error correlation
- **011: Testing Infrastructure** - Test fixtures, integration test support, mock providers
- **012: Documentation and Examples** - User guide, API docs, sample applications

### Phase 5: Production Readiness (Issues 013-015)
Performance, reliability, and operational concerns.

- **013: Performance Optimization** - Batch processing, sampling strategies, overhead reduction
- **014: Metrics and Monitoring** - Instrumentation metrics, export health, diagnostics
- **015: Release Preparation** - Versioning, changelog, publishing pipeline, migration guide

## Dependency Graph

```
001 (Project Setup)
  └─> 002 (Gradle Config)
        └─> 003 (Domain Model)
              ├─> 004 (OTel-Sentry Bridge)
              │     ├─> 005 (Command Tracing)
              │     ├─> 006 (Event Tracing)
              │     └─> 007 (Query Tracing)
              │           └─> 008 (Spring Boot)
              │                 ├─> 009 (Saga Tracing)
              │                 ├─> 010 (DLQ Integration)
              │                 └─> 011 (Testing)
              │                       └─> 012 (Documentation)
              │                             └─> 013 (Performance)
              │                                   └─> 014 (Metrics)
              │                                         └─> 015 (Release)
```

## Issue Template Structure

Each issue follows a consistent template:

1. **Header** - Phase, priority, complexity, status, dependencies
2. **Overview** - Clear description of what the issue accomplishes
3. **Goals** - Specific objectives to achieve
4. **Technical Requirements** - Components to create, dependencies, configuration
5. **Implementation Guidance** - Step-by-step approach with code examples
6. **Integration Points** - How this integrates with other components
7. **Testing Requirements** - Unit tests, integration tests, coverage targets
8. **Acceptance Criteria** - Specific, measurable criteria for completion
9. **Definition of Done** - Comprehensive checklist for issue completion
10. **Resources** - Links to relevant documentation
11. **Notes** - Additional context, gotchas, considerations

## Getting Started

### For Implementers

1. **Start with Phase 1** - Foundation issues must be completed in order
2. **Read Dependencies** - Each issue lists prerequisite issues
3. **Follow the Template** - Use provided code examples as starting points
4. **Run Tests** - Ensure all tests pass before marking issue complete
5. **Update Documentation** - Keep docs in sync with implementation
6. **Mark Complete** - Update issue status when Definition of Done is met

### For Project Managers

1. **Track Progress** - Use issue status field to monitor completion
2. **Assign Work** - Use "Assigned To" field for ownership
3. **Manage Dependencies** - Ensure prerequisite issues complete first
4. **Review Acceptance Criteria** - Verify criteria met before closing
5. **Monitor Complexity** - Balance team capacity with issue complexity

### For Reviewers

1. **Check Acceptance Criteria** - Verify all criteria are met
2. **Review Tests** - Ensure coverage targets achieved
3. **Validate Integration** - Confirm integration points work correctly
4. **Check Code Quality** - Verify detekt and ktlint pass
5. **Review Documentation** - Ensure KDoc is complete and clear

## Complexity Estimation

- **Small** - 1-3 days, single component, minimal dependencies
- **Medium** - 3-7 days, multiple components, moderate integration
- **Large** - 7-14 days, complex integration, extensive testing
- **XLarge** - 14+ days, architectural changes, cross-cutting concerns

## Priority Levels

- **Critical** - Blocks other work, core functionality, must complete first
- **High** - Important for MVP, key features, significant value
- **Medium** - Enhances usability, nice-to-have features
- **Low** - Optional improvements, future enhancements

## Status Values

- **Not Started** - Issue not yet begun
- **In Progress** - Active development underway
- **Blocked** - Waiting on dependencies or external factors
- **In Review** - Implementation complete, under code review
- **Testing** - Implementation and review complete, testing in progress
- **Complete** - All Definition of Done items checked, merged to main

## Technology Stack

### Core
- **Language**: Kotlin 1.9.22+
- **JVM Target**: Java 17
- **Build Tool**: Gradle 8.5+ with Kotlin DSL

### Dependencies
- **Axon Framework**: 4.9.x - Event sourcing and CQRS framework
- **Sentry Java SDK**: 7.x - Error tracking and performance monitoring
- **OpenTelemetry**: 1.33.x - Distributed tracing instrumentation
- **Spring Boot**: 3.2.x - Auto-configuration and integration (optional)

### Testing
- **JUnit 5**: Unit testing framework
- **Mockk**: Kotlin-friendly mocking library
- **AssertJ**: Fluent assertions
- **Axon Test**: Axon Framework testing support

### Code Quality
- **detekt**: Static code analysis
- **ktlint**: Kotlin linting
- **Gradle wrapper**: Reproducible builds

## Development Workflow

1. **Branch** - Create feature branch from main
2. **Implement** - Follow implementation guidance in issue
3. **Test** - Write and run unit and integration tests
4. **Lint** - Run `./gradlew detekt ktlint`
5. **Build** - Run `./gradlew build` and ensure success
6. **Commit** - Make atomic commits with clear messages
7. **Push** - Push branch to origin
8. **Review** - Create PR and request review
9. **Merge** - Merge to main after approval
10. **Update** - Mark issue as Complete

## Code Quality Standards

All code must:
- Pass detekt static analysis with zero warnings
- Pass ktlint formatting checks
- Achieve minimum test coverage targets (specified per issue)
- Include comprehensive KDoc for public APIs
- Follow Kotlin coding conventions
- Handle errors gracefully
- Log appropriately (debug, info, warn, error)

## Testing Standards

All implementations must include:
- Unit tests for each component (80%+ coverage minimum)
- Integration tests for cross-component functionality
- Edge case and error handling tests
- Performance tests for critical paths
- Test documentation (what is being tested and why)

## Documentation Standards

All public APIs must include:
- KDoc describing purpose and behavior
- Parameter descriptions with types and constraints
- Return value descriptions
- Exception documentation (@throws)
- Usage examples where helpful
- Since tags for version tracking

## Contributing

When implementing an issue:

1. **Read Thoroughly** - Understand all sections before starting
2. **Ask Questions** - Clarify ambiguities before implementing
3. **Follow Examples** - Use provided code as templates
4. **Test Comprehensively** - Exceed minimum coverage when possible
5. **Document Everything** - Future you will thank present you
6. **Review Your Own Work** - Self-review before requesting review
7. **Update Issue** - Keep status and notes current

## Resources

### Axon Framework
- [Axon Reference Guide](https://docs.axoniq.io/reference-guide/)
- [Axon Framework GitHub](https://github.com/AxonFramework/AxonFramework)
- [Axon University](https://university.axoniq.io/)

### OpenTelemetry
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [OTel Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OTel Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)

### Sentry
- [Sentry Java SDK](https://docs.sentry.io/platforms/java/)
- [Sentry Performance Monitoring](https://docs.sentry.io/product/performance/)
- [Sentry OpenTelemetry](https://docs.sentry.io/platforms/java/performance/instrumentation/opentelemetry/)

### Kotlin
- [Kotlin Language Guide](https://kotlinlang.org/docs/home.html)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Kotlin for Java Developers](https://kotlinlang.org/docs/java-to-kotlin-interop.html)

### Spring Boot
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Boot Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)

## Project Structure

```
axon-sentry-tracing/
├── docs/
│   ├── issues/              # This directory - implementation issues
│   ├── api/                 # Generated API documentation
│   ├── guides/              # User guides and tutorials
│   └── architecture/        # Architecture decision records
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/github/axonsentry/
│   │   │       ├── tracing/        # Core domain models (Issue 003)
│   │   │       ├── config/         # Configuration classes (Issue 003)
│   │   │       ├── sentry/         # Sentry integration (Issue 004)
│   │   │       ├── axon/           # Axon interceptors (Issues 005-007)
│   │   │       └── spring/         # Spring Boot support (Issue 008)
│   │   └── resources/
│   │       └── META-INF/
│   │           └── spring/         # Spring auto-config registration
│   └── test/
│       ├── kotlin/          # Unit and integration tests
│       └── resources/       # Test resources and fixtures
├── gradle/                  # Gradle wrapper
├── build.gradle.kts        # Build configuration (Issue 002)
├── settings.gradle.kts     # Project settings (Issue 002)
├── README.md               # Project overview
└── CLAUDE.md              # Claude Code guidance (Issue 001)
```

## Success Metrics

The project will be considered successful when:

- [ ] All Phase 1-3 issues completed (minimal viable product)
- [ ] Test coverage > 85% across all modules
- [ ] Zero detekt or ktlint violations
- [ ] Spans visible in Sentry UI
- [ ] Spring Boot auto-configuration works
- [ ] Published to Maven Central
- [ ] Documentation complete and clear
- [ ] At least one sample application
- [ ] Performance overhead < 5% in typical scenarios

## Support and Questions

For questions about specific issues:
1. Review the issue documentation thoroughly
2. Check linked resources for additional context
3. Review related issues for patterns
4. Consult Axon, OpenTelemetry, or Sentry documentation
5. Ask for clarification in issue comments

## License

This project is licensed under the Apache License 2.0. See LICENSE file for details.

---

**Last Updated**: 2025-11-17
**Total Issues**: 8 (5 created, 7 planned)
**Phase**: Foundation (in progress)
**Status**: Issue documentation in progress
