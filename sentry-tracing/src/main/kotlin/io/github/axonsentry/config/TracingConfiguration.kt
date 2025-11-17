package io.github.axonsentry.config

/**
 * Configuration for Axon-Sentry tracing integration.
 *
 * @property enabled Master switch for all tracing functionality
 * @property traceCommands Enable command tracing
 * @property traceEvents Enable event tracing
 * @property traceQueries Enable query tracing
 * @property traceEventProcessors Enable event processor tracing
 * @property traceSagas Enable saga tracing
 * @property captureCommandPayloads Include command payloads in spans (be cautious with sensitive data)
 * @property captureEventPayloads Include event payloads in spans
 * @property captureQueryPayloads Include query payloads in spans
 * @property sentryDsn Sentry Data Source Name for error reporting
 * @property environment Deployment environment (dev, staging, production)
 * @property tracesSampleRate Sample rate for traces (0.0 to 1.0)
 * @property attachStacktrace Include stacktraces in Sentry events
 * @property customAttributeProviders List of custom attribute providers for adding custom span attributes
 *
 * @since 1.0.0
 */
data class TracingConfiguration(
    val enabled: Boolean = true,
    val traceCommands: Boolean = true,
    val traceEvents: Boolean = true,
    val traceQueries: Boolean = true,
    val traceEventProcessors: Boolean = true,
    val traceSagas: Boolean = true,
    val captureCommandPayloads: Boolean = false,
    val captureEventPayloads: Boolean = false,
    val captureQueryPayloads: Boolean = false,
    val sentryDsn: String? = null,
    val environment: String = "development",
    val tracesSampleRate: Double = 1.0,
    val attachStacktrace: Boolean = true,
    val customAttributeProviders: List<CustomAttributeProvider> = emptyList(),
) {
    init {
        require(tracesSampleRate in 0.0..1.0) {
            "tracesSampleRate must be between 0.0 and 1.0, got $tracesSampleRate"
        }
    }

    /**
     * Creates a builder for fluent configuration.
     *
     * @return Builder initialized with this configuration's values
     */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Builder for fluent TracingConfiguration creation.
     */
    class Builder(config: TracingConfiguration = TracingConfiguration()) {
        var enabled: Boolean = config.enabled
        var traceCommands: Boolean = config.traceCommands
        var traceEvents: Boolean = config.traceEvents
        var traceQueries: Boolean = config.traceQueries
        var traceEventProcessors: Boolean = config.traceEventProcessors
        var traceSagas: Boolean = config.traceSagas
        var captureCommandPayloads: Boolean = config.captureCommandPayloads
        var captureEventPayloads: Boolean = config.captureEventPayloads
        var captureQueryPayloads: Boolean = config.captureQueryPayloads
        var sentryDsn: String? = config.sentryDsn
        var environment: String = config.environment
        var tracesSampleRate: Double = config.tracesSampleRate
        var attachStacktrace: Boolean = config.attachStacktrace
        private val customAttributeProviders: MutableList<CustomAttributeProvider> =
            config.customAttributeProviders.toMutableList()

        /**
         * Adds a custom attribute provider to the configuration.
         *
         * @param provider The custom attribute provider to add
         * @return This builder for method chaining
         */
        fun addAttributeProvider(provider: CustomAttributeProvider) =
            apply {
                customAttributeProviders.add(provider)
            }

        /**
         * Builds the TracingConfiguration.
         *
         * @return TracingConfiguration instance
         */
        fun build(): TracingConfiguration =
            TracingConfiguration(
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
                customAttributeProviders = customAttributeProviders.toList(),
            )
    }

    companion object {
        /**
         * Creates a configuration builder.
         *
         * @return New Builder instance
         */
        fun builder(): Builder = Builder()

        /**
         * Default configuration with all tracing enabled.
         *
         * @return Default TracingConfiguration
         */
        fun default(): TracingConfiguration = TracingConfiguration()

        /**
         * Minimal configuration with only errors tracked.
         * All tracing features are disabled.
         *
         * @return Errors-only TracingConfiguration
         */
        fun errorsOnly(): TracingConfiguration =
            TracingConfiguration(
                traceCommands = false,
                traceEvents = false,
                traceQueries = false,
                traceEventProcessors = false,
                traceSagas = false,
            )
    }
}

/**
 * Functional interface for providing custom span attributes.
 *
 * Implementations can extract custom attributes from Axon messages
 * to enrich tracing spans with application-specific data.
 *
 * @since 1.0.0
 */
fun interface CustomAttributeProvider {
    /**
     * Provides custom attributes for a given message.
     *
     * @param message The Axon message being traced
     * @return Map of attribute key-value pairs to add to the span
     */
    fun provideAttributes(message: Any): Map<String, String>
}
