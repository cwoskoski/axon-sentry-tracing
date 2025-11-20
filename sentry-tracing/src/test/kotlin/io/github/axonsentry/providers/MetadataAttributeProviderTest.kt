package io.github.axonsentry.providers

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.MetaData
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MetadataAttributeProvider")
class MetadataAttributeProviderTest {
    @Nested
    @DisplayName("Standard Metadata")
    inner class StandardMetadata {
        @Test
        @DisplayName("should extract metadata with prefix")
        fun `extracts metadata with prefix`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata =
                MetaData.with("userId", "user-123")
                    .and("requestId", "req-456")
                    .and("tenantId", "tenant-789")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("metadata.userId", "user-123")
            assertThat(attributes).containsEntry("metadata.requestId", "req-456")
            assertThat(attributes).containsEntry("metadata.tenantId", "tenant-789")
        }

        @Test
        @DisplayName("should return empty map for empty metadata")
        fun `returns empty map for empty metadata`() {
            // Given
            val provider = MetadataAttributeProvider()
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty()
        }
    }

    @Nested
    @DisplayName("Custom Prefix")
    inner class CustomPrefix {
        @Test
        @DisplayName("should use custom prefix when specified")
        fun `uses custom prefix`() {
            // Given
            val provider = MetadataAttributeProvider(prefix = "custom")
            val metadata = MetaData.with("key", "value")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("custom.key", "value")
        }

        @Test
        @DisplayName("should handle empty prefix")
        fun `handles empty prefix`() {
            // Given
            val provider = MetadataAttributeProvider(prefix = "")
            val metadata = MetaData.with("key", "value")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("key", "value")
        }
    }

    @Nested
    @DisplayName("Key Filtering")
    inner class KeyFiltering {
        @Test
        @DisplayName("should filter keys using predicate")
        fun `filters keys using predicate`() {
            // Given
            val provider =
                MetadataAttributeProvider(
                    keyFilter = { key -> key.startsWith("include") },
                )
            val metadata =
                MetaData.with("includeThis", "value1")
                    .and("excludeThis", "value2")
                    .and("includeAlso", "value3")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("metadata.includeThis", "value1")
            assertThat(attributes).containsEntry("metadata.includeAlso", "value3")
            assertThat(attributes).doesNotContainKey("metadata.excludeThis")
        }

        @Test
        @DisplayName("should return all keys when no filter specified")
        fun `returns all keys when no filter`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata =
                MetaData.with("key1", "value1")
                    .and("key2", "value2")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).hasSize(2)
            assertThat(attributes).containsKey("metadata.key1")
            assertThat(attributes).containsKey("metadata.key2")
        }
    }

    @Nested
    @DisplayName("Value Types")
    inner class ValueTypes {
        @Test
        @DisplayName("should handle string values")
        fun `handles string values`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata = MetaData.with("stringKey", "stringValue")

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("metadata.stringKey", "stringValue")
        }

        @Test
        @DisplayName("should handle numeric values")
        fun `handles numeric values`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata =
                MetaData.with("intKey", 42)
                    .and("longKey", 123456789L)
                    .and("doubleKey", 3.14)

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("metadata.intKey", 42)
            assertThat(attributes).containsEntry("metadata.longKey", 123456789L)
            assertThat(attributes).containsEntry("metadata.doubleKey", 3.14)
        }

        @Test
        @DisplayName("should handle boolean values")
        fun `handles boolean values`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata = MetaData.with("boolKey", true)

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("metadata.boolKey", true)
        }

        @Test
        @DisplayName("should handle null values gracefully")
        fun `handles null values`() {
            // Given
            val provider = MetadataAttributeProvider()
            val metadata = MetaData.with("nullKey", null)

            val message =
                GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))
                    .withMetaData(metadata)

            // When
            val attributes = provider.provideAttributes(message)

            // Then - Null values should be included with string representation
            assertThat(attributes).containsKey("metadata.nullKey")
        }
    }

    @Nested
    @DisplayName("Priority")
    inner class Priority {
        @Test
        @DisplayName("should have default priority of 0")
        fun `has default priority`() {
            // Given
            val provider = MetadataAttributeProvider()

            // When
            val priority = provider.priority()

            // Then
            assertThat(priority).isEqualTo(0)
        }
    }

    // Test domain objects
    data class TestCommand(val id: String)
}
