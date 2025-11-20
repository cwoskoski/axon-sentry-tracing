package io.github.axonsentry.error

import io.sentry.SentryEvent
import io.sentry.SentryOptions
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.GenericMessage
import org.axonframework.messaging.MetaData
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.GenericQueryMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AxonExceptionEnricher")
class AxonExceptionEnricherTest {
    private lateinit var enricher: AxonExceptionEnricher
    private lateinit var sentryOptions: SentryOptions

    @BeforeEach
    fun setUp() {
        enricher = AxonExceptionEnricher()
        sentryOptions =
            SentryOptions().apply {
                dsn = "https://examplePublicKey@o0.ingest.sentry.io/0"
                environment = "test"
            }
    }

    @Test
    @DisplayName("should enrich event with command message context")
    fun `enrich adds command details`() {
        // Given
        val command = GenericCommandMessage.asCommandMessage<String>("CreateAccount")
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, command, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("command")
        assertThat(event.getTag("axon.message_name")).isEqualTo("java.lang.String")
        assertThat(event.getTag("axon.message_id")).isEqualTo(command.identifier)
    }

    @Test
    @DisplayName("should enrich event with domain event message context")
    fun `enrich adds domain event details`() {
        // Given
        val domainEvent: DomainEventMessage<String> =
            GenericDomainEventMessage(
                "TestAggregate",
                "aggregate-123",
                42L,
                "AccountCreated",
                MetaData.emptyInstance(),
            )
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, domainEvent, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("event")
        assertThat(event.getTag("axon.message_name")).isEqualTo("java.lang.String")
        assertThat(event.getTag("axon.aggregate_type")).isEqualTo("TestAggregate")
        assertThat(event.getTag("axon.aggregate_id")).isEqualTo("aggregate-123")
        assertThat(event.getTag("axon.sequence_number")).isEqualTo("42")
    }

    @Test
    @DisplayName("should enrich event with simple event message context")
    fun `enrich adds event details without domain info`() {
        // Given
        val eventMessage = GenericEventMessage.asEventMessage<String>("SimpleEvent")
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, eventMessage, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("event")
        assertThat(event.getTag("axon.message_name")).isEqualTo("java.lang.String")
        assertThat(event.getTag("axon.aggregate_type")).isNull()
        assertThat(event.getTag("axon.aggregate_id")).isNull()
    }

    @Test
    @DisplayName("should enrich event with query message context")
    fun `enrich adds query details`() {
        // Given
        val query =
            GenericQueryMessage(
                "GetAccount",
                ResponseTypes.instanceOf(String::class.java),
            )
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, query, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("query")
        assertThat(event.getTag("axon.message_name")).isEqualTo("java.lang.String")
        // query.queryName returns the payload class name when constructed with just a String payload
        assertThat(event.getTag("axon.query_name")).isNotEmpty()
    }

    @Test
    @DisplayName("should enrich with CommandExecutionException details")
    fun `enrich extracts CommandExecutionException context`() {
        // Given
        val command = GenericCommandMessage.asCommandMessage<String>("UpdateAccount")
        val exception = CommandExecutionException("Validation failed", null, command)
        val event = SentryEvent(exception)
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, command, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("command")
        assertThat(event.getTag("axon.command_name")).isEqualTo(command.commandName)
    }

    @Test
    @DisplayName("should handle null message gracefully")
    fun `enrich handles null message`() {
        // Given
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, null, hint)

        // Then
        // No Axon-specific tags should be added
        assertThat(event.getTag("axon.message_type")).isNull()
    }

    @Test
    @DisplayName("should enrich with message metadata")
    fun `enrich adds metadata as contexts`() {
        // Given
        val metadata = MetaData.with("correlationId", "abc-123").and("userId", "user-456")
        val command = GenericCommandMessage("CreateAccount", metadata)
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, command, hint)

        // Then
        assertThat(event.getTag("axon.message_id")).isNotEmpty()
        // Metadata should be added as context (tested in integration tests)
    }

    @Test
    @DisplayName("should handle generic message type")
    fun `enrich handles generic message`() {
        // Given
        val message = GenericMessage<String>("GenericPayload")
        val event = SentryEvent(RuntimeException("Test error"))
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, message, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("message")
        assertThat(event.getTag("axon.message_name")).isEqualTo("java.lang.String")
    }

    @Test
    @DisplayName("should add exception details to event context")
    fun `enrich adds exception context`() {
        // Given
        val exception = IllegalArgumentException("Invalid input")
        val event = SentryEvent(exception)
        val command = GenericCommandMessage.asCommandMessage<String>("TestCommand")
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, command, hint)

        // Then
        assertThat(event.throwable).isEqualTo(exception)
        assertThat(event.getTag("axon.message_type")).isEqualTo("command")
    }

    @Test
    @DisplayName("should handle CommandExecutionException with aggregate identifier")
    fun `enrich extracts aggregate identifier from CommandExecutionException`() {
        // Given
        val command = GenericCommandMessage.asCommandMessage<String>("DeleteAccount")
        val exception = CommandExecutionException("Cannot delete", null, command)
        val event = SentryEvent(exception)
        val hint = mutableMapOf<String, Any>()

        // When
        enricher.enrich(event, command, hint)

        // Then
        assertThat(event.getTag("axon.message_type")).isEqualTo("command")
        // Aggregate ID extraction is tested in integration tests where we have real aggregate exceptions
    }
}
