package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class W3CPropagatorDebugTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting = OpenTelemetryExtension.create()
    }

    @Test
    fun `debug W3C propagator behavior`() {
        val tracer = otelTesting.openTelemetry.getTracer("test")
        val span = tracer.spanBuilder("test-span").startSpan()

        println("Span context valid: ${span.spanContext.isValid}")
        println("Span context sampled: ${span.spanContext.isSampled}")
        println("Trace ID: ${span.spanContext.traceId}")
        println("Span ID: ${span.spanContext.spanId}")

        val propagator = W3CTraceContextPropagator.getInstance()
        val carrier = mutableMapOf<String, Any>()

        Context.current().with(span).makeCurrent().use {
            propagator.inject(Context.current(), carrier, MetaDataSetter)
        }

        println("Carrier after injection: $carrier")
        assertThat(carrier).containsKey("traceparent")

        span.end()
    }
}
