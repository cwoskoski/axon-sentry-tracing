# Issue Templates for Axon-Sentry-Tracing

This document provides detailed issue templates that can be used to create GitHub issues for each work item. Copy the relevant template and fill in specific details.

---

## Template: Foundation Issues (Phase 0)

### Issue 001: Project Structure and Multi-Module Setup

**Labels:** `phase-0`, `critical`, `foundation`, `setup`

**Title:** Set up Gradle multi-module project structure

**Description:**

Create the foundational Gradle multi-module project structure for axon-sentry-tracing with proper organization and build configuration.

**Modules to Create:**
- Root project with `settings.gradle.kts`
- `sentry-tracing` (core library module)
- `sentry-tracing-spring-boot-autoconfigure`
- `sentry-tracing-spring-boot-starter`
- `sentry-tracing-example` (demo application)

**Technical Requirements:**
- Gradle 8.x with Kotlin DSL
- Gradle wrapper committed to repository
- Proper module naming following Maven conventions
- Module dependency hierarchy: starter → autoconfigure → core

**Acceptance Criteria:**
- [ ] `settings.gradle.kts` includes all modules
- [ ] Each module has proper `build.gradle.kts`
- [ ] Project builds successfully with `./gradlew build`
- [ ] Gradle wrapper scripts (`gradlew`, `gradlew.bat`) committed
- [ ] Module dependency graph is correct (no circular deps)
- [ ] All modules compile without errors

**Definition of Done:**
- Project structure created and committed
- CI pipeline builds project successfully
- README updated with build instructions
- All team members can build project locally

**Estimated Effort:** 1-2 days

---

### Issue 002: Root Build Configuration

**Labels:** `phase-0`, `critical`, `build-config`

**Title:** Configure root-level Gradle build with version catalog

**Description:**

Set up root-level Gradle configuration with Kotlin DSL, version catalog for dependency management, and common build logic shared across all modules.

**Key Elements:**
- `libs.versions.toml` for centralized dependency management
- Kotlin JVM plugin configuration
- Java toolchain setup (Java 17 minimum)
- Common repositories (Maven Central, Gradle Plugin Portal)
- Convention plugins or `subprojects {}` configuration
- Build scan integration (optional but recommended)

**Version Catalog Structure:**
```toml
[versions]
kotlin = "1.9.22"
axon = "4.9.1"
sentry = "7.2.0"
opentelemetry = "1.34.1"
spring-boot = "3.2.2"

[libraries]
axon-core = { module = "org.axonframework:axon-core", version.ref = "axon" }
sentry-java = { module = "io.sentry:sentry", version.ref = "sentry" }
# ... more libraries
```

**Acceptance Criteria:**
- [ ] `gradle/libs.versions.toml` created with all major dependencies
- [ ] Kotlin plugin applied with correct version
- [ ] Java toolchain configured (Java 17+)
- [ ] All modules can reference version catalog dependencies
- [ ] Common build logic defined in root or convention plugins
- [ ] Build reproducible (same inputs → same outputs)
- [ ] No deprecated Gradle features used

**Definition of Done:**
- Version catalog complete with all known dependencies
- All modules use version catalog references
- Documentation explains how to add new dependencies
- CI validates Gradle configuration

**Estimated Effort:** 1-2 days

**Dependencies:** Issue 001

---

## Template: Core Integration Issues (Phase 1)

### Issue 009: Sentry OpenTelemetry Integration Setup

**Labels:** `phase-1`, `critical`, `core`, `integration`

**Title:** Implement core Sentry OpenTelemetry SDK initialization

**Description:**

Implement the foundational integration between OpenTelemetry SDK and Sentry, providing the basis for all tracing functionality.

**Key Components to Implement:**

1. **SentryOpenTelemetryConfiguration**
   - Configures OpenTelemetry SDK with Sentry backend
   - Manages SDK lifecycle (initialization, shutdown)
   - Integrates Sentry SpanProcessor

2. **Configuration Model**
   ```kotlin
   data class SentryTracingConfiguration(
       val dsn: String,
       val environment: String = "production",
       val release: String? = null,
       val tracesSampleRate: Double = 1.0,
       val enabled: Boolean = true,
       val enableTracing: Boolean = true
   )
   ```

3. **OpenTelemetry SDK Builder Integration**
   - Set up Sentry as trace exporter
   - Configure trace propagators (W3C Trace Context)
   - Set up resource attributes
   - Configure batch span processor

**Technical Considerations:**
- Use Sentry's `SentrySpanProcessor` for span export
- Ensure proper OpenTelemetry SDK lifecycle management
- Handle initialization failures gracefully
- Support programmatic configuration
- Thread-safe initialization (singleton pattern)

**Acceptance Criteria:**
- [ ] `SentryOpenTelemetryConfiguration` class initializes OpenTelemetry SDK
- [ ] Sentry DSN configuration validated
- [ ] OpenTelemetry SDK properly configured with Sentry exporter
- [ ] Test spans successfully sent to Sentry test endpoint
- [ ] Trace context propagates using W3C Trace Context format
- [ ] Configuration validation prevents invalid states
- [ ] Shutdown hook properly closes SDK resources
- [ ] Unit tests verify configuration logic
- [ ] Integration test sends test span to Sentry

**Testing Strategy:**
- Unit tests: Configuration validation, builder setup
- Integration tests: End-to-end span export to Sentry
- Use Sentry test DSN or mock Sentry endpoint
- Verify span attributes and format

**Definition of Done:**
- Implementation complete and merged
- All tests passing in CI
- Code reviewed and approved
- API documentation (KDoc) complete
- Integration test demonstrates spans in Sentry

**Estimated Effort:** 1-2 weeks

**Dependencies:** Issue 003

**References:**
- [Sentry OpenTelemetry Integration Docs](https://docs.sentry.io/platforms/java/tracing/instrumentation/opentelemetry/)
- [OpenTelemetry Java SDK](https://opentelemetry.io/docs/instrumentation/java/)

---

### Issue 010: Axon SpanFactory Implementation

**Labels:** `phase-1`, `critical`, `core`, `axon-integration`

**Title:** Implement Axon SpanFactory for OpenTelemetry spans

**Description:**

Implement Axon Framework's `SpanFactory` interface to create OpenTelemetry spans for Axon messages, forming the core integration point.

**Key Components:**

1. **SentrySpanFactory**
   ```kotlin
   class SentrySpanFactory(
       private val tracer: Tracer,
       private val attributeProviders: List<SpanAttributeProvider> = emptyList()
   ) : SpanFactory {
       override fun createRootTrace(messageSupplier: Supplier<out Message<*>>): Span
       override fun createHandlerSpan(messageSupplier: Supplier<out Message<*>>): Span
       override fun createDispatchSpan(messageSupplier: Supplier<out Message<*>>): Span
   }
   ```

2. **Span Lifecycle Management**
   - Proper span start/end timing
   - Exception handling and error recording
   - Span context extraction/injection
   - Parent-child span relationships

3. **Integration with OpenTelemetry**
   - Use `Tracer` to create spans
   - Set span kind appropriately (CLIENT, SERVER, INTERNAL)
   - Apply span attributes from message metadata
   - Handle span scope management

**Technical Considerations:**
- Axon's `SpanFactory` returns Axon's `Span` interface
- Need adapter between OpenTelemetry `Span` and Axon `Span`
- Proper exception handling to not break message processing
- Thread-safe span creation
- Efficient attribute extraction

**Acceptance Criteria:**
- [ ] `SentrySpanFactory` implements `org.axonframework.tracing.SpanFactory`
- [ ] Creates valid OpenTelemetry spans for all message types
- [ ] Span hierarchy maintained (parent-child relationships)
- [ ] Errors recorded on spans with exception details
- [ ] Span names follow consistent naming convention
- [ ] Span lifecycle properly managed (start/end)
- [ ] Thread-safe implementation
- [ ] Unit tests cover all span creation scenarios
- [ ] Integration test verifies spans appear in OpenTelemetry

**Testing Strategy:**
- Unit tests: Mock Tracer, verify span creation calls
- Span attribute verification
- Error handling scenarios
- Parent span context propagation
- Integration test with real OpenTelemetry SDK

**Definition of Done:**
- Implementation complete and reviewed
- All tests passing
- Integration with Axon verified
- Performance impact measured (< 1ms overhead)
- Documentation complete

**Estimated Effort:** 1-2 weeks

**Dependencies:** Issue 009

**References:**
- [Axon SpanFactory Documentation](https://docs.axoniq.io/reference-guide/monitoring-and-metrics/tracing)
- [OpenTelemetry Span API](https://opentelemetry.io/docs/instrumentation/java/manual/#spans)

---

### Issue 011: Command Message Tracing

**Labels:** `phase-1`, `critical`, `core`, `commands`

**Title:** Implement comprehensive command message tracing

**Description:**

Implement tracing for Axon command messages including dispatch, handling, and result capture with appropriate span attributes.

**Features to Implement:**

1. **Command Dispatch Tracing**
   - Create span when command dispatched
   - Capture command name, type, aggregate ID
   - Record dispatch timestamp
   - Propagate context to handler

2. **Command Handler Tracing**
   - Create handler span as child of dispatch span
   - Capture handler class and method
   - Record execution time
   - Capture command result or exception

3. **Command-Specific Attributes**
   ```kotlin
   span.setAttribute("axon.message.type", "COMMAND")
   span.setAttribute("axon.command.name", commandName)
   span.setAttribute("axon.aggregate.id", aggregateId)
   span.setAttribute("axon.aggregate.type", aggregateType)
   span.setAttribute("axon.handler.class", handlerClass)
   ```

4. **Result Capture**
   - Success vs failure status
   - Result type information
   - Exception details on failure

**Technical Implementation:**
- Use `SpanFactory.createDispatchSpan()` for command dispatch
- Use `SpanFactory.createHandlerSpan()` for command handling
- Extract metadata from `CommandMessage`
- Handle generic command result types
- Integrate with command gateway and bus

**Acceptance Criteria:**
- [ ] Command dispatch creates span with correct attributes
- [ ] Command handler creates child span
- [ ] Parent-child relationship maintained
- [ ] Command success captured in span status
- [ ] Command failure recorded with exception
- [ ] Aggregate ID and type captured
- [ ] Handler information recorded
- [ ] Integration test verifies end-to-end command tracing
- [ ] Spans visible in Sentry with correct hierarchy

**Testing Strategy:**
- Unit tests: Attribute extraction, span creation
- Integration tests: Full command dispatch → handler → result flow
- Error scenarios: Command validation failure, handler exception
- Use Axon Test framework for realistic scenarios

**Definition of Done:**
- Implementation complete
- All tests passing
- Integration test shows command traces in Sentry
- Documentation with code examples
- Performance validated (< 5% overhead)

**Estimated Effort:** 3-5 days

**Dependencies:** Issue 010

---

## Template: Spring Boot Integration Issues (Phase 2)

### Issue 021: Spring Boot Auto-Configuration Class

**Labels:** `phase-2`, `critical`, `spring-boot`, `autoconfigure`

**Title:** Implement Spring Boot auto-configuration for Axon-Sentry integration

**Description:**

Create Spring Boot auto-configuration that automatically sets up Axon-Sentry tracing when both Axon and Sentry are on the classpath.

**Key Components:**

1. **Auto-Configuration Class**
   ```kotlin
   @Configuration
   @ConditionalOnClass(AxonConfiguration::class, Sentry::class)
   @ConditionalOnProperty(
       prefix = "axon.sentry",
       name = ["enabled"],
       havingValue = "true",
       matchIfMissing = true
   )
   @EnableConfigurationProperties(SentryTracingProperties::class)
   class SentryTracingAutoConfiguration {

       @Bean
       @ConditionalOnMissingBean
       fun sentrySpanFactory(
           tracer: Tracer,
           properties: SentryTracingProperties
       ): SpanFactory {
           return SentrySpanFactory(tracer)
       }

       @Bean
       @ConditionalOnMissingBean
       fun openTelemetrySdk(
           properties: SentryTracingProperties
       ): OpenTelemetry {
           // Initialize OpenTelemetry with Sentry
       }
   }
   ```

2. **Bean Configuration**
   - `SpanFactory` bean for Axon integration
   - `OpenTelemetry` SDK bean
   - `Tracer` bean from OpenTelemetry SDK
   - Optional beans for attribute providers

3. **Conditional Logic**
   - Only activate if Axon and Sentry on classpath
   - Respect `axon.sentry.enabled` property
   - Allow user bean overrides with `@ConditionalOnMissingBean`
   - Handle missing optional dependencies gracefully

4. **Integration with Axon Configuration**
   - Register `SpanFactory` with Axon `Configurer`
   - Ensure proper initialization order
   - Work with Axon Spring Boot starter

**Technical Considerations:**
- Use Spring Boot 2.7+ and 3.x compatible annotations
- Proper bean ordering with `@AutoConfigureAfter`
- Handle both programmatic and annotation-based Axon config
- Thread-safe bean initialization
- Graceful degradation if configuration incomplete

**Acceptance Criteria:**
- [ ] Auto-configuration activates with Axon + Sentry on classpath
- [ ] `SpanFactory` bean registered automatically
- [ ] OpenTelemetry SDK initialized with Sentry
- [ ] User-defined beans override auto-configuration
- [ ] Configuration can be disabled with `axon.sentry.enabled=false`
- [ ] Works with Spring Boot 2.7.x and 3.x
- [ ] No errors if optional dependencies missing
- [ ] Integration test with `@SpringBootTest`
- [ ] Conditional logic tested thoroughly

**Testing Strategy:**
- `@SpringBootTest` with different configurations
- Test with and without Sentry on classpath
- Test with user-provided custom beans
- Test property-based enable/disable
- Verify bean wiring with ApplicationContext inspection

**Definition of Done:**
- Auto-configuration complete and tested
- Works in example application
- No classpath conflicts
- Documentation explains override mechanism
- Code review approved

**Estimated Effort:** 3-5 days

**Dependencies:** Issues 009, 010

---

## Template: Advanced Features Issues (Phase 3)

### Issue 029: Intelligent Sampling Strategies

**Labels:** `phase-3`, `enhancement`, `performance`, `sampling`

**Title:** Implement intelligent sampling strategies for production optimization

**Description:**

Implement advanced sampling strategies beyond simple rate-based sampling to optimize trace volume and cost while maintaining observability.

**Sampling Strategies to Implement:**

1. **Error-Based Sampler**
   - Always sample traces containing errors
   - Configurable error types to include/exclude
   - Preserve failed command/query traces

2. **Latency-Based Sampler**
   - Sample slow operations above threshold
   - Percentile-based sampling (e.g., sample p95 and above)
   - Configurable latency thresholds per message type

3. **Business Rule Sampler**
   - Sample based on message content
   - Configurable predicate functions
   - Example: Sample specific aggregate types or IDs

4. **Composite Sampler**
   - Combine multiple strategies with OR logic
   - Fallback to rate-based sampling if no rule matches
   - Configurable sampling order/priority

5. **Per-Message-Type Sampling**
   ```kotlin
   samplingConfiguration {
       commands {
           sampleRate = 0.1
           alwaysSampleErrors = true
       }
       queries {
           sampleRate = 0.05
       }
       events {
           sampleRate = 0.2
           alwaysSampleTypes = listOf("CriticalEvent")
       }
   }
   ```

**Technical Implementation:**
- Implement `io.opentelemetry.sdk.trace.samplers.Sampler` interface
- Composable sampler architecture
- Efficient sampling decision (< 1ms overhead)
- Sampling decision recorded in span attributes
- Integration with OpenTelemetry SDK sampler chain

**Configuration:**
```kotlin
data class SamplingConfiguration(
    val defaultSampleRate: Double = 1.0,
    val alwaysSampleErrors: Boolean = true,
    val latencyThresholdMs: Long = 1000,
    val perMessageTypeSampleRates: Map<String, Double> = emptyMap(),
    val customSamplingRules: List<SamplingRule> = emptyList()
)
```

**Acceptance Criteria:**
- [ ] Error-based sampler always samples failed traces
- [ ] Latency-based sampler captures slow operations
- [ ] Business rule sampler supports custom predicates
- [ ] Composite sampler combines strategies correctly
- [ ] Per-message-type rates configurable
- [ ] Sampling decision recorded in span
- [ ] Performance overhead < 1ms per sampling decision
- [ ] Unit tests for each strategy
- [ ] Integration test verifies sampling behavior
- [ ] Documentation with configuration examples

**Testing Strategy:**
- Unit tests: Each sampler in isolation
- Property-based testing for rate accuracy
- Performance tests: Sampling decision latency
- Integration tests: Verify correct spans sampled
- Statistical validation of sample rates

**Definition of Done:**
- All samplers implemented and tested
- Configuration options documented
- Example application demonstrates sampling
- Performance validated
- User guide includes sampling best practices

**Estimated Effort:** 1-2 weeks

**Dependencies:** Issue 009

---

## Template: Production Readiness Issues (Phase 4)

### Issue 041: Performance Testing and Optimization

**Labels:** `phase-4`, `critical`, `performance`, `optimization`

**Title:** Conduct performance testing and optimize for production workloads

**Description:**

Comprehensive performance testing to measure overhead and optimize the library for production use with minimal impact on application performance.

**Performance Testing Areas:**

1. **Throughput Impact**
   - Measure messages/second with vs without tracing
   - Test under various load levels
   - Identify throughput degradation percentage
   - Target: < 5% throughput reduction

2. **Latency Overhead**
   - Measure p50, p95, p99 latency impact
   - Per-message-type latency analysis
   - Span creation overhead
   - Span export latency
   - Target: < 5ms added latency at p95

3. **Memory Usage**
   - Heap usage with tracing enabled
   - Span buffer memory consumption
   - Memory leak detection (long-running tests)
   - GC pressure analysis
   - Target: < 50MB additional heap for typical workload

4. **CPU Overhead**
   - CPU usage with tracing enabled
   - Profiling hot paths
   - Thread pool utilization
   - Target: < 5% additional CPU usage

5. **High-Concurrency Scenarios**
   - Test with 100+ concurrent threads
   - Thread safety verification
   - Lock contention analysis
   - Concurrent span creation performance

6. **Stress Testing**
   - Sustained high load (hours/days)
   - Resource exhaustion scenarios
   - Graceful degradation under stress
   - Recovery after overload

**Testing Tools:**
- JMH (Java Microbenchmark Harness) for microbenchmarks
- Gatling or JMeter for load testing
- YourKit or JProfiler for profiling
- VisualVM for memory analysis
- Axon Benchmark suite integration

**Optimization Opportunities:**
- Object pooling for frequently created objects
- Lazy attribute extraction
- Batch span processing
- Efficient data structures
- Reduce allocations in hot paths

**Acceptance Criteria:**
- [ ] Throughput overhead < 5% at 10k msg/sec
- [ ] Latency overhead < 5ms at p95
- [ ] Memory overhead < 50MB for typical workload
- [ ] CPU overhead < 5%
- [ ] No memory leaks in 24-hour stress test
- [ ] No thread safety issues under concurrency
- [ ] Performance benchmarks documented
- [ ] Optimization applied where needed
- [ ] Performance regression tests in CI
- [ ] Performance guide published

**Performance Benchmark Results Format:**
```
Scenario: Command Processing (10,000 commands/sec)
- Baseline (no tracing): 10,234 msg/sec, p95 latency: 12ms
- With tracing:         9,891 msg/sec, p95 latency: 15ms
- Overhead:             3.4% throughput, 3ms latency

Memory Usage:
- Baseline heap: 256MB
- With tracing:  289MB (+33MB)

CPU Usage:
- Baseline: 45%
- With tracing: 47% (+2%)
```

**Definition of Done:**
- All performance tests executed
- Results documented and published
- Optimization completed where needed
- Performance regression tests in CI
- Performance tuning guide available
- Meets all performance targets

**Estimated Effort:** 1-2 weeks

**Dependencies:** Issue 030

---

## Template: Documentation Issues (Phase 5)

### Issue 047: Comprehensive README

**Labels:** `phase-5`, `critical`, `documentation`

**Title:** Create comprehensive project README

**Description:**

Create a professional, comprehensive README that serves as the primary entry point for users discovering and evaluating the library.

**README Structure:**

1. **Header Section**
   - Project title and tagline
   - Badges (build status, coverage, version, license)
   - One-sentence description
   - Key features bullet points

2. **Quick Start**
   - Minimal setup example (< 10 lines)
   - Gradle/Maven dependency snippet
   - Basic configuration
   - Expected result

3. **Features**
   - Comprehensive feature list
   - Feature highlights with brief explanations
   - Links to detailed documentation

4. **Installation**
   - Gradle Kotlin DSL example
   - Gradle Groovy DSL example
   - Maven example
   - Version compatibility matrix

5. **Configuration**
   - Spring Boot configuration example
   - Programmatic configuration example
   - Key configuration properties table
   - Link to full configuration reference

6. **Usage Examples**
   - Basic usage with Axon
   - Custom span attributes
   - Sampling configuration
   - Error handling

7. **Documentation Links**
   - User guide
   - API documentation
   - Example application
   - Migration guide (if applicable)

8. **Compatibility**
   - Supported Axon versions
   - Supported Spring Boot versions
   - Supported Java versions
   - Sentry SDK compatibility

9. **Contributing**
   - Link to CONTRIBUTING.md
   - How to report issues
   - Development setup
   - Code of conduct

10. **License**
    - License type
    - Copyright statement

**README Quality Criteria:**
- Scannable (headers, bullets, code blocks)
- Complete but not overwhelming
- Code examples that compile and run
- Professional appearance
- Mobile-friendly formatting
- Up-to-date with latest version

**Example Quick Start Section:**
```markdown
## Quick Start

### 1. Add Dependency

```kotlin
dependencies {
    implementation("io.axoniq.extensions:sentry-tracing-spring-boot-starter:1.0.0")
}
```

### 2. Configure Sentry DSN

```yaml
axon:
  sentry:
    dsn: https://examplePublicKey@o0.ingest.sentry.io/0
    environment: production
```

### 3. Traces Appear Automatically

That's it! All your Axon commands, queries, and events are now traced in Sentry.
```

**Acceptance Criteria:**
- [ ] README is comprehensive yet scannable
- [ ] Quick start takes < 5 minutes
- [ ] All code examples tested and working
- [ ] Badges display correctly
- [ ] Links to all relevant resources work
- [ ] Compatibility matrix accurate
- [ ] Professional appearance
- [ ] Reviewed by multiple team members
- [ ] Published on GitHub repository
- [ ] Referenced in Maven Central listing

**Definition of Done:**
- README complete and published
- All examples verified working
- Links tested
- Peer reviewed
- User feedback incorporated

**Estimated Effort:** 2-3 days

**Dependencies:** Issue 020

---

## Issue Checklist Template

Use this checklist for each issue to track completion:

```markdown
## Implementation Checklist

### Development
- [ ] Design reviewed and approved
- [ ] Implementation complete
- [ ] Code follows project conventions
- [ ] No compiler warnings
- [ ] No static analysis violations

### Testing
- [ ] Unit tests written and passing
- [ ] Integration tests written and passing
- [ ] Test coverage meets target (80%+)
- [ ] Edge cases tested
- [ ] Error scenarios tested

### Documentation
- [ ] KDoc/Javadoc complete for public APIs
- [ ] README updated (if applicable)
- [ ] User guide updated (if applicable)
- [ ] CHANGELOG.md updated
- [ ] Migration notes added (if breaking change)

### Quality Assurance
- [ ] Code reviewed and approved
- [ ] CI build passing
- [ ] Performance impact assessed
- [ ] Security implications considered
- [ ] Backward compatibility verified

### Definition of Done
- [ ] All acceptance criteria met
- [ ] Merged to main branch
- [ ] Issue closed
- [ ] Stakeholders notified
```

---

## GitHub Issue Labels

Recommended labels for organizing issues:

**Phase Labels:**
- `phase-0` - Foundation & Setup
- `phase-1` - Core Integration
- `phase-2` - Spring Boot Integration
- `phase-3` - Advanced Features
- `phase-4` - Production Readiness
- `phase-5` - Documentation & Examples

**Priority Labels:**
- `critical` - Must have for release
- `high` - Important for release
- `medium` - Nice to have
- `low` - Future enhancement

**Type Labels:**
- `feature` - New functionality
- `bug` - Bug fix
- `enhancement` - Improvement to existing feature
- `documentation` - Documentation only
- `testing` - Testing infrastructure
- `performance` - Performance related
- `security` - Security related

**Component Labels:**
- `core` - Core library
- `spring-boot` - Spring Boot integration
- `build` - Build configuration
- `ci-cd` - CI/CD pipeline
- `example` - Example application

**Status Labels:**
- `blocked` - Blocked by another issue
- `in-progress` - Currently being worked on
- `review` - In code review
- `testing` - In testing phase
- `done` - Completed

---

## Project Board Columns

Suggested GitHub Project board structure:

1. **Backlog** - All planned issues
2. **Ready** - Issues ready to be worked on (dependencies met)
3. **In Progress** - Currently being developed
4. **Code Review** - Awaiting review
5. **Testing** - In testing phase
6. **Done** - Completed and merged

**Automation Rules:**
- Move to "In Progress" when issue assigned
- Move to "Code Review" when PR created
- Move to "Done" when PR merged and issue closed

---

## Version Planning

Suggested version milestones:

**v0.1.0-SNAPSHOT** (MVP)
- Issues 001-020 (Phases 0-1)
- Basic integration working
- Internal testing only

**v0.5.0-SNAPSHOT** (Feature Complete)
- Issues 001-038 (Phases 0-3)
- All planned features
- Beta testing

**v1.0.0-RC1** (Release Candidate)
- Issues 001-046 (Phases 0-4)
- Production ready
- Community testing

**v1.0.0** (General Availability)
- Issues 001-052 (All phases)
- Full documentation
- Public release

---

This template document provides ready-to-use issue descriptions that can be copied directly into GitHub Issues, ensuring consistent structure and quality across all work items.
