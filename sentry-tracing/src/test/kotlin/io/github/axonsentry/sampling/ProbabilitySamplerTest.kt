package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for [ProbabilitySampler].
 *
 * Tests probability-based sampling with hash-based deterministic sampling.
 */
class ProbabilitySamplerTest {
    @Test
    fun `should sample all traces when probability is 1_0`() {
        // Given
        val sampler = ProbabilitySampler(1.0)
        val traceIds = generateTraceIds(100)

        // When
        val results =
            traceIds.map { traceId ->
                sampler.shouldSample(
                    Context.root(),
                    traceId,
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then
        assertThat(results).allMatch { it.decision == SamplingDecision.RECORD_AND_SAMPLE }
    }

    @Test
    fun `should sample no traces when probability is 0_0`() {
        // Given
        val sampler = ProbabilitySampler(0.0)
        val traceIds = generateTraceIds(100)

        // When
        val results =
            traceIds.map { traceId ->
                sampler.shouldSample(
                    Context.root(),
                    traceId,
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                )
            }

        // Then
        assertThat(results).allMatch { it.decision == SamplingDecision.DROP }
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.1, 0.25, 0.5, 0.75])
    fun `should sample approximately correct percentage of traces`(probability: Double) {
        // Given
        val sampler = ProbabilitySampler(probability)
        val traceIds = generateTraceIds(10000)
        val tolerance = 0.05 // 5% tolerance - sequential IDs don't distribute perfectly

        // When
        val sampledCount =
            traceIds.count { traceId ->
                sampler.shouldSample(
                    Context.root(),
                    traceId,
                    "test-span",
                    SpanKind.INTERNAL,
                    Attributes.empty(),
                    mutableListOf(),
                ).decision == SamplingDecision.RECORD_AND_SAMPLE
            }

        // Then
        val actualRate = sampledCount.toDouble() / traceIds.size
        assertThat(actualRate)
            .isGreaterThanOrEqualTo(probability - tolerance)
            .isLessThanOrEqualTo(probability + tolerance)
    }

    @Test
    fun `should deterministically sample same trace ID`() {
        // Given
        val sampler = ProbabilitySampler(0.5)
        val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"

        // When
        val result1 =
            sampler.shouldSample(
                Context.root(),
                traceId,
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )
        val result2 =
            sampler.shouldSample(
                Context.root(),
                traceId,
                "test-span",
                SpanKind.INTERNAL,
                Attributes.empty(),
                mutableListOf(),
            )

        // Then - same trace ID should always get same sampling decision
        assertThat(result1.decision).isEqualTo(result2.decision)
    }

    @Test
    fun `should throw exception when probability is negative`() {
        // Expect
        assertThatThrownBy {
            ProbabilitySampler(-0.1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Probability must be between 0.0 and 1.0")
    }

    @Test
    fun `should throw exception when probability is greater than 1_0`() {
        // Expect
        assertThatThrownBy {
            ProbabilitySampler(1.1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Probability must be between 0.0 and 1.0")
    }

    @Test
    fun `should return description with probability value`() {
        // Given
        val probability = 0.25
        val sampler = ProbabilitySampler(probability)

        // When
        val description = sampler.description

        // Then
        assertThat(description).contains("0.25")
        assertThat(description).contains("ProbabilitySampler")
    }

    private fun generateTraceIds(count: Int): List<String> {
        // Generate realistic random hex trace IDs for better distribution testing
        val random = java.util.Random(42) // Fixed seed for reproducibility
        return (1..count).map {
            // Generate 32-character hex trace ID (128 bits)
            buildString {
                repeat(32) {
                    append(random.nextInt(16).toString(16))
                }
            }
        }
    }
}
