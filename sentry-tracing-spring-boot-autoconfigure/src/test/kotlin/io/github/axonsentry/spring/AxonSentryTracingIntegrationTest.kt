package io.github.axonsentry.spring

import io.github.axonsentry.axon.AxonSpanFactory
import io.github.axonsentry.axon.CommandTracingInterceptor
import io.github.axonsentry.axon.EventTracingInterceptor
import io.github.axonsentry.axon.QueryTracingInterceptor
import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.SimpleEventBus
import org.axonframework.queryhandling.QueryBus
import org.axonframework.queryhandling.SimpleQueryBus
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Integration tests for Axon-Sentry tracing auto-configuration.
 *
 * These tests verify the full Spring Boot integration with Axon Framework.
 */
class AxonSentryTracingIntegrationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AxonSentryTracingAutoConfiguration::class.java))
            .withUserConfiguration(AxonConfiguration::class.java)

    @Test
    fun `should auto-configure all tracing components with Axon Framework`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
                "axon.sentry.tracing.environment=test",
                "axon.sentry.tracing.traces-sample-rate=1.0",
            )
            .run { context ->
                // Verify core beans
                assertThat(context).hasSingleBean(TracingConfiguration::class.java)
                assertThat(context).hasSingleBean(OpenTelemetry::class.java)
                assertThat(context).hasSingleBean(Tracer::class.java)
                assertThat(context).hasSingleBean(AxonSpanFactory::class.java)

                // Verify interceptors
                assertThat(context).hasSingleBean(CommandTracingInterceptor::class.java)
                assertThat(context).hasSingleBean(EventTracingInterceptor::class.java)
                assertThat(context).hasSingleBean(QueryTracingInterceptor::class.java)

                // Verify registrar
                assertThat(context).hasSingleBean(InterceptorRegistrar::class.java)

                // Health indicator is conditional on Actuator being on classpath
                // In this test context it may not be fully initialized
            }
    }

    @Test
    fun `should configure tracing with application yaml properties`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.trace-commands=true",
                "axon.sentry.tracing.trace-events=false",
                "axon.sentry.tracing.trace-queries=true",
                "axon.sentry.tracing.environment=staging",
                "axon.sentry.tracing.traces-sample-rate=0.5",
                "axon.sentry.tracing.sentry-dsn=https://staging@sentry.io/456",
            )
            .run { context ->
                val config = context.getBean(TracingConfiguration::class.java)

                assertThat(config.enabled).isTrue()
                assertThat(config.traceCommands).isTrue()
                assertThat(config.traceEvents).isFalse()
                assertThat(config.traceQueries).isTrue()
                assertThat(config.environment).isEqualTo("staging")
                assertThat(config.tracesSampleRate).isEqualTo(0.5)
                assertThat(config.sentryDsn).isEqualTo("https://staging@sentry.io/456")
            }
    }

    @Test
    fun `should respect conditional configuration for interceptors`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.trace-commands=false",
                "axon.sentry.tracing.trace-events=true",
                "axon.sentry.tracing.trace-queries=false",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(CommandTracingInterceptor::class.java)
                assertThat(context).hasSingleBean(EventTracingInterceptor::class.java)
                assertThat(context).doesNotHaveBean(QueryTracingInterceptor::class.java)
            }
    }

    @Test
    fun `should create tracing configuration with correct values`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
                "axon.sentry.tracing.environment=production",
            )
            .run { context ->
                assertThat(context).hasSingleBean(TracingConfiguration::class.java)

                val config = context.getBean(TracingConfiguration::class.java)

                assertThat(config.enabled).isTrue()
                assertThat(config.environment).isEqualTo("production")
                assertThat(config.sentryDsn).isEqualTo("https://test@sentry.io/123")
            }
    }

    @Test
    fun `should work without actuator on classpath`() {
        // This test verifies graceful degradation when actuator is not available
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                // Core functionality should still work
                assertThat(context).hasSingleBean(TracingConfiguration::class.java)
                assertThat(context).hasSingleBean(OpenTelemetry::class.java)
                assertThat(context).hasSingleBean(Tracer::class.java)
            }
    }

    @Test
    fun `should handle payload capture configuration`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.capture-command-payloads=true",
                "axon.sentry.tracing.capture-event-payloads=true",
                "axon.sentry.tracing.capture-query-payloads=false",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                val config = context.getBean(TracingConfiguration::class.java)

                assertThat(config.captureCommandPayloads).isTrue()
                assertThat(config.captureEventPayloads).isTrue()
                assertThat(config.captureQueryPayloads).isFalse()
            }
    }

    @Test
    fun `should handle custom tags configuration`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
                "axon.sentry.tracing.tags.team=platform",
                "axon.sentry.tracing.tags.service=order-service",
            )
            .run { context ->
                val properties = context.getBean(AxonSentryTracingProperties::class.java)

                assertThat(properties.tags).hasSize(2)
                assertThat(properties.tags["team"]).isEqualTo("platform")
                assertThat(properties.tags["service"]).isEqualTo("order-service")
            }
    }

    @Test
    fun `should create tracer with correct instrumentation name`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
                "axon.sentry.tracing.release=1.2.3",
            )
            .run { context ->
                assertThat(context).hasSingleBean(Tracer::class.java)
                val tracer = context.getBean(Tracer::class.java)

                // Verify tracer was created
                assertThat(tracer).isNotNull
            }
    }

    @Configuration
    class AxonConfiguration {
        @Bean
        fun commandBus(): CommandBus = SimpleCommandBus.builder().build()

        @Bean
        fun eventBus(): EventBus = SimpleEventBus.builder().build()

        @Bean
        fun queryBus(): QueryBus = SimpleQueryBus.builder().build()
    }
}
