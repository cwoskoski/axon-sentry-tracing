package io.github.axonsentry.spring

import io.github.axonsentry.axon.AxonSpanFactory
import io.github.axonsentry.axon.CommandTracingInterceptor
import io.github.axonsentry.axon.EventTracingInterceptor
import io.github.axonsentry.axon.QueryTracingInterceptor
import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Unit tests for AxonSentryTracingAutoConfiguration.
 *
 * These tests verify conditional bean creation and configuration behavior.
 */
class AxonSentryTracingAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AxonSentryTracingAutoConfiguration::class.java))

    @Test
    fun `should not auto-configure when tracing is disabled`() {
        contextRunner
            .withPropertyValues("axon.sentry.tracing.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(TracingConfiguration::class.java)
                assertThat(context).doesNotHaveBean(OpenTelemetry::class.java)
                assertThat(context).doesNotHaveBean(Tracer::class.java)
                assertThat(context).doesNotHaveBean(AxonSpanFactory::class.java)
            }
    }

    @Test
    fun `should not create command interceptor when command tracing is disabled`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.trace-commands=false",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(CommandTracingInterceptor::class.java)
            }
    }

    @Test
    fun `should not create event interceptor when event tracing is disabled`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.trace-events=false",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(EventTracingInterceptor::class.java)
            }
    }

    @Test
    fun `should not create query interceptor when query tracing is disabled`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.trace-queries=false",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(QueryTracingInterceptor::class.java)
            }
    }

    @Test
    fun `should respect custom TracingConfiguration bean`() {
        contextRunner
            .withUserConfiguration(CustomConfigurationConfig::class.java)
            .withPropertyValues("axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123")
            .run { context ->
                assertThat(context).hasSingleBean(TracingConfiguration::class.java)
                val config = context.getBean(TracingConfiguration::class.java)
                assertThat(config.environment).isEqualTo("custom-environment")
            }
    }

    @Test
    fun `should create InterceptorRegistrar bean`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).hasSingleBean(InterceptorRegistrar::class.java)
            }
    }

    @Test
    fun `should bind properties from application properties`() {
        contextRunner
            .withPropertyValues(
                "axon.sentry.tracing.enabled=true",
                "axon.sentry.tracing.environment=staging",
                "axon.sentry.tracing.traces-sample-rate=0.5",
                "axon.sentry.tracing.sentry-dsn=https://test@sentry.io/123",
            )
            .run { context ->
                assertThat(context).hasSingleBean(AxonSentryTracingProperties::class.java)
                val properties = context.getBean(AxonSentryTracingProperties::class.java)
                assertThat(properties.enabled).isTrue()
                assertThat(properties.environment).isEqualTo("staging")
                assertThat(properties.tracesSampleRate).isEqualTo(0.5)
            }
    }

    @Configuration
    class CustomConfigurationConfig {
        @Bean
        fun tracingConfiguration(): TracingConfiguration {
            return TracingConfiguration(
                environment = "custom-environment",
                sentryDsn = "https://custom@sentry.io/999",
            )
        }
    }
}
