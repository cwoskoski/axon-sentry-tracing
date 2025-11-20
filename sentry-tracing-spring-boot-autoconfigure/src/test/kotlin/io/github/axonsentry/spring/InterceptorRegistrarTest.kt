package io.github.axonsentry.spring

import io.github.axonsentry.axon.CommandTracingInterceptor
import io.github.axonsentry.axon.EventTracingInterceptor
import io.github.axonsentry.axon.QueryTracingInterceptor
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.axonframework.commandhandling.CommandBus
import org.axonframework.eventhandling.EventBus
import org.axonframework.queryhandling.QueryBus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

/**
 * Unit tests for InterceptorRegistrar.
 */
class InterceptorRegistrarTest {
    private lateinit var registrar: InterceptorRegistrar

    @BeforeEach
    fun setUp() {
        registrar = InterceptorRegistrar()
    }

    @Test
    fun `should register command interceptor when both bus and interceptor are available`() {
        val commandBus = mockk<CommandBus>(relaxed = true)
        val commandInterceptor = mockk<CommandTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "commandBus", commandBus)
        ReflectionTestUtils.setField(registrar, "commandTracingInterceptor", commandInterceptor)

        registrar.registerInterceptors()

        verify { commandBus.registerDispatchInterceptor(commandInterceptor) }
        verify { commandBus.registerHandlerInterceptor(commandInterceptor) }
    }

    @Test
    fun `should not register command interceptor when bus is missing`() {
        val commandInterceptor = mockk<CommandTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "commandBus", null)
        ReflectionTestUtils.setField(registrar, "commandTracingInterceptor", commandInterceptor)

        // Should not throw exception
        registrar.registerInterceptors()

        // No verification needed - just ensure no exception
    }

    @Test
    fun `should not register command interceptor when interceptor is missing`() {
        val commandBus = mockk<CommandBus>(relaxed = true)

        ReflectionTestUtils.setField(registrar, "commandBus", commandBus)
        ReflectionTestUtils.setField(registrar, "commandTracingInterceptor", null)

        registrar.registerInterceptors()

        verify { commandBus wasNot Called }
    }

    @Test
    fun `should register event interceptor when both bus and interceptor are available`() {
        val eventBus = mockk<EventBus>(relaxed = true)
        val eventInterceptor = mockk<EventTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "eventBus", eventBus)
        ReflectionTestUtils.setField(registrar, "eventTracingInterceptor", eventInterceptor)

        registrar.registerInterceptors()

        verify { eventBus.registerDispatchInterceptor(eventInterceptor) }
    }

    @Test
    fun `should not register event interceptor when bus is missing`() {
        val eventInterceptor = mockk<EventTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "eventBus", null)
        ReflectionTestUtils.setField(registrar, "eventTracingInterceptor", eventInterceptor)

        // Should not throw exception
        registrar.registerInterceptors()
    }

    @Test
    fun `should not register event interceptor when interceptor is missing`() {
        val eventBus = mockk<EventBus>(relaxed = true)

        ReflectionTestUtils.setField(registrar, "eventBus", eventBus)
        ReflectionTestUtils.setField(registrar, "eventTracingInterceptor", null)

        registrar.registerInterceptors()

        verify { eventBus wasNot Called }
    }

    @Test
    fun `should register query interceptor when both bus and interceptor are available`() {
        val queryBus = mockk<QueryBus>(relaxed = true)
        val queryInterceptor = mockk<QueryTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "queryBus", queryBus)
        ReflectionTestUtils.setField(registrar, "queryTracingInterceptor", queryInterceptor)

        registrar.registerInterceptors()

        verify { queryBus.registerDispatchInterceptor(queryInterceptor) }
        verify { queryBus.registerHandlerInterceptor(queryInterceptor) }
    }

    @Test
    fun `should not register query interceptor when bus is missing`() {
        val queryInterceptor = mockk<QueryTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "queryBus", null)
        ReflectionTestUtils.setField(registrar, "queryTracingInterceptor", queryInterceptor)

        // Should not throw exception
        registrar.registerInterceptors()
    }

    @Test
    fun `should not register query interceptor when interceptor is missing`() {
        val queryBus = mockk<QueryBus>(relaxed = true)

        ReflectionTestUtils.setField(registrar, "queryBus", queryBus)
        ReflectionTestUtils.setField(registrar, "queryTracingInterceptor", null)

        registrar.registerInterceptors()

        verify { queryBus wasNot Called }
    }

    @Test
    fun `should handle all interceptors being missing gracefully`() {
        ReflectionTestUtils.setField(registrar, "commandBus", null)
        ReflectionTestUtils.setField(registrar, "eventBus", null)
        ReflectionTestUtils.setField(registrar, "queryBus", null)
        ReflectionTestUtils.setField(registrar, "commandTracingInterceptor", null)
        ReflectionTestUtils.setField(registrar, "eventTracingInterceptor", null)
        ReflectionTestUtils.setField(registrar, "queryTracingInterceptor", null)

        // Should not throw exception
        registrar.registerInterceptors()
    }

    @Test
    fun `should register all interceptors when all components are available`() {
        val commandBus = mockk<CommandBus>(relaxed = true)
        val eventBus = mockk<EventBus>(relaxed = true)
        val queryBus = mockk<QueryBus>(relaxed = true)
        val commandInterceptor = mockk<CommandTracingInterceptor>()
        val eventInterceptor = mockk<EventTracingInterceptor>()
        val queryInterceptor = mockk<QueryTracingInterceptor>()

        ReflectionTestUtils.setField(registrar, "commandBus", commandBus)
        ReflectionTestUtils.setField(registrar, "eventBus", eventBus)
        ReflectionTestUtils.setField(registrar, "queryBus", queryBus)
        ReflectionTestUtils.setField(registrar, "commandTracingInterceptor", commandInterceptor)
        ReflectionTestUtils.setField(registrar, "eventTracingInterceptor", eventInterceptor)
        ReflectionTestUtils.setField(registrar, "queryTracingInterceptor", queryInterceptor)

        registrar.registerInterceptors()

        verify { commandBus.registerDispatchInterceptor(commandInterceptor) }
        verify { commandBus.registerHandlerInterceptor(commandInterceptor) }
        verify { eventBus.registerDispatchInterceptor(eventInterceptor) }
        verify { queryBus.registerDispatchInterceptor(queryInterceptor) }
        verify { queryBus.registerHandlerInterceptor(queryInterceptor) }
    }
}
