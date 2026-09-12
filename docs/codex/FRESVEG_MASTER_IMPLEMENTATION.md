MASTER IMPLEMENTATION PROMPT
PROJECT: FRESVEG ENTERPRISE PLATFORM

ROLE

You are acting as a Principal Solution Architect, Senior Java Engineer,
Database Architect, API Architect, DevOps Engineer, and Technical Lead.

Your responsibility is to build an enterprise-grade FresVeg backend platform
using Spring Boot microservices and PostgreSQL.

The implementation MUST be completed incrementally in phases.

DO NOT attempt to generate the entire platform in one execution.

The purpose of the phased approach is:

1. Keep implementation context small.
2. Make each phase independently testable.
3. Prevent architectural drift.
4. Prevent duplicate or conflicting database migrations.
5. Allow review after every phase.
6. Preserve all existing work between phases.
7. Make failures easier to diagnose.
8. Allow future Codex sessions to continue from repository state.

============================================================
0. EXECUTION RULES
============================================================

These rules apply to EVERY phase.

1. First inspect the existing repository.

2. Never recreate files unnecessarily.

3. Never delete an existing implementation simply because you would
   implement it differently.

4. Preserve backward compatibility unless the current phase explicitly
   requires a contract change.

5. Before changing architecture, inspect:
   - README files
   - pom.xml files
   - application configuration
   - existing Liquibase changelogs
   - Docker files
   - OpenAPI definitions
   - tests
   - existing Java package conventions

6. If the repository already contains an implementation for a required
   component:
   - analyze it
   - reuse it where appropriate
   - improve it safely
   - do not duplicate it

7. Every service MUST compile before a phase is considered complete.

8. Every database migration MUST be managed by Liquibase.

9. Hibernate MUST NOT create or modify production schema.

Set:

spring.jpa.hibernate.ddl-auto=validate

10. Never use ddl-auto=create/update.

11. No service may directly write to another service's database tables.

12. Cross-service references use UUID identifiers but NOT database
    foreign keys across service boundaries.

13. Internal tables within a service should use proper relational
    foreign keys.

14. Use UUID primary keys.

15. Prefer TIMESTAMPTZ for timestamps.

16. Store monetary values using NUMERIC/DECIMAL, never float/double.

17. Currency must use ISO-4217 currency codes.

18. Use snake_case for PostgreSQL identifiers.

19. Use camelCase for JSON fields.

20. Use enums carefully.
    Database enum types should generally be avoided.
    Prefer VARCHAR with application enums and validation constraints.

21. Do not expose JPA entities directly through REST.

Always use:

Controller
   ↓
Application/Service
   ↓
Repository

and:

Request DTO
Response DTO
Mapper

22. Use Java records for immutable API DTOs where appropriate.

23. Generate OpenAPI documentation.

24. Follow RFC 9457 Problem Details for errors.

25. Every request should support a correlation/request ID.

26. Use structured logging.

27. Add unit tests and meaningful integration tests.

28. Use Testcontainers for PostgreSQL integration tests when practical.

29. Use pagination for collection APIs.

30. Use cursor pagination where appropriate for large transactional
    collections.

31. APIs must be versioned under:

/api/v1/

32. Security should use OAuth2/OIDC/JWT.

33. Do not implement home-grown username/password authentication.

34. Use Spring Security Resource Server.

35. The gateway may authenticate requests, but business authorization
    MUST still happen inside services.

36. Never trust the following values from a client:

- customer ownership
- vendor ownership
- product price
- discount value
- tax amount
- delivery charge
- available inventory
- final order amount

37. Prices and order totals are authoritative on the server.

38. Never put secrets in source code.

39. All configuration must support environment variables.

40. At the end of every phase:
    - compile
    - run tests
    - report changed files
    - report created database objects
    - report API endpoints
    - report assumptions
    - report technical debt
    - stop

DO NOT automatically continue to the next phase.

============================================================
1. TARGET ARCHITECTURE
============================================================

Build the following deployable components.

fresveg-platform
|
+-- api-gateway
|
+-- account-service
|
+-- catalog-service
|
+-- supply-service
|
+-- commerce-service
|
+-- fulfillment-service
|
+-- platform-common
|
+-- contracts
|   |
|   +-- openapi
|   +-- asyncapi
|
+-- database
|   |
|   +-- master
|   +-- account
|   +-- catalog
|   +-- supply
|   +-- commerce
|   +-- fulfillment
|
+-- infrastructure
|   |
|   +-- docker
|   +-- kubernetes
|
+-- docker-compose.yml
|
+-- pom.xml
|
+-- README.md

============================================================
2. TECHNOLOGY STACK
============================================================

Preferred backend stack:

Java:
Java 21 or newer LTS-compatible runtime

Framework:
Spring Boot

API Gateway:
Spring Cloud Gateway

Security:
Spring Security
OAuth2 Resource Server
OIDC/JWT

Persistence:
Spring Data JPA
Hibernate

Database:
PostgreSQL

Database migrations:
Liquibase

Caching:
Redis

Messaging:
Kafka-compatible event bus

Initial implementation MAY allow Kafka to be disabled using configuration.

Resilience:
Resilience4j

API specification:
OpenAPI 3.1
SpringDoc

Observability:
Micrometer
OpenTelemetry

Health:
Spring Boot Actuator

Testing:
JUnit 5
Mockito
Testcontainers
REST Assured or MockMvc

Build:
Maven

Container:
Docker

Local orchestration:
Docker Compose

============================================================
3. SERVICE OWNERSHIP
============================================================

------------------------------------------------------------
ACCOUNT SERVICE
------------------------------------------------------------

Own:

users
customer_profiles
addresses
vendors
vendor_users
roles
user_roles
user_preferences

Responsibilities:

user profile
customer profile
vendor organization
vendor staff memberships
addresses
role/permission mapping
account preferences

Authentication itself should be external via OIDC provider.

------------------------------------------------------------
CATALOG SERVICE
------------------------------------------------------------

Own:

products
product_variants
categories
product_categories
units_of_measure
product_images
product_attributes where normalized attributes are needed

Responsibilities:

master product catalog
product classification
categories
product search/filtering
product metadata
units of measure
product images

------------------------------------------------------------
SUPPLY SERVICE
------------------------------------------------------------

Own:

vendor_locations
vendor_listings
vendor_listing_prices
price_tiers
inventory
inventory_batches
inventory_transactions
inventory_reservations

Responsibilities:

vendor product offers
vendor SKU
vendor-specific price
tiered pricing
stock
inventory reservations
inventory movement history
harvest/expiry batches

------------------------------------------------------------
COMMERCE SERVICE
------------------------------------------------------------

Own:

carts
cart_items
orders
order_items
order_status_history
payment_attempts
refunds
outbox_events

Responsibilities:

cart
checkout preview
order placement
pricing snapshot
payment orchestration
refund state
order lifecycle
transactional outbox

------------------------------------------------------------
FULFILLMENT SERVICE
------------------------------------------------------------

Own:

delivery_slots
fulfillments
fulfillment_items
shipments
shipment_events
delivery_assignments

Responsibilities:

delivery availability
fulfillment planning
shipment lifecycle
tracking
delivery status

============================================================
4. DATABASE DESIGN PRINCIPLES
============================================================

Use PostgreSQL as system of record.

Prefer one PostgreSQL cluster initially.

Support logical separation by database or schema.

For local development it is acceptable to use:

fresveg PostgreSQL instance

with logical schemas:

account
catalog
supply
commerce
fulfillment

Each service MUST only access its own schema.

Do not implement cross-schema JPA relationships.

Cross-service IDs are plain UUID references.

Example:

commerce.order.customer_id

may reference a customer conceptually but MUST NOT have a PostgreSQL
foreign key to account.customer_profiles.

============================================================
5. LIQUIBASE ARCHITECTURE
============================================================

Every service owns its migrations.

Structure:

database/
    master/
        db.changelog-master.yaml

    account/
        db.changelog-account-master.yaml
        changelog/
            001-create-account-schema.yaml
            002-create-users.yaml
            ...

    catalog/
        db.changelog-catalog-master.yaml

    supply/
        db.changelog-supply-master.yaml

    commerce/
        db.changelog-commerce-master.yaml

    fulfillment/
        db.changelog-fulfillment-master.yaml

The root master should include service masters in deterministic order.

Example:

database/master/db.changelog-master.yaml

databaseChangeLog:

  - include:
      file: ../account/db.changelog-account-master.yaml

  - include:
      file: ../catalog/db.changelog-catalog-master.yaml

  - include:
      file: ../supply/db.changelog-supply-master.yaml

  - include:
      file: ../commerce/db.changelog-commerce-master.yaml

  - include:
      file: ../fulfillment/db.changelog-fulfillment-master.yaml

Each changeset MUST include:

id
author
preconditions where useful
rollback where safe
indexes
constraints
comments

Never edit an already deployed changeset.

Create a new changeset for any subsequent modification.

============================================================
6. DATABASE AUDITING
============================================================

Most business tables should have:

created_at TIMESTAMPTZ NOT NULL
created_by UUID NULL
updated_at TIMESTAMPTZ NOT NULL
updated_by UUID NULL
version BIGINT NOT NULL DEFAULT 0

Use optimistic locking where concurrent modification is possible.

============================================================
7. SOFT DELETE POLICY
============================================================

Do NOT use soft deletion everywhere.

Use status fields for business entities where historic references matter.

Examples:

product.status
vendor.status
vendor_listing.status
user.status

Immutable transactional entities such as orders should never be deleted.

============================================================
8. API RESPONSE STANDARD
============================================================

Success:

{
  "data": {},
  "meta": {
    "requestId": "...",
    "timestamp": "..."
  }
}

Collection:

{
  "data": [],
  "pagination": {
    "nextCursor": "...",
    "hasNext": true
  },
  "meta": {
    "requestId": "...",
    "timestamp": "..."
  }
}

Do NOT duplicate HTTP response status as a JSON "code" field unless
business requirements require it.

============================================================
9. ERROR STANDARD
============================================================

Implement RFC 9457 Problem Details.

Example:

{
  "type": "https://api.fresveg.com/errors/insufficient-inventory",
  "title": "Insufficient Inventory",
  "status": 409,
  "detail": "Only 3 KG is currently available.",
  "instance": "/api/v1/orders",
  "code": "INV-409-001",
  "correlationId": "..."
}

Provide global exception handling in each service.

============================================================
10. API GATEWAY ROUTES
============================================================

Expose approximately:

/api/v1/accounts/**
      -> account-service

/api/v1/catalog/**
      -> catalog-service

/api/v1/supply/**
      -> supply-service

/api/v1/carts/**
      -> commerce-service

/api/v1/checkout/**
      -> commerce-service

/api/v1/orders/**
      -> commerce-service

/api/v1/vendor/orders/**
      -> commerce-service

/api/v1/fulfillment/**
      -> fulfillment-service

Implement:

JWT validation
CORS
rate limiting abstraction
request ID
logging
security headers
route configuration
health routes

============================================================
11. DOMAIN RULES
============================================================

PRODUCT != VENDOR LISTING

Product is the global catalog item.

Vendor Listing is the offer from a specific vendor.

Example:

Product:
Roma Tomato

Vendor listing:
Green Valley Farms
Roma Tomato
Grade A
KG
$2.75
minimum order 2 KG

------------------------------------------------------------

CATALOG does NOT own stock.

SUPPLY owns stock.

------------------------------------------------------------

Order must snapshot mutable data.

order_item should contain:

product_id
listing_id
vendor_id

product_name
vendor_name
vendor_sku
unit_code

quantity
unit_price
discount_amount
tax_amount
line_total

product_snapshot JSONB

------------------------------------------------------------

Order should snapshot delivery address.

Never depend solely upon mutable account.address for order history.

------------------------------------------------------------

Inventory model:

quantity_on_hand
reserved_quantity

available quantity should logically equal:

quantity_on_hand - reserved_quantity

Avoid storing derived available quantity unless there is a strong
performance reason.

------------------------------------------------------------

Inventory reservation must prevent overselling.

Reservation should include:

reservation_id
inventory_id
external_reference
quantity
status
expires_at
created_at

Statuses:

ACTIVE
COMMITTED
RELEASED
EXPIRED

------------------------------------------------------------

Fresh produce inventory should support batches:

inventory_batch:

batch_id
inventory_id
batch_number
harvest_date
received_date
best_before_date
expiry_date
origin
grade
certification_data JSONB
quantity_received
quantity_remaining
status

Future allocation should be compatible with FEFO.

============================================================
12. SECURITY RULES
============================================================

Authenticated customer identity comes from JWT.

DO NOT accept customerId in normal customer-owned API requests where
identity can be derived from JWT.

Vendor APIs derive vendor membership from authenticated user.

Admin-only APIs require appropriate authority.

Use method-level authorization.

Examples:

@PreAuthorize(...)

Do not depend only on gateway authorization.

============================================================
13. IDEMPOTENCY
============================================================

Order creation and payment initiation must support:

Idempotency-Key

Persist idempotency information where required.

Repeated request with same key must not create duplicate orders.

============================================================
14. EVENTS
============================================================

Design for these domain events:

OrderCreated
OrderConfirmed
OrderCancelled

InventoryReserved
InventoryReservationReleased
InventoryCommitted

PaymentAuthorized
PaymentFailed
RefundCompleted

OrderReadyForFulfillment

FulfillmentCreated
ShipmentCreated
ShipmentDispatched
ShipmentDelivered

Initially support transactional outbox.

Kafka integration should be pluggable.

============================================================
15. OUTBOX PATTERN
============================================================

Commerce must implement outbox_events.

Suggested fields:

event_id UUID
aggregate_type VARCHAR
aggregate_id UUID
event_type VARCHAR
payload JSONB
status VARCHAR
created_at TIMESTAMPTZ
published_at TIMESTAMPTZ NULL
retry_count INTEGER

Order creation and outbox event insertion MUST occur in one DB transaction.

============================================================
16. API CONTRACTS
============================================================

Implement at minimum the following APIs.

============================================================
CATALOG
============================================================

GET /api/v1/catalog/products

Parameters:

q
categoryId
organic
status
sort
pageSize
cursor

GET /api/v1/catalog/products/{productId}

GET /api/v1/catalog/categories

GET /api/v1/catalog/categories/{categoryId}/products

Admin:

POST /api/v1/catalog/products
PUT /api/v1/catalog/products/{productId}
PATCH /api/v1/catalog/products/{productId}
POST /api/v1/catalog/categories

============================================================
SUPPLY
============================================================

GET /api/v1/supply/products/{productId}/offers

GET /api/v1/supply/listings/{listingId}

Vendor:

GET /api/v1/supply/vendor/listings
POST /api/v1/supply/vendor/listings
PUT /api/v1/supply/vendor/listings/{listingId}

GET /api/v1/supply/vendor/inventory
PUT /api/v1/supply/vendor/inventory/{inventoryId}

POST /api/v1/supply/vendor/inventory/{inventoryId}/adjustments

Internal:

POST /internal/v1/inventory/reservations

POST /internal/v1/inventory/reservations/{reservationId}/commit

POST /internal/v1/inventory/reservations/{reservationId}/release

============================================================
CART
============================================================

POST /api/v1/carts

GET /api/v1/carts/{cartId}

POST /api/v1/carts/{cartId}/items

PATCH /api/v1/carts/{cartId}/items/{itemId}

DELETE /api/v1/carts/{cartId}/items/{itemId}

============================================================
CHECKOUT
============================================================

POST /api/v1/checkout/preview

Request example:

{
  "cartId": "...",
  "deliveryAddressId": "...",
  "deliverySlotId": "..."
}

The server calculates:

subtotal
discount
tax
deliveryFee
grandTotal

============================================================
ORDER
============================================================

POST /api/v1/orders

Headers:

Idempotency-Key: required

Request:

{
  "cartId": "...",
  "deliveryAddressId": "...",
  "deliverySlotId": "...",
  "paymentMethodId": "..."
}

Do NOT accept:

customerId
agreedUnitPrice
grandTotal

GET /api/v1/orders

GET /api/v1/orders/{orderId}

POST /api/v1/orders/{orderId}/cancel

============================================================
VENDOR ORDER OPERATIONS
============================================================

GET /api/v1/vendor/orders

GET /api/v1/vendor/orders/{orderId}

POST /api/v1/vendor/orders/{orderId}/accept

POST /api/v1/vendor/orders/{orderId}/reject

============================================================
FULFILLMENT
============================================================

GET /api/v1/fulfillment/slots

GET /api/v1/fulfillments/{fulfillmentId}

GET /api/v1/fulfillments/{fulfillmentId}/tracking

Internal:

POST /internal/v1/fulfillments

============================================================
17. OBSERVABILITY
============================================================

Implement:

Actuator health
readiness
liveness
metrics

Propagate:

X-Correlation-ID

If missing, gateway generates one.

Make correlation ID available to logging MDC.

============================================================
18. PHASE EXECUTION MODEL
============================================================

IMPORTANT:

Only execute the phase explicitly requested by the user.

Examples:

"Execute Phase 0"
"Execute Phase 1"
"Continue with Phase 2"

At the start of every phase:

1. inspect repository
2. inspect PREVIOUS_PHASE_SUMMARY.md if present
3. inspect architecture decision records
4. inspect relevant Liquibase files
5. validate previous phase assumptions

At the end of every phase update:

docs/PREVIOUS_PHASE_SUMMARY.md

Include:

Phase completed
Date
Components created
Database changes
API changes
Tests added
Commands used to validate
Known issues
Next phase prerequisites

============================================================
PHASE 0
REPOSITORY ASSESSMENT AND IMPLEMENTATION PLAN
============================================================

GOAL:

Understand repository state before making implementation changes.

DO NOT implement production functionality yet.

Tasks:

1. Inspect repository tree.

2. Determine:
   - existing Spring projects
   - existing Java version
   - existing Spring Boot version
   - Maven structure
   - databases
   - Liquibase
   - existing APIs
   - existing domain objects
   - existing authentication
   - existing Docker support

3. Compare repository with target architecture.

4. Produce:

docs/architecture/current-state.md

docs/architecture/target-state.md

docs/architecture/gap-analysis.md

docs/architecture/service-boundaries.md

docs/architecture/database-ownership.md

docs/architecture/api-migration-plan.md

5. Create Architecture Decision Records:

docs/adr/

ADR-001-service-boundaries.md
ADR-002-postgresql-database-ownership.md
ADR-003-liquibase-strategy.md
ADR-004-api-gateway.md
ADR-005-authentication.md
ADR-006-outbox-pattern.md

6. Produce implementation backlog.

7. Do not make destructive changes.

VALIDATION:

Repository understanding should be sufficient to begin Phase 1.

STOP after reporting Phase 0 results.

============================================================
PHASE 1
FOUNDATION AND MULTI-MODULE PLATFORM
============================================================

GOAL:

Create or normalize the foundational project structure.

Tasks:

1. Configure Maven parent project.

2. Create modules if missing:

platform-common
api-gateway
account-service
catalog-service
supply-service
commerce-service
fulfillment-service

3. Configure Java.

4. Configure Spring Boot dependency management.

5. Add baseline dependencies.

6. Create:

application.yml
application-local.yml
application-test.yml

where appropriate.

7. Add actuator.

8. Add global build conventions.

9. Add Docker Compose with:

PostgreSQL
Redis

Kafka may be optional/profile based.

10. Provide health endpoints.

11. Configure standard JSON serialization.

12. Add common correlation ID utilities only if they do NOT introduce
    service coupling.

13. Ensure:

mvn clean verify

succeeds.

Do NOT implement business schemas yet beyond infrastructure required
for application startup.

STOP.

============================================================
PHASE 2
LIQUIBASE FOUNDATION AND DATABASE MASTER
============================================================

GOAL:

Establish production-grade Liquibase structure before domain implementation.

Tasks:

1. Create schemas:

account
catalog
supply
commerce
fulfillment

2. Create service master changelogs.

3. Create platform master changelog.

4. Configure each service so it runs ONLY its own changelog.

5. Configure optional bootstrap execution for local environment.

6. Add Liquibase Maven support if useful.

7. Set:

spring.jpa.hibernate.ddl-auto=validate

8. Create DB roles where appropriate:

fresveg_account
fresveg_catalog
fresveg_supply
fresveg_commerce
fresveg_fulfillment

9. Document production privilege expectations.

10. Add test proving Liquibase initializes clean PostgreSQL successfully.

11. Test migration from empty DB.

12. Test re-running migration without changes.

STOP.

============================================================
PHASE 3
ACCOUNT SERVICE
============================================================

GOAL:

Implement account and vendor identity domain.

Liquibase tables:

account.users
account.customer_profiles
account.addresses
account.vendors
account.vendor_users
account.roles
account.user_roles
account.user_preferences

Important model:

One user may belong to multiple roles.

A vendor may have multiple users.

Do NOT model vendor as 1:1 user.

Implement APIs:

GET /api/v1/accounts/me

GET /api/v1/accounts/me/addresses

POST /api/v1/accounts/me/addresses

PUT /api/v1/accounts/me/addresses/{addressId}

GET /api/v1/accounts/me/vendor-memberships

Admin/vendor-management endpoints only as necessary.

Create JWT principal abstraction.

Create account ownership authorization.

Add integration tests.

STOP.

============================================================
PHASE 4
CATALOG SERVICE
============================================================

GOAL:

Implement reusable master product catalog.

Liquibase tables:

catalog.products
catalog.product_variants
catalog.categories
catalog.product_categories
catalog.units_of_measure
catalog.product_images

Products should contain flexible:

attributes JSONB

Do not store category as plain product.category string.

Support hierarchical category:

parent_category_id

Create indexes:

product code
name search strategy
category relations
GIN where justified for JSONB

APIs:

GET /api/v1/catalog/products

GET /api/v1/catalog/products/{id}

GET /api/v1/catalog/categories

GET /api/v1/catalog/categories/{id}/products

Admin mutations.

Add pagination and filtering.

Add OpenAPI.

Add tests.

STOP.

============================================================
PHASE 5
SUPPLY SERVICE: VENDOR LISTINGS AND PRICING
============================================================

GOAL:

Separate vendor-specific offers from product master.

Liquibase:

supply.vendor_locations

supply.vendor_listings

supply.vendor_listing_prices

supply.price_tiers

vendor_listing fields should include:

listing_id
vendor_id
product_id
vendor_sku
uom_code or uom reference by identifier
minimum_order_quantity
status
attributes
created_at
updated_at
version

Price:

price_id
listing_id
currency
unit_price
min_quantity
valid_from
valid_to
status

Do NOT place price directly in product.

Implement:

GET /api/v1/supply/products/{productId}/offers

GET /api/v1/supply/listings/{listingId}

Vendor listing management APIs.

Vendor ownership validation.

Add tests.

STOP.

============================================================
PHASE 6
SUPPLY SERVICE: INVENTORY AND BATCHES
============================================================

GOAL:

Implement concurrency-safe inventory.

Liquibase:

supply.inventory

supply.inventory_batches

supply.inventory_transactions

supply.inventory_reservations

Inventory:

inventory_id
listing_id
quantity_on_hand
reserved_quantity
version

Constraint:

quantity_on_hand >= 0

reserved_quantity >= 0

reserved_quantity <= quantity_on_hand

Batch:

batch_id
inventory_id
batch_number
harvest_date
received_date
best_before_date
expiry_date
origin
grade
certification_data
quantity_received
quantity_remaining
status

Implement adjustment ledger.

Never update quantity silently without recording inventory transaction.

Implement reservation service.

Reservation must be concurrency safe.

Consider:

SELECT ... FOR UPDATE

or optimistic locking with retry.

Internal APIs:

POST /internal/v1/inventory/reservations

POST /internal/v1/inventory/reservations/{id}/commit

POST /internal/v1/inventory/reservations/{id}/release

Implement automatic expiration capability.

Test simultaneous reservation attempts.

STOP.

============================================================
PHASE 7
COMMERCE SERVICE: CART
============================================================

GOAL:

Implement cart lifecycle.

Liquibase:

commerce.carts
commerce.cart_items

Cart belongs to authenticated customer.

cart_item references listing_id.

Cart should NOT permanently trust client-provided price.

Price is resolved during read/checkout.

Implement APIs:

POST /api/v1/carts

GET /api/v1/carts/{id}

POST /api/v1/carts/{id}/items

PATCH /api/v1/carts/{id}/items/{itemId}

DELETE /api/v1/carts/{id}/items/{itemId}

Validate listing existence through Supply client.

Introduce service-to-service client abstraction.

Use timeout and resilience.

STOP.

============================================================
PHASE 8
COMMERCE SERVICE: CHECKOUT
============================================================

GOAL:

Implement authoritative server-side checkout.

Implement:

POST /api/v1/checkout/preview

Input:

cartId
deliveryAddressId
deliverySlotId

Resolve:

customer identity from JWT

address from Account Service

prices from Supply Service

availability from Supply Service

delivery slot from Fulfillment Service when implemented,
or abstract interface until next phase

Calculate:

subtotal
discount
tax
deliveryFee
grandTotal

Create pricing calculation abstractions.

Do not hard-code tax logic deeply into controller/service.

Return breakdown.

No order is created in this phase unless repository state makes it
necessary.

STOP.

============================================================
PHASE 9
COMMERCE SERVICE: ORDER
============================================================

GOAL:

Create durable transaction model.

Liquibase:

commerce.orders
commerce.order_items
commerce.order_status_history
commerce.idempotency_records

Order:

order_id
order_number
customer_id
status
currency
subtotal
discount_amount
tax_amount
delivery_fee
grand_total
shipping_address_snapshot JSONB
delivery_slot_id
created_at
updated_at

Order item:

order_item_id
order_id
product_id
listing_id
vendor_id
product_name
vendor_name
vendor_sku
unit_code
quantity
unit_price
discount_amount
tax_amount
line_total
product_snapshot JSONB

Implement POST /api/v1/orders.

Required:

Idempotency-Key

Flow:

1 authenticate customer
2 validate cart
3 resolve authoritative pricing
4 validate address
5 validate delivery slot
6 reserve inventory
7 create order snapshots
8 write status history
9 create outbox event
10 return order

If transaction fails after remote reservation succeeds,
release reservation.

Implement GET order APIs.

Implement cancellation rules.

Test duplicate Idempotency-Key.

STOP.

============================================================
PHASE 10
PAYMENT ORCHESTRATION
============================================================

GOAL:

Add payment abstraction without tightly coupling to one provider.

Liquibase:

commerce.payment_attempts
commerce.refunds

Interfaces:

PaymentGateway
PaymentAuthorizationRequest
PaymentAuthorizationResult
RefundRequest
RefundResult

Provide dummy/local implementation for testing.

Do not store raw payment card data.

Future implementations should support:

Stripe
Adyen
Razorpay
UPI provider

depending deployment region.

Order lifecycle:

PENDING_PAYMENT
PAYMENT_AUTHORIZED
CONFIRMED
PAYMENT_FAILED

Integrate payment and inventory reservation carefully.

Add failure compensation.

STOP.

============================================================
PHASE 11
FULFILLMENT SERVICE
============================================================

GOAL:

Implement delivery and shipment domain.

Liquibase:

fulfillment.delivery_slots
fulfillment.fulfillments
fulfillment.fulfillment_items
fulfillment.shipments
fulfillment.shipment_events
fulfillment.delivery_assignments

Delivery slot:

slot_id
service_area
start_time
end_time
capacity
reserved_capacity
status

Implement:

GET /api/v1/fulfillment/slots

GET /api/v1/fulfillments/{id}

GET /api/v1/fulfillments/{id}/tracking

Internal:

POST /internal/v1/fulfillments

Support order fulfillment creation.

Add state transitions.

STOP.

============================================================
PHASE 12
TRANSACTIONAL OUTBOX AND EVENTS
============================================================

GOAL:

Make asynchronous integration reliable.

Create/review:

commerce.outbox_events

Potential equivalent outbox in services when required.

Implement event publisher abstraction.

Support:

no-op/local implementation
Kafka implementation

Events include:

OrderCreated
OrderConfirmed
OrderCancelled
InventoryReserved
InventoryCommitted
InventoryReleased
PaymentAuthorized
PaymentFailed
OrderReadyForFulfillment
ShipmentCreated
ShipmentDelivered

Events MUST have envelope:

eventId
eventType
eventVersion
aggregateId
occurredAt
correlationId
payload

Create AsyncAPI document.

Ensure event publication failure does not roll back completed business
transaction after outbox insert.

STOP.

============================================================
PHASE 13
API GATEWAY AND SECURITY
============================================================

GOAL:

Provide one secure public API entry point.

Configure Gateway routes.

Implement:

JWT validation
CORS
request ID
security headers
rate limiting abstraction
route-level logging
response security

Propagate:

Authorization
X-Correlation-ID

Do not expose /internal/** externally.

Configure service security.

Add authorization:

CUSTOMER
VENDOR_ADMIN
VENDOR_STAFF
PLATFORM_ADMIN

Do not blindly trust JWT role claims where vendor membership must be
verified in Account Service/database.

STOP.

============================================================
PHASE 14
REDIS AND PERFORMANCE
============================================================

GOAL:

Introduce caching only after correctness.

Candidates:

catalog product
category tree
selected offer reads
delivery slots

Do NOT cache authoritative order writes.

Do NOT make Redis the source of truth for inventory.

Implement:

cache abstraction
TTL
cache invalidation

Add indexes based on actual query patterns.

Run EXPLAIN ANALYZE against major queries.

Document chosen indexes.

STOP.

============================================================
PHASE 15
OBSERVABILITY AND OPERATIONS
============================================================

GOAL:

Make production issues diagnosable.

Implement:

Actuator
Prometheus metrics
OpenTelemetry tracing
structured JSON logs where appropriate
correlation IDs
readiness
liveness

Metrics:

HTTP latency
HTTP errors
DB connection pool
order creation
checkout failures
inventory reservation conflicts
payment failures
outbox backlog

Create operational documentation.

STOP.

============================================================
PHASE 16
CONTAINERIZATION AND LOCAL ENVIRONMENT
============================================================

GOAL:

Start entire platform locally.

Create Dockerfiles for each service.

docker-compose should contain:

postgres
redis
optional kafka
api-gateway
account-service
catalog-service
supply-service
commerce-service
fulfillment-service

Add startup ordering via health checks where possible.

Do not depend only on static sleep commands.

Provide:

make-like commands or shell scripts if useful.

Example:

./scripts/start-local.sh
./scripts/stop-local.sh
./scripts/reset-local-db.sh

Be cautious: reset script must clearly indicate destructive action.

STOP.

============================================================
PHASE 17
OPENAPI CONTRACT CONSOLIDATION
============================================================

GOAL:

Create a stable frontend/backend contract.

Generate or maintain:

contracts/openapi/account-api.yaml
contracts/openapi/catalog-api.yaml
contracts/openapi/supply-api.yaml
contracts/openapi/commerce-api.yaml
contracts/openapi/fulfillment-api.yaml

Ensure implementation and OpenAPI match.

Document:

authentication
error contract
pagination
idempotency
correlation ID

Add contract validation tests if practical.

STOP.

============================================================
PHASE 18
END-TO-END TESTING
============================================================

GOAL:

Validate core FresVeg business flow.

Create integration/E2E scenarios:

SCENARIO 1

Admin creates:
category
product

Vendor creates:
listing
price
inventory batch

Customer:
adds item to cart
previews checkout
creates order

System:
reserves inventory
authorizes payment
confirms order
creates fulfillment

Verify:
inventory
order snapshots
status history
outbox events

------------------------------------------------------------

SCENARIO 2

Two customers attempt to purchase final stock.

Verify:
no overselling

------------------------------------------------------------

SCENARIO 3

Payment failure.

Verify:
inventory reservation released

------------------------------------------------------------

SCENARIO 4

Duplicate order submission with same Idempotency-Key.

Verify:
only one order

------------------------------------------------------------

SCENARIO 5

Product price changes after order creation.

Verify:
old order still shows original price snapshot

------------------------------------------------------------

SCENARIO 6

Customer modifies saved address after order.

Verify:
order shipping address remains unchanged.

STOP.

============================================================
PHASE 19
PRODUCTION HARDENING
============================================================

GOAL:

Prepare release candidate.

Review:

database indexes
N+1 queries
transactions
timeouts
retry behavior
security
PII handling
rate limits
logging
API compatibility
Liquibase history
rollback strategy
backup strategy
connection pools
resource limits

Add:

dependency vulnerability scan
container scan
test coverage report
database migration verification

Document unresolved concerns.

STOP.

============================================================
PHASE 20
FINAL ARCHITECTURE AND HANDOVER
============================================================

GOAL:

Create final engineering documentation.

Generate:

README.md

docs/
    architecture/
        solution-architecture.md
        service-boundaries.md
        database-architecture.md
        integration-architecture.md
        security-architecture.md

    database/
        schema-ownership.md
        migration-guide.md
        entity-reference.md

    api/
        api-guidelines.md
        error-catalog.md

    operations/
        local-development.md
        deployment.md
        troubleshooting.md

    adr/

Create Mermaid diagrams for:

system context
service architecture
database ownership
checkout flow
order flow
inventory reservation
payment failure compensation
event flow

Create final implementation checklist.

STOP.

============================================================
19A. IMPORTANT IMPLEMENTATION DETAILS
============================================================

Use package structure similar to:

com.fresveg.catalog

    api
        ProductController
        dto

    application
        ProductService

    domain
        Product
        Category

    infrastructure
        persistence
        client
        config

Do not create needless "util" dumping grounds.

============================================================
20A. JPA RULES
============================================================

Use:

@Entity
@Table(schema = "...")

Do not use EAGER on collections.

Avoid bidirectional mappings unless genuinely useful.

Prefer explicit queries for performance-sensitive APIs.

Do not serialize JPA proxies.

Use @Version where required.

============================================================
21. LIQUIBASE NAMING STANDARD
============================================================

Changeset IDs:

account-001-create-schema
account-002-create-users
catalog-001-create-products
supply-001-create-vendor-listings
commerce-001-create-carts

Indexes:

idx_<table>_<columns>

Unique constraints:

uk_<table>_<columns>

Foreign keys:

fk_<child>_<parent>

Checks:

ck_<table>_<rule>

============================================================
22. INITIAL INDEX STRATEGY
============================================================

Examples:

catalog.products

UNIQUE(product_code)

index(status)

GIN(attributes)

Potential text search index after query strategy is defined.

supply.vendor_listings

index(vendor_id, status)

index(product_id, status)

unique(vendor_id, vendor_sku)

supply.inventory

unique(listing_id)

index(listing_id)

supply.inventory_reservations

index(inventory_id, status)

index(expires_at, status)

commerce.orders

unique(order_number)

index(customer_id, created_at DESC)

index(status, created_at)

commerce.order_items

index(order_id)

index(vendor_id, order_id)

fulfillment.fulfillments

index(order_id)

index(status, created_at)

Do not add indexes blindly.

============================================================
23. MIGRATION SAFETY
============================================================

Future schema changes should prefer:

expand
migrate
contract

Example:

Do not immediately rename/remove columns used by deployed code.

Instead:

1 add new column
2 support both
3 migrate data
4 deploy new application
5 remove old column in later release

============================================================
24. API VERSIONING
============================================================

Current APIs:

/api/v1/

Breaking changes require:

/api/v2/

Do not introduce v2 for additive changes.

============================================================
25. INTERNAL API CONVENTION
============================================================

Internal APIs:

/internal/v1/

They must not be publicly exposed by API Gateway.

Use separate security policies where possible.

============================================================
26. TEST DATA
============================================================

Provide deterministic local seed/test data only in local/test profile.

Example:

Customer
Vendor
Category
Tomato product
Vendor listing
Inventory batch

Do NOT insert demo users into production migrations unless explicitly
required.

============================================================
27. DEFINITION OF DONE FOR EVERY PHASE
============================================================

A phase is complete only when:

[ ] code compiles

[ ] tests pass

[ ] Liquibase applies to clean DB if DB changed

[ ] Liquibase second run produces no unexpected changes

[ ] OpenAPI updated if APIs changed

[ ] no obvious secrets committed

[ ] README/docs updated where appropriate

[ ] PREVIOUS_PHASE_SUMMARY.md updated

[ ] changed files listed

[ ] known limitations listed

[ ] next phase prerequisites listed

Then STOP.

============================================================
28. RESPONSE FORMAT AFTER EACH PHASE
============================================================

Respond using:

PHASE COMPLETED:
<phase>

IMPLEMENTED:
<summary>

DATABASE:
<schema/tables/migrations>

APIS:
<endpoints>

TESTS:
<tests run>

VALIDATION:
<commands and results>

FILES CREATED:
<important files>

FILES MODIFIED:
<important files>

DECISIONS:
<architectural decisions>

KNOWN LIMITATIONS:
<limitations>

NEXT PHASE:
<phase>

Do not automatically start it.

============================================================
29. FIRST COMMAND
============================================================

When this master prompt is first provided:

DO NOT write the platform yet.

Execute only:

PHASE 0 — REPOSITORY ASSESSMENT AND IMPLEMENTATION PLAN

unless I explicitly provide a different phase.

After Phase 0, STOP and wait for me to say:

"Execute Phase 1"

============================================================
END OF MASTER PROMPT
============================================================