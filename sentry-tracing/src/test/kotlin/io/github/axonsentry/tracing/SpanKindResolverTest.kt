package io.github.axonsentry.tracing

import io.opentelemetry.api.trace.SpanKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SpanKindResolver")
class SpanKindResolverTest {
    private val resolver = SpanKindResolver()

    @Test
    @DisplayName("should resolve dispatch operations to CLIENT span kind")
    fun `resolveDispatchKind returns CLIENT`() {
        // When
        val result = resolver.resolveDispatchKind()

        // Then
        assertThat(result).isEqualTo(SpanKind.CLIENT)
    }

    @Test
    @DisplayName("should resolve publish operations to PRODUCER span kind")
    fun `resolvePublishKind returns PRODUCER`() {
        // When
        val result = resolver.resolvePublishKind()

        // Then
        assertThat(result).isEqualTo(SpanKind.PRODUCER)
    }

    @Test
    @DisplayName("should resolve handler operations to CONSUMER span kind")
    fun `resolveHandlerKind returns CONSUMER`() {
        // When
        val result = resolver.resolveHandlerKind()

        // Then
        assertThat(result).isEqualTo(SpanKind.CONSUMER)
    }

    @Test
    @DisplayName("should resolve internal operations to INTERNAL span kind")
    fun `resolveInternalKind returns INTERNAL`() {
        // When
        val result = resolver.resolveInternalKind()

        // Then
        assertThat(result).isEqualTo(SpanKind.INTERNAL)
    }
}
