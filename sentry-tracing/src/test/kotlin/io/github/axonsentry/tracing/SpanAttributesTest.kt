package io.github.axonsentry.tracing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class SpanAttributesTest {
    @Test
    fun `all constants are non-null and non-empty`() {
        // Given / When
        val constants =
            SpanAttributes::class.memberProperties
                .filter { it.isConst }
                .map { it.call() as String }

        // Then
        assertThat(constants).isNotEmpty
        assertThat(constants).allMatch { it.isNotBlank() }
    }

    @Test
    fun `no duplicate values across attributes`() {
        // Given / When
        val constants =
            SpanAttributes::class.memberProperties
                .filter { it.isConst }
                .map { it.call() as String }

        // Then
        assertThat(constants).doesNotHaveDuplicates()
    }

    @Test
    fun `axon-specific attributes have proper prefix`() {
        // Given / When
        val axonAttributes =
            listOf(
                SpanAttributes.AXON_MESSAGE_TYPE,
                SpanAttributes.AXON_MESSAGE_NAME,
                SpanAttributes.AXON_MESSAGE_ID,
                SpanAttributes.AXON_AGGREGATE_ID,
                SpanAttributes.AXON_AGGREGATE_TYPE,
                SpanAttributes.AXON_SEQUENCE_NUMBER,
                SpanAttributes.AXON_ROUTING_KEY,
                SpanAttributes.AXON_COMMAND_NAME,
                SpanAttributes.AXON_COMMAND_RESULT_TYPE,
                SpanAttributes.AXON_EVENT_TYPE,
                SpanAttributes.AXON_EVENT_TIMESTAMP,
                SpanAttributes.AXON_QUERY_NAME,
                SpanAttributes.AXON_QUERY_RESPONSE_TYPE,
                SpanAttributes.AXON_HANDLER_CLASS,
                SpanAttributes.AXON_HANDLER_METHOD,
                SpanAttributes.AXON_PROCESSING_GROUP,
                SpanAttributes.AXON_SEGMENT_ID,
            )

        // Then
        assertThat(axonAttributes).allMatch { it.startsWith("axon.") }
    }

    @Test
    fun `messaging attributes follow semantic conventions`() {
        // Then
        assertThat(SpanAttributes.MESSAGING_SYSTEM).isEqualTo("messaging.system")
        assertThat(SpanAttributes.MESSAGING_OPERATION).isEqualTo("messaging.operation")
        assertThat(SpanAttributes.MESSAGING_MESSAGE_ID).isEqualTo("messaging.message.id")
        assertThat(SpanAttributes.MESSAGING_DESTINATION).isEqualTo("messaging.destination.name")
    }

    @Test
    fun `error attributes have proper naming`() {
        // Given / When
        val errorAttributes =
            listOf(
                SpanAttributes.ERROR,
                SpanAttributes.ERROR_TYPE,
                SpanAttributes.ERROR_MESSAGE,
                SpanAttributes.ERROR_STACKTRACE,
            )

        // Then
        assertThat(errorAttributes).allMatch { it.startsWith("error") }
    }

    @Test
    fun `message type constants have correct values`() {
        // Then
        assertThat(SpanAttributes.MESSAGE_TYPE_COMMAND).isEqualTo("command")
        assertThat(SpanAttributes.MESSAGE_TYPE_EVENT).isEqualTo("event")
        assertThat(SpanAttributes.MESSAGE_TYPE_QUERY).isEqualTo("query")
    }

    @Test
    fun `operation constants have correct values`() {
        // Then
        assertThat(SpanAttributes.OPERATION_SEND).isEqualTo("send")
        assertThat(SpanAttributes.OPERATION_RECEIVE).isEqualTo("receive")
        assertThat(SpanAttributes.OPERATION_PROCESS).isEqualTo("process")
    }

    @Test
    fun `command attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_COMMAND_NAME).isNotBlank()
        assertThat(SpanAttributes.AXON_COMMAND_RESULT_TYPE).isNotBlank()
    }

    @Test
    fun `event attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_EVENT_TYPE).isNotBlank()
        assertThat(SpanAttributes.AXON_EVENT_TIMESTAMP).isNotBlank()
    }

    @Test
    fun `query attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_QUERY_NAME).isNotBlank()
        assertThat(SpanAttributes.AXON_QUERY_RESPONSE_TYPE).isNotBlank()
    }

    @Test
    fun `handler attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_HANDLER_CLASS).isNotBlank()
        assertThat(SpanAttributes.AXON_HANDLER_METHOD).isNotBlank()
    }

    @Test
    fun `processing context attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_PROCESSING_GROUP).isNotBlank()
        assertThat(SpanAttributes.AXON_SEGMENT_ID).isNotBlank()
    }

    @Test
    fun `aggregate attributes are defined`() {
        // Then
        assertThat(SpanAttributes.AXON_AGGREGATE_ID).isEqualTo("axon.aggregate.id")
        assertThat(SpanAttributes.AXON_AGGREGATE_TYPE).isEqualTo("axon.aggregate.type")
    }

    @Test
    fun `all attribute keys use dot notation`() {
        // Given / When
        val constants =
            SpanAttributes::class.memberProperties
                .filter { it.isConst }
                .filter {
                    !it.name.startsWith("MESSAGE_TYPE_") &&
                        !it.name.startsWith("OPERATION_") &&
                        it.name != "ERROR" // "error" is a simple boolean flag without namespace
                }
                .map { it.call() as String }

        // Then
        assertThat(constants).allMatch { it.contains(".") }
    }

    @Test
    fun `attribute keys follow naming convention`() {
        // Given / When
        val constants =
            SpanAttributes::class.memberProperties
                .filter { it.isConst }
                .map { it.call() as String }

        // Then - should be lowercase with dots or underscores, no uppercase
        assertThat(constants).allMatch { key ->
            key.all { it.isLowerCase() || it.isDigit() || it == '.' || it == '_' }
        }
    }
}
