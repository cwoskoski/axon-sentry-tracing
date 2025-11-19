# Issue 021: Spring Boot Auto-Configuration Enhancement

**Phase:** Spring Boot Integration
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 020

## Overview
Enhance Spring Boot auto-configuration from Issue 008 to provide zero-config setup for Spring Boot applications, automatic bean creation, and seamless integration with Spring Boot's observability features.

## Goals
- Auto-configure all tracing components
- Auto-detect Axon configuration
- Integrate with Spring Boot Actuator
- Support conditional configuration
- Enable/disable via properties
- Provide sensible defaults
- Support multiple Sentry instances

## Technical Requirements

### Components to Create

1. **SentryTracingAutoConfiguration** (`io.github.axonsentry.autoconfigure.SentryTracingAutoConfiguration.kt`)
2. **AxonTracingAutoConfiguration** (`io.github.axonsentry.autoconfigure.AxonTracingAutoConfiguration.kt`)
3. **SentryTracingProperties** (`io.github.axonsentry.autoconfigure.SentryTracingProperties.kt`)

### Implementation Example

```kotlin
package io.github.axonsentry.autoconfigure

import io.github.axonsentry.axon.*
import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.TraceContextPropagator
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.sentry.Sentry
import org.axonframework.commandhandling.CommandBus
import org.axonframework.config.Configurer
import org.axonframework.eventhandling.EventBus
import org.axonframework.queryhandling.QueryBus
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(Sentry::class, Configurer::class)
@ConditionalOnProperty(
    prefix = "axon.sentry.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(SentryTracingProperties::class)
class SentryTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun tracingConfiguration(
        properties: SentryTracingProperties
    ): TracingConfiguration {
        return TracingConfiguration(
            enabled = properties.enabled,
            sentryDsn = properties.dsn,
            traceCommands = properties.traceCommands,
            traceEvents = properties.traceEvents,
            traceQueries = properties.traceQueries,
            captureCommandPayloads = properties.capturePayloads.commands,
            captureEventPayloads = properties.capturePayloads.events,
            captureQueryPayloads = properties.capturePayloads.queries,
            sampleRate = properties.sampling.rate
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun openTelemetrySdk(
        properties: SentryTracingProperties
    ): OpenTelemetrySdk {
        // Configure OpenTelemetry with Sentry exporter
        return OpenTelemetrySdk.builder()
            .setTracerProvider(/* configure */)
            .build()
    }

    @Bean
    @ConditionalOnMissingBean
    fun tracer(openTelemetry: OpenTelemetrySdk): Tracer {
        return openTelemetry.getTracer("io.github.axonsentry")
    }

    @Bean
    @ConditionalOnMissingBean
    fun axonSpanFactory(
        tracer: Tracer,
        configuration: TracingConfiguration
    ): AxonSpanFactory {
        return AxonSpanFactory(tracer, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun commandTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration
    ): CommandTracingInterceptor {
        return CommandTracingInterceptor(spanFactory, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun eventTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration
    ): EventTracingInterceptor {
        return EventTracingInterceptor(spanFactory, configuration)
    }

    @Bean
    @ConditionalOnMissingBean
    fun queryTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration
    ): QueryTracingInterceptor {
        return QueryTracingInterceptor(spanFactory, configuration)
    }

    @Bean
    fun axonTracingConfigurer(
        configurer: Configurer,
        commandInterceptor: CommandTracingInterceptor,
        eventInterceptor: EventTracingInterceptor,
        queryInterceptor: QueryTracingInterceptor
    ): AxonTracingConfigurer {
        return AxonTracingConfigurer(
            configurer,
            commandInterceptor,
            eventInterceptor,
            queryInterceptor
        )
    }
}

class AxonTracingConfigurer(
    private val configurer: Configurer,
    private val commandInterceptor: CommandTracingInterceptor,
    private val eventInterceptor: EventTracingInterceptor,
    private val queryInterceptor: QueryTracingInterceptor
) {
    init {
        configurer.eventProcessing()
            .registerDefaultHandlerInterceptor { _, _ -> eventInterceptor }

        configurer.onInitialize { config ->
            config.commandBus().registerDispatchInterceptor(commandInterceptor)
            config.commandBus().registerHandlerInterceptor(commandInterceptor)

            config.queryBus().registerDispatchInterceptor(queryInterceptor)
            config.queryBus().registerHandlerInterceptor(queryInterceptor)
        }
    }
}
```

## Acceptance Criteria
- [ ] Zero-config Spring Boot setup
- [ ] All beans auto-configured
- [ ] Properties-driven configuration
- [ ] Conditional beans work correctly

## Definition of Done
- [ ] Implementation complete
- [ ] Tests passing
- [ ] Documentation updated
- [ ] Changes committed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
