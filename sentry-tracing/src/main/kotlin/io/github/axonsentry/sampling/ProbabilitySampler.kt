package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult

/**
 * Probability-based sampler that samples a fixed percentage of traces.
 *
 * Uses hash-based deterministic sampling to ensure consistent sampling decisions
 * for the same trace ID across distributed services. This is critical for maintaining
 * complete trace context in distributed systems.
 *
 * The sampling decision is made based on the hash of the trace ID, ensuring:
 * - Deterministic: Same trace ID always gets the same decision
 * - Distributed: All services make the same decision for a trace
 * - Unbiased: Traces are sampled uniformly across the ID space
 *
 * Example usage:
 * ```kotlin
 * // Sample 10% of traces
 * val sampler = ProbabilitySampler(0.1)
 *
 * // Sample all traces (useful for development)
 * val sampler = ProbabilitySampler(1.0)
 *
 * // Disable sampling (errors still reported)
 * val sampler = ProbabilitySampler(0.0)
 * ```
 *
 * @property probability Sample rate from 0.0 (sample nothing) to 1.0 (sample everything)
 * @throws IllegalArgumentException if probability is not in range [0.0, 1.0]
 *
 * @since 1.0.0
 */
class ProbabilitySampler(
    private val probability: Double,
) : Sampler {
    init {
        require(probability in 0.0..1.0) {
            "Probability must be between 0.0 and 1.0, got $probability"
        }
    }

    companion object {
        private const val HASH_MODULUS = 100000L
        private const val HASH_PRIME = 31L
        private const val KEEP_POSITIVE_MASK = 0x7FFFFFFFFFFFFFFFL
    }

    /**
     * Determines whether a span should be sampled based on trace ID hash.
     *
     * The algorithm:
     * 1. Hash the trace ID to get a uniform distribution
     * 2. Map hash to range [0, 1)
     * 3. Sample if mapped value < probability
     *
     * This ensures deterministic sampling - same trace ID always gets same decision.
     *
     * @param parentContext The parent context (not used for probability-based sampling)
     * @param traceId The trace ID (used for deterministic hashing)
     * @param name The span name (not used for probability-based sampling)
     * @param spanKind The span kind (not used for probability-based sampling)
     * @param attributes The span attributes (not used for probability-based sampling)
     * @param parentLinks The parent links (not used for probability-based sampling)
     * @return Sampling result indicating whether to sample this span
     */
    override fun shouldSample(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: MutableList<LinkData>,
    ): SamplingResult {
        // Use a better hash function for more uniform distribution
        // Convert trace ID to long for better distribution than String.hashCode()
        val hash = computeHash(traceId)

        // Map hash to [0, 1) range using a large prime modulus
        val normalizedHash = (hash % HASH_MODULUS) / HASH_MODULUS.toDouble()

        // Sample if normalized hash is less than probability
        val shouldSample = normalizedHash < probability

        return if (shouldSample) {
            SamplingResult.recordAndSample()
        } else {
            SamplingResult.drop()
        }
    }

    /**
     * Computes a hash for the trace ID that provides better distribution
     * than String.hashCode().
     *
     * Uses a simple but effective hash function that combines characters
     * with prime number multipliers for good distribution.
     *
     * @param traceId The trace ID to hash
     * @return Positive long hash value
     */
    private fun computeHash(traceId: String): Long {
        var hash = 0L
        for (char in traceId) {
            hash = (hash * HASH_PRIME + char.code) and KEEP_POSITIVE_MASK // Keep positive
        }
        return hash
    }

    /**
     * Returns a description of this sampler for debugging and logging.
     *
     * @return String description including probability value
     */
    override fun getDescription(): String = "ProbabilitySampler{$probability}"
}
