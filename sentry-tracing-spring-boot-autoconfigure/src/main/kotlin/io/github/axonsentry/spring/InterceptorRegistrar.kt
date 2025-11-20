package io.github.axonsentry.spring

import io.github.axonsentry.axon.CommandTracingInterceptor
import io.github.axonsentry.axon.EventTracingInterceptor
import io.github.axonsentry.axon.QueryTracingInterceptor
import jakarta.annotation.PostConstruct
import org.axonframework.commandhandling.CommandBus
import org.axonframework.eventhandling.EventBus
import org.axonframework.queryhandling.QueryBus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Registers tracing interceptors with Axon Framework buses.
 *
 * This component handles the registration of all tracing interceptors
 * (command, event, query) with their corresponding Axon buses.
 *
 * All dependencies are optional, allowing the library to work even if
 * certain Axon components are not configured or if specific interceptors
 * are disabled via configuration.
 *
 * Registration happens during Spring's post-construction phase via
 * [PostConstruct], ensuring all beans are fully initialized before
 * interceptor registration.
 *
 * Example scenarios:
 * - If CommandBus is not available, command interceptor registration is skipped
 * - If command tracing is disabled, CommandTracingInterceptor bean won't exist
 * - If all components are available, all interceptors are registered
 *
 * @since 1.0.0
 */
@Component
class InterceptorRegistrar {
    private val logger = LoggerFactory.getLogger(InterceptorRegistrar::class.java)

    @Autowired(required = false)
    private var commandBus: CommandBus? = null

    @Autowired(required = false)
    private var eventBus: EventBus? = null

    @Autowired(required = false)
    private var queryBus: QueryBus? = null

    @Autowired(required = false)
    private var commandTracingInterceptor: CommandTracingInterceptor? = null

    @Autowired(required = false)
    private var eventTracingInterceptor: EventTracingInterceptor? = null

    @Autowired(required = false)
    private var queryTracingInterceptor: QueryTracingInterceptor? = null

    /**
     * Registers all available interceptors with their corresponding buses.
     *
     * Called automatically by Spring after all beans are initialized.
     * Handles missing components gracefully - if a bus or interceptor
     * is not available, its registration is simply skipped.
     *
     * Registration results are logged at INFO level for visibility.
     */
    @PostConstruct
    fun registerInterceptors() {
        logger.debug("Starting interceptor registration")

        registerCommandInterceptors()
        registerEventInterceptors()
        registerQueryInterceptors()

        logger.info("Interceptor registration complete")
    }

    private fun registerCommandInterceptors() {
        val bus = commandBus
        val interceptor = commandTracingInterceptor

        if (bus == null) {
            logger.debug("CommandBus not available, skipping command interceptor registration")
            return
        }

        if (interceptor == null) {
            logger.debug("CommandTracingInterceptor not available, skipping registration")
            return
        }

        // Register as both dispatch and handler interceptor
        bus.registerDispatchInterceptor(interceptor)
        bus.registerHandlerInterceptor(interceptor)
        logger.info("Registered CommandTracingInterceptor with CommandBus (dispatch + handler)")
    }

    private fun registerEventInterceptors() {
        val bus = eventBus
        val interceptor = eventTracingInterceptor

        if (bus == null) {
            logger.debug("EventBus not available, skipping event interceptor registration")
            return
        }

        if (interceptor == null) {
            logger.debug("EventTracingInterceptor not available, skipping registration")
            return
        }

        // Register dispatch interceptor
        // Note: Event handler interceptors are registered with event processors,
        // not the bus. This is handled by Axon's configuration infrastructure
        // when it detects MessageHandlerInterceptor beans.
        bus.registerDispatchInterceptor(interceptor)
        logger.info("Registered EventTracingInterceptor with EventBus (dispatch)")
        logger.debug("Event handler interceptor will be registered with processors by Axon")
    }

    private fun registerQueryInterceptors() {
        val bus = queryBus
        val interceptor = queryTracingInterceptor

        if (bus == null) {
            logger.debug("QueryBus not available, skipping query interceptor registration")
            return
        }

        if (interceptor == null) {
            logger.debug("QueryTracingInterceptor not available, skipping registration")
            return
        }

        // Register as both dispatch and handler interceptor
        bus.registerDispatchInterceptor(interceptor)
        bus.registerHandlerInterceptor(interceptor)
        logger.info("Registered QueryTracingInterceptor with QueryBus (dispatch + handler)")
    }
}
