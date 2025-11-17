package io.github.axonsentry.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TracingConfigurationTest {
    @Test
    fun `builder pattern produces correct configuration`() {
        // Given / When
        val config =
            TracingConfiguration.builder()
                .apply {
                    enabled = true
                    traceCommands = true
                    traceEvents = false
                    sentryDsn = "https://example@sentry.io/123"
                    environment = "production"
                    tracesSampleRate = 0.5
                }
                .build()

        // Then
        assertThat(config.enabled).isTrue()
        assertThat(config.traceCommands).isTrue()
        assertThat(config.traceEvents).isFalse()
        assertThat(config.sentryDsn).isEqualTo("https://example@sentry.io/123")
        assertThat(config.environment).isEqualTo("production")
        assertThat(config.tracesSampleRate).isEqualTo(0.5)
    }

    @Test
    fun `validation rejects sample rate less than 0`() {
        // When / Then
        assertThatThrownBy {
            TracingConfiguration(tracesSampleRate = -0.1)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tracesSampleRate must be between 0.0 and 1.0")
    }

    @Test
    fun `validation rejects sample rate greater than 1`() {
        // When / Then
        assertThatThrownBy {
            TracingConfiguration(tracesSampleRate = 1.1)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tracesSampleRate must be between 0.0 and 1.0")
    }

    @Test
    fun `validation accepts sample rate of 0`() {
        // When
        val config = TracingConfiguration(tracesSampleRate = 0.0)

        // Then
        assertThat(config.tracesSampleRate).isEqualTo(0.0)
    }

    @Test
    fun `validation accepts sample rate of 1`() {
        // When
        val config = TracingConfiguration(tracesSampleRate = 1.0)

        // Then
        assertThat(config.tracesSampleRate).isEqualTo(1.0)
    }

    @Test
    fun `default configuration has sensible values`() {
        // When
        val config = TracingConfiguration.default()

        // Then
        assertThat(config.enabled).isTrue()
        assertThat(config.traceCommands).isTrue()
        assertThat(config.traceEvents).isTrue()
        assertThat(config.traceQueries).isTrue()
        assertThat(config.traceEventProcessors).isTrue()
        assertThat(config.traceSagas).isTrue()
        assertThat(config.captureCommandPayloads).isFalse()
        assertThat(config.captureEventPayloads).isFalse()
        assertThat(config.captureQueryPayloads).isFalse()
        assertThat(config.environment).isEqualTo("development")
        assertThat(config.tracesSampleRate).isEqualTo(1.0)
        assertThat(config.attachStacktrace).isTrue()
        assertThat(config.customAttributeProviders).isEmpty()
    }

    @Test
    fun `errorsOnly disables all tracing flags`() {
        // When
        val config = TracingConfiguration.errorsOnly()

        // Then
        assertThat(config.enabled).isTrue() // Master switch remains enabled
        assertThat(config.traceCommands).isFalse()
        assertThat(config.traceEvents).isFalse()
        assertThat(config.traceQueries).isFalse()
        assertThat(config.traceEventProcessors).isFalse()
        assertThat(config.traceSagas).isFalse()
    }

    @Test
    fun `custom attribute providers are preserved`() {
        // Given
        val provider1 = CustomAttributeProvider { mapOf("key1" to "value1") }
        val provider2 = CustomAttributeProvider { mapOf("key2" to "value2") }

        // When
        val config =
            TracingConfiguration.builder()
                .addAttributeProvider(provider1)
                .addAttributeProvider(provider2)
                .build()

        // Then
        assertThat(config.customAttributeProviders).hasSize(2)
        assertThat(config.customAttributeProviders[0]).isSameAs(provider1)
        assertThat(config.customAttributeProviders[1]).isSameAs(provider2)
    }

    @Test
    fun `toBuilder creates builder with same values`() {
        // Given
        val original =
            TracingConfiguration(
                enabled = true,
                traceCommands = false,
                sentryDsn = "https://example@sentry.io/123",
                environment = "staging",
                tracesSampleRate = 0.75,
            )

        // When
        val rebuilt =
            original.toBuilder()
                .apply {
                    traceQueries = false
                }
                .build()

        // Then
        assertThat(rebuilt.enabled).isEqualTo(original.enabled)
        assertThat(rebuilt.traceCommands).isEqualTo(original.traceCommands)
        assertThat(rebuilt.sentryDsn).isEqualTo(original.sentryDsn)
        assertThat(rebuilt.environment).isEqualTo(original.environment)
        assertThat(rebuilt.tracesSampleRate).isEqualTo(original.tracesSampleRate)
        assertThat(rebuilt.traceQueries).isFalse() // Modified value
    }

    @Test
    fun `builder preserves custom attribute providers when using toBuilder`() {
        // Given
        val provider = CustomAttributeProvider { mapOf("key" to "value") }
        val original =
            TracingConfiguration.builder()
                .addAttributeProvider(provider)
                .build()

        // When
        val rebuilt = original.toBuilder().build()

        // Then
        assertThat(rebuilt.customAttributeProviders).hasSize(1)
        assertThat(rebuilt.customAttributeProviders[0]).isSameAs(provider)
    }

    @Test
    fun `data class copy works correctly`() {
        // Given
        val original =
            TracingConfiguration(
                enabled = true,
                traceCommands = true,
                sentryDsn = "https://example@sentry.io/123",
            )

        // When
        val copy =
            original.copy(
                traceCommands = false,
                environment = "production",
            )

        // Then
        assertThat(copy.enabled).isTrue() // Unchanged
        assertThat(copy.traceCommands).isFalse() // Changed
        assertThat(copy.sentryDsn).isEqualTo("https://example@sentry.io/123") // Unchanged
        assertThat(copy.environment).isEqualTo("production") // Changed
    }

    @Test
    fun `custom attribute provider functional interface works`() {
        // Given
        val provider =
            CustomAttributeProvider { message ->
                mapOf("messageType" to message::class.simpleName!!)
            }

        // When
        val attributes = provider.provideAttributes("test message")

        // Then
        assertThat(attributes).containsEntry("messageType", "String")
    }

    @Test
    fun `builder allows incremental configuration`() {
        // Given
        val builder = TracingConfiguration.builder()

        // When
        builder.enabled = false
        builder.traceCommands = true
        builder.environment = "test"
        val config1 = builder.build()

        builder.traceEvents = false
        val config2 = builder.build()

        // Then
        assertThat(config1.enabled).isFalse()
        assertThat(config1.traceCommands).isTrue()
        assertThat(config1.traceEvents).isTrue() // Default value

        assertThat(config2.enabled).isFalse()
        assertThat(config2.traceCommands).isTrue()
        assertThat(config2.traceEvents).isFalse() // Modified value
    }
}
