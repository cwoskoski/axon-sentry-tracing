package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.data.SpanData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpanFilterTest {
    @Test
    fun `ConfigurationBasedSpanFilter returns false when tracing is disabled`() {
        // Given
        val configuration = TracingConfiguration(enabled = false)
        val filter = ConfigurationBasedSpanFilter(configuration)
        val span = mockSpan()

        // When
        val result = filter.shouldExport(span)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `ConfigurationBasedSpanFilter respects command tracing configuration`() {
        // Given
        val configEnabled = TracingConfiguration(traceCommands = true)
        val configDisabled = TracingConfiguration(traceCommands = false)
        val filterEnabled = ConfigurationBasedSpanFilter(configEnabled)
        val filterDisabled = ConfigurationBasedSpanFilter(configDisabled)
        val commandSpan = mockSpan(SpanAttributes.MESSAGE_TYPE_COMMAND)

        // When / Then
        assertThat(filterEnabled.shouldExport(commandSpan)).isTrue()
        assertThat(filterDisabled.shouldExport(commandSpan)).isFalse()
    }

    @Test
    fun `ConfigurationBasedSpanFilter respects event tracing configuration`() {
        // Given
        val configEnabled = TracingConfiguration(traceEvents = true)
        val configDisabled = TracingConfiguration(traceEvents = false)
        val filterEnabled = ConfigurationBasedSpanFilter(configEnabled)
        val filterDisabled = ConfigurationBasedSpanFilter(configDisabled)
        val eventSpan = mockSpan(SpanAttributes.MESSAGE_TYPE_EVENT)

        // When / Then
        assertThat(filterEnabled.shouldExport(eventSpan)).isTrue()
        assertThat(filterDisabled.shouldExport(eventSpan)).isFalse()
    }

    @Test
    fun `ConfigurationBasedSpanFilter respects query tracing configuration`() {
        // Given
        val configEnabled = TracingConfiguration(traceQueries = true)
        val configDisabled = TracingConfiguration(traceQueries = false)
        val filterEnabled = ConfigurationBasedSpanFilter(configEnabled)
        val filterDisabled = ConfigurationBasedSpanFilter(configDisabled)
        val querySpan = mockSpan(SpanAttributes.MESSAGE_TYPE_QUERY)

        // When / Then
        assertThat(filterEnabled.shouldExport(querySpan)).isTrue()
        assertThat(filterDisabled.shouldExport(querySpan)).isFalse()
    }

    @Test
    fun `ConfigurationBasedSpanFilter exports unknown message types by default`() {
        // Given
        val configuration = TracingConfiguration(enabled = true)
        val filter = ConfigurationBasedSpanFilter(configuration)
        val unknownSpan = mockSpan("unknown")

        // When
        val result = filter.shouldExport(unknownSpan)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `ConfigurationBasedSpanFilter exports spans without message type by default`() {
        // Given
        val configuration = TracingConfiguration(enabled = true)
        val filter = ConfigurationBasedSpanFilter(configuration)
        val spanWithoutType = mockSpan(null)

        // When
        val result = filter.shouldExport(spanWithoutType)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `CompositeSpanFilter returns true when all filters pass`() {
        // Given
        val filter1 = SpanFilter { true }
        val filter2 = SpanFilter { true }
        val compositeFilter = CompositeSpanFilter(listOf(filter1, filter2))
        val span = mockSpan()

        // When
        val result = compositeFilter.shouldExport(span)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `CompositeSpanFilter returns false when any filter fails`() {
        // Given
        val filter1 = SpanFilter { true }
        val filter2 = SpanFilter { false }
        val compositeFilter = CompositeSpanFilter(listOf(filter1, filter2))
        val span = mockSpan()

        // When
        val result = compositeFilter.shouldExport(span)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `CompositeSpanFilter returns false when all filters fail`() {
        // Given
        val filter1 = SpanFilter { false }
        val filter2 = SpanFilter { false }
        val compositeFilter = CompositeSpanFilter(listOf(filter1, filter2))
        val span = mockSpan()

        // When
        val result = compositeFilter.shouldExport(span)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `CompositeSpanFilter with empty filter list returns true`() {
        // Given
        val compositeFilter = CompositeSpanFilter(emptyList())
        val span = mockSpan()

        // When
        val result = compositeFilter.shouldExport(span)

        // Then
        assertThat(result).isTrue()
    }

    private fun mockSpan(messageType: String? = null): SpanData {
        val span = mockk<SpanData>()
        val attributes =
            if (messageType != null) {
                Attributes.of(AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE), messageType)
            } else {
                Attributes.empty()
            }

        every { span.attributes } returns attributes
        return span
    }
}
