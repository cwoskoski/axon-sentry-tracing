# Issue 008: Spring Boot Auto-Configuration

**Phase:** Integration
**Priority:** High
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 003, 004, 005, 006, 007

## Overview
Create Spring Boot auto-configuration to enable zero-configuration setup of axon-sentry-tracing in Spring Boot applications. This makes the library easy to adopt and configure via application properties.

## Goals
- Provide auto-configuration for all tracing components
- Enable configuration via application.properties/yaml
- Auto-detect Axon Framework components
- Register interceptors automatically
- Initialize OpenTelemetry and Sentry automatically
- Support conditional configuration
- Provide sensible defaults
- Enable easy customization

## Technical Requirements

### Components to Create

1. **AxonSentryTracingAutoConfiguration** (`io.github.axonsentry/spring/AxonSentryTracingAutoConfiguration.kt`)
   - Purpose: Main auto-configuration class
   - Key responsibilities:
     - Create and configure all tracing beans
     - Register interceptors with Axon components
     - Initialize OpenTelemetry and Sentry
     - Apply configuration properties

2. **AxonSentryTracingProperties** (`io.github.axonsentry/spring/AxonSentryTracingProperties.kt`)
   - Purpose: Configuration properties class
   - Key responsibilities:
     - Map application properties to configuration
     - Provide validation
     - Document all properties
     - Provide sensible defaults

3. **InterceptorRegistrar** (`io.github.axonsentry/spring/InterceptorRegistrar.kt`)
   - Purpose: Register interceptors with Axon buses
   - Key responsibilities:
     - Register command interceptors
     - Register event interceptors
     - Register query interceptors
     - Handle optional Axon components

4. **TracingHealthIndicator** (`io.github.axonsentry/spring/TracingHealthIndicator.kt`)
   - Purpose: Spring Boot health indicator
   - Key responsibilities:
     - Report tracing status
     - Check Sentry connectivity
     - Provide diagnostic information

### Dependencies

Add to build.gradle.kts:
```kotlin
// Spring Boot support (optional)
compileOnly("org.springframework.boot:spring-boot-starter:3.2.x")
compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.2.x")
compileOnly("org.springframework.boot:spring-boot-starter-actuator:3.2.x")

// Annotation processing for configuration properties
kapt("org.springframework.boot:spring-boot-configuration-processor:3.2.x")
```

### Configuration

Create `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.github.axonsentry.spring.AxonSentryTracingAutoConfiguration
```

Create configuration metadata for IDE support in `src/main/resources/META-INF/spring-configuration-metadata.json`.

## Implementation Guidance

### Step-by-Step Approach

1. **Create AxonSentryTracingProperties**
   - Define all configuration properties
   - Add JSR-303 validation
   - Document each property

2. **Implement AxonSentryTracingAutoConfiguration**
   - Create conditional beans
   - Initialize OpenTelemetry
   - Initialize Sentry
   - Create interceptors
   - Register with Axon

3. **Create InterceptorRegistrar**
   - Use BeanPostProcessor pattern
   - Register interceptors on Axon components
   - Handle missing components gracefully

4. **Implement TracingHealthIndicator**
   - Check configuration
   - Verify Sentry connection
   - Report span export status

5. **Create Configuration Metadata**
   - Generate JSON for IDE autocomplete
   - Document all properties

### Code Examples

#### AxonSentryTracingProperties.kt
```kotlin
package io.github.axonsentry.spring

import io.github.axonsentry.config.TracingConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin

/**
 * Configuration properties for Axon-Sentry tracing integration.
 *
 * Prefix: `axon.sentry.tracing`
 */
@ConfigurationProperties(prefix = "axon.sentry.tracing")
@Validated
data class AxonSentryTracingProperties(
    /**
     * Enable or disable tracing entirely.
     */
    var enabled: Boolean = true,

    /**
     * Enable command tracing.
     */
    var traceCommands: Boolean = true,

    /**
     * Enable event tracing.
     */
    var traceEvents: Boolean = true,

    /**
     * Enable query tracing.
     */
    var traceQueries: Boolean = true,

    /**
     * Enable event processor tracing.
     */
    var traceEventProcessors: Boolean = true,

    /**
     * Enable saga tracing.
     */
    var traceSagas: Boolean = true,

    /**
     * Capture command payloads in spans (be cautious with sensitive data).
     */
    var captureCommandPayloads: Boolean = false,

    /**
     * Capture event payloads in spans.
     */
    var captureEventPayloads: Boolean = false,

    /**
     * Capture query payloads in spans.
     */
    var captureQueryPayloads: Boolean = false,

    /**
     * Sentry Data Source Name (DSN).
     * Can also be configured via SENTRY_DSN environment variable.
     */
    var sentryDsn: String? = System.getenv("SENTRY_DSN"),

    /**
     * Deployment environment (e.g., development, staging, production).
     */
    var environment: String = "development",

    /**
     * Sample rate for traces (0.0 to 1.0).
     * 1.0 means trace everything, 0.0 means trace nothing.
     */
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    var tracesSampleRate: Double = 1.0,

    /**
     * Attach stack traces to Sentry events.
     */
    var attachStacktrace: Boolean = true,

    /**
     * Release version to report to Sentry.
     */
    var release: String? = null,

    /**
     * Additional tags to add to all spans.
     */
    var tags: Map<String, String> = emptyMap()
) {
    /**
     * Converts to TracingConfiguration domain model.
     */
    fun toTracingConfiguration(): TracingConfiguration {
        return TracingConfiguration(
            enabled = enabled,
            traceCommands = traceCommands,
            traceEvents = traceEvents,
            traceQueries = traceQueries,
            traceEventProcessors = traceEventProcessors,
            traceSagas = traceSagas,
            captureCommandPayloads = captureCommandPayloads,
            captureEventPayloads = captureEventPayloads,
            captureQueryPayloads = captureQueryPayloads,
            sentryDsn = sentryDsn,
            environment = environment,
            tracesSampleRate = tracesSampleRate,
            attachStacktrace = attachStacktrace
        )
    }
}
```

#### AxonSentryTracingAutoConfiguration.kt
```kotlin
package io.github.axonsentry.spring

import io.github.axonsentry.axon.*
import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.sentry.SentryTracingInitializer
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.axonframework.commandhandling.CommandBus
import org.axonframework.eventhandling.EventBus
import org.axonframework.queryhandling.QueryBus
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import javax.annotation.PreDestroy

/**
 * Spring Boot auto-configuration for Axon-Sentry tracing integration.
 *
 * Automatically configures:
 * - OpenTelemetry SDK
 * - Sentry integration
 * - Axon message interceptors
 * - Tracing components
 *
 * Enable with property: `axon.sentry.tracing.enabled=true` (default)
 */
@AutoConfiguration
@ConditionalOnClass(CommandBus::class, OpenTelemetry::class)
@ConditionalOnProperty(
    prefix = "axon.sentry.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(AxonSentryTracingProperties::class)
class AxonSentryTracingAutoConfiguration(
    private val properties: AxonSentryTracingProperties
) {
    private val logger = LoggerFactory.getLogger(AxonSentryTracingAutoConfiguration::class.java)

    @Volatile
    private var initializer: SentryTracingInitializer? = null

    @Bean
    @ConditionalOnMissingBean
    fun tracingConfiguration(): TracingConfiguration {
        return properties.toTracingConfiguration()
    }

    @Bean
    @ConditionalOnMissingBean
    fun openTelemetry(configuration: TracingConfiguration): OpenTelemetry {
        logger.info("Initializing Axon-Sentry tracing integration")

        initializer = SentryTracingInitializer.getInstance(configuration)
        return initializer!!.initialize()
    }

    @Bean
    @ConditionalOnMissingBean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        return openTelemetry.getTracer(
            "axon-sentry-tracing",
            properties.release ?: "unknown"
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun commandSpanFactory(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): CommandSpanFactory {
        return CommandSpanFactory(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun eventSpanFactory(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): EventSpanFactory {
        return EventSpanFactory(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun querySpanFactory(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): QuerySpanFactory {
        return QuerySpanFactory(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-commands"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun commandDispatchInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): CommandDispatchInterceptor {
        logger.debug("Registering CommandDispatchInterceptor")
        return CommandDispatchInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-commands"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun commandHandlerInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): CommandHandlerInterceptor {
        logger.debug("Registering CommandHandlerInterceptor")
        return CommandHandlerInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-events"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun eventDispatchInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): EventDispatchInterceptor {
        logger.debug("Registering EventDispatchInterceptor")
        return EventDispatchInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-event-processors"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun eventHandlerInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): EventHandlerInterceptor {
        logger.debug("Registering EventHandlerInterceptor")
        return EventHandlerInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-queries"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun queryDispatchInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): QueryDispatchInterceptor {
        logger.debug("Registering QueryDispatchInterceptor")
        return QueryDispatchInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-queries"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun queryHandlerInterceptor(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): QueryHandlerInterceptor {
        logger.debug("Registering QueryHandlerInterceptor")
        return QueryHandlerInterceptor(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun interceptorRegistrar(): InterceptorRegistrar {
        return InterceptorRegistrar()
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Axon-Sentry tracing")
        initializer?.shutdown()
    }
}
```

#### InterceptorRegistrar.kt
```kotlin
package io.github.axonsentry.spring

import io.github.axonsentry.axon.*
import org.axonframework.commandhandling.CommandBus
import org.axonframework.eventhandling.EventBus
import org.axonframework.queryhandling.QueryBus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/**
 * Registers tracing interceptors with Axon buses.
 */
@Component
class InterceptorRegistrar {
    private val logger = LoggerFactory.getLogger(InterceptorRegistrar::class.java)

    @Autowired(required = false)
    private var commandBus: CommandBus? = null

    @Autowired(required = false)
    private var eventBus: EventBus? = null

    @Autowired(required = false)
    private var queryBus: QueryBus? = null

    @Autowired(required = false)
    private var commandDispatchInterceptor: CommandDispatchInterceptor? = null

    @Autowired(required = false)
    private var commandHandlerInterceptor: CommandHandlerInterceptor? = null

    @Autowired(required = false)
    private var eventDispatchInterceptor: EventDispatchInterceptor? = null

    @Autowired(required = false)
    private var eventHandlerInterceptor: EventHandlerInterceptor? = null

    @Autowired(required = false)
    private var queryDispatchInterceptor: QueryDispatchInterceptor? = null

    @Autowired(required = false)
    private var queryHandlerInterceptor: QueryHandlerInterceptor? = null

    @PostConstruct
    fun registerInterceptors() {
        commandBus?.let { bus ->
            commandDispatchInterceptor?.let {
                bus.registerDispatchInterceptor(it)
                logger.info("Registered CommandDispatchInterceptor with CommandBus")
            }
            commandHandlerInterceptor?.let {
                bus.registerHandlerInterceptor(it)
                logger.info("Registered CommandHandlerInterceptor with CommandBus")
            }
        }

        eventBus?.let { bus ->
            eventDispatchInterceptor?.let {
                bus.registerDispatchInterceptor(it)
                logger.info("Registered EventDispatchInterceptor with EventBus")
            }
            // Note: Event handler interceptors register with event processors, not the bus
        }

        queryBus?.let { bus ->
            queryDispatchInterceptor?.let {
                bus.registerDispatchInterceptor(it)
                logger.info("Registered QueryDispatchInterceptor with QueryBus")
            }
            queryHandlerInterceptor?.let {
                bus.registerHandlerInterceptor(it)
                logger.info("Registered QueryHandlerInterceptor with QueryBus")
            }
        }
    }
}
```

#### TracingHealthIndicator.kt
```kotlin
package io.github.axonsentry.spring

import io.github.axonsentry.config.TracingConfiguration
import io.sentry.Sentry
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component

/**
 * Health indicator for Axon-Sentry tracing.
 *
 * Reports status of tracing integration in Spring Boot Actuator health endpoint.
 */
@Component
@ConditionalOnClass(HealthIndicator::class)
class TracingHealthIndicator(
    private val configuration: TracingConfiguration
) : HealthIndicator {

    override fun health(): Health {
        val builder = if (configuration.enabled) {
            Health.up()
        } else {
            Health.status("DISABLED")
        }

        return builder
            .withDetail("enabled", configuration.enabled)
            .withDetail("commands", configuration.traceCommands)
            .withDetail("events", configuration.traceEvents)
            .withDetail("queries", configuration.traceQueries)
            .withDetail("eventProcessors", configuration.traceEventProcessors)
            .withDetail("sagas", configuration.traceSagas)
            .withDetail("sampleRate", configuration.tracesSampleRate)
            .withDetail("environment", configuration.environment)
            .withDetail("sentryConfigured", configuration.sentryDsn != null)
            .apply {
                try {
                    val isEnabled = Sentry.isEnabled()
                    withDetail("sentryConnected", isEnabled)
                } catch (e: Exception) {
                    withDetail("sentryConnected", false)
                    withDetail("sentryError", e.message)
                }
            }
            .build()
    }
}
```

### Integration Points
- Integrates with Spring Boot auto-configuration system
- Registers with Spring's application context
- Uses Spring Boot properties system
- Integrates with Actuator health checks
- Auto-detects Axon Framework components

## Testing Requirements

### Unit Tests
- [ ] Test: Properties bind from application.properties
- [ ] Test: Configuration validation works
- [ ] Test: Beans are created conditionally
- [ ] Test: Interceptors register when enabled
- [ ] Test: Interceptors don't register when disabled
- [ ] Test: Health indicator reports correct status

### Integration Tests
- [ ] Integration: Full Spring Boot app with auto-config
- [ ] Integration: Properties from application.yaml
- [ ] Integration: Conditional configuration works
- [ ] Integration: Health endpoint returns status
- [ ] Integration: Interceptors are active

### Test Coverage Target
80%+ coverage

## Acceptance Criteria
- [ ] Auto-configuration class created
- [ ] Configuration properties class created
- [ ] All tracing components auto-configured
- [ ] Interceptors register automatically
- [ ] Properties validation works
- [ ] Health indicator available
- [ ] Configuration metadata for IDE support
- [ ] All tests passing
- [ ] Documentation includes property reference

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (80%+ coverage)
- [ ] Integration tests with Spring Boot app passing
- [ ] Configuration metadata generated
- [ ] IDE autocomplete tested
- [ ] Code meets quality standards
- [ ] KDoc complete
- [ ] Property reference documentation created
- [ ] PR reviewed and approved
- [ ] Changes committed to main branch

## Resources
- [Spring Boot Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration)
- [Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [Configuration Metadata](https://docs.spring.io/spring-boot/docs/current/reference/html/configuration-metadata.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

## Notes
- Use `@ConditionalOnClass` to make Spring Boot dependency optional
- Provide meaningful defaults for all properties
- Document all properties in KDoc and metadata
- Support SENTRY_DSN environment variable
- Make auto-configuration non-invasive and easy to override
- Test with both properties and YAML configuration
- Ensure graceful degradation if Axon components are missing
- Consider providing configuration profiles (dev, prod)
- Health indicator should not fail if Sentry is unreachable

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
