# Quick Reference Guide

## 🚀 Essential Commands

### Development Setup
```bash
# Start infrastructure services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f [service_name]

# Build project
mvn clean install

# Run application
mvn spring-boot:run

# Build for production
mvn clean package -DskipTests

# Run tests
mvn test

# Run with specific profile
java -jar target/GorceryEcom-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Database Operations
```bash
# Connect to MySQL
mysql -h localhost -u root -p grocery_ecom

# Run Flyway migrations
mvn flyway:migrate

# Repair Flyway
mvn flyway:repair

# Connect to Redis
docker exec -it grocery_ecom_redis redis-cli

# Check Redis keys
redis-cli KEYS '*'

# Flush Redis cache
redis-cli FLUSHALL
```

## 🌐 API Endpoints Quick Reference

### Authentication
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/auth/register` | User registration |
| POST | `/api/v1/auth/login` | User login |
| POST | `/api/v1/auth/refresh-token` | Refresh JWT token |
| POST | `/api/v1/auth/logout` | User logout |

### Users
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/users/{id}` | Get user details |
| PUT | `/api/v1/users/{id}` | Update user profile |
| GET | `/api/v1/users/{id}/orders` | Get user orders |
| POST | `/api/v1/users/{id}/change-password` | Change password |

### Vendors
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/vendors` | Register vendor |
| GET | `/api/v1/vendors/{id}` | Get vendor info |
| PUT | `/api/v1/vendors/{id}` | Update vendor info |
| GET | `/api/v1/vendors/{id}/products` | Get vendor products |
| GET | `/api/v1/vendors/{id}/metrics` | Get vendor analytics |

### Products
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/products` | List all products |
| GET | `/api/v1/products/{id}` | Get product details |
| POST | `/api/v1/products` | Create product (vendor) |
| PUT | `/api/v1/products/{id}` | Update product (vendor) |
| DELETE | `/api/v1/products/{id}` | Delete product (vendor) |

### Orders
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/orders` | Create order |
| GET | `/api/v1/orders/{id}` | Get order details |
| GET | `/api/v1/orders` | List user orders |
| PUT | `/api/v1/orders/{id}/cancel` | Cancel order |
| PUT | `/api/v1/orders/{id}/track` | Track order |

### Payments
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/payments` | Initiate payment |
| GET | `/api/v1/payments/{id}` | Get payment status |
| POST | `/api/v1/payments/{id}/confirm` | Confirm payment |
| POST | `/api/v1/payments/{id}/refund` | Refund payment |

### Search
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/search?q=query` | Search products |
| GET | `/api/v1/search/filters` | Get available filters |
| GET | `/api/v1/search/suggestions?q=query` | Get search suggestions |

### Notifications
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/notifications` | Get notifications |
| PUT | `/api/v1/notifications/{id}/read` | Mark as read |
| DELETE | `/api/v1/notifications/{id}` | Delete notification |

### Admin
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/admin/users` | List all users |
| PUT | `/api/v1/admin/users/{id}/approve` | Approve user/vendor |
| GET | `/api/v1/admin/analytics` | View analytics |
| PUT | `/api/v1/admin/config` | Update config |

## 🛠️ Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://localhost:8080 | N/A |
| Swagger UI | http://localhost:8080/api/swagger-ui.html | N/A |
| API Docs | http://localhost:8080/api/v3/api-docs | N/A |
| Actuator | http://localhost:8080/api/actuator | N/A |
| Metrics | http://localhost:8080/api/actuator/metrics | N/A |
| Health | http://localhost:8080/api/actuator/health | N/A |
| MySQL | localhost:3306 | root / password |
| Redis | localhost:6379 | (no auth) |
| RabbitMQ Web | http://localhost:15672 | guest / guest |
| Kibana | http://localhost:5601 | N/A |
| Elasticsearch | http://localhost:9200 | N/A |

## 📝 Environment Configuration

### Development (application-dev.properties)
```properties
spring.jpa.hibernate.ddl-auto=update
logging.level.root=DEBUG
spring.datasource.url=jdbc:mysql://localhost:3306/grocery_ecom
```

### Production (application-prod.properties)
```properties
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=WARN
server.ssl.enabled=true
```

### Activate Profile
```bash
java -jar app.jar --spring.profiles.active=prod
export SPRING_PROFILES_ACTIVE=prod
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=prod
```

## 🔑 Important Configuration Keys

### Database
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/grocery_ecom
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.hikari.maximum-pool-size=50
```

### Redis
```properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.jedis.pool.max-active=20
```

### RabbitMQ
```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

### JWT
```properties
jwt.secret=your-secret-key-minimum-256-bits
jwt.expiration=900000
jwt.refresh-token-expiration=604800000
```

## 📚 Important Files & Locations

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies |
| `application.properties` | Application config |
| `docker-compose.yml` | Local dev environment |
| `Dockerfile` | Production image |
| `.gitignore` | Git ignore rules |
| `ARCHITECTURE.md` | System design |
| `SETUP.md` | Setup guide |
| `README.md` | Project overview |
| `USER_MODULE_GUIDE.md` | First module example |

## 🔍 Common curl Commands

### Register User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username":"john",
    "email":"john@example.com",
    "password":"SecurePass123",
    "firstName":"John",
    "lastName":"Doe"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username":"john",
    "password":"SecurePass123"
  }'
```

### Get User Info
```bash
curl -X GET http://localhost:8080/api/v1/users/1 \
  -H "Authorization: Bearer {JWT_TOKEN}"
```

### Create Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -d '{
    "items": [{"productId": 1, "quantity": 2}],
    "shippingAddress": "123 Main St"
  }'
```

### Search Products
```bash
curl -X GET "http://localhost:8080/api/v1/search?q=laptop&category=electronics&min_price=500&max_price=1000"
```

### Check Health
```bash
curl http://localhost:8080/api/actuator/health
```

## 🧪 Testing Commands

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run specific test method
mvn test -Dtest=UserServiceTest#testSaveUser

# Run with coverage
mvn test jacoco:report

# View coverage report
mvn jacoco:report && open target/site/jacoco/index.html

# Integration tests
mvn verify

# Performance testing
mvn test -Dgroups=performance
```

## 🐛 Debugging

### Enable Debug Logging
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
```

### View Application Logs
```bash
tail -f logs/GorceryEcom.log
```

### View Docker Logs
```bash
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f rabbitmq
```

### Check Running Containers
```bash
docker-compose ps
```

### Execute Command in Container
```bash
docker exec -it grocery_ecom_mysql mysql -u root -p
docker exec -it grocery_ecom_redis redis-cli
docker exec -it grocery_ecom_rabbitmq rabbitmqctl status
```

## 📊 Performance Monitoring

### View Metrics
```bash
curl http://localhost:8080/api/actuator/metrics
```

### Specific Metrics
```bash
# JVM Memory
curl http://localhost:8080/api/actuator/metrics/jvm.memory.used

# Database Connections
curl http://localhost:8080/api/actuator/metrics/db.connection.active

# HTTP Requests
curl http://localhost:8080/api/actuator/metrics/http.server.requests
```

### Check Database Performance
```bash
mysql> SHOW PROCESSLIST;
mysql> SHOW SLOW LOGS;
```

### Monitor Redis
```bash
redis-cli INFO stats
redis-cli KEYS '*' | wc -l
redis-cli INFO memory
```

## 🚨 Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Database connection refused | `docker-compose up -d mysql && docker-compose logs mysql` |
| Redis connection refused | `docker-compose up -d redis && docker-compose logs redis` |
| Port already in use | `lsof -i :8080` then `kill -9 {PID}` |
| Build failing | `mvn clean install -DskipTests` |
| Tests failing | `mvn clean test` |
| Module not found | Check `pom.xml` dependencies |
| JWT token expired | Use refresh token endpoint |

## 📋 Development Checklist

- [ ] Docker containers running (`docker-compose ps`)
- [ ] Database migrations executed (`mvn flyway:migrate`)
- [ ] Application starting (`mvn spring-boot:run`)
- [ ] Health check passing (http://localhost:8080/api/actuator/health)
- [ ] Swagger accessible (http://localhost:8080/api/swagger-ui.html)
- [ ] Tests passing (`mvn test`)
- [ ] No build warnings (`mvn clean install`)

## 🎯 Quick Module Creation Template

```bash
# 1. Create folders
mkdir -p src/main/java/com/GroceryEcom/GorceryEcom/modules/{module_name}/{controller,service,repository,model,dto}

# 2. Create Entity class
# 3. Create DTOs
# 4. Create Repository interface
# 5. Create Service interface & implementation
# 6. Create Controller
# 7. Create Flyway migration
# 8. Write tests
# 9. Test endpoints
```

## 🔗 Useful Links

- **Spring Boot Docs**: https://spring.io/projects/spring-boot/
- **MySQL Docs**: https://dev.mysql.com/doc/
- **Redis Docs**: https://redis.io/docs/
- **RabbitMQ Docs**: https://www.rabbitmq.com/documentation.html
- **JWT**: https://jwt.io/
- **REST Best Practices**: https://restfulapi.net/
- **Microservices Patterns**: https://microservices.io/patterns/

---

**Remember**: Always check `SETUP.md` for detailed instructions and `ARCHITECTURE.md` for design decisions!

