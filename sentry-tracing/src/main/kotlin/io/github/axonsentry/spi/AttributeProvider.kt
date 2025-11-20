package io.github.axonsentry.spi

import org.axonframework.messaging.Message

/**
 * Service Provider Interface (SPI) for providing custom attributes to spans.
 *
 * Implementations of this interface can add domain-specific metadata, business context,
 * and custom tags to enrich traces without modifying core library code.
 *
 * ## Usage
 * Implement this interface to provide custom attributes based on message content:
 *
 * ```kotlin
 * class TenantAttributeProvider : AttributeProvider {
 *     override fun provideAttributes(message: Message<*>): Map<String, Any> {
 *         val tenantId = message.metaData["tenantId"] as? String
 *         return if (tenantId != null) {
 *             mapOf("tenant.id" to tenantId)
 *         } else {
 *             emptyMap()
 *         }
 *     }
 *
 *     override fun priority(): Int = 100
 * }
 * ```
 *
 * ## Provider Ordering
 * Providers are applied in priority order (higher priority = applied earlier).
 * If multiple providers set the same attribute key, the last provider wins.
 *
 * ## Performance Considerations
 * - Keep attribute extraction lightweight (target <10μs per provider)
 * - Return empty map for irrelevant messages instead of null
 * - Avoid expensive operations like database queries or external API calls
 * - Cache computed attributes when possible
 *
 * @since 1.0.0
 * @see io.github.axonsentry.tracing.CompositeAttributeProvider
 */
interface AttributeProvider {
    /**
     * Provides attributes for the given message.
     *
     * This method is called for every message being traced. Implementations should:
     * - Extract relevant data from message payload or metadata
     * - Return a map of attribute keys to values
     * - Return an empty map if no attributes are applicable
     * - Handle null/missing values gracefully
     *
     * ## Attribute Keys
     * - Use dot notation for hierarchical keys (e.g., "tenant.id", "user.email")
     * - Avoid key collisions with standard Axon attributes (see [SpanAttributes])
     * - Keep keys concise but descriptive
     *
     * ## Attribute Values
     * Supported value types:
     * - String
     * - Number (Int, Long, Double, etc.)
     * - Boolean
     * - Arrays of the above types
     *
     * Complex objects will be converted using [toString()].
     *
     * @param message The Axon message being traced (command, event, or query)
     * @return Map of attribute keys to values, or empty map if no attributes to add
     */
    fun provideAttributes(message: Message<*>): Map<String, Any>

    /**
     * Returns the priority for applying this provider.
     *
     * Providers with higher priority values are applied earlier in the chain.
     * Use priority to control attribute precedence when multiple providers
     * might set the same attribute key.
     *
     * ## Priority Guidelines
     * - **1000+**: Critical business context (tenant ID, organization)
     * - **500-999**: Important metadata (user ID, correlation ID)
     * - **100-499**: Useful context (request ID, session ID)
     * - **0-99**: Optional context (tags, labels)
     * - **Negative**: Fallback/default values
     *
     * @return Priority value (default: 0)
     */
    fun priority(): Int = 0
}
