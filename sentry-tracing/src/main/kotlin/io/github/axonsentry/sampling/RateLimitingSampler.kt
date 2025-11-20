package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Rate-limiting sampler that limits the number of traces per second using a token bucket algorithm.
 *
 * This sampler ensures that trace collection doesn't overwhelm your Sentry quotas or impact
 * application performance during traffic spikes. It uses a token bucket algorithm to:
 * - Allow bursts up to the bucket capacity
 * - Refill tokens at a steady rate
 * - Drop traces when no tokens are available
 *
 * The token bucket algorithm provides:
 * - **Smooth rate limiting**: Doesn't suddenly block all traffic
 * - **Burst tolerance**: Handles short traffic spikes gracefully
 * - **Predictable costs**: Guarantees maximum traces per time period
 * - **Thread-safe**: Safe for concurrent use across multiple threads
 *
 * Example usage:
 * ```kotlin
 * // Allow up to 100 traces per second
 * val sampler = RateLimitingSampler(tracesPerSecond = 100)
 *
 * // Allow 10 traces per second with custom burst capacity
 * val sampler = RateLimitingSampler(tracesPerSecond = 10, burstCapacity = 20)
 * ```
 *
 * @property tracesPerSecond Maximum number of traces to sample per second
 * @property burstCapacity Maximum burst capacity (defaults to tracesPerSecond)
 * @throws IllegalArgumentException if tracesPerSecond is not positive
 *
 * @since 1.0.0
 */
class RateLimitingSampler(
    private val tracesPerSecond: Int,
    private val burstCapacity: Int = tracesPerSecond,
) : Sampler {
    private val tokensPerNano = tracesPerSecond.toDouble() / 1_000_000_000.0
    private val availableTokens = AtomicReference(burstCapacity.toDouble())
    private val lastRefillTime = AtomicLong(System.nanoTime())

    init {
        require(tracesPerSecond > 0) {
            "tracesPerSecond must be positive, got $tracesPerSecond"
        }
        require(burstCapacity > 0) {
            "burstCapacity must be positive, got $burstCapacity"
        }
    }

    /**
     * Determines whether a span should be sampled based on available tokens.
     *
     * The token bucket algorithm:
     * 1. Refill tokens based on time elapsed since last refill
     * 2. Try to consume one token
     * 3. Sample if token was available, drop otherwise
     *
     * This is thread-safe and lock-free using atomic operations.
     *
     * @param parentContext The parent context (not used for rate-limiting)
     * @param traceId The trace ID (not used for rate-limiting)
     * @param name The span name (not used for rate-limiting)
     * @param spanKind The span kind (not used for rate-limiting)
     * @param attributes The span attributes (not used for rate-limiting)
     * @param parentLinks The parent links (not used for rate-limiting)
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
        refillTokens()

        // Try to consume a token atomically
        var hasToken = false
        availableTokens.updateAndGet { currentTokens ->
            if (currentTokens >= 1.0) {
                hasToken = true
                currentTokens - 1.0
            } else {
                hasToken = false
                currentTokens
            }
        }

        return if (hasToken) {
            SamplingResult.recordAndSample()
        } else {
            SamplingResult.drop()
        }
    }

    /**
     * Refills tokens based on elapsed time since last refill.
     *
     * Uses atomic operations to ensure thread-safety without locks.
     */
    private fun refillTokens() {
        val now = System.nanoTime()
        val lastRefill = lastRefillTime.get()
        val elapsed = now - lastRefill

        if (elapsed > 0) {
            // Calculate tokens to add based on elapsed time
            val tokensToAdd = elapsed * tokensPerNano

            if (tokensToAdd >= 1.0) {
                // Try to update last refill time (only one thread succeeds)
                if (lastRefillTime.compareAndSet(lastRefill, now)) {
                    // Add tokens up to burst capacity
                    availableTokens.updateAndGet { currentTokens ->
                        minOf(currentTokens + tokensToAdd, burstCapacity.toDouble())
                    }
                }
            }
        }
    }

    /**
     * Returns a description of this sampler for debugging and logging.
     *
     * @return String description including rate limit
     */
    override fun getDescription(): String =
        "RateLimitingSampler{" +
            "tracesPerSecond=$tracesPerSecond, " +
            "burstCapacity=$burstCapacity" +
            "}"
}
