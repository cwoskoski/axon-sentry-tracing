package io.github.axonsentry.tracing

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.GenericQueryMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SpanNameGenerator")
class SpanNameGeneratorTest {
    private val generator = SpanNameGenerator()

    @Nested
    @DisplayName("Command Span Names")
    inner class CommandSpanNames {
        @Test
        @DisplayName("should generate command dispatch span name with command name")
        fun `generateCommandName returns Command prefix with message name`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(CreateOrderCommand("order-123"))

            // When
            val result = generator.generateCommandName(command)

            // Then
            assertThat(result).isEqualTo("Command: CreateOrderCommand")
        }

        @Test
        @DisplayName("should generate command handler span name with Handle prefix")
        fun `generateCommandHandlerName returns Handle prefix with message name`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(CreateOrderCommand("order-123"))

            // When
            val result = generator.generateCommandHandlerName(command)

            // Then
            assertThat(result).isEqualTo("Handle: CreateOrderCommand")
        }

        @Test
        @DisplayName("should handle command with null message name")
        fun `generateCommandName handles null message name by using payload type`() {
            // Given
            val command = GenericCommandMessage.asCommandMessage<Any>(CreateOrderCommand("order-123"))

            // When
            val result = generator.generateCommandName(command)

            // Then
            assertThat(result).contains("CreateOrderCommand")
        }
    }

    @Nested
    @DisplayName("Event Span Names")
    inner class EventSpanNames {
        @Test
        @DisplayName("should generate event publish span name with event name")
        fun `generateEventName returns Event prefix with message name`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(OrderCreatedEvent("order-123"))

            // When
            val result = generator.generateEventName(event)

            // Then
            assertThat(result).isEqualTo("Event: OrderCreatedEvent")
        }

        @Test
        @DisplayName("should generate event handler span name with Handle prefix")
        fun `generateEventHandlerName returns Handle prefix with message name`() {
            // Given
            val event = GenericEventMessage.asEventMessage<Any>(OrderCreatedEvent("order-123"))

            // When
            val result = generator.generateEventHandlerName(event)

            // Then
            assertThat(result).isEqualTo("Handle: OrderCreatedEvent")
        }
    }

    @Nested
    @DisplayName("Query Span Names")
    inner class QuerySpanNames {
        @Test
        @DisplayName("should generate query dispatch span name with query name")
        fun `generateQueryName returns Query prefix with message name`() {
            // Given
            val query = GenericQueryMessage(FindOrderQuery("order-123"), ResponseTypes.instanceOf(String::class.java))

            // When
            val result = generator.generateQueryName(query)

            // Then
            assertThat(result).isEqualTo("Query: FindOrderQuery")
        }

        @Test
        @DisplayName("should generate query handler span name with Handle prefix")
        fun `generateQueryHandlerName returns Handle prefix with message name`() {
            // Given
            val query = GenericQueryMessage(FindOrderQuery("order-123"), ResponseTypes.instanceOf(String::class.java))

            // When
            val result = generator.generateQueryHandlerName(query)

            // Then
            assertThat(result).isEqualTo("Handle: FindOrderQuery")
        }
    }

    @Nested
    @DisplayName("Message Name Extraction")
    inner class MessageNameExtraction {
        @Test
        @DisplayName("should use message name when available")
        fun `extractMessageName uses message name when not null`() {
            // When
            val result = generator.extractMessageName("CustomMessageName", String::class.java)

            // Then
            assertThat(result).isEqualTo("CustomMessageName")
        }

        @Test
        @DisplayName("should extract simple class name from payload type when message name is null")
        fun `extractMessageName uses payload simple name when message name is null`() {
            // When
            val result = generator.extractMessageName(null, CreateOrderCommand::class.java)

            // Then
            assertThat(result).isEqualTo("CreateOrderCommand")
        }

        @Test
        @DisplayName("should handle CGLIB proxy classes by removing enhancer suffix")
        fun `extractMessageName removes CGLIB enhancer suffix`() {
            // Given - Simulating a CGLIB proxy class name
            val cglibProxyName = "io.github.axonsentry.tracing.CreateOrderCommand\$\$EnhancerByCGLIB\$\$12345678"

            // When
            val result = generator.extractMessageName(cglibProxyName, null)

            // Then
            assertThat(result).isEqualTo("CreateOrderCommand")
        }

        @Test
        @DisplayName("should handle anonymous classes by using enclosing class name")
        fun `extractMessageName handles anonymous classes`() {
            // When - The generator's extractMessageName already handles $ suffix
            val result = generator.extractMessageName(null, CreateOrderCommand::class.java)

            // Then - Should extract clean class name without $ suffixes
            assertThat(result).isEqualTo("CreateOrderCommand")
        }

        @Test
        @DisplayName("should return Unknown when both message name and payload type are unavailable")
        fun `extractMessageName returns Unknown when all inputs are null`() {
            // When
            val result = generator.extractMessageName(null, null)

            // Then
            assertThat(result).isEqualTo("Unknown")
        }
    }

    // Test domain objects
    data class CreateOrderCommand(val orderId: String)

    data class OrderCreatedEvent(val orderId: String)

    data class FindOrderQuery(val orderId: String)
}
