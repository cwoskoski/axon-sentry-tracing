package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.opentelemetry.SentrySpanProcessor
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Initializes Sentry SDK with OpenTelemetry tracing integration.
 *
 * This class configures both Sentry and OpenTelemetry to work together,
 * enabling Axon Framework traces to appear in Sentry's performance monitoring.
 *
 * @property configuration The tracing configuration to apply
 * @since 1.0.0
 */
class SentryTracingInitializer(
    private val configuration: TracingConfiguration,
) {
    private val logger = LoggerFactory.getLogger(SentryTracingInitializer::class.java)

    private var openTelemetry: OpenTelemetry? = null
    private var tracerProvider: SdkTracerProvider? = null

    /**
     * Initializes Sentry and OpenTelemetry with configured options.
     *
     * @return Configured OpenTelemetry instance
     * @throws IllegalStateException if Sentry DSN is not configured when tracing is enabled
     */
    fun initialize(): OpenTelemetry {
        if (!configuration.enabled) {
            logger.warn("Tracing is disabled in configuration")
            return OpenTelemetry.noop()
        }

        requireNotNull(configuration.sentryDsn) {
            "Sentry DSN must be configured when tracing is enabled"
        }

        // Initialize Sentry SDK
        Sentry.init { options ->
            configureSentryOptions(options)
        }

        // Create Sentry's native OpenTelemetry span processor
        val sentrySpanProcessor = SentrySpanProcessor()

        // Build tracer provider with Sentry processor
        tracerProvider =
            SdkTracerProvider.builder()
                .addSpanProcessor(sentrySpanProcessor)
                .setSampler(Sampler.traceIdRatioBased(configuration.tracesSampleRate))
                .setResource(Resource.getDefault())
                .build()

        // Build OpenTelemetry SDK
        openTelemetry =
            OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider!!)
                .build()

        logger.info("Sentry tracing initialized with DSN: ${maskDsn(configuration.sentryDsn)}")

        return openTelemetry!!
    }

    /**
     * Configures Sentry options from TracingConfiguration.
     */
    private fun configureSentryOptions(options: SentryOptions) {
        options.dsn = configuration.sentryDsn
        options.environment = configuration.environment
        options.tracesSampleRate = configuration.tracesSampleRate
        options.isAttachStacktrace = configuration.attachStacktrace
        options.enableTracing = true

        // Configure before-send hook
        options.setBeforeSend { event, _ ->
            // Allow event to be sent
            event
        }

        // Configure before-breadcrumb hook
        options.setBeforeBreadcrumb { breadcrumb, _ ->
            // Allow breadcrumb to be added
            breadcrumb
        }
    }

    /**
     * Shuts down tracing and flushes pending spans.
     *
     * This method should be called during application shutdown to ensure
     * all pending spans are exported to Sentry.
     */
    fun shutdown() {
        logger.info("Shutting down Sentry tracing...")
        tracerProvider?.shutdown()
        Sentry.close()
        logger.info("Sentry tracing shutdown complete")
    }

    /**
     * Flushes pending spans to Sentry.
     *
     * @param timeout Maximum time to wait for flush
     * @param unit Time unit for timeout
     * @return true if flush completed within timeout, false otherwise
     */
    fun flush(
        timeout: Long = 5,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): Boolean {
        val flushResult = tracerProvider?.forceFlush()?.join(timeout, unit)
        return flushResult?.isSuccess ?: false
    }

    /**
     * Returns the configured OpenTelemetry instance.
     *
     * @return OpenTelemetry instance or null if not initialized
     */
    fun getOpenTelemetry(): OpenTelemetry? = openTelemetry

    /**
     * Masks the sensitive key portion of DSN for logging.
     */
    private fun maskDsn(dsn: String): String {
        return dsn.replaceFirst(Regex("//[^@]+@"), "//***@")
    }

    companion object {
        @Volatile
        private var instance: SentryTracingInitializer? = null

        /**
         * Gets or creates the singleton instance.
         *
         * @param configuration The tracing configuration
         * @return Singleton instance of SentryTracingInitializer
         */
        fun getInstance(configuration: TracingConfiguration): SentryTracingInitializer {
            return instance ?: synchronized(this) {
                instance ?: SentryTracingInitializer(configuration).also { instance = it }
            }
        }

        /**
         * Resets the singleton instance (primarily for testing).
         */
        internal fun reset() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }
    }
}
