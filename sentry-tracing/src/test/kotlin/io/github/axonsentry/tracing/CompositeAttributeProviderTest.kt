package io.github.axonsentry.tracing

import io.github.axonsentry.spi.AttributeProvider
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.Message
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CompositeAttributeProvider")
class CompositeAttributeProviderTest {
    @Nested
    @DisplayName("Single Provider")
    inner class SingleProvider {
        @Test
        @DisplayName("should return attributes from single provider")
        fun `returns attributes from single provider`() {
            // Given
            val provider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "custom.attribute" to "value",
                        )
                }

            val composite = CompositeAttributeProvider(listOf(provider))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("custom.attribute", "value")
        }
    }

    @Nested
    @DisplayName("Multiple Providers")
    inner class MultipleProviders {
        @Test
        @DisplayName("should merge attributes from multiple providers")
        fun `merges attributes from multiple providers`() {
            // Given
            val provider1 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "provider1.attribute" to "value1",
                        )
                }

            val provider2 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "provider2.attribute" to "value2",
                        )
                }

            val composite = CompositeAttributeProvider(listOf(provider1, provider2))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("provider1.attribute", "value1")
            assertThat(attributes).containsEntry("provider2.attribute", "value2")
        }

        @Test
        @DisplayName("should handle empty providers list")
        fun `handles empty providers list`() {
            // Given
            val composite = CompositeAttributeProvider(emptyList())
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty()
        }
    }

    @Nested
    @DisplayName("Provider Ordering")
    inner class ProviderOrdering {
        @Test
        @DisplayName("should apply providers in priority order (highest first)")
        fun `applies providers in priority order`() {
            // Given
            val lowPriorityProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "order" to "low",
                        )

                    override fun priority(): Int = 10
                }

            val highPriorityProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "order" to "high",
                        )

                    override fun priority(): Int = 100
                }

            // Add providers in wrong order to test sorting
            val composite = CompositeAttributeProvider(listOf(lowPriorityProvider, highPriorityProvider))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then - Last provider wins (low priority overwrites high priority)
            assertThat(attributes).containsEntry("order", "low")
        }

        @Test
        @DisplayName("should handle providers with same priority")
        fun `handles providers with same priority`() {
            // Given
            val provider1 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "provider" to "first",
                            "unique1" to "value1",
                        )

                    override fun priority(): Int = 50
                }

            val provider2 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "provider" to "second",
                            "unique2" to "value2",
                        )

                    override fun priority(): Int = 50
                }

            val composite = CompositeAttributeProvider(listOf(provider1, provider2))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then - Should have both unique attributes and last provider wins for duplicate
            assertThat(attributes).containsEntry("unique1", "value1")
            assertThat(attributes).containsEntry("unique2", "value2")
            assertThat(attributes).containsEntry("provider", "second")
        }
    }

    @Nested
    @DisplayName("Attribute Overrides")
    inner class AttributeOverrides {
        @Test
        @DisplayName("should allow later providers to override earlier providers")
        fun `later providers override earlier providers`() {
            // Given
            val firstProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "shared.attribute" to "first-value",
                        )
                }

            val secondProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "shared.attribute" to "second-value",
                        )
                }

            val composite = CompositeAttributeProvider(listOf(firstProvider, secondProvider))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("shared.attribute", "second-value")
        }
    }

    @Nested
    @DisplayName("Empty Results")
    inner class EmptyResults {
        @Test
        @DisplayName("should handle providers that return empty maps")
        fun `handles providers that return empty maps`() {
            // Given
            val emptyProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()
                }

            val valueProvider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "attribute" to "value",
                        )
                }

            val composite = CompositeAttributeProvider(listOf(emptyProvider, valueProvider))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("attribute", "value")
        }

        @Test
        @DisplayName("should return empty map when all providers return empty")
        fun `returns empty map when all providers return empty`() {
            // Given
            val emptyProvider1 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()
                }

            val emptyProvider2 =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()
                }

            val composite = CompositeAttributeProvider(listOf(emptyProvider1, emptyProvider2))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).isEmpty()
        }
    }

    @Nested
    @DisplayName("Various Value Types")
    inner class VariousValueTypes {
        @Test
        @DisplayName("should handle string, numeric, and boolean values")
        fun `handles various value types`() {
            // Given
            val provider =
                object : AttributeProvider {
                    override fun provideAttributes(message: Message<*>): Map<String, Any> =
                        mapOf(
                            "string.value" to "text",
                            "int.value" to 42,
                            "long.value" to 123456789L,
                            "double.value" to 3.14,
                            "boolean.value" to true,
                        )
                }

            val composite = CompositeAttributeProvider(listOf(provider))
            val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test"))

            // When
            val attributes = composite.provideAttributes(message)

            // Then
            assertThat(attributes).containsEntry("string.value", "text")
            assertThat(attributes).containsEntry("int.value", 42)
            assertThat(attributes).containsEntry("long.value", 123456789L)
            assertThat(attributes).containsEntry("double.value", 3.14)
            assertThat(attributes).containsEntry("boolean.value", true)
        }
    }

    // Test domain objects
    data class TestCommand(val id: String)
}
