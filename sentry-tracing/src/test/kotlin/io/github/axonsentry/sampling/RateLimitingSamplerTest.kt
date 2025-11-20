package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RateLimitingSampler].
 *
 * Tests rate-limiting based sampling with token bucket algorithm.
 */
class RateLimitingSamplerTest {
    @Test
    fun `should sample traces within rate limit`() {
        // Given
        val sampler = RateLimitingSampler(tracesPerSecond = 10)

        // When - Sample 10 traces (within limit)
        val results =
            (1..10).map {
                sampler.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - All should be sampled
        assertThat(results).allMatch { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
    }

    @Test
    fun `should drop traces exceeding rate limit`() {
        // Given
        val sampler = RateLimitingSampler(tracesPerSecond = 5)

        // When - Sample 10 traces (exceeds limit of 5)
        val results =
            (1..10).map {
                sampler.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - First 5 sampled, rest dropped
        val sampledCount = results.count { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
        assertThat(sampledCount).isEqualTo(5)

        val droppedCount = results.count { it.decision == SamplingDecision.DROP }
        assertThat(droppedCount).isEqualTo(5)
    }

    @Test
    fun `should refill tokens after time period`() {
        // Given
        val sampler = RateLimitingSampler(tracesPerSecond = 2)

        // When - Exhaust tokens
        repeat(2) {
            sampler.shouldSample(
                Context.root(),
                generateTraceId(it),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )
        }

        // Then - Next sample should be dropped (no tokens)
        val droppedResult =
            sampler.shouldSample(
                Context.root(),
                generateTraceId(100),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )
        assertThat(droppedResult.decision).isEqualTo(SamplingDecision.DROP)

        // When - Wait for refill (simulate by sleeping)
        Thread.sleep(1100) // Wait >1 second for tokens to refill

        // Then - Should be able to sample again
        val sampledResult =
            sampler.shouldSample(
                Context.root(),
                generateTraceId(101),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )
        assertThat(sampledResult.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    @Test
    fun `should handle burst capacity`() {
        // Given - Allow 10 traces per second with default burst
        val sampler = RateLimitingSampler(tracesPerSecond = 10)

        // When - Send burst of traces
        val results =
            (1..20).map {
                sampler.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - Should sample up to burst capacity (10 + some buffer)
        val sampledCount = results.count { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
        assertThat(sampledCount).isGreaterThanOrEqualTo(10).isLessThanOrEqualTo(15)
    }

    @Test
    fun `should throw exception when rate is zero`() {
        // Expect
        assertThatThrownBy {
            RateLimitingSampler(tracesPerSecond = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must be positive")
    }

    @Test
    fun `should throw exception when rate is negative`() {
        // Expect
        assertThatThrownBy {
            RateLimitingSampler(tracesPerSecond = -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must be positive")
    }

    @Test
    fun `should return description with rate value`() {
        // Given
        val tracesPerSecond = 25
        val sampler = RateLimitingSampler(tracesPerSecond)

        // When
        val description = sampler.description

        // Then
        assertThat(description).contains("25")
        assertThat(description).contains("RateLimitingSampler")
    }

    @Test
    fun `should work with very high rates`() {
        // Given
        val sampler = RateLimitingSampler(tracesPerSecond = 10000)

        // When - Sample many traces
        val results =
            (1..1000).map {
                sampler.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - All should be sampled (within rate)
        assertThat(results).allMatch { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
    }

    private fun generateTraceId(seed: Int): String {
        return "%032x".format(java.util.Locale.ROOT, seed)
    }
}
