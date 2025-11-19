# Issue 019: Core Integration Tests

**Phase:** Core Integration
**Priority:** Critical
**Complexity:** Large
**Status:** Not Started
**Dependencies:** 018

## Overview
Implement end-to-end integration tests that verify complete tracing functionality with Axon Framework and Sentry, including distributed tracing scenarios and real message processing.

## Goals
- Test end-to-end command tracing
- Test end-to-end event tracing
- Test end-to-end query tracing
- Verify distributed trace propagation
- Test Sentry integration
- Validate span hierarchies
- Test realistic scenarios

## Testing Requirements

### Integration Test Scenarios
- [ ] Command dispatch to handler with aggregate
- [ ] Event publication to multiple handlers
- [ ] Query with scatter-gather
- [ ] Distributed transaction across services
- [ ] Saga correlation
- [ ] Error scenarios with recovery

### Test Structure
```kotlin
@SpringBootTest
@EnableAxonServer
class CommandTracingIntegrationTest {

    @Test
    fun `should trace command from dispatch to handler`() {
        // Given
        val command = CreateOrderCommand(orderId, customerId)

        // When
        commandGateway.sendAndWait(command)

        // Then
        assertSpanHierarchy {
            clientSpan("Command: CreateOrderCommand") {
                consumerSpan("Handle: CreateOrderCommand") {
                    attributes {
                        "axon.aggregate_id" shouldBe orderId
                        "axon.aggregate_type" shouldBe "Order"
                    }
                }
            }
        }
    }
}
```

## Acceptance Criteria
- [ ] All integration tests passing
- [ ] End-to-end tracing verified
- [ ] Sentry integration tested
- [ ] Distributed scenarios covered

## Definition of Done
- [ ] Tests implemented
- [ ] CI passing
- [ ] Documentation updated
- [ ] Changes committed

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
