package io.github.axonsentry.providers

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CorrelationIdAttributeProvider")
class CorrelationIdAttributeProviderTest {
    @Nested
    @DisplayName("Default Correlation ID Key")
    inner class DefaultKey {
        @Test
        @DisplayName("should extract correlation ID from metadata")
        fun `extracts correlation ID from metadata`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata = MetaData.with("correlationId", "corr-12345")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("correlation.id", "corr-12345")
        }

        @Test
        @DisplayName("should return empty map when correlation ID not present")
        fun `returns empty map when correlation ID not present`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata = MetaData.with("someOtherKey", "value")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty()
        }

        @Test
        @DisplayName("should return empty map when no metadata")
        fun `returns empty map when no metadata`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty()
        }
    }

    @Nested
    @DisplayName("Custom Metadata Key")
    inner class CustomMetadataKey {
        @Test
        @DisplayName("should extract correlation ID from custom metadata key")
        fun `extracts from custom metadata key`() {
            // Given
            val provider = CorrelationIdAttributeProvider(metadataKey = "requestId")
            val metadata = MetaData.with("requestId", "req-67890")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("correlation.id", "req-67890")
        }

        @Test
        @DisplayName("should not extract from default key when custom key specified")
        fun `ignores default key when custom key specified`() {
            // Given
            val provider = CorrelationIdAttributeProvider(metadataKey = "requestId")
            val metadata = MetaData.with("correlationId", "corr-12345") // Default key

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty() // Should not find it in default key
        }
    }

    @Nested
    @DisplayName("Custom Attribute Key")
    inner class CustomAttributeKey {
        @Test
        @DisplayName("should use custom attribute key")
        fun `uses custom attribute key`() {
            // Given
            val provider = CorrelationIdAttributeProvider(attributeKey = "trace.correlation")
            val metadata = MetaData.with("correlationId", "corr-12345")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("trace.correlation", "corr-12345")
            assertThat(attributes).doesNotContainKey("correlation.id")
        }
    }

    @Nested
    @DisplayName("Value Types")
    inner class ValueTypes {
        @Test
        @DisplayName("should handle string correlation IDs")
        fun `handles string values`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata = MetaData.with("correlationId", "string-id")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("correlation.id", "string-id")
        }

        @Test
        @DisplayName("should convert non-string values to string")
        fun `converts non-string values`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata = MetaData.with("correlationId", 12345)

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("correlation.id", "12345")
        }

        @Test
        @DisplayName("should handle null correlation ID gracefully")
        fun `handles null values`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata = MetaData.with("correlationId", null)

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then - Should return empty map for null values
            assertThat(attributes).isEmpty()
        }
    }

    @Nested
    @DisplayName("Priority")
    inner class Priority {
        @Test
        @DisplayName("should have priority of 100 by default")
        fun `has default priority of 100`() {
            // Given
            val provider = CorrelationIdAttributeProvider()

            // When
            val priority = provider.priority()

            // Then
            assertThat(priority).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    inner class IntegrationScenarios {
        @Test
        @DisplayName("should work with multiple metadata entries")
        fun `works with multiple metadata entries`() {
            // Given
            val provider = CorrelationIdAttributeProvider()
            val metadata =
                MetaData.with("correlationId", "corr-12345")
                    .and("userId", "user-123")
                    .and("requestId", "req-456")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then - Should only extract correlation ID
            assertThat(attributes).hasSize(1)
            assertThat(attributes).containsEntry("correlation.id", "corr-12345")
        }
    }

    // Test domain objects
    data class TestCommand(val id: String)
}
