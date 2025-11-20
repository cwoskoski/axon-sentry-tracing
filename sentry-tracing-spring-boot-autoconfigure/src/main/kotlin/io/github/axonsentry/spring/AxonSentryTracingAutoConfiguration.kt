package io.github.axonsentry.spring

import io.github.axonsentry.axon.AxonSpanFactory
import io.github.axonsentry.axon.CommandTracingInterceptor
import io.github.axonsentry.axon.EventTracingInterceptor
import io.github.axonsentry.axon.QueryTracingInterceptor
import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.sentry.SentryTracingInitializer
import io.github.axonsentry.tracing.AttributeApplier
import io.github.axonsentry.tracing.SpanKindResolver
import io.github.axonsentry.tracing.SpanNameGenerator
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import jakarta.annotation.PreDestroy
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

/**
 * Spring Boot auto-configuration for Axon-Sentry tracing integration.
 *
 * Automatically configures:
 * - OpenTelemetry SDK with Sentry integration
 * - Axon message interceptors for commands, events, and queries
 * - Tracing components (span factories, enrichers)
 * - Health indicators for monitoring
 *
 * This auto-configuration is active when:
 * - Axon Framework is on the classpath (CommandBus class available)
 * - OpenTelemetry is on the classpath
 * - Property `axon.sentry.tracing.enabled` is true (default)
 *
 * Example usage:
 * ```yaml
 * axon:
 *   sentry:
 *     tracing:
 *       enabled: true
 *       sentry-dsn: https://key@sentry.io/project
 *       environment: production
 *       traces-sample-rate: 0.1
 * ```
 *
 * The auto-configuration will:
 * 1. Initialize Sentry SDK with OpenTelemetry
 * 2. Create and register tracing interceptors with Axon buses
 * 3. Configure span factories for creating traces
 * 4. Enable health indicators for monitoring
 *
 * All beans can be overridden by providing your own bean definitions.
 *
 * @property properties Configuration properties bound from application.properties/yaml
 *
 * @since 1.0.0
 */
@Suppress("TooManyFunctions") // Auto-configuration classes naturally have many bean factory methods
@AutoConfiguration
@ConditionalOnClass(CommandBus::class, EventBus::class, QueryBus::class, OpenTelemetry::class)
@ConditionalOnProperty(
    prefix = "axon.sentry.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(AxonSentryTracingProperties::class)
class AxonSentryTracingAutoConfiguration(
    private val properties: AxonSentryTracingProperties,
) {
    private val logger = LoggerFactory.getLogger(AxonSentryTracingAutoConfiguration::class.java)

    @Volatile
    private var initializer: SentryTracingInitializer? = null

    /**
     * Creates TracingConfiguration domain model from Spring Boot properties.
     *
     * @return TracingConfiguration instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun tracingConfiguration(): TracingConfiguration {
        val config = properties.toTracingConfiguration()
        logger.debug("Created TracingConfiguration: enabled=${config.enabled}, environment=${config.environment}")
        return config
    }

    /**
     * Initializes OpenTelemetry SDK with Sentry integration.
     *
     * This bean creates the OpenTelemetry SDK instance configured to export
     * spans to Sentry. The SDK is initialized lazily and will validate that
     * Sentry DSN is configured when tracing is enabled.
     *
     * @param configuration The tracing configuration
     * @return Configured OpenTelemetry instance
     * @throws IllegalStateException if Sentry DSN is not configured when tracing is enabled
     */
    @Bean
    @ConditionalOnMissingBean
    fun openTelemetry(configuration: TracingConfiguration): OpenTelemetry {
        logger.info("Initializing Axon-Sentry tracing integration")
        logger.info(
            "Configuration: environment=${configuration.environment}, " +
                "sampleRate=${configuration.tracesSampleRate}",
        )

        initializer = SentryTracingInitializer.getInstance(configuration)
        val otel = initializer!!.initialize()

        logger.info("Axon-Sentry tracing initialized successfully")
        return otel
    }

    /**
     * Creates OpenTelemetry Tracer for creating spans.
     *
     * The tracer is named "axon-sentry-tracing" with version from properties.
     *
     * @param openTelemetry The OpenTelemetry SDK instance
     * @return Configured Tracer
     */
    @Bean
    @ConditionalOnMissingBean
    fun tracer(openTelemetry: OpenTelemetry): Tracer {
        val version = properties.release ?: "unknown"
        logger.debug("Creating tracer: instrumentationName=axon-sentry-tracing, version=$version")
        return openTelemetry.getTracer("axon-sentry-tracing", version)
    }

    /**
     * Creates SpanNameGenerator for generating span names.
     *
     * @return SpanNameGenerator instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun spanNameGenerator(): SpanNameGenerator {
        logger.debug("Creating SpanNameGenerator")
        return SpanNameGenerator()
    }

    /**
     * Creates SpanKindResolver for determining span kinds.
     *
     * @return SpanKindResolver instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun spanKindResolver(): SpanKindResolver {
        logger.debug("Creating SpanKindResolver")
        return SpanKindResolver()
    }

    /**
     * Creates AttributeApplier for applying attributes to spans.
     *
     * @param configuration The tracing configuration
     * @return AttributeApplier instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun attributeApplier(configuration: TracingConfiguration): AttributeApplier {
        logger.debug("Creating AttributeApplier")
        return AttributeApplier(configuration)
    }

    /**
     * Creates AxonSpanFactory for creating Axon-specific spans.
     *
     * The span factory encapsulates all span creation logic for
     * commands, events, queries, and their handlers.
     *
     * @param tracer The OpenTelemetry tracer
     * @param configuration The tracing configuration
     * @param spanNameGenerator Generator for span names
     * @param spanKindResolver Resolver for span kinds
     * @param attributeApplier Applier for span attributes
     * @return AxonSpanFactory instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun axonSpanFactory(
        tracer: Tracer,
        configuration: TracingConfiguration,
        spanNameGenerator: SpanNameGenerator,
        spanKindResolver: SpanKindResolver,
        attributeApplier: AttributeApplier,
    ): AxonSpanFactory {
        logger.debug("Creating AxonSpanFactory")
        return AxonSpanFactory(tracer, configuration, spanNameGenerator, spanKindResolver, attributeApplier)
    }

    /**
     * Creates CommandTracingInterceptor for command tracing.
     *
     * Active when `axon.sentry.tracing.trace-commands` is true (default).
     *
     * @param spanFactory Factory for creating command spans
     * @param configuration The tracing configuration
     * @return CommandTracingInterceptor instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-commands"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun commandTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration,
    ): CommandTracingInterceptor {
        logger.debug("Creating CommandTracingInterceptor")
        return CommandTracingInterceptor(spanFactory, configuration)
    }

    /**
     * Creates EventTracingInterceptor for event tracing.
     *
     * Active when `axon.sentry.tracing.trace-events` is true (default).
     *
     * @param spanFactory Factory for creating event spans
     * @param configuration The tracing configuration
     * @return EventTracingInterceptor instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-events"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun eventTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration,
    ): EventTracingInterceptor {
        logger.debug("Creating EventTracingInterceptor")
        return EventTracingInterceptor(spanFactory, configuration)
    }

    /**
     * Creates QueryTracingInterceptor for query tracing.
     *
     * Active when `axon.sentry.tracing.trace-queries` is true (default).
     *
     * @param spanFactory Factory for creating query spans
     * @param configuration The tracing configuration
     * @return QueryTracingInterceptor instance
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "axon.sentry.tracing",
        name = ["trace-queries"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun queryTracingInterceptor(
        spanFactory: AxonSpanFactory,
        configuration: TracingConfiguration,
    ): QueryTracingInterceptor {
        logger.debug("Creating QueryTracingInterceptor")
        return QueryTracingInterceptor(spanFactory, configuration)
    }

    /**
     * Creates InterceptorRegistrar to register interceptors with Axon buses.
     *
     * The registrar handles auto-wiring optional Axon components and
     * registers the appropriate interceptors.
     *
     * @return InterceptorRegistrar instance
     */
    @Bean
    @ConditionalOnMissingBean
    fun interceptorRegistrar(): InterceptorRegistrar {
        logger.debug("Creating InterceptorRegistrar")
        return InterceptorRegistrar()
    }

    /**
     * Shuts down tracing and flushes pending spans on application shutdown.
     *
     * Ensures all pending spans are exported to Sentry before the
     * application terminates.
     */
    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Axon-Sentry tracing")
        try {
            initializer?.shutdown()
            logger.info("Axon-Sentry tracing shutdown complete")
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Error during tracing shutdown", e)
        }
    }
}
