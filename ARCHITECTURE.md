# GroceryEcom - High-Level Backend System Architecture

## 1. Overview
- **Scale**: 1 Million Concurrent Users
- **Architecture Pattern**: Modular Monolith (Ready for Microservices Migration)
- **Technology Stack**: Spring Boot 4.1.0, Java 21, MySQL, Redis, RabbitMQ
- **Database**: MySQL 8.0+ with read replicas & sharding
- **Cache**: Redis for session & data caching
- **Message Queue**: RabbitMQ for async operations

---

## 2. System Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    Load Balancer (Nginx/AWS ALB)        │
├─────────────────────────────────────────────────────────┤
│                   API Gateway / Rate Limiter             │
├─────────────────────────────────────────────────────────┤
│  Spring Boot Application (Modular Monolith Structure)    │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Presentation Layer (REST Controllers/GraphQL)    │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Business Logic Layer (Services/Use Cases)        │  │
│  │  - User Module                                    │  │
│  │  - Vendor Module                                  │  │
│  │  - Product Module                                 │  │
│  │  - Order Module                                   │  │
│  │  - Payment Module                                 │  │
│  │  - Notification Module                            │  │
│  │  - Analytics Module                               │  │
│  │  - Search Module                                  │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Data Access Layer (JPA Repositories)             │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Infrastructure (Cache, Queue, File Storage)      │  │
│  └───────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│  External Services: MySQL, Redis, RabbitMQ, S3/Minio   │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Modular Structure (7 Core Modules)

### 3.1 User Module
- User registration, login, profiles
- Role-based access control (RBAC)
- Token management (JWT)
- User preferences & settings

### 3.2 Vendor Module
- Vendor onboarding & KYC
- Vendor catalog management
- Commission management
- Vendor analytics & dashboard

### 3.3 Product Module
- Product catalog (searchable, filterable)
- Category management
- Inventory management (stock levels)
- Pricing strategies (dynamic pricing)
- Product reviews & ratings

### 3.4 Order Module
- Shopping cart management
- Order creation & processing
- Order status tracking
- Return & refund management
- Order history

### 3.5 Payment Module
- Payment gateway integration
- Wallet management
- Transaction history
- Refund processing
- Multiple payment methods support

### 3.6 Notification Module
- Email notifications
- SMS notifications
- Push notifications
- In-app notifications
- Notification preferences

### 3.7 Search & Analytics Module
- Elasticsearch for fast product search
- User behavior analytics
- Sales analytics
- Recommendation engine (ML-ready)

---

## 4. Database Design Strategy

### 4.1 Sharding Strategy (for 1M concurrent users)
- **Primary Shard Key**: User ID (Hash-based partitioning)
- **Secondary Shards**: Vendor ID for vendor data

### 4.2 Database Schema Categories

**Core Tables**:
- users
- vendors
- products
- orders
- order_items
- payments
- transactions

**Inventory Tables**:
- inventory
- inventory_audit

**Search Tables**:
- product_categories
- product_tags
- product_search_index (denormalized)

**Analytics Tables**:
- user_activity_log
- vendor_metrics
- sales_metrics

### 4.3 Performance Optimizations
- **Read Replicas**: 3-5 replicas for read-heavy operations
- **Write Splitting**: Primary for writes, replicas for reads
- **Partitioning**: Time-based partitioning for analytics tables
- **Indexing**: Strategic indexes on frequently queried columns

---

## 5. Caching Strategy

### 5.1 Redis Cache Layers
```
Level 1: Session Cache (TTL: 30 min)
- User sessions
- Authentication tokens

Level 2: Application Cache (TTL: 1-24 hours)
- Product catalog (hot items)
- Vendor information
- Categories & tags
- User profiles

Level 3: Distributed Cache (TTL: varies)
- Shopping carts
- Order status
- Inventory levels (real-time)
```

### 5.2 Cache Invalidation Strategy
- Event-driven invalidation
- Time-based TTL
- LRU eviction policy for memory management

---

## 6. Message Queue Architecture (RabbitMQ)

### 6.1 Priority Queues
```
Priority 1 (Critical):
- Payment transactions
- Order confirmations

Priority 2 (High):
- Notifications
- Inventory updates

Priority 3 (Normal):
- Analytics events
- Recommendation updates
```

### 6.2 Message Flow
- Order Service → Order Created Event → Payment Service
- Payment Service → Payment Confirmed → Notification Service
- Inventory Service → Stock Updated → Search Index Update

---

## 7. API Design

### 7.1 REST API Endpoints Structure
```
/api/v1/auth/
  - POST /login
  - POST /register
  - POST /logout
  - POST /refresh-token

/api/v1/users/
  - GET /{id}
  - PUT /{id}
  - GET /{id}/orders
  - GET /{id}/wishlist

/api/v1/vendors/
  - POST / (register)
  - GET /{id}
  - GET /{id}/products
  - GET /{id}/metrics

/api/v1/products/
  - GET / (with filters, pagination)
  - GET /{id}
  - POST / (vendor)
  - PUT /{id} (vendor)

/api/v1/orders/
  - POST / (create)
  - GET /{id}
  - GET / (list user orders)
  - PUT /{id}/cancel

/api/v1/payments/
  - POST / (initiate)
  - GET /{id}/status
  - POST /{id}/confirm

/api/v1/search/
  - GET / (advanced search with Elasticsearch)
```

### 7.2 API Response Format
```json
{
  "success": true,
  "data": { },
  "pagination": {
    "page": 1,
    "size": 20,
    "total": 100,
    "hasMore": true
  },
  "timestamp": "2026-08-10T10:00:00Z",
  "requestId": "abc-123-def"
}
```

---

## 8. Scalability for 1 Million Concurrent Users

### 8.1 Server-Side Scalability
- **Horizontal Scaling**: 10-20 Spring Boot instances behind load balancer
- **CPU**: 4-8 cores per instance
- **Memory**: 16-32GB per instance (JVM heap: 12-24GB)
- **Connection Pool**: HikariCP with 30-50 connections per instance

### 8.2 Database Scalability
- **MySQL Cluster**: Master-Slave replication
- **Read Replicas**: 5+ for read operations
- **Sharding**: Horizontal partitioning by user ID
- **Connection Pooling**: Shared across all services

### 8.3 Cache Scalability
- **Redis Cluster**: 6-9 nodes (minimum 3 for HA)
- **Memory per node**: 64-128GB
- **Eviction Policy**: allkeys-lru
- **Persistence**: RDB snapshots + AOF

### 8.4 Message Queue Scalability
- **RabbitMQ Cluster**: 3-5 node cluster
- **Queue Sharding**: Multiple queues for parallel processing
- **Consumer Groups**: Scale consumers based on load

---

## 9. Security Architecture

### 9.1 Authentication & Authorization
- **JWT Tokens** with 15-min expiry
- **Refresh Tokens** with 7-day expiry
- **Role-Based Access Control (RBAC)**
- **OAuth 2.0** for third-party integrations

### 9.2 Data Security
- **HTTPS/TLS** for all communications
- **Database Encryption** (at-rest & in-transit)
- **Sensitive Data Masking** in logs
- **PCI DSS Compliance** for payment data

### 9.3 API Security
- **Rate Limiting**: 100 requests/minute per user
- **CORS**: Configured for specific domains
- **API Key Management**: For vendor integrations
- **Request Signing**: For sensitive operations

---

## 10. Monitoring & Observability

### 10.1 Metrics Collection
- Application Metrics: Spring Boot Actuator
- System Metrics: CPU, Memory, Disk I/O
- Database Metrics: Query performance, connection pool
- Cache Metrics: Hit rate, evictions

### 10.2 Logging Strategy
- **Centralized Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **Log Levels**: Debug, Info, Warn, Error
- **Structured Logging**: JSON format for parsing

### 10.3 Alerting
- Database connection pool exhaustion
- Cache hit rate < 80%
- Error rate > 1%
- Response time > 2 seconds (p95)

---

## 11. Deployment & DevOps

### 11.1 Containerization
- **Docker**: Multi-stage builds for optimization
- **Docker Compose**: Local development
- **Kubernetes**: Production orchestration

### 11.2 CI/CD Pipeline
- GitHub Actions or GitLab CI
- Automated tests (Unit, Integration, Load)
- Security scanning (SAST, dependency scanning)
- Automated rollback on failures

### 11.3 Environment Configuration
- Development: Single instance, embedded Redis
- Staging: Multi-instance, separate services
- Production: Full cluster setup with HA

---

## 12. Migration Path to Microservices

When ready to scale beyond monolith:

1. **Phase 1**: Extract Payment Module → Microservice
2. **Phase 2**: Extract Search Module → Microservice
3. **Phase 3**: Extract Notification Module → Microservice
4. **Phase 4**: Extract Order Module → Microservice
5. **Phase 5**: Extract Vendor Module → Microservice

Each extraction follows:
- Database isolation (separate schema/instance)
- Message-based communication (RabbitMQ)
- Service registry (Eureka/Consul)
- API Gateway (Netflix Zuul/Spring Cloud Gateway)

---

## 13. Project Structure

```
src/main/java/com/GroceryEcom/
├── modules/
│   ├── user/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── model/
│   │   └── dto/
│   ├── vendor/
│   ├── product/
│   ├── order/
│   ├── payment/
│   ├── notification/
│   └── search/
├── common/
│   ├── config/
│   ├── exception/
│   ├── util/
│   ├── constants/
│   └── interceptor/
├── infrastructure/
│   ├── cache/
│   ├── queue/
│   ├── storage/
│   └── security/
└── GorceryEcomApplication.java
```

---

## 14. Performance Targets

| Metric | Target |
|--------|--------|
| API Response Time (p95) | < 200ms |
| Database Query Time (p95) | < 100ms |
| Cache Hit Rate | > 85% |
| Throughput | > 10,000 req/sec |
| Availability | 99.99% (4 9s) |
| Data Consistency | Eventual (base case) |

---

## 15. Technology Decision Rationale

- **Spring Boot**: Mature, well-documented, excellent ecosystem
- **MySQL**: ACID compliance, wide adoption, proven scalability
- **Redis**: Sub-millisecond latency, high throughput, perfect for caching
- **RabbitMQ**: Reliable message delivery, flexible routing, clustering support
- **Elasticsearch**: Full-text search, aggregations, real-time analytics

---

## Next Steps

1. ✅ Define entity models & database schema
2. ✅ Implement module structure
3. ✅ Set up configuration management
4. ✅ Implement security framework
5. ✅ Create API endpoints
6. ✅ Integrate Redis for caching
7. ✅ Integrate RabbitMQ for messaging
8. ✅ Add monitoring & logging
9. ✅ Load testing & optimization
10. ✅ Deployment configuration


