package io.github.axonsentry.spring

import io.github.axonsentry.config.TracingConfiguration
import io.sentry.Sentry
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component

/**
 * Spring Boot Actuator health indicator for Axon-Sentry tracing.
 *
 * Reports the status of the tracing integration in the application's
 * health endpoint. This provides visibility into:
 * - Whether tracing is enabled
 * - Which components are being traced
 * - Sentry connection status
 * - Current configuration settings
 *
 * The health indicator is automatically registered when Spring Boot
 * Actuator is on the classpath.
 *
 * Example health response:
 * ```json
 * {
 *   "status": "UP",
 *   "components": {
 *     "tracingHealthIndicator": {
 *       "status": "UP",
 *       "details": {
 *         "enabled": true,
 *         "commands": true,
 *         "events": true,
 *         "queries": true,
 *         "eventProcessors": true,
 *         "sagas": true,
 *         "sampleRate": 1.0,
 *         "environment": "development",
 *         "sentryConfigured": true,
 *         "sentryConnected": true
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * Status meanings:
 * - UP: Tracing is enabled and Sentry is connected
 * - DISABLED: Tracing is disabled in configuration
 * - DOWN: Tracing is enabled but Sentry connection failed
 *
 * @property configuration The tracing configuration to report on
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(HealthIndicator::class)
class TracingHealthIndicator(
    private val configuration: TracingConfiguration,
) : HealthIndicator {
    /**
     * Checks and reports the health of the tracing integration.
     *
     * This method:
     * 1. Checks if tracing is enabled
     * 2. Reports configuration details
     * 3. Attempts to verify Sentry connectivity
     * 4. Returns appropriate health status
     *
     * The health check does not fail if Sentry is unreachable to avoid
     * marking the application as unhealthy due to external service issues.
     *
     * @return Health status with tracing details
     */
    override fun health(): Health {
        val builder =
            if (configuration.enabled) {
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
                checkSentryConnection()
            }
            .build()
    }

    /**
     * Checks Sentry connectivity and adds details to health.
     *
     * This is a best-effort check that won't fail the health check
     * even if Sentry is unreachable. It adds:
     * - sentryConnected: true/false
     * - sentryError: error message if connection failed
     */
    private fun Health.Builder.checkSentryConnection() {
        try {
            val isEnabled = Sentry.isEnabled()
            withDetail("sentryConnected", isEnabled)

            if (!isEnabled && configuration.enabled && configuration.sentryDsn != null) {
                withDetail("sentryWarning", "Sentry SDK is not enabled despite configuration")
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Don't fail health check, but report the error
            withDetail("sentryConnected", false)
            withDetail("sentryError", e.message ?: "Unknown error")
        }
    }
}
