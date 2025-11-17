# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This repository contains **axon-sentry-tracing**, a Kotlin library that integrates Sentry tracing and error monitoring with Axon Framework via OpenTelemetry. The library provides distributed tracing for event-sourced, CQRS applications with minimal configuration.

## Project Context

- **Language**: Kotlin 1.9.22+
- **JVM Target**: Java 17
- **Build Tool**: Gradle 8.5+ with Kotlin DSL
- **Primary Dependencies**:
  - Axon Framework 4.9.x
  - Sentry Java SDK 7.x
  - OpenTelemetry 1.33.x
  - Spring Boot 3.2.x (optional, for auto-configuration)

## Architecture Overview

The library operates through message interceptors that:
1. Intercept Axon commands, events, and queries
2. Create OpenTelemetry spans with Axon-specific attributes
3. Propagate trace context through message metadata
4. Export spans to Sentry for visualization

Key components:
- **Core Domain** (`io.github.axonsentry.tracing`): TraceContext, SpanAttributes, configuration models
- **Sentry Integration** (`io.github.axonsentry.sentry`): SpanExporter, Sentry bridge, initialization
- **Axon Interceptors** (`io.github.axonsentry.axon`): Message interceptors for commands, events, queries
- **Spring Boot** (`io.github.axonsentry.spring`): Auto-configuration and properties binding

## Development Workflow

### Building
```bash
./gradlew build
```

### Testing
```bash
./gradlew test
```

### Code Quality
```bash
./gradlew detekt ktlint
```

### Local Publishing
```bash
./gradlew publishToMavenLocal
```

## Code Standards

- **Kotlin Coding Conventions**: Follow official Kotlin style guide
- **detekt**: Zero warnings required
- **ktlint**: All files must pass linting
- **Test Coverage**: Minimum 85% for core components
- **KDoc**: All public APIs must be documented
- **Immutability**: Prefer immutable data structures
- **Null Safety**: Leverage Kotlin's null safety features

## Testing Strategy

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test interaction with Axon and OpenTelemetry
- **Spring Boot Tests**: Test auto-configuration and properties binding
- **Example Application**: Serves as integration test and documentation

Test libraries:
- JUnit 5 (Jupiter)
- Mockk (Kotlin mocking)
- AssertJ (fluent assertions)
- Axon Test (Axon-specific testing)

## Implementation Issues

Detailed technical documentation for each feature is located in `docs/issues/`:

- **Foundation** (001-004): Project setup, Gradle, domain model, Sentry integration
- **Core Tracing** (005-007): Command, event, and query interceptors
- **Integration** (008): Spring Boot auto-configuration
- **Examples** (009): Sample applications

Each issue contains:
- Overview and goals
- Technical requirements
- Implementation guidance with code examples
- Testing requirements
- Acceptance criteria
- Definition of done

See `docs/issues/README.md` for the complete index.

## Common Development Tasks

### Adding a New Interceptor

1. Create factory class in `io.github.axonsentry.axon`
2. Implement dispatch and handler interceptors
3. Add span attributes to `SpanAttributes` object
4. Register in Spring auto-configuration
5. Add configuration properties
6. Write comprehensive tests
7. Update documentation

### Adding New Span Attributes

1. Add constant to `SpanAttributes` object
2. Follow OpenTelemetry semantic conventions
3. Document attribute purpose in KDoc
4. Use attribute in appropriate interceptors
5. Add tests verifying attribute capture

### Modifying Configuration

1. Update `TracingConfiguration` data class
2. Update `AxonSentryTracingProperties` for Spring Boot
3. Add validation if needed
4. Update configuration metadata JSON
5. Document in README and user guide
6. Add tests for new configuration

## Directory Structure

```
axon-sentry-tracing/
├── docs/
│   ├── issues/              # Implementation documentation
│   ├── api/                 # Generated API docs
│   ├── guides/              # User guides
│   └── architecture/        # Architecture decisions
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── io/github/axonsentry/
│   │   │       ├── tracing/        # Core domain models
│   │   │       ├── config/         # Configuration
│   │   │       ├── sentry/         # Sentry integration
│   │   │       ├── axon/           # Axon interceptors
│   │   │       └── spring/         # Spring Boot support
│   │   └── resources/
│   │       └── META-INF/
│   │           └── spring/         # Auto-config registration
│   └── test/
│       ├── kotlin/          # Tests
│       └── resources/       # Test resources
├── examples/
│   └── spring-boot-demo/    # Example application
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── CLAUDE.md               # This file
```

## Dependencies Management

Dependencies are managed in `gradle.properties`:
- Keep versions centralized
- Document why each dependency is needed
- Use `api` scope for exposed dependencies (Axon, OTel, Sentry)
- Use `implementation` scope for internal dependencies
- Mark Spring Boot as `compileOnly` to make it optional

## Git Workflow

1. Create feature branch from main
2. Make atomic commits with clear messages
3. Run all tests and quality checks before pushing
4. Create PR with description referencing issue
5. Address review feedback
6. Squash merge to main

Commit message format:
```
feat: Add saga tracing support (#009)

- Implement SagaDispatchInterceptor
- Add saga-specific span attributes
- Update Spring auto-configuration
- Add integration tests

Closes #009
```

## OpenTelemetry Best Practices

- Use appropriate SpanKind (CLIENT, SERVER, PRODUCER, CONSUMER, INTERNAL)
- Follow semantic conventions where applicable
- Add Axon-specific attributes with `axon.` prefix
- Ensure spans are always ended (use try-finally or use {})
- Propagate context through metadata, not thread locals
- Use current context as parent by default

## Sentry Integration Notes

- Spans are batched for efficiency
- Root spans become Sentry transactions
- Child spans are nested appropriately
- Exceptions are captured and linked to spans
- Sampling is applied at trace level
- DSN can come from env var or config

## Performance Considerations

- Span creation is lightweight (~10-50μs)
- Metadata serialization adds minimal overhead
- Async batched export prevents blocking
- Sampling reduces overhead in production
- Payload capture is opt-in (can be large/sensitive)
- Filter spans before export when possible

## Debugging Tips

- Enable DEBUG logging: `io.github.axonsentry=DEBUG`
- Check health endpoint: `/actuator/health`
- Verify Sentry DSN configuration
- Use Sentry debug mode for troubleshooting
- Inspect message metadata for trace context
- Check OpenTelemetry span export logs

## Common Issues

**Issue**: Spans not appearing in Sentry
- Check Sentry DSN is configured
- Verify `enabled=true` in configuration
- Check sample rate is > 0
- Review Sentry exporter logs

**Issue**: Missing trace context in downstream events
- Verify dispatch interceptor is registered
- Check metadata enrichment is working
- Ensure parent context is active

**Issue**: High overhead
- Reduce sample rate
- Disable payload capture
- Filter spans at export

## Release Process

1. Update version in `gradle.properties`
2. Update CHANGELOG.md
3. Run full test suite
4. Build and test locally
5. Create release tag
6. Publish to Maven Central
7. Create GitHub release
8. Update documentation

## Resources

- [Axon Framework Docs](https://docs.axoniq.io/reference-guide/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Sentry Java SDK](https://docs.sentry.io/platforms/java/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Kotlin Language Guide](https://kotlinlang.org/docs/home.html)

## Questions?

See `docs/issues/README.md` for comprehensive implementation guidance, or review existing issues for patterns and examples.
