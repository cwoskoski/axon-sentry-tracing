package io.github.axonsentry.spring

import io.github.axonsentry.config.TracingConfiguration
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for Axon-Sentry tracing integration.
 *
 * Maps application.properties/yaml to TracingConfiguration domain model.
 * All properties can be configured via `axon.sentry.tracing.*` prefix.
 *
 * Example configuration:
 * ```yaml
 * axon:
 *   sentry:
 *     tracing:
 *       enabled: true
 *       sentry-dsn: https://key@sentry.io/project
 *       environment: production
 *       traces-sample-rate: 0.1
 *       trace-commands: true
 *       trace-events: true
 *       trace-queries: true
 * ```
 *
 * @property enabled Master switch for all tracing functionality (default: true)
 * @property traceCommands Enable command tracing (default: true)
 * @property traceEvents Enable event tracing (default: true)
 * @property traceQueries Enable query tracing (default: true)
 * @property traceEventProcessors Enable event processor tracing (default: true)
 * @property traceSagas Enable saga tracing (default: true)
 * @property captureCommandPayloads Capture command payloads in spans (default: false, be cautious with sensitive data)
 * @property captureEventPayloads Capture event payloads in spans (default: false)
 * @property captureQueryPayloads Capture query payloads in spans (default: false)
 * @property sentryDsn Sentry Data Source Name (can also use SENTRY_DSN environment variable)
 * @property environment Deployment environment (e.g., development, staging, production)
 * @property tracesSampleRate Sample rate for traces from 0.0 to 1.0 (1.0 = trace everything)
 * @property attachStacktrace Attach stack traces to Sentry events (default: true)
 * @property release Release version to report to Sentry
 * @property tags Additional tags to add to all spans
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "axon.sentry.tracing")
@Validated
class AxonSentryTracingProperties {
    /**
     * Enable or disable tracing entirely.
     * When disabled, no tracing interceptors are registered.
     */
    var enabled: Boolean = true

    /**
     * Enable command tracing.
     * Creates spans for command dispatch and handling.
     */
    var traceCommands: Boolean = true

    /**
     * Enable event tracing.
     * Creates spans for event publication and handling.
     */
    var traceEvents: Boolean = true

    /**
     * Enable query tracing.
     * Creates spans for query dispatch and handling.
     */
    var traceQueries: Boolean = true

    /**
     * Enable event processor tracing.
     * Enriches event spans with processor information.
     */
    var traceEventProcessors: Boolean = true

    /**
     * Enable saga tracing.
     * Creates spans for saga invocations.
     */
    var traceSagas: Boolean = true

    /**
     * Capture command payloads in spans.
     * WARNING: Be cautious with sensitive data. Payloads may contain
     * personally identifiable information (PII) or secrets.
     */
    var captureCommandPayloads: Boolean = false

    /**
     * Capture event payloads in spans.
     * Consider data sensitivity before enabling.
     */
    var captureEventPayloads: Boolean = false

    /**
     * Capture query payloads in spans.
     * Consider data sensitivity before enabling.
     */
    var captureQueryPayloads: Boolean = false

    /**
     * Sentry Data Source Name (DSN).
     * Can also be configured via SENTRY_DSN environment variable.
     * Required when tracing is enabled.
     */
    var sentryDsn: String? = System.getenv("SENTRY_DSN")

    /**
     * Deployment environment (e.g., development, staging, production).
     * Used to categorize traces in Sentry.
     */
    var environment: String = "development"

    /**
     * Sample rate for traces (0.0 to 1.0).
     * - 1.0 = trace 100% of requests (useful for development/staging)
     * - 0.1 = trace 10% of requests (common for production)
     * - 0.0 = disable all tracing
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    var tracesSampleRate: Double = 1.0

    /**
     * Attach stack traces to Sentry events.
     * Provides additional context for errors.
     */
    var attachStacktrace: Boolean = true

    /**
     * Release version to report to Sentry.
     * Helps track which version of your application produced traces.
     * If not set, will use "unknown".
     */
    var release: String? = null

    /**
     * Additional tags to add to all spans.
     * Useful for categorizing traces by team, service, etc.
     * Example: mapOf("team" to "payments", "service" to "order-service")
     */
    var tags: Map<String, String> = emptyMap()

    /**
     * Converts Spring Boot properties to TracingConfiguration domain model.
     *
     * This enables separation between Spring Boot configuration layer
     * and the core domain configuration.
     *
     * @return TracingConfiguration instance
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
            attachStacktrace = attachStacktrace,
        )
    }
}
