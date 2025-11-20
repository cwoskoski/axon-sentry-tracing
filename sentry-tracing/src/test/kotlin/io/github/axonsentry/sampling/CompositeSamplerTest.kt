package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CompositeSampler].
 *
 * Tests composite sampling logic with AND/OR combinations.
 */
class CompositeSamplerTest {
    @Test
    fun `should sample when all samplers with AND logic accept`() {
        // Given - Both samplers always sample
        val sampler1 = ProbabilitySampler(1.0)
        val sampler2 = ProbabilitySampler(1.0)
        val composite = CompositeSampler.and(sampler1, sampler2)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then
        assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    @Test
    fun `should drop when any sampler with AND logic drops`() {
        // Given - First samples all, second samples none
        val sampler1 = ProbabilitySampler(1.0)
        val sampler2 = ProbabilitySampler(0.0)
        val composite = CompositeSampler.and(sampler1, sampler2)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then
        assertThat(result.decision).isEqualTo(SamplingDecision.DROP)
    }

    @Test
    fun `should sample when any sampler with OR logic accepts`() {
        // Given - First samples none, second samples all
        val sampler1 = ProbabilitySampler(0.0)
        val sampler2 = ProbabilitySampler(1.0)
        val composite = CompositeSampler.or(sampler1, sampler2)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then
        assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    @Test
    fun `should drop when all samplers with OR logic drop`() {
        // Given - Both samplers drop all
        val sampler1 = ProbabilitySampler(0.0)
        val sampler2 = ProbabilitySampler(0.0)
        val composite = CompositeSampler.or(sampler1, sampler2)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then
        assertThat(result.decision).isEqualTo(SamplingDecision.DROP)
    }

    @Test
    fun `should combine multiple samplers with AND logic`() {
        // Given - Three samplers with different probabilities
        val sampler1 = ProbabilitySampler(1.0) // Always samples
        val sampler2 = ProbabilitySampler(1.0) // Always samples
        val sampler3 = ProbabilitySampler(1.0) // Always samples
        val composite = CompositeSampler.and(sampler1, sampler2, sampler3)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then - All must accept for AND
        assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    @Test
    fun `should combine multiple samplers with OR logic`() {
        // Given - Three samplers where only one samples
        val sampler1 = ProbabilitySampler(0.0) // Never samples
        val sampler2 = ProbabilitySampler(1.0) // Always samples
        val sampler3 = ProbabilitySampler(0.0) // Never samples
        val composite = CompositeSampler.or(sampler1, sampler2, sampler3)

        // When
        val result =
            composite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then - Any one acceptance is enough for OR
        assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    @Test
    fun `should combine probability and rate limiting with AND`() {
        // Given - 50% probability AND 100 traces/sec rate limit
        val probabilitySampler = ProbabilitySampler(0.5)
        val rateLimitSampler = RateLimitingSampler(100)
        val composite = CompositeSampler.and(probabilitySampler, rateLimitSampler)

        // When - Sample many traces
        val results =
            (1..1000).map {
                composite.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - Should sample approximately 50 (50% of 100 rate limit)
        val sampledCount = results.count { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
        assertThat(sampledCount).isLessThanOrEqualTo(100) // Rate limit enforced
    }

    @Test
    fun `should combine probability and rate limiting with OR`() {
        // Given - 10% probability OR 5 traces/sec rate limit
        val probabilitySampler = ProbabilitySampler(0.1)
        val rateLimitSampler = RateLimitingSampler(5)
        val composite = CompositeSampler.or(probabilitySampler, rateLimitSampler)

        // When - Sample many traces quickly
        val results =
            (1..100).map {
                composite.shouldSample(
                    Context.root(),
                    generateTraceId(it),
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then - Should sample at least 10% (probability) even if rate limit exceeded
        val sampledCount = results.count { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
        assertThat(sampledCount).isGreaterThanOrEqualTo(5) // At least rate limit
    }

    @Test
    fun `should throw exception when creating AND sampler with no samplers`() {
        // Expect
        assertThatThrownBy {
            CompositeSampler.and()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least one sampler")
    }

    @Test
    fun `should throw exception when creating OR sampler with no samplers`() {
        // Expect
        assertThatThrownBy {
            CompositeSampler.or()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least one sampler")
    }

    @Test
    fun `should return description for AND sampler`() {
        // Given
        val sampler1 = ProbabilitySampler(0.5)
        val sampler2 = RateLimitingSampler(100)
        val composite = CompositeSampler.and(sampler1, sampler2)

        // When
        val description = composite.description

        // Then
        assertThat(description).contains("CompositeSampler")
        assertThat(description).contains("AND")
    }

    @Test
    fun `should return description for OR sampler`() {
        // Given
        val sampler1 = ProbabilitySampler(0.5)
        val sampler2 = RateLimitingSampler(100)
        val composite = CompositeSampler.or(sampler1, sampler2)

        // When
        val description = composite.description

        // Then
        assertThat(description).contains("CompositeSampler")
        assertThat(description).contains("OR")
    }

    @Test
    fun `should support nested composite samplers`() {
        // Given - (sampler1 AND sampler2) OR sampler3
        val sampler1 = ProbabilitySampler(1.0)
        val sampler2 = ProbabilitySampler(1.0)
        val sampler3 = ProbabilitySampler(1.0)
        val andComposite = CompositeSampler.and(sampler1, sampler2)
        val orComposite = CompositeSampler.or(andComposite, sampler3)

        // When
        val result =
            orComposite.shouldSample(
                Context.root(),
                generateTraceId(1),
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then
        assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE)
    }

    private fun generateTraceId(seed: Int): String {
        return "%032x".format(java.util.Locale.ROOT, seed)
    }
}
