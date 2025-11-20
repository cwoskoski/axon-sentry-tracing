package io.github.axonsentry.spi

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.messaging.Message
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AttributeProvider")
class AttributeProviderTest {
    @Test
    @DisplayName("should provide custom attributes for message")
    fun `provides custom attributes`() {
        // Given
        val provider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> =
                    mapOf(
                        "custom.attribute" to "custom-value",
                        "custom.count" to 42,
                    )
            }

        val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))

        // When
        val attributes = provider.provideAttributes(message)

        // Then
        assertThat(attributes).containsEntry("custom.attribute", "custom-value")
        assertThat(attributes).containsEntry("custom.count", 42)
    }

    @Test
    @DisplayName("should have default priority of 0")
    fun `has default priority`() {
        // Given
        val provider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()
            }

        // When
        val priority = provider.priority()

        // Then
        assertThat(priority).isEqualTo(0)
    }

    @Test
    @DisplayName("should allow custom priority")
    fun `allows custom priority`() {
        // Given
        val provider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()

                override fun priority(): Int = 100
            }

        // When
        val priority = provider.priority()

        // Then
        assertThat(priority).isEqualTo(100)
    }

    @Test
    @DisplayName("should return empty map when no attributes")
    fun `returns empty map when no attributes`() {
        // Given
        val provider =
            object : AttributeProvider {
                override fun provideAttributes(message: Message<*>): Map<String, Any> = emptyMap()
            }

        val message = GenericCommandMessage.asCommandMessage<Any>(TestCommand("test-123"))

        // When
        val attributes = provider.provideAttributes(message)

        // Then
        assertThat(attributes).isEmpty()
    }

    // Test domain objects
    data class TestCommand(val id: String)
}
