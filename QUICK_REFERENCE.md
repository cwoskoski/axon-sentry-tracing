# Axon-Sentry-Tracing Quick Reference

## Project At-A-Glance

| Attribute | Value |
|-----------|-------|
| **Project Name** | axon-sentry-tracing |
| **Description** | Sentry distributed tracing integration for Axon Framework using OpenTelemetry |
| **Language** | Kotlin |
| **Build Tool** | Gradle (Kotlin DSL) |
| **License** | Apache 2.0 |
| **Min Java Version** | 17 |
| **Total Issues** | 52 |
| **Estimated Timeline** | 8-12 weeks |
| **MVP Timeline** | 3-4 weeks |

---

## Phase Summary

| Phase | Name | Issues | Duration | Key Deliverable |
|-------|------|--------|----------|-----------------|
| 0 | Foundation & Setup | 001-008 | 1 week | Buildable project |
| 1 | Core Integration | 009-020 | 2-3 weeks | Working MVP |
| 2 | Spring Boot | 021-028 | 1-2 weeks | Auto-configuration |
| 3 | Advanced Features | 029-038 | 2-3 weeks | Enterprise features |
| 4 | Production Ready | 039-046 | 1-2 weeks | Quality assured |
| 5 | Documentation | 047-052 | 1 week | Release ready |

---

## Module Structure

```
axon-sentry-tracing/
├── sentry-tracing/                          # Core library
│   ├── src/main/kotlin/
│   │   └── io/axoniq/extensions/sentry/
│   │       ├── SentrySpanFactory.kt
│   │       ├── SentryOpenTelemetryConfiguration.kt
│   │       ├── SpanAttributeProvider.kt
│   │       └── sampling/
│   └── build.gradle.kts
│
├── sentry-tracing-spring-boot-autoconfigure/ # Auto-config
│   ├── src/main/kotlin/
│   │   └── io/axoniq/extensions/sentry/springboot/
│   │       ├── SentryTracingAutoConfiguration.kt
│   │       └── SentryTracingProperties.kt
│   └── build.gradle.kts
│
├── sentry-tracing-spring-boot-starter/       # Starter
│   └── build.gradle.kts (dependency aggregation only)
│
├── sentry-tracing-example/                   # Demo app
│   └── src/main/kotlin/
│       └── io/axoniq/extensions/sentry/example/
│
├── build.gradle.kts                          # Root build
├── settings.gradle.kts                       # Module config
└── gradle/libs.versions.toml                 # Version catalog
```

---

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Kotlin | 1.9.22+ | Language |
| Axon Framework | 4.9.1+ | CQRS/ES framework |
| Sentry Java SDK | 7.2.0+ | Error tracking & tracing |
| OpenTelemetry SDK | 1.34.1+ | Tracing instrumentation |
| Spring Boot | 2.7.x / 3.x | Auto-configuration (optional) |
| JUnit 5 | 5.10.x | Testing |
| Mockk | 1.13.x | Kotlin mocking |

---

## Critical Path Issues (MVP)

Must be completed in order:

1. **001** - Project Structure
2. **002** - Root Build Config
3. **003** - Core Module Build
4. **009** - Sentry OpenTelemetry Integration
5. **010** - Axon SpanFactory Implementation
6. **011** - Command Tracing
7. **012** - Query Tracing
8. **013** - Event Tracing
9. **019** - Basic Integration Testing
10. **020** - MVP Documentation

**Critical Path Duration:** 3-4 weeks

---

## Issue Priority Breakdown

| Priority | Count | Issues |
|----------|-------|--------|
| Critical | 18 | 001-003, 009-013, 021-024, 039-040, 046-047 |
| High | 22 | 004-007, 014, 017, 019-020, 025-028, 029-030, 041-043, 045, 048-050 |
| Medium | 10 | 008, 016, 018, 026-027, 031-034, 044, 051-052 |
| Low | 2 | 035-038 (some) |

---

## Complexity Breakdown

| Complexity | Story Points | Count | Total Points |
|------------|--------------|-------|--------------|
| Small | 2 | 7 | 14 |
| Medium | 5 | 32 | 160 |
| Large | 13 | 11 | 143 |
| XLarge | 21 | 2 | 42 |
| **TOTAL** | | **52** | **359** |

---

## Key Technical Decisions

1. **Build System:** Gradle with Kotlin DSL
2. **Java Version:** Java 17 minimum
3. **Kotlin Version:** 1.9.22+
4. **Spring Boot Support:** 2.7.x and 3.x
5. **Testing:** JUnit 5 + Mockk
6. **License:** Apache 2.0
7. **Trace Propagation:** W3C Trace Context (primary)
8. **Sampling:** Intelligent with error-based default
9. **Artifact Hosting:** Maven Central
10. **Documentation:** GitHub Pages

---

## Version Milestones

| Version | Milestone | Timeline | Scope |
|---------|-----------|----------|-------|
| v0.1.0-SNAPSHOT | MVP | Week 4 | Phase 0-1 complete |
| v0.2.0-SNAPSHOT | Spring Boot | Week 6 | Phase 2 complete |
| v0.5.0-SNAPSHOT | Feature Complete | Week 9 | Phase 3 complete |
| v1.0.0-RC1 | Release Candidate | Week 11 | Phase 4 complete |
| v1.0.0 | General Availability | Week 12 | All phases complete |

---

## Success Criteria

### MVP (v0.1.0)
- [ ] Commands, queries, events traced to Sentry
- [ ] Trace context propagates correctly
- [ ] Errors correlated with traces
- [ ] Example application demonstrates functionality
- [ ] Basic integration tests passing

### Spring Boot Ready (v0.2.0)
- [ ] Zero-config Spring Boot integration
- [ ] Configuration via application.yml
- [ ] Starter module published
- [ ] Spring Boot example working

### Production Ready (v1.0.0)
- [ ] 80%+ test coverage
- [ ] Performance overhead < 5%
- [ ] No critical security vulnerabilities
- [ ] Comprehensive documentation
- [ ] Maven Central artifact published

---

## Development Workflow

### 1. Pick an Issue
- Check dependencies are met
- Assign issue to yourself
- Move to "In Progress" on project board

### 2. Implementation
- Create feature branch: `feature/XXX-short-description`
- Follow coding standards (Kotlin conventions)
- Write tests (TDD encouraged)
- Update documentation as needed

### 3. Testing
- Unit tests pass locally
- Integration tests pass (if applicable)
- Code coverage meets target (80%+)
- Manual testing in example app

### 4. Pull Request
- Create PR with issue reference
- Fill out PR template
- Request review from team
- Address feedback
- CI must pass

### 5. Merge
- Squash merge to main
- Delete feature branch
- Close issue
- Update project board

---

## Build Commands

```bash
# Build entire project
./gradlew build

# Run tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Code coverage report
./gradlew jacocoTestReport

# Run example application
./gradlew :sentry-tracing-example:bootRun

# Publish to local Maven
./gradlew publishToMavenLocal

# Check dependencies
./gradlew dependencies

# Code style check
./gradlew detekt

# Format code
./gradlew ktlintFormat
```

---

## Test Commands

```bash
# Run unit tests only
./gradlew test

# Run integration tests only
./gradlew integrationTest

# Run specific test class
./gradlew test --tests "SentrySpanFactoryTest"

# Run tests with coverage
./gradlew test jacocoTestReport

# Run tests in continuous mode
./gradlew test --continuous

# Run performance tests
./gradlew jmh
```

---

## Configuration Examples

### Programmatic Configuration

```kotlin
val configuration = SentryTracingConfiguration(
    dsn = "https://examplePublicKey@o0.ingest.sentry.io/0",
    environment = "production",
    release = "1.0.0",
    tracesSampleRate = 1.0,
    enabled = true
)

val spanFactory = SentrySpanFactory(
    tracer = OpenTelemetry.get().getTracer("axon-sentry"),
    configuration = configuration
)

// Register with Axon
Configurer.defaultConfiguration()
    .configureSpanFactory { spanFactory }
    .start()
```

### Spring Boot Configuration

```yaml
axon:
  sentry:
    enabled: true
    dsn: https://examplePublicKey@o0.ingest.sentry.io/0
    environment: production
    release: 1.0.0
    trace-sample-rate: 1.0
    profiles-sample-rate: 0.0
    enable-command-tracing: true
    enable-query-tracing: true
    enable-event-tracing: true

    # Sampling configuration
    sampling:
      always-sample-errors: true
      latency-threshold-ms: 1000
      per-message-type:
        CreateOrderCommand: 1.0
        OrderCreatedEvent: 0.5
```

---

## Common Issues and Solutions

### Issue: Spans not appearing in Sentry
**Solution:**
1. Verify DSN is correct
2. Check Sentry project has tracing enabled
3. Verify sample rate > 0
4. Check network connectivity
5. Look for errors in logs

### Issue: Trace context not propagating
**Solution:**
1. Verify W3C Trace Context propagator configured
2. Check message metadata includes trace headers
3. Verify SpanFactory registered with Axon
4. Check for async boundaries losing context

### Issue: Performance overhead too high
**Solution:**
1. Enable sampling (reduce sample rate)
2. Use async span export
3. Reduce attribute extraction
4. Disable tracing for high-volume events
5. Profile and optimize hot paths

### Issue: Spring Boot auto-configuration not activating
**Solution:**
1. Check Axon and Sentry on classpath
2. Verify `axon.sentry.enabled` not set to false
3. Check Spring Boot version compatibility
4. Review auto-configuration report: `--debug`
5. Check for bean definition conflicts

---

## Code Quality Standards

### Code Coverage
- **Core Module:** 85%+ line coverage
- **Autoconfigure Module:** 80%+ line coverage
- **Overall:** 80%+ line coverage
- **Critical Paths:** 100% coverage

### Code Style
- Follow Kotlin coding conventions
- Use ktlint for formatting
- Use detekt for static analysis
- Max line length: 120 characters
- Meaningful variable/function names

### Documentation
- All public APIs have KDoc
- Include code examples in documentation
- Document exceptions thrown
- Explain parameters and return values
- Include @since version tags

### Testing
- Unit tests for all public APIs
- Integration tests for key scenarios
- Test error cases and edge cases
- Use descriptive test names
- Follow AAA pattern (Arrange, Act, Assert)

---

## CI/CD Pipeline

### GitHub Actions Workflow

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'

      - name: Build
        run: ./gradlew build

      - name: Test
        run: ./gradlew test integrationTest

      - name: Code Coverage
        run: ./gradlew jacocoTestReport

      - name: Upload Coverage
        uses: codecov/codecov-action@v3

      - name: Publish Artifacts
        if: github.ref == 'refs/heads/main'
        run: ./gradlew publish
```

### Quality Gates

All must pass before merge:
- [ ] Build successful
- [ ] All tests passing
- [ ] Code coverage ≥ 80%
- [ ] No new detekt violations
- [ ] Code review approved
- [ ] Documentation updated

---

## Release Checklist

### Pre-Release
- [ ] All planned issues completed
- [ ] All tests passing
- [ ] Code coverage targets met
- [ ] Documentation up to date
- [ ] CHANGELOG.md updated
- [ ] Version number updated
- [ ] GitHub release notes drafted

### Release
- [ ] Tag version in git
- [ ] Build release artifacts
- [ ] Sign artifacts with GPG
- [ ] Publish to Maven Central
- [ ] Create GitHub release
- [ ] Update documentation site
- [ ] Announce on Axon community

### Post-Release
- [ ] Monitor for issues
- [ ] Respond to community feedback
- [ ] Update roadmap
- [ ] Plan next version

---

## Helpful Links

### Documentation
- [Axon Framework Docs](https://docs.axoniq.io/)
- [Sentry Java SDK Docs](https://docs.sentry.io/platforms/java/)
- [OpenTelemetry Java Docs](https://opentelemetry.io/docs/instrumentation/java/)
- [Spring Boot Docs](https://spring.io/projects/spring-boot)

### Community
- [Axon Discuss](https://discuss.axoniq.io/)
- [Sentry Community](https://discord.gg/sentry)
- [OpenTelemetry Slack](https://cloud-native.slack.com/)

### Tools
- [Kotlin Docs](https://kotlinlang.org/docs/home.html)
- [Gradle Docs](https://docs.gradle.org/)
- [JUnit 5 Docs](https://junit.org/junit5/docs/current/user-guide/)

---

## Contact and Support

- **Project Owner:** [Your Name]
- **Repository:** github.com/[org]/axon-sentry-tracing
- **Issues:** github.com/[org]/axon-sentry-tracing/issues
- **Discussions:** github.com/[org]/axon-sentry-tracing/discussions
- **Email:** [your-email@example.com]

---

## Glossary

| Term | Definition |
|------|------------|
| **Axon Framework** | CQRS/Event Sourcing framework for Java |
| **Sentry** | Application monitoring and error tracking platform |
| **OpenTelemetry** | Observability framework for cloud-native software |
| **Span** | A unit of work in distributed tracing |
| **Trace** | Collection of spans representing end-to-end request flow |
| **SpanFactory** | Axon interface for creating trace spans |
| **CQRS** | Command Query Responsibility Segregation pattern |
| **Event Sourcing** | Storing state as sequence of events |
| **Sampling** | Selective tracing to reduce volume/cost |
| **Propagation** | Passing trace context across boundaries |
| **DSN** | Data Source Name (Sentry connection string) |
| **Aggregate** | Domain object in DDD/Event Sourcing |
| **Saga** | Long-running process coordinator |

---

## Quick Start for Developers

### First Time Setup

```bash
# Clone repository
git clone https://github.com/[org]/axon-sentry-tracing.git
cd axon-sentry-tracing

# Build project
./gradlew build

# Run tests
./gradlew test

# Run example app
./gradlew :sentry-tracing-example:bootRun
```

### Daily Development

```bash
# Pull latest changes
git pull origin main

# Create feature branch
git checkout -b feature/XXX-description

# Make changes and test
./gradlew test

# Commit and push
git add .
git commit -m "feat: implement XXX"
git push origin feature/XXX-description

# Create PR on GitHub
```

---

This quick reference provides at-a-glance information for developers working on the axon-sentry-tracing project. Keep it handy for quick lookups!
