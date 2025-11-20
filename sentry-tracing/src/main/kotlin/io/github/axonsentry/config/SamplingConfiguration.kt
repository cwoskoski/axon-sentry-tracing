package io.github.axonsentry.config

/**
 * Configuration for trace sampling strategies.
 *
 * Sampling controls which traces are sent to Sentry, allowing you to:
 * - Reduce costs by sampling a percentage of traces
 * - Control rate limits to prevent overwhelming Sentry quotas
 * - Combine multiple sampling strategies for complex scenarios
 *
 * Example configurations:
 * ```kotlin
 * // Sample 10% of traces
 * SamplingConfiguration(probability = 0.1)
 *
 * // Rate limit to 100 traces per second
 * SamplingConfiguration(tracesPerSecond = 100)
 *
 * // Sample 10% AND limit to 50 per second
 * SamplingConfiguration(
 *     probability = 0.1,
 *     tracesPerSecond = 50
 * )
 * ```
 *
 * @property enabled Enable sampling (default: true)
 * @property probability Probability-based sampling rate (0.0 to 1.0, null = disabled)
 * @property tracesPerSecond Rate limit in traces per second (null = no limit)
 * @property combineStrategy How to combine multiple samplers: "AND" or "OR" (default: "AND")
 *
 * @since 1.0.0
 */
data class SamplingConfiguration(
    val enabled: Boolean = true,
    val probability: Double? = null,
    val tracesPerSecond: Int? = null,
    val combineStrategy: CombineStrategy = CombineStrategy.AND,
) {
    init {
        probability?.let {
            require(it in 0.0..1.0) {
                "probability must be between 0.0 and 1.0, got $it"
            }
        }
        tracesPerSecond?.let {
            require(it > 0) {
                "tracesPerSecond must be positive, got $it"
            }
        }
    }

    /**
     * Returns true if any sampling strategy is configured.
     */
    fun hasSamplingStrategy(): Boolean {
        return enabled && (probability != null || tracesPerSecond != null)
    }

    /**
     * Strategy for combining multiple samplers.
     */
    enum class CombineStrategy {
        /** All samplers must accept (more restrictive) */
        AND,

        /** Any sampler accepting is sufficient (less restrictive) */
        OR,
    }

    companion object {
        /**
         * Default configuration with sampling disabled.
         */
        fun default(): SamplingConfiguration = SamplingConfiguration()

        /**
         * Development configuration: sample all traces.
         */
        fun development(): SamplingConfiguration = SamplingConfiguration(probability = 1.0)

        /**
         * Production configuration: sample 10% up to 100 traces/sec.
         */
        fun production(): SamplingConfiguration =
            SamplingConfiguration(
                probability = 0.1,
                tracesPerSecond = 100,
                combineStrategy = CombineStrategy.AND,
            )

        /**
         * High-traffic configuration: sample 1% up to 50 traces/sec.
         */
        fun highTraffic(): SamplingConfiguration =
            SamplingConfiguration(
                probability = 0.01,
                tracesPerSecond = 50,
                combineStrategy = CombineStrategy.AND,
            )
    }
}
