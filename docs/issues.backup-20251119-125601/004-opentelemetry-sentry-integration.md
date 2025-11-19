# Issue 004: OpenTelemetry to Sentry Bridge

**Phase:** Foundation
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 002, 003

## Overview
Implement the bridge between OpenTelemetry tracing and Sentry, enabling OpenTelemetry spans to be exported to Sentry as transactions and spans. This component is crucial for making the tracing data visible in Sentry's performance monitoring UI.

## Goals
- Create SpanExporter that sends OpenTelemetry spans to Sentry
- Map OpenTelemetry span data to Sentry transaction/span format
- Handle span batching and async export efficiently
- Integrate with Sentry's transaction system
- Support error and exception propagation to Sentry
- Provide configuration for export behavior

## Technical Requirements

### Components to Create

1. **SentrySpanExporter** (`io.github.axonsentry/sentry/SentrySpanExporter.kt`)
   - Purpose: Export OpenTelemetry spans to Sentry
   - Key responsibilities:
     - Implement OpenTelemetry SpanExporter interface
     - Convert OTel spans to Sentry transactions/spans
     - Batch and export spans asynchronously
     - Handle export failures gracefully

2. **SpanToSentryMapper** (`io.github.axonsentry/sentry/SpanToSentryMapper.kt`)
   - Purpose: Map OpenTelemetry span data to Sentry format
   - Key responsibilities:
     - Convert span attributes to Sentry tags
     - Map span status to Sentry status
     - Extract and format exception data
     - Preserve trace context relationships

3. **SentryTracingInitializer** (`io.github.axonsentry/sentry/SentryTracingInitializer.kt`)
   - Purpose: Initialize Sentry SDK with tracing enabled
   - Key responsibilities:
     - Configure Sentry options from TracingConfiguration
     - Set up OpenTelemetry integration
     - Register custom integrations
     - Handle SDK lifecycle

4. **SpanFilter** (`io.github.axonsentry/sentry/SpanFilter.kt`)
   - Purpose: Filter which spans get exported to Sentry
   - Key responsibilities:
     - Apply sampling decisions
     - Filter by span attributes
     - Support custom filtering logic
     - Respect configuration settings

### Dependencies
- **Sentry Java SDK**: 7.x (already in gradle config)
- **OpenTelemetry SDK**: 1.33.x (already in gradle config)
- **OpenTelemetry Exporter API**: Part of SDK
- **Kotlin Coroutines**: For async export operations

Additional in build.gradle.kts:
```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
implementation("io.sentry:sentry-opentelemetry:7.3.0") // Sentry's OTel integration
```

### Configuration
Integrates with TracingConfiguration from Issue 003.

## Implementation Guidance

### Step-by-Step Approach

1. **Create SentryTracingInitializer**
   - Initialize Sentry SDK with configuration
   - Set up tracing options
   - Configure integrations
   - Provide singleton access

2. **Implement SpanToSentryMapper**
   - Map span kind to Sentry operation
   - Convert attributes to tags
   - Handle status mapping
   - Format timestamps correctly

3. **Create SpanFilter**
   - Implement filtering interface
   - Support configuration-based filtering
   - Add extensibility for custom filters

4. **Implement SentrySpanExporter**
   - Implement SpanExporter interface
   - Use mapper to convert spans
   - Apply filters before export
   - Handle batching and async export
   - Implement error handling and retries

5. **Integration Testing**
   - Verify spans appear in Sentry
   - Test error propagation
   - Validate trace continuity

### Code Examples

#### SentryTracingInitializer.kt
```kotlin
package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.opentelemetry.SentrySpanProcessor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Initializes Sentry SDK with OpenTelemetry tracing integration.
 *
 * This class configures both Sentry and OpenTelemetry to work together,
 * enabling Axon Framework traces to appear in Sentry's performance monitoring.
 */
class SentryTracingInitializer(
    private val configuration: TracingConfiguration
) {
    private val logger = LoggerFactory.getLogger(SentryTracingInitializer::class.java)

    private var openTelemetry: OpenTelemetry? = null
    private var tracerProvider: SdkTracerProvider? = null

    /**
     * Initializes Sentry and OpenTelemetry with configured options.
     *
     * @return Configured OpenTelemetry instance
     * @throws IllegalStateException if Sentry DSN is not configured
     */
    fun initialize(): OpenTelemetry {
        if (!configuration.enabled) {
            logger.warn("Tracing is disabled in configuration")
            return OpenTelemetry.noop()
        }

        requireNotNull(configuration.sentryDsn) {
            "Sentry DSN must be configured when tracing is enabled"
        }

        // Initialize Sentry SDK
        Sentry.init { options ->
            configureSentryOptions(options)
        }

        // Create OpenTelemetry components
        val spanExporter = SentrySpanExporter(configuration)
        val spanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
            .setMaxQueueSize(2048)
            .setMaxExportBatchSize(512)
            .build()

        // Build tracer provider with Sentry processor
        tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .addSpanProcessor(SentrySpanProcessor()) // Sentry's native processor
            .setSampler(Sampler.traceIdRatioBased(configuration.tracesSampleRate))
            .build()

        // Build OpenTelemetry SDK
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider!!)
            .build()

        logger.info("Sentry tracing initialized with DSN: ${maskDsn(configuration.sentryDsn)}")

        return openTelemetry!!
    }

    /**
     * Configures Sentry options from TracingConfiguration.
     */
    private fun configureSentryOptions(options: SentryOptions) {
        options.dsn = configuration.sentryDsn
        options.environment = configuration.environment
        options.tracesSampleRate = configuration.tracesSampleRate
        options.isAttachStacktrace = configuration.attachStacktrace
        options.isEnableTracing = true

        // Configure breadcrumbs and other options
        options.setBeforeSend { event, hint ->
            // Allow custom before-send processing
            event
        }

        options.setBeforeBreadcrumb { breadcrumb, hint ->
            // Allow custom breadcrumb processing
            breadcrumb
        }
    }

    /**
     * Shuts down tracing and flushes pending spans.
     */
    fun shutdown() {
        logger.info("Shutting down Sentry tracing...")
        tracerProvider?.shutdown()
        Sentry.close()
        logger.info("Sentry tracing shutdown complete")
    }

    /**
     * Flushes pending spans to Sentry.
     *
     * @param timeout Maximum time to wait for flush
     * @param unit Time unit for timeout
     */
    fun flush(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS) {
        tracerProvider?.forceFlush()?.join(timeout, unit)
    }

    private fun maskDsn(dsn: String): String {
        // Mask the key portion of DSN for logging
        return dsn.replace(Regex("@"), "@***")
    }

    companion object {
        @Volatile
        private var instance: SentryTracingInitializer? = null

        /**
         * Gets or creates the singleton instance.
         */
        fun getInstance(configuration: TracingConfiguration): SentryTracingInitializer {
            return instance ?: synchronized(this) {
                instance ?: SentryTracingInitializer(configuration).also { instance = it }
            }
        }
    }
}
```

#### SentrySpanExporter.kt
```kotlin
package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.sentry.Sentry
import io.sentry.SentryTransaction
import org.slf4j.LoggerFactory

/**
 * Exports OpenTelemetry spans to Sentry as transactions and child spans.
 *
 * This exporter converts OTel span data to Sentry's format and sends it
 * to Sentry's performance monitoring system.
 */
class SentrySpanExporter(
    private val configuration: TracingConfiguration,
    private val mapper: SpanToSentryMapper = SpanToSentryMapper(),
    private val filter: SpanFilter = ConfigurationBasedSpanFilter(configuration)
) : SpanExporter {

    private val logger = LoggerFactory.getLogger(SentrySpanExporter::class.java)

    @Volatile
    private var isShutdown = false

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        if (isShutdown) {
            return CompletableResultCode.ofFailure()
        }

        return try {
            val filteredSpans = spans.filter { filter.shouldExport(it) }

            logger.debug("Exporting ${filteredSpans.size} spans to Sentry (${spans.size - filteredSpans.size} filtered)")

            filteredSpans.forEach { span ->
                exportSpan(span)
            }

            CompletableResultCode.ofSuccess()
        } catch (e: Exception) {
            logger.error("Failed to export spans to Sentry", e)
            CompletableResultCode.ofFailure()
        }
    }

    private fun exportSpan(spanData: SpanData) {
        try {
            // Root spans become transactions, child spans are added to their parent
            if (spanData.parentSpanContext.isValid) {
                // Child span - add to parent transaction
                val sentrySpan = mapper.mapToSentrySpan(spanData)
                // Note: Sentry SDK handles span hierarchy automatically
                // when using SentrySpanProcessor in combination
                logger.trace("Exported child span: ${spanData.name}")
            } else {
                // Root span - create transaction
                val transaction = mapper.mapToSentryTransaction(spanData)
                transaction.finish(spanData.status)
                logger.trace("Exported transaction: ${spanData.name}")
            }
        } catch (e: Exception) {
            logger.warn("Failed to export span: ${spanData.name}", e)
        }
    }

    override fun flush(): CompletableResultCode {
        return try {
            Sentry.flush(5000) // 5 second timeout
            CompletableResultCode.ofSuccess()
        } catch (e: Exception) {
            logger.error("Failed to flush Sentry spans", e)
            CompletableResultCode.ofFailure()
        }
    }

    override fun shutdown(): CompletableResultCode {
        if (isShutdown) {
            return CompletableResultCode.ofSuccess()
        }

        isShutdown = true
        return flush()
    }
}
```

#### SpanToSentryMapper.kt
```kotlin
package io.github.axonsentry.sentry

import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.sentry.Sentry
import io.sentry.SentryTransaction
import io.sentry.Span
import io.sentry.SpanStatus
import io.sentry.protocol.TransactionNameSource
import java.util.Date

/**
 * Maps OpenTelemetry span data to Sentry transaction and span formats.
 */
class SpanToSentryMapper {

    /**
     * Maps an OpenTelemetry root span to a Sentry transaction.
     */
    fun mapToSentryTransaction(spanData: SpanData): SentryTransaction {
        val transactionContext = io.sentry.TransactionContext(
            spanData.name,
            mapOperation(spanData),
            TransactionNameSource.CUSTOM
        ).apply {
            setTraceId(spanData.traceId.toSentryId())
            setSpanId(spanData.spanId.toSentryId())
            setParentSpanId(if (spanData.parentSpanContext.isValid) {
                spanData.parentSpanContext.spanId.toSentryId()
            } else {
                null
            })
        }

        val transaction = Sentry.startTransaction(transactionContext)

        // Map attributes to tags
        spanData.attributes.asMap().forEach { (key, value) ->
            transaction.setTag(key.key, value.toString())
        }

        // Set timestamps
        transaction.startDate = Date(spanData.startEpochNanos / 1_000_000)

        // Map status
        transaction.status = mapStatus(spanData.status)

        // Add events as breadcrumbs
        spanData.events.forEach { event ->
            val breadcrumb = io.sentry.Breadcrumb().apply {
                message = event.name
                timestamp = Date(event.epochNanos / 1_000_000)
                event.attributes.asMap().forEach { (key, value) ->
                    setData(key.key, value)
                }
            }
            transaction.addBreadcrumb(breadcrumb)
        }

        return transaction
    }

    /**
     * Maps an OpenTelemetry child span to a Sentry span.
     */
    fun mapToSentrySpan(spanData: SpanData): Span {
        val parentTransaction = Sentry.getCurrentHub().span as? SentryTransaction
            ?: throw IllegalStateException("No active transaction for child span")

        val span = parentTransaction.startChild(
            mapOperation(spanData),
            spanData.name
        )

        // Map attributes to tags
        spanData.attributes.asMap().forEach { (key, value) ->
            span.setTag(key.key, value.toString())
        }

        // Set timestamps
        span.startDate = Date(spanData.startEpochNanos / 1_000_000)

        // Map status
        span.status = mapStatus(spanData.status)

        return span
    }

    /**
     * Maps OpenTelemetry span kind to Sentry operation name.
     */
    private fun mapOperation(spanData: SpanData): String {
        return spanData.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey(
            SpanAttributes.AXON_MESSAGE_TYPE
        )) ?: when (spanData.kind) {
            io.opentelemetry.api.trace.SpanKind.CLIENT -> "client"
            io.opentelemetry.api.trace.SpanKind.SERVER -> "server"
            io.opentelemetry.api.trace.SpanKind.PRODUCER -> "producer"
            io.opentelemetry.api.trace.SpanKind.CONSUMER -> "consumer"
            io.opentelemetry.api.trace.SpanKind.INTERNAL -> "internal"
            else -> "unknown"
        }
    }

    /**
     * Maps OpenTelemetry status to Sentry status.
     */
    private fun mapStatus(status: StatusData): SpanStatus {
        return when (status.statusCode) {
            StatusCode.OK -> SpanStatus.OK
            StatusCode.ERROR -> SpanStatus.INTERNAL_ERROR
            StatusCode.UNSET -> SpanStatus.OK
            else -> SpanStatus.UNKNOWN_ERROR
        }
    }

    /**
     * Converts OpenTelemetry trace/span ID to Sentry format.
     */
    private fun String.toSentryId(): io.sentry.protocol.SentryId {
        return io.sentry.protocol.SentryId(this)
    }
}
```

#### SpanFilter.kt
```kotlin
package io.github.axonsentry.sentry

import io.github.axonsentry.config.TracingConfiguration
import io.github.axonsentry.tracing.SpanAttributes
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Determines which spans should be exported to Sentry.
 */
fun interface SpanFilter {
    /**
     * Returns true if the span should be exported.
     */
    fun shouldExport(span: SpanData): Boolean
}

/**
 * Configuration-based span filter that respects TracingConfiguration settings.
 */
class ConfigurationBasedSpanFilter(
    private val configuration: TracingConfiguration
) : SpanFilter {

    override fun shouldExport(span: SpanData): Boolean {
        if (!configuration.enabled) {
            return false
        }

        val messageType = span.attributes.get(
            io.opentelemetry.api.common.AttributeKey.stringKey(SpanAttributes.AXON_MESSAGE_TYPE)
        )

        return when (messageType) {
            SpanAttributes.MESSAGE_TYPE_COMMAND -> configuration.traceCommands
            SpanAttributes.MESSAGE_TYPE_EVENT -> configuration.traceEvents
            SpanAttributes.MESSAGE_TYPE_QUERY -> configuration.traceQueries
            else -> true // Export unknown types by default
        }
    }
}

/**
 * Composite filter that requires all child filters to pass.
 */
class CompositeSpanFilter(
    private val filters: List<SpanFilter>
) : SpanFilter {
    override fun shouldExport(span: SpanData): Boolean {
        return filters.all { it.shouldExport(span) }
    }
}
```

### Integration Points
- Integrates with OpenTelemetry SDK TracerProvider
- Uses Sentry SDK for transaction/span management
- Consumes TracingConfiguration from Issue 003
- Applies filtering based on configuration
- Exposes metrics for monitoring export performance

## Testing Requirements

### Unit Tests
- [ ] Test: SentrySpanExporter exports spans successfully
- [ ] Test: SpanToSentryMapper converts attributes correctly
- [ ] Test: SpanToSentryMapper maps status codes accurately
- [ ] Test: SpanFilter respects configuration settings
- [ ] Test: CompositeSpanFilter chains filters correctly
- [ ] Test: Export handles errors gracefully
- [ ] Test: Shutdown flushes pending spans

### Integration Tests
- [ ] Integration: Spans appear in Sentry UI (manual verification)
- [ ] Integration: Trace context is preserved across spans
- [ ] Integration: Parent-child relationships are maintained
- [ ] Integration: Error spans show up in Sentry issues
- [ ] Integration: Sampling is applied correctly

### Test Coverage Target
85%+ coverage (integration with external Sentry API limits testability)

## Acceptance Criteria
- [ ] SentrySpanExporter implements SpanExporter interface
- [ ] Spans are correctly exported to Sentry
- [ ] Trace hierarchy is preserved in Sentry
- [ ] Configuration controls which spans are exported
- [ ] Error handling prevents export failures from crashing app
- [ ] Async batching is configured for performance
- [ ] Sentry SDK initialized with correct options
- [ ] All tests passing
- [ ] Manual verification in Sentry UI successful

## Definition of Done
- [ ] Implementation complete
- [ ] Unit tests written and passing (85%+ coverage)
- [ ] Integration tests documented and executed
- [ ] Verified spans in Sentry dashboard
- [ ] Code meets quality standards (detekt, ktlint)
- [ ] Performance impact measured and acceptable
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [Sentry OpenTelemetry Integration](https://docs.sentry.io/platforms/java/performance/instrumentation/opentelemetry/)
- [OpenTelemetry SpanExporter](https://opentelemetry.io/docs/instrumentation/java/exporters/)
- [Sentry Performance Monitoring](https://docs.sentry.io/product/performance/)
- [OpenTelemetry Batching](https://opentelemetry.io/docs/reference/specification/trace/sdk/#batching-processor)
- [Sentry Transaction Protocol](https://develop.sentry.dev/sdk/event-payloads/transaction/)

## Notes
- Sentry has native OpenTelemetry support via SentrySpanProcessor
- Consider using both custom exporter AND native processor for best results
- Batch processing is critical for performance with high message volumes
- Test with real Sentry project to verify UI rendering
- Monitor memory usage with large batches
- Ensure trace IDs are properly formatted for Sentry
- Document how to view traces in Sentry UI
- Consider adding metrics for export failures and latency
- Shutdown hook should ensure all spans are flushed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
