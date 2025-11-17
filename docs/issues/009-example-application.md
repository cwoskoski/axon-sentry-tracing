# Issue 009: Example Spring Boot Application

**Phase:** Documentation
**Priority:** Medium
**Complexity:** Medium
**Status:** Not Started
**Dependencies:** 008

## Overview
Create a comprehensive example Spring Boot application demonstrating the axon-sentry-tracing library in action. This serves as both documentation and a testing ground for the library's features, showing developers how to integrate and use it in real-world scenarios.

## Goals
- Demonstrate all tracing features in a working application
- Provide clear, copy-paste-ready configuration examples
- Show best practices for Axon + Sentry integration
- Serve as integration testing environment
- Document common use cases and patterns
- Make it easy for users to get started

## Technical Requirements

### Components to Create

1. **Example Application** (`examples/spring-boot-demo/`)
   - Purpose: Full working Spring Boot + Axon + Sentry app
   - Key responsibilities:
     - Demonstrate command, event, query tracing
     - Show aggregate and saga tracing
     - Illustrate configuration options
     - Provide realistic domain model

2. **Domain Model** (Bank Account example)
   - Purpose: Realistic, understandable domain
   - Key responsibilities:
     - Commands: CreateAccount, DepositMoney, WithdrawMoney
     - Events: AccountCreated, MoneyDeposited, MoneyWithdrawn
     - Queries: FindAccount, ListAccounts
     - Aggregate: BankAccount
     - Saga: MoneyTransferSaga (optional)

3. **Configuration Examples**
   - Purpose: Show different configuration scenarios
   - Key responsibilities:
     - Development configuration
     - Production configuration
     - Custom attribute providers
     - Sampling strategies

4. **Docker Compose Setup**
   - Purpose: Easy local testing with Sentry
   - Key responsibilities:
     - Self-hosted Sentry instance
     - Application container
     - Database (Postgres for event store)

### Dependencies

Create new Gradle module:
```kotlin
// examples/spring-boot-demo/build.gradle.kts
dependencies {
    implementation(project(":"))  // The library itself
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.axonframework:axon-spring-boot-starter")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.axonframework:axon-test")
}
```

### Configuration

Create application.yml with sensible defaults and comments.

## Implementation Guidance

### Step-by-Step Approach

1. **Create Module Structure**
   - Add example module to settings.gradle.kts
   - Create build.gradle.kts for example
   - Set up package structure

2. **Implement Domain Model**
   - Create commands, events, queries
   - Implement BankAccount aggregate
   - Create query handlers and projections
   - Add validation and business logic

3. **Configure Application**
   - Create application.yml with tracing config
   - Set up Axon configuration
   - Configure Sentry DSN
   - Add database configuration

4. **Create REST API**
   - Controllers for commands and queries
   - Error handling
   - API documentation

5. **Add Docker Compose**
   - Sentry service
   - PostgreSQL service
   - Application service
   - Volume configuration

6. **Create Documentation**
   - README with setup instructions
   - API usage examples
   - Sentry dashboard guide

### Code Examples

#### Domain Model (BankAccount.kt)
```kotlin
package io.github.axonsentry.example.domain

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.math.BigDecimal
import java.util.UUID

// Commands
data class CreateAccountCommand(val accountId: UUID, val initialBalance: BigDecimal)
data class DepositMoneyCommand(val accountId: UUID, val amount: BigDecimal)
data class WithdrawMoneyCommand(val accountId: UUID, val amount: BigDecimal)

// Events
data class AccountCreatedEvent(val accountId: UUID, val initialBalance: BigDecimal)
data class MoneyDepositedEvent(val accountId: UUID, val amount: BigDecimal)
data class MoneyWithdrawnEvent(val accountId: UUID, val amount: BigDecimal)

// Queries
data class FindAccountQuery(val accountId: UUID)
data class ListAccountsQuery(val page: Int = 0, val size: Int = 20)

// Aggregate
@Aggregate
class BankAccount() {

    @AggregateIdentifier
    private lateinit var accountId: UUID
    private var balance: BigDecimal = BigDecimal.ZERO

    @CommandHandler
    constructor(command: CreateAccountCommand) : this() {
        require(command.initialBalance >= BigDecimal.ZERO) {
            "Initial balance cannot be negative"
        }

        AggregateLifecycle.apply(
            AccountCreatedEvent(command.accountId, command.initialBalance)
        )
    }

    @CommandHandler
    fun handle(command: DepositMoneyCommand) {
        require(command.amount > BigDecimal.ZERO) {
            "Deposit amount must be positive"
        }

        AggregateLifecycle.apply(
            MoneyDepositedEvent(command.accountId, command.amount)
        )
    }

    @CommandHandler
    fun handle(command: WithdrawMoneyCommand) {
        require(command.amount > BigDecimal.ZERO) {
            "Withdrawal amount must be positive"
        }
        require(balance >= command.amount) {
            "Insufficient funds. Current balance: $balance"
        }

        AggregateLifecycle.apply(
            MoneyWithdrawnEvent(command.accountId, command.amount)
        )
    }

    @EventSourcingHandler
    fun on(event: AccountCreatedEvent) {
        this.accountId = event.accountId
        this.balance = event.initialBalance
    }

    @EventSourcingHandler
    fun on(event: MoneyDepositedEvent) {
        this.balance += event.amount
    }

    @EventSourcingHandler
    fun on(event: MoneyWithdrawnEvent) {
        this.balance -= event.amount
    }
}
```

#### Application Configuration (application.yml)
```yaml
spring:
  application:
    name: axon-sentry-example

  datasource:
    url: jdbc:postgresql://localhost:5432/axon_example
    username: axon
    password: axon

  jpa:
    hibernate:
      ddl-auto: update

axon:
  sentry:
    tracing:
      enabled: true
      trace-commands: true
      trace-events: true
      trace-queries: true
      trace-event-processors: true

      # Sentry Configuration
      sentry-dsn: ${SENTRY_DSN:https://your-dsn@sentry.io/project}
      environment: ${ENVIRONMENT:development}
      traces-sample-rate: 1.0

      # Payload Capture (use with caution in production)
      capture-command-payloads: true
      capture-event-payloads: true
      capture-query-payloads: false

      # Custom Tags
      tags:
        service: bank-account-service
        version: 1.0.0

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,tracing

  endpoint:
    health:
      show-details: always

logging:
  level:
    io.github.axonsentry: DEBUG
    org.axonframework: INFO
```

#### REST Controller (AccountController.kt)
```kotlin
package io.github.axonsentry.example.api

import io.github.axonsentry.example.domain.*
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    fun createAccount(
        @RequestBody request: CreateAccountRequest
    ): CompletableFuture<ResponseEntity<CreateAccountResponse>> {
        val accountId = UUID.randomUUID()

        return commandGateway.send<UUID>(
            CreateAccountCommand(accountId, request.initialBalance)
        ).thenApply {
            ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateAccountResponse(it))
        }
    }

    @PostMapping("/{accountId}/deposit")
    fun deposit(
        @PathVariable accountId: UUID,
        @RequestBody request: DepositRequest
    ): CompletableFuture<ResponseEntity<Void>> {
        return commandGateway.send<Void>(
            DepositMoneyCommand(accountId, request.amount)
        ).thenApply {
            ResponseEntity.ok().build()
        }
    }

    @PostMapping("/{accountId}/withdraw")
    fun withdraw(
        @PathVariable accountId: UUID,
        @RequestBody request: WithdrawRequest
    ): CompletableFuture<ResponseEntity<Void>> {
        return commandGateway.send<Void>(
            WithdrawMoneyCommand(accountId, request.amount)
        ).thenApply {
            ResponseEntity.ok().build()
        }
    }

    @GetMapping("/{accountId}")
    fun getAccount(
        @PathVariable accountId: UUID
    ): CompletableFuture<ResponseEntity<AccountView>> {
        return queryGateway.query(
            FindAccountQuery(accountId),
            ResponseTypes.instanceOf(AccountView::class.java)
        ).thenApply {
            ResponseEntity.ok(it)
        }
    }

    @GetMapping
    fun listAccounts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): CompletableFuture<ResponseEntity<List<AccountView>>> {
        return queryGateway.query(
            ListAccountsQuery(page, size),
            ResponseTypes.multipleInstancesOf(AccountView::class.java)
        ).thenApply {
            ResponseEntity.ok(it)
        }
    }
}

// DTOs
data class CreateAccountRequest(val initialBalance: BigDecimal)
data class CreateAccountResponse(val accountId: UUID)
data class DepositRequest(val amount: BigDecimal)
data class WithdrawRequest(val amount: BigDecimal)
data class AccountView(val accountId: UUID, val balance: BigDecimal)
```

#### Docker Compose (docker-compose.yml)
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: axon_example
      POSTGRES_USER: axon
      POSTGRES_PASSWORD: axon
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  sentry:
    image: getsentry/sentry:latest
    environment:
      SENTRY_SECRET_KEY: 'your-secret-key-here'
      SENTRY_POSTGRES_HOST: sentry-postgres
      SENTRY_REDIS_HOST: sentry-redis
    ports:
      - "9000:9000"
    depends_on:
      - sentry-postgres
      - sentry-redis

  sentry-postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: sentry
      POSTGRES_USER: sentry
      POSTGRES_PASSWORD: sentry
    volumes:
      - sentry_postgres_data:/var/lib/postgresql/data

  sentry-redis:
    image: redis:7-alpine
    volumes:
      - sentry_redis_data:/data

  application:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/axon_example
      SENTRY_DSN: ${SENTRY_DSN}
      ENVIRONMENT: development
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - sentry

volumes:
  postgres_data:
  sentry_postgres_data:
  sentry_redis_data:
```

#### README.md (examples/spring-boot-demo/README.md)
```markdown
# Axon-Sentry-Tracing Example Application

A comprehensive Spring Boot application demonstrating the axon-sentry-tracing library.

## Features

- Event-sourced bank account aggregate
- Command, event, and query tracing
- Sentry integration for error tracking
- REST API for account operations
- Docker Compose for local development

## Quick Start

### Prerequisites

- JDK 17+
- Docker and Docker Compose
- Sentry DSN (or use local Sentry instance)

### Running Locally

1. Start infrastructure:
```bash
docker-compose up -d postgres sentry
```

2. Configure Sentry DSN:
```bash
export SENTRY_DSN=your-sentry-dsn
```

3. Run application:
```bash
./gradlew :examples:spring-boot-demo:bootRun
```

4. Access application:
- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Sentry: http://localhost:9000

## API Examples

### Create Account
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"initialBalance": 1000.00}'
```

### Deposit Money
```bash
curl -X POST http://localhost:8080/api/accounts/{accountId}/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00}'
```

### Get Account
```bash
curl http://localhost:8080/api/accounts/{accountId}
```

## Viewing Traces in Sentry

1. Navigate to Sentry dashboard
2. Go to Performance section
3. View transactions by operation:
   - `Command: CreateAccountCommand`
   - `Handle: CreateAccountCommand`
   - `Publish: AccountCreatedEvent`
   - `Query: FindAccountQuery`

## Configuration

See `src/main/resources/application.yml` for configuration options.

Key properties:
- `axon.sentry.tracing.enabled` - Enable/disable tracing
- `axon.sentry.tracing.traces-sample-rate` - Sample rate (0.0 to 1.0)
- `axon.sentry.tracing.capture-*-payloads` - Capture message payloads

## Testing

Run integration tests:
```bash
./gradlew :examples:spring-boot-demo:test
```

## Architecture

```
REST API
  └─> Command Gateway
        └─> Command Bus (traced)
              └─> Aggregate
                    └─> Event Store (traced)
                          └─> Event Bus (traced)
                                └─> Event Handlers (traced)
                                      └─> Query Model

Query Gateway (traced)
  └─> Query Bus
        └─> Query Handlers
```

## License

Apache License 2.0
```

### Integration Points
- Demonstrates all library features in realistic context
- Provides copy-paste configuration examples
- Serves as integration test environment
- Documents common patterns and use cases

## Testing Requirements

### Manual Testing
- [ ] Application starts successfully
- [ ] Commands execute and create spans
- [ ] Events are published and traced
- [ ] Queries execute and are traced
- [ ] Spans appear in Sentry UI
- [ ] Error tracking works
- [ ] Health endpoint returns tracing status

### Integration Tests
- [ ] Test: End-to-end command flow
- [ ] Test: Event processing is traced
- [ ] Test: Query execution is traced
- [ ] Test: Errors are captured in Sentry

## Acceptance Criteria
- [ ] Complete working application
- [ ] Domain model implemented (BankAccount)
- [ ] REST API functional
- [ ] Docker Compose setup works
- [ ] Comprehensive README
- [ ] All traces visible in Sentry
- [ ] Configuration examples provided
- [ ] API examples documented

## Definition of Done
- [ ] Implementation complete
- [ ] Application runs successfully
- [ ] All features demonstrated
- [ ] Docker Compose tested
- [ ] README complete with examples
- [ ] Traces verified in Sentry UI
- [ ] Code meets quality standards
- [ ] PR reviewed and approved
- [ ] Documentation updated
- [ ] Changes committed to main branch

## Resources
- [Spring Boot Guides](https://spring.io/guides)
- [Axon Quick Start](https://docs.axoniq.io/reference-guide/getting-started/quick-start)
- [Sentry Getting Started](https://docs.sentry.io/platforms/java/guides/spring-boot/)

## Notes
- Keep domain model simple but realistic
- Use meaningful example data
- Show both success and error scenarios
- Include configuration comments
- Make it easy to run locally
- Provide curl examples for all endpoints
- Document how to view traces in Sentry
- Consider video tutorial or screenshots
- Make example production-like but educational

---
**Created:** 2025-11-17
**Last Updated:** 2025-11-17
**Assigned To:** Unassigned
