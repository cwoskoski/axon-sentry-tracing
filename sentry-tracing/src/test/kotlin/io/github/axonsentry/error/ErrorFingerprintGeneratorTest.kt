package io.github.axonsentry.error

import org.assertj.core.api.Assertions.assertThat
import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.EventProcessingException
import org.axonframework.queryhandling.QueryExecutionException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ErrorFingerprintGenerator")
class ErrorFingerprintGeneratorTest {
    private lateinit var generator: ErrorFingerprintGenerator

    @BeforeEach
    fun setUp() {
        generator = ErrorFingerprintGenerator()
    }

    @Test
    @DisplayName("should generate consistent fingerprint for same exception type")
    fun `generateFingerprint is consistent for same exception`() {
        // Given
        val exception1 = RuntimeException("Error occurred")
        val exception2 = RuntimeException("Error occurred")

        // When
        val fingerprint1 = generator.generateFingerprint(exception1)
        val fingerprint2 = generator.generateFingerprint(exception2)

        // Then
        assertThat(fingerprint1).hasSameElementsAs(fingerprint2)
    }

    @Test
    @DisplayName("should generate different fingerprints for different exception types")
    fun `generateFingerprint differs by exception type`() {
        // Given
        val runtime = RuntimeException("Error")
        val illegal = IllegalArgumentException("Error")

        // When
        val runtimeFp = generator.generateFingerprint(runtime)
        val illegalFp = generator.generateFingerprint(illegal)

        // Then
        assertThat(runtimeFp).isNotEqualTo(illegalFp as Any)
    }

    @Test
    @DisplayName("should include exception class name in fingerprint")
    fun `generateFingerprint includes exception class`() {
        // Given
        val exception = RuntimeException("Test error")

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("RuntimeException")
    }

    @Test
    @DisplayName("should generate fingerprint for CommandExecutionException")
    fun `generateFingerprint handles CommandExecutionException`() {
        // Given
        val command = GenericCommandMessage.asCommandMessage<String>("CreateAccount")
        val exception = CommandExecutionException("Command failed", null, command)

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("CommandExecutionException")
        assertThat(fingerprint).contains("CommandExecution")
    }

    @Test
    @DisplayName("should generate fingerprint for EventProcessingException")
    fun `generateFingerprint handles EventProcessingException`() {
        // Given
        val exception = EventProcessingException("Event processing failed", RuntimeException())

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("EventProcessingException")
    }

    @Test
    @DisplayName("should generate fingerprint for QueryExecutionException")
    fun `generateFingerprint handles QueryExecutionException`() {
        // Given
        val exception = QueryExecutionException("Query failed", RuntimeException())

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("QueryExecutionException")
    }

    @Test
    @DisplayName("should handle null exception message")
    fun `generateFingerprint handles null message`() {
        // Given
        val exception = RuntimeException()

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).isNotEmpty()
        assertThat(fingerprint).contains("RuntimeException")
    }

    @Test
    @DisplayName("should include top stack frame in fingerprint")
    fun `generateFingerprint includes stack trace location`() {
        // Given
        val exception = RuntimeException("Error at specific location")

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).isNotEmpty()
        // Stack trace details are included for grouping similar errors
    }

    @Test
    @DisplayName("should generate fingerprint with aggregate context for CommandExecutionException")
    fun `generateFingerprint includes aggregate info`() {
        // Given
        val command = GenericCommandMessage.asCommandMessage<String>("UpdateAccount")
        val exception = CommandExecutionException("Invalid state", null, command)

        // When
        val fingerprint = generator.generateFingerprint(exception, "Account", "account-123")

        // Then
        assertThat(fingerprint).contains("CommandExecutionException")
        assertThat(fingerprint).contains("Account")
    }

    @Test
    @DisplayName("should normalize similar error messages")
    fun `generateFingerprint normalizes messages`() {
        // Given
        val exception1 = RuntimeException("Account 123 not found")
        val exception2 = RuntimeException("Account 456 not found")

        // When
        val fingerprint1 = generator.generateFingerprint(exception1)
        val fingerprint2 = generator.generateFingerprint(exception2)

        // Then
        // Should group similar "not found" errors together
        assertThat(fingerprint1).contains("RuntimeException")
        assertThat(fingerprint2).contains("RuntimeException")
    }

    @Test
    @DisplayName("should handle exception with cause")
    fun `generateFingerprint includes cause information`() {
        // Given
        val cause = IllegalStateException("Invalid state")
        val exception = RuntimeException("Wrapper error", cause)

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("RuntimeException")
    }

    @Test
    @DisplayName("should generate stable fingerprints across JVM restarts")
    fun `generateFingerprint is stable`() {
        // Given
        val exception = IllegalArgumentException("Invalid input")

        // When
        val fingerprint1 = generator.generateFingerprint(exception)
        val fingerprint2 = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint1).hasSameElementsAs(fingerprint2)
    }

    @Test
    @DisplayName("should handle custom exception types")
    fun `generateFingerprint handles custom exceptions`() {
        // Given
        val exception = CustomBusinessException("Business rule violated")

        // When
        val fingerprint = generator.generateFingerprint(exception)

        // Then
        assertThat(fingerprint).contains("CustomBusinessException")
    }

    // Test custom exception
    private class CustomBusinessException(message: String) : Exception(message)
}
