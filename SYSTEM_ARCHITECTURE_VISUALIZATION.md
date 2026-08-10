# System Architecture Visualization

## 1. High-Level System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATIONS                                 │
│                  (Web Frontend, Mobile Apps, Admin Portal)                    │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
          ┌──────────────────┐      ┌──────────────────┐
          │  Load Balancer   │      │  API Gateway     │
          │  (Nginx/ALB)     │      │  (Rate Limiter)  │
          └────────┬─────────┘      └────────┬─────────┘
                   │                         │
        ┌──────────┴──────────┬──────────────┴──────────┬──────────┐
        │                     │                         │          │
        ▼                     ▼                         ▼          ▼
    ┌────────┐            ┌────────┐            ┌────────┐    ┌────────┐
    │Instance│            │Instance│            │Instance│    │Instance│
    │  #1    │            │  #2    │     ...    │  #N    │    │  #N+1  │
    └────┬───┘            └────┬───┘            └────┬───┘    └────┬───┘
         │                     │                     │             │
         └─────────────────────┼─────────────────────┼─────────────┘
                               │
                      ┌────────┴────────┐
                      │                 │
                      ▼                 ▼
            ┌──────────────────┐  ┌──────────────────┐
            │   MySQL Cluster  │  │  Redis Cluster   │
            │  (Primary + Read │  │  (Cache Layer)   │
            │   Replicas)      │  │                  │
            └────────┬─────────┘  └────────┬─────────┘
                     │                     │
         ┌───────────┴─────────────────────┴─────────┐
         │                                           │
         ▼                                           ▼
    ┌──────────────┐                      ┌──────────────────┐
    │  RabbitMQ    │                      │ Elasticsearch    │
    │  (Messaging) │                      │ (Full-text       │
    │              │                      │  Search)         │
    └──────────────┘                      └──────────────────┘
```

## 2. Modular Monolith Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot Application                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    Presentation Layer                             │  │
│  │  REST Controllers for All 7 Modules                              │  │
│  │  ├── GET /api/v1/auth/*         ├── GET /api/v1/products/*      │  │
│  │  ├── POST /api/v1/auth/*        ├── POST /api/v1/products/*     │  │
│  │  ├── GET /api/v1/users/*        ├── GET /api/v1/orders/*        │  │
│  │  ├── POST /api/v1/users/*       ├── POST /api/v1/orders/*       │  │
│  │  ├── GET /api/v1/vendors/*      ├── GET /api/v1/payments/*      │  │
│  │  ├── POST /api/v1/vendors/*     ├── POST /api/v1/payments/*     │  │
│  │  ├── GET /api/v1/search/*       ├── POST /api/v1/notifications/*│  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                  Business Logic Layer                             │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                │  │
│  │  │   User      │ │   Vendor    │ │   Product   │                │  │
│  │  │  Service    │ │  Service    │ │  Service    │                │  │
│  │  │  Module     │ │  Module     │ │  Module     │                │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                │  │
│  │  │   Order     │ │   Payment   │ │ Notification│                │  │
│  │  │  Service    │ │  Service    │ │  Service    │                │  │
│  │  │  Module     │ │  Module     │ │  Module     │                │  │
│  │  └─────────────┘ └─────────────┘ └─────────────┘                │  │
│  │  ┌─────────────────────────────────────────┐                    │  │
│  │  │   Search Service Module (Elasticsearch) │                    │  │
│  │  └─────────────────────────────────────────┘                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │               Data Access Layer (JPA/Repositories)               │  │
│  │  UserRepository | VendorRepository | ProductRepository |         │  │
│  │  OrderRepository | PaymentRepository | SearchRepository          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │               Common/Shared Components                           │  │
│  │  ┌─────────────────────────────────────────────────────────────┐ │  │
│  │  │ Config Layer: Redis, RabbitMQ, Security, JWT             │ │  │
│  │  │ Exception Handling: Global Handler, Custom Exceptions    │ │  │
│  │  │ Utilities: API Response, Base Entity, DTOs, Constants    │ │  │
│  │  │ Interceptors: Request/Response logging, auth checks      │ │  │
│  │  └─────────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │            Infrastructure Layer                                  │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │  │
│  │  │ Cache Layer  │ │ Queue Layer  │ │ Storage      │             │  │
│  │  │ (Redis)      │ │ (RabbitMQ)   │ │ (S3/MinIO)   │             │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## 3. Data Flow - Order Processing Example

```
User API Request                        System Response
     │                                       ▲
     │                                       │
     ▼                                       │
  ┌──────────────┐                  ┌───────────────┐
  │ Order POST   │                  │ Order Created │
  │ /api/v1/     │                  │ 201 Created   │
  │ orders       │                  └───────────────┘
  └──────┬───────┘                           ▲
         │                                   │
         ▼                                   │
  ┌──────────────────┐                      │
  │ OrderController  │                      │
  └────────┬─────────┘                      │
           │                                │
           ▼                                │
  ┌──────────────────────┐                 │
  │ OrderService         │                 │
  │ .createOrder()       │                 │
  └────────┬─────────────┘                 │
           │                               │
           ▼                               │
  ┌──────────────────────────────┐         │
  │ 1. Validate Order            │         │
  │ 2. Check Inventory (Redis)   │         │
  │ 3. Save to Database (MySQL)  │         │
  │ 4. Publish Event to RabbitMQ │         │
  └────────┬─────────────────────┘         │
           │                               │
           ├──────────────────┬────────────┤
           │                  │            │
           ▼                  ▼            ▼
  ┌──────────────┐  ┌──────────────┐  ┌───────────┐
  │ Update Cache │  │ Send Message │  │ Update    │
  │ (Redis)      │  │ to Queue     │  │ Inventory │
  │              │  │ (RabbitMQ)   │  │ Cache     │
  └──────────────┘  └──────┬───────┘  └───────────┘
                           │
                           ▼
           ┌───────────────────────────────────┐
           │ Message Queue Consumers:          │
           │ - Payment Service                 │
           │ - Notification Service            │
           │ - Inventory Service               │
           │ - Analytics Service               │
           └───────────────────────────────────┘
```

## 4. Cache Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    Redis Cache Layers                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Level 1: Session Cache (TTL: 30 min)                      │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Key Pattern       │ Example Value                  │  │
│  │─────────────────────────────────────────────────────│  │
│  │ user:{userId}     │ {id, username, email, role}   │  │
│  │ session:{token}   │ {userId, roles, expiresAt}    │  │
│  │ cart:{userId}     │ [{productId, qty, price}]     │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
│  Level 2: Application Cache (TTL: 1-24 hours)             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ product:{productId}      │ Full product details    │  │
│  │ vendor:{vendorId}        │ Vendor info             │  │
│  │ category:all             │ List of categories      │  │
│  │ hot-products:{date}      │ Trending products       │  │
│  │ product-reviews:{id}     │ Product reviews         │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
│  Level 3: Distributed Cache (Real-time)                   │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ inventory:{productId}    │ Stock level (live)      │  │
│  │ order-status:{orderId}   │ Current order status    │  │
│  │ payment-status:{id}      │ Payment state           │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 5. Message Queue Flow (RabbitMQ)

```
┌──────────────────────────────────────────────────────────────────┐
│                        Topic Exchange                             │
│                    (ecom-exchange)                                │
└────────────────────────┬─────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┬──────────────┬──────────┐
          │              │              │              │          │
          ▼              ▼              ▼              ▼          ▼
    ┌─────────┐    ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
    │order.   │    │order.    │  │payment.  │  │inventory │  │vendor.   │
    │created  │    │confirmed │  │processed │  │.updated  │  │registered│
    │queue    │    │queue     │  │queue     │  │queue     │  │queue     │
    └────┬────┘    └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
         │              │              │              │             │
         ▼              ▼              ▼              ▼             ▼
    ┌────────────────┐  ┌───────────────┐  ┌────────────────┐  ┌──────────┐
    │Payment Service │  │Notification   │  │Inventory       │  │Analytics │
    │- Initiate      │  │Service        │  │Service         │  │Service   │
    │  payment       │  │- Send emails  │  │- Update stock  │  │- Track   │
    │- Process       │  │- Send SMS     │  │- Update cache  │  │  metrics │
    │  transaction   │  │- Push notify  │  │- Trigger alert │  │          │
    └────────────────┘  └───────────────┘  └────────────────┘  └──────────┘
```

## 6. Database Schema Relationships

```
┌──────────────────┐
│     USERS        │
├──────────────────┤
│ id (PK)          │
│ username         │
│ email            │
│ password_hash    │
│ role             │◄─────────────┐
│ is_active        │              │
│ created_at       │              │
└──────────────────┘              │
         │                        │
         │                   ┌────┴────────────┐
         │                   │                 │
         ▼                   ▼                 ▼
    ┌──────────────┐  ┌─────────────┐  ┌──────────────┐
    │ VENDORS      │  │ ORDERS      │  │ PRODUCTS     │
    ├──────────────┤  ├─────────────┤  ├──────────────┤
    │ id (PK)      │  │ id (PK)     │  │ id (PK)      │
    │ user_id (FK) │  │ user_id (FK)│  │ vendor_id(FK)│
    │ shop_name    │  │ vendor_id(FK)  │ category_id  │
    │ commission   │  │ total_amount│  │ name         │
    │ is_verified  │  │ status      │  │ price        │
    │ created_at   │  │ created_at  │  │ stock        │
    └──────────────┘  └──────┬──────┘  │ rating       │
         │                   │        │ created_at   │
         │                   │        └──────────────┘
         │            ┌──────┴─────┐        │
         │            ▼            ▼        │
         │       ┌──────────────┐          │
         │       │ ORDER_ITEMS  │          │
         │       ├──────────────┤          │
         │       │ id (PK)      │          │
         │       │ order_id(FK) │          │
         │       │ product_id(FK)◄────────┘
         │       │ quantity     │
         │       │ price        │
         │       └──────────────┘
         │
         ▼
    ┌──────────────┐
    │ PAYMENTS     │
    ├──────────────┤
    │ id (PK)      │
    │ order_id(FK) │
    │ amount       │
    │ status       │
    │ method       │
    │ transaction_ │
    │ id           │
    │ created_at   │
    └──────────────┘
```

## 7. API Request/Response Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                     Client Request                              │
│  POST /api/v1/orders                                            │
│  Authorization: Bearer {JWT_TOKEN}                              │
│  Content-Type: application/json                                 │
│                                                                 │
│  {                                                              │
│    "items": [{productId: 1, qty: 2}],                          │
│    "shippingAddress": "..."                                    │
│  }                                                              │
└────────────────────┬──────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                   API Gateway Processing                        │
│  1. Rate limiting check (100 req/min)                          │
│  2. Authentication validation                                   │
│  3. CORS verification                                          │
│  4. Request logging                                            │
└────────────────────┬──────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Application Processing                             │
│  1. Request validation (DTO validation)                         │
│  2. Business logic execution                                    │
│  3. Database transaction                                        │
│  4. Cache updates                                               │
│  5. Message queue publishing                                    │
└────────────────────┬──────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Success Response                               │
│  HTTP/1.1 201 Created                                           │
│  Content-Type: application/json                                 │
│                                                                 │
│  {                                                              │
│    "success": true,                                             │
│    "data": {                                                    │
│      "orderId": 12345,                                          │
│      "status": "PENDING",                                       │
│      "createdAt": "2026-08-10T10:30:00Z"                       │
│    },                                                           │
│    "message": "Order created successfully",                     │
│    "timestamp": "2026-08-10T10:30:00Z",                        │
│    "requestId": "abc-123-def"                                  │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

## 8. Scalability Architecture - Load Distribution

```
                      ┌─────────────────┐
                      │   1M Concurrent │
                      │   Users         │
                      └────────┬────────┘
                               │
                      ┌────────▼────────┐
                      │ Load Balancer   │
                      │ (Nginx/ALB)     │
                      └────────┬────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
   ┌─────────┐            ┌─────────┐            ┌─────────┐
   │ Instance│            │ Instance│            │ Instance│
   │   #1    │            │   #2    │      ...   │   #20   │
   │         │            │         │            │         │
   │ Each:   │            │ Each:   │            │ Each:   │
   │ 4-8 CPU │            │ 4-8 CPU │            │ 4-8 CPU │
   │ 16-32GB │            │ 16-32GB │            │ 16-32GB │
   │ RAM     │            │ RAM     │            │ RAM     │
   │ 50K     │            │ 50K     │            │ 50K     │
   │ req/sec │            │ req/sec │            │ req/sec │
   └────┬────┘            └────┬────┘            └────┬────┘
        │                      │                      │
        └──────────────────────┼──────────────────────┘
                               │
                   ┌───────────┴────────────┐
                   │                        │
                   ▼                        ▼
            ┌──────────────┐        ┌──────────────┐
            │ MySQL        │        │ Redis        │
            │ (Sharded)    │        │ (Cluster)    │
            │              │        │              │
            │ Master +     │        │ 6-9 nodes    │
            │ 5 Read       │        │ 128GB RAM    │
            │ Replicas     │        │ each         │
            └──────────────┘        └──────────────┘
            
            Total Throughput: 20,000+ req/sec
            Total Capacity: 1M+ concurrent users
```

---

This visualization helps understand how all components work together to create a highly scalable multivendor ecommerce platform!

