package io.github.axonsentry.error

import org.axonframework.commandhandling.CommandExecutionException
import org.axonframework.eventhandling.EventProcessingException
import org.axonframework.queryhandling.QueryExecutionException
import org.slf4j.LoggerFactory

/**
 * Generates consistent fingerprints for error grouping in Sentry.
 *
 * Error fingerprints are used by Sentry to intelligently group similar errors
 * together. This generator creates stable, meaningful fingerprints based on:
 *
 * - Exception type/class name
 * - Message pattern (normalized to group similar messages)
 * - Stack trace location (top frames)
 * - Axon-specific context (aggregate type, command/query name)
 *
 * The goal is to group errors that represent the same underlying issue while
 * keeping distinct problems separate. For example:
 *
 * - "Account 123 not found" and "Account 456 not found" → same fingerprint
 * - ValidationException vs IllegalStateException → different fingerprints
 * - Same exception in different aggregates → different fingerprints
 *
 * Fingerprints are returned as a list of strings that Sentry uses for grouping.
 *
 * @since 1.0.0
 */
class ErrorFingerprintGenerator {
    private val logger = LoggerFactory.getLogger(ErrorFingerprintGenerator::class.java)

    /**
     * Generates a fingerprint for an exception.
     *
     * The fingerprint consists of multiple components that help identify
     * the error uniquely while allowing appropriate grouping:
     *
     * 1. Exception class name
     * 2. Normalized error message pattern
     * 3. Stack trace location (top frame)
     *
     * @param exception The exception to fingerprint
     * @param aggregateType Optional aggregate type for additional context
     * @param aggregateId Optional aggregate ID (not included in fingerprint to allow grouping)
     * @return List of fingerprint components for Sentry grouping
     */
    fun generateFingerprint(
        exception: Throwable,
        aggregateType: String? = null,
        @Suppress("UNUSED_PARAMETER") aggregateId: String? = null,
    ): List<String> {
        return try {
            val components = mutableListOf<String>()

            // 1. Exception type (most important for grouping)
            components.add(exception.javaClass.simpleName)

            // 2. Add Axon-specific context
            when (exception) {
                is CommandExecutionException -> {
                    components.add("CommandExecution")
                    aggregateType?.let { components.add(it) }
                }
                is EventProcessingException -> {
                    components.add("EventProcessing")
                }
                is QueryExecutionException -> {
                    components.add("QueryExecution")
                }
            }

            // 3. Add aggregate type if provided (for non-Axon exceptions)
            if (aggregateType != null && exception !is CommandExecutionException) {
                components.add(aggregateType)
            }

            // 4. Normalized message pattern (helps group similar errors)
            exception.message?.let { message ->
                val normalized = normalizeMessage(message)
                if (normalized.isNotEmpty()) {
                    components.add(normalized)
                }
            }

            // 5. Stack trace location (top frame for precise grouping)
            exception.stackTrace.firstOrNull()?.let { frame ->
                components.add("${frame.className}.${frame.methodName}")
            }

            // Return unique components only
            components.distinct()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            logger.error("Failed to generate error fingerprint", e)
            // Fallback to simple fingerprint
            listOf(exception.javaClass.simpleName)
        }
    }

    /**
     * Normalizes an error message by removing variable parts.
     *
     * This helps group similar errors that differ only in IDs, numbers, etc.
     * For example:
     * - "Account 123 not found" → "Account {id} not found"
     * - "Invalid amount: 99.99" → "Invalid amount: {number}"
     *
     * @param message The error message to normalize
     * @return Normalized message pattern
     */
    private fun normalizeMessage(message: String): String {
        var normalized = message

        // Replace UUIDs with placeholder
        normalized = normalized.replace(UUID_PATTERN, "{uuid}")

        // Replace numbers with placeholder
        normalized = normalized.replace(NUMBER_PATTERN, "{number}")

        // Replace quoted strings with placeholder
        normalized = normalized.replace(QUOTED_STRING_PATTERN, "{string}")

        // Truncate to reasonable length
        if (normalized.length > MAX_MESSAGE_LENGTH) {
            normalized = normalized.substring(0, MAX_MESSAGE_LENGTH)
        }

        return normalized
    }

    companion object {
        private val UUID_PATTERN =
            Regex(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            )
        private val NUMBER_PATTERN = Regex("\\b\\d+(\\.\\d+)?\\b")
        private val QUOTED_STRING_PATTERN = Regex("\"[^\"]*\"")
        private const val MAX_MESSAGE_LENGTH = 100
    }
}
