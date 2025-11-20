package io.github.axonsentry.tracing

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageMetadataCarrier")
class MessageMetadataCarrierTest {
    @Nested
    @DisplayName("MetaDataGetter")
    inner class MetaDataGetterTest {
        @Test
        fun `should return all keys from metadata`() {
            // Given
            val metadata =
                MetaData.with(
                    "traceparent",
                    "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                ).and("tracestate", "vendor=value")
                    .and("other-key", "other-value")

            // When
            val keys = MetaDataGetter.keys(metadata)

            // Then
            assertThat(keys).containsExactlyInAnyOrder(
                "traceparent",
                "tracestate",
                "other-key",
            )
        }

        @Test
        fun `should get string value from metadata`() {
            // Given
            val metadata = MetaData.with("traceparent", "00-trace-id-span-id-01")

            // When
            val value = MetaDataGetter.get(metadata, "traceparent")

            // Then
            assertThat(value).isEqualTo("00-trace-id-span-id-01")
        }

        @Test
        fun `should return null for missing key`() {
            // Given
            val metadata = MetaData.with("other-key", "value")

            // When
            val value = MetaDataGetter.get(metadata, "traceparent")

            // Then
            assertThat(value).isNull()
        }

        @Test
        fun `should return null for null carrier`() {
            // When
            val value = MetaDataGetter.get(null, "traceparent")

            // Then
            assertThat(value).isNull()
        }

        @Test
        fun `should convert non-string value to string`() {
            // Given
            val metadata = MetaData.with("number", 42)

            // When
            val value = MetaDataGetter.get(metadata, "number")

            // Then
            assertThat(value).isEqualTo("42")
        }

        @Test
        fun `should handle integer values`() {
            // Given
            val metadata = MetaData.with("count", 100)

            // When
            val value = MetaDataGetter.get(metadata, "count")

            // Then
            assertThat(value).isEqualTo("100")
        }

        @Test
        fun `should handle boolean values`() {
            // Given
            val metadata = MetaData.with("flag", true)

            // When
            val value = MetaDataGetter.get(metadata, "flag")

            // Then
            assertThat(value).isEqualTo("true")
        }

        @Test
        fun `should handle empty metadata`() {
            // Given
            val metadata = MetaData.emptyInstance()

            // When
            val keys = MetaDataGetter.keys(metadata)

            // Then
            assertThat(keys).isEmpty()
        }
    }

    @Nested
    @DisplayName("MetaDataSetter")
    inner class MetaDataSetterTest {
        @Test
        fun `should set value in mutable map`() {
            // Given
            val carrier = mutableMapOf<String, Any>()

            // When
            MetaDataSetter.set(carrier, "traceparent", "00-trace-id-span-id-01")

            // Then
            assertThat(carrier).containsEntry("traceparent", "00-trace-id-span-id-01")
        }

        @Test
        fun `should set multiple values`() {
            // Given
            val carrier = mutableMapOf<String, Any>()

            // When
            MetaDataSetter.set(carrier, "traceparent", "00-trace-id-span-id-01")
            MetaDataSetter.set(carrier, "tracestate", "vendor=value")
            MetaDataSetter.set(carrier, "baggage", "key=value")

            // Then
            assertThat(carrier).containsOnlyKeys("traceparent", "tracestate", "baggage")
            assertThat(carrier["traceparent"]).isEqualTo("00-trace-id-span-id-01")
            assertThat(carrier["tracestate"]).isEqualTo("vendor=value")
            assertThat(carrier["baggage"]).isEqualTo("key=value")
        }

        @Test
        fun `should overwrite existing value`() {
            // Given
            val carrier = mutableMapOf<String, Any>("traceparent" to "old-value")

            // When
            MetaDataSetter.set(carrier, "traceparent", "new-value")

            // Then
            assertThat(carrier["traceparent"]).isEqualTo("new-value")
        }

        @Test
        fun `should handle null carrier gracefully`() {
            // When / Then - should not throw
            MetaDataSetter.set(null, "traceparent", "value")
        }

        @Test
        fun `should preserve existing entries when adding new ones`() {
            // Given
            val carrier =
                mutableMapOf<String, Any>(
                    "existing-key" to "existing-value",
                )

            // When
            MetaDataSetter.set(carrier, "new-key", "new-value")

            // Then
            assertThat(carrier).containsEntry("existing-key", "existing-value")
            assertThat(carrier).containsEntry("new-key", "new-value")
        }

        @Test
        fun `should store values as String type`() {
            // Given
            val carrier = mutableMapOf<String, Any>()

            // When
            MetaDataSetter.set(carrier, "key", "value")

            // Then
            assertThat(carrier["key"]).isInstanceOf(String::class.java)
        }
    }

    @Nested
    @DisplayName("Integration")
    inner class IntegrationTest {
        @Test
        fun `should round-trip trace context through metadata`() {
            // Given
            val originalTraceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
            val originalTracestate = "vendor1=value1,vendor2=value2"

            val carrier = mutableMapOf<String, Any>()
            MetaDataSetter.set(carrier, "traceparent", originalTraceparent)
            MetaDataSetter.set(carrier, "tracestate", originalTracestate)

            val metadata = MetaData.from(carrier)

            // When
            val retrievedTraceparent = MetaDataGetter.get(metadata, "traceparent")
            val retrievedTracestate = MetaDataGetter.get(metadata, "tracestate")

            // Then
            assertThat(retrievedTraceparent).isEqualTo(originalTraceparent)
            assertThat(retrievedTracestate).isEqualTo(originalTracestate)
        }

        @Test
        fun `should handle W3C baggage format`() {
            // Given
            val baggage = "userId=alice,serverNode=DF%2028,isProduction=false"
            val carrier = mutableMapOf<String, Any>()

            // When
            MetaDataSetter.set(carrier, "baggage", baggage)
            val metadata = MetaData.from(carrier)
            val retrieved = MetaDataGetter.get(metadata, "baggage")

            // Then
            assertThat(retrieved).isEqualTo(baggage)
        }
    }
}
