package io.github.axonsentry.sampling

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import io.opentelemetry.sdk.trace.samplers.SamplingResult

/**
 * Composite sampler that combines multiple samplers using AND or OR logic.
 *
 * This sampler allows building complex sampling strategies by composing simpler samplers:
 * - **AND logic**: All samplers must accept for the trace to be sampled
 * - **OR logic**: Any sampler accepting will cause the trace to be sampled
 *
 * Common use cases:
 * - Combine probability and rate limiting: `and(ProbabilitySampler(0.1), RateLimitingSampler(100))`
 * - Sample all errors OR 10% of successes: `or(ErrorSampler(), ProbabilitySampler(0.1))`
 * - Complex nested conditions: `or(and(sampler1, sampler2), sampler3)`
 *
 * Example usage:
 * ```kotlin
 * // Sample 10% of traces, but never more than 100 per second
 * val sampler = CompositeSampler.and(
 *     ProbabilitySampler(0.1),
 *     RateLimitingSampler(100)
 * )
 *
 * // Sample all error traces OR 1% of other traces
 * val sampler = CompositeSampler.or(
 *     ErrorSampler(),
 *     ProbabilitySampler(0.01)
 * )
 *
 * // Nested: (10% probability AND <100/sec) OR always sample errors
 * val sampler = CompositeSampler.or(
 *     CompositeSampler.and(
 *         ProbabilitySampler(0.1),
 *         RateLimitingSampler(100)
 *     ),
 *     ErrorSampler()
 * )
 * ```
 *
 * @property samplers List of samplers to combine
 * @property logic The combination logic (AND or OR)
 * @throws IllegalArgumentException if samplers list is empty
 *
 * @since 1.0.0
 */
class CompositeSampler private constructor(
    private val samplers: List<Sampler>,
    private val logic: Logic,
) : Sampler {
    init {
        require(samplers.isNotEmpty()) {
            "CompositeSampler requires at least one sampler"
        }
    }

    /**
     * Determines whether a span should be sampled based on the combined logic.
     *
     * For AND logic:
     * - Evaluates samplers in order until one drops the trace
     * - If all samplers accept, the trace is sampled
     *
     * For OR logic:
     * - Evaluates samplers in order until one accepts the trace
     * - If any sampler accepts, the trace is sampled
     *
     * @param parentContext The parent context
     * @param traceId The trace ID
     * @param name The span name
     * @param spanKind The span kind
     * @param attributes The span attributes
     * @param parentLinks The parent links
     * @return Sampling result based on combined sampler decisions
     */
    override fun shouldSample(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: MutableList<LinkData>,
    ): SamplingResult {
        return when (logic) {
            Logic.AND -> shouldSampleAnd(parentContext, traceId, name, spanKind, attributes, parentLinks)
            Logic.OR -> shouldSampleOr(parentContext, traceId, name, spanKind, attributes, parentLinks)
        }
    }

    /**
     * AND logic: All samplers must accept.
     */
    @Suppress("LongParameterList") // Must match OpenTelemetry Sampler interface
    private fun shouldSampleAnd(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: MutableList<LinkData>,
    ): SamplingResult {
        for (sampler in samplers) {
            val result = sampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks)
            if (result.decision != SamplingDecision.RECORD_AND_SAMPLE) {
                return SamplingResult.drop()
            }
        }
        return SamplingResult.recordAndSample()
    }

    /**
     * OR logic: Any sampler accepting is sufficient.
     */
    @Suppress("LongParameterList") // Must match OpenTelemetry Sampler interface
    private fun shouldSampleOr(
        parentContext: Context,
        traceId: String,
        name: String,
        spanKind: SpanKind,
        attributes: Attributes,
        parentLinks: MutableList<LinkData>,
    ): SamplingResult {
        for (sampler in samplers) {
            val result = sampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks)
            if (result.decision == SamplingDecision.RECORD_AND_SAMPLE) {
                return SamplingResult.recordAndSample()
            }
        }
        return SamplingResult.drop()
    }

    /**
     * Returns a description of this sampler for debugging and logging.
     *
     * @return String description including logic type and child samplers
     */
    override fun getDescription(): String {
        val samplerDescriptions = samplers.joinToString(", ") { it.description }
        return "CompositeSampler{logic=$logic, samplers=[$samplerDescriptions]}"
    }

    /**
     * Combination logic for composite sampler.
     */
    enum class Logic {
        /** All samplers must accept for trace to be sampled */
        AND,

        /** Any sampler accepting will cause trace to be sampled */
        OR,
    }

    companion object {
        /**
         * Creates a composite sampler with AND logic.
         * All samplers must accept for the trace to be sampled.
         *
         * @param samplers Samplers to combine (at least one required)
         * @return CompositeSampler with AND logic
         * @throws IllegalArgumentException if no samplers provided
         */
        fun and(vararg samplers: Sampler): CompositeSampler {
            return CompositeSampler(samplers.toList(), Logic.AND)
        }

        /**
         * Creates a composite sampler with OR logic.
         * Any sampler accepting will cause the trace to be sampled.
         *
         * @param samplers Samplers to combine (at least one required)
         * @return CompositeSampler with OR logic
         * @throws IllegalArgumentException if no samplers provided
         */
        fun or(vararg samplers: Sampler): CompositeSampler {
            return CompositeSampler(samplers.toList(), Logic.OR)
        }
    }
}
