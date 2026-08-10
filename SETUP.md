# GroceryEcom - Setup & Deployment Guide

## Quick Start Guide

### Prerequisites
- Java 21 (JDK)
- Maven 3.9+
- Docker & Docker Compose
- Git

### Local Development Setup

#### 1. Clone the Repository
```bash
git clone <repository-url>
cd GorceryEcom
```

#### 2. Start Infrastructure Services
```bash
# Start MySQL, Redis, RabbitMQ, and Elasticsearch
docker-compose up -d

# Verify services are running
docker-compose ps
```

#### 3. Build the Application
```bash
# Clean and build
mvn clean install

# Skip tests for faster build
mvn clean install -DskipTests
```

#### 4. Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Using Java directly
java -jar target/GorceryEcom-0.0.1-SNAPSHOT.jar
```

#### 5. Verify Application Health
```bash
# Check application health
curl http://localhost:8080/api/actuator/health

# View application metrics
curl http://localhost:8080/api/actuator/metrics
```

### Access Infrastructure Services

**MySQL Database**
- Host: localhost:3306
- Username: root
- Password: password
- Database: grocery_ecom

```bash
mysql -h localhost -u root -p grocery_ecom
```

**Redis CLI**
```bash
docker exec -it grocery_ecom_redis redis-cli
```

**RabbitMQ Management UI**
- URL: http://localhost:15672
- Username: guest
- Password: guest

**Kibana (Log Visualization)**
- URL: http://localhost:5601

---

## Project Structure

```
GorceryEcom/
├── src/
│   ├── main/
│   │   ├── java/com/GroceryEcom/GorceryEcom/
│   │   │   ├── modules/              # Feature modules
│   │   │   │   ├── user/
│   │   │   │   ├── vendor/
│   │   │   │   ├── product/
│   │   │   │   ├── order/
│   │   │   │   ├── payment/
│   │   │   │   ├── notification/
│   │   │   │   └── search/
│   │   │   ├── common/               # Shared code
│   │   │   │   ├── config/
│   │   │   │   ├── exception/
│   │   │   │   ├── util/
│   │   │   │   ├── constants/
│   │   │   │   └── interceptor/
│   │   │   ├── infrastructure/       # Cross-cutting concerns
│   │   │   │   ├── cache/
│   │   │   │   ├── queue/
│   │   │   │   ├── storage/
│   │   │   │   └── security/
│   │   │   └── GorceryEcomApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       ├── application-prod.properties
│   │       └── db/migration/         # Flyway migrations
│   └── test/
├── pom.xml
├── docker-compose.yml
├── ARCHITECTURE.md
├── SETUP.md
└── README.md
```

---

## Configuration Management

### Environment-Specific Properties

**Development** (`application-dev.properties`)
```properties
spring.jpa.hibernate.ddl-auto=update
logging.level.root=DEBUG
spring.redis.host=localhost
spring.datasource.url=jdbc:mysql://localhost:3306/grocery_ecom
```

**Production** (`application-prod.properties`)
```properties
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=WARN
spring.redis.cluster.nodes=redis1:6379,redis2:6379,redis3:6379
spring.datasource.url=jdbc:mysql://mysql-cluster:3306/grocery_ecom
```

**Running with specific profile:**
```bash
java -jar target/GorceryEcom-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## Database Migration

### Using Flyway

1. Create migration file: `src/main/resources/db/migration/V1__Initial_Schema.sql`

2. Run migrations:
```bash
# Automatic on application startup
mvn spring-boot:run

# Manual migration
mvn flyway:migrate
```

### Sample Migration File
```sql
-- src/main/resources/db/migration/V1__Initial_Schema.sql

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_username (username),
    INDEX idx_role (role)
);
```

---

## Testing

### Unit Tests
```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=UserServiceTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests
```bash
# Run integration tests
mvn verify

# Run with specific profile
mvn verify -P integration-tests
```

### Load Testing
```bash
# Using JMeter (install separately)
jmeter -n -t src/test/jmeter/LoadTest.jmx -l results.jtl -j jmeter.log
```

---

## API Documentation

### Swagger/OpenAPI
- URL: http://localhost:8080/api/swagger-ui.html
- API Docs: http://localhost:8080/api/v3/api-docs

### Base API URL
```
http://localhost:8080/api/v1
```

### Sample API Endpoints

**User Registration**
```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Login**
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "SecurePassword123!"
}
```

---

## Monitoring & Observability

### Application Metrics
```bash
curl http://localhost:8080/api/actuator/metrics
```

### Database Metrics
```bash
curl http://localhost:8080/api/actuator/metrics/db.connection.active
```

### JVM Metrics
```bash
curl http://localhost:8080/api/actuator/metrics/jvm.memory.used
```

### Custom Health Check
```bash
curl http://localhost:8080/api/actuator/health
```

---

## Performance Tuning

### JVM Configuration
```bash
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+ParallelRefProcEnabled \
     -jar target/GorceryEcom-0.0.1-SNAPSHOT.jar
```

### Database Connection Pool
- Adjust in `application.properties`:
```properties
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
```

### Redis Optimization
- Enable pipelining for batch operations
- Use Redis Cluster for high availability
- Monitor memory usage and set appropriate eviction policies

---

## Deployment Options

### Option 1: Kubernetes Deployment

1. Build Docker image:
```bash
docker build -t grocery-ecom:1.0.0 .
```

2. Push to registry:
```bash
docker tag grocery-ecom:1.0.0 your-registry/grocery-ecom:1.0.0
docker push your-registry/grocery-ecom:1.0.0
```

3. Deploy to Kubernetes:
```bash
kubectl apply -f k8s/deployment.yaml
```

### Option 2: AWS Deployment

1. Create ECS task definition
2. Create load balancer (ALB)
3. Create RDS instance for MySQL
4. Create ElastiCache for Redis
5. Deploy using ECS service

### Option 3: Docker Compose (Production-like)

```bash
# Scale services
docker-compose up -d --scale app=3

# View logs
docker-compose logs -f app
```

---

## Troubleshooting

### Common Issues

**Issue: Database connection timeout**
```bash
# Verify MySQL is running
docker-compose ps mysql

# Check MySQL logs
docker-compose logs mysql

# Restart MySQL
docker-compose restart mysql
```

**Issue: Redis connection refused**
```bash
# Verify Redis is running
docker-compose ps redis

# Check Redis logs
docker-compose logs redis

# Test Redis connection
docker exec -it grocery_ecom_redis redis-cli ping
```

**Issue: RabbitMQ not accepting connections**
```bash
# Verify RabbitMQ is running
docker-compose ps rabbitmq

# Check RabbitMQ logs
docker-compose logs rabbitmq

# Restart RabbitMQ
docker-compose restart rabbitmq
```

### Debug Mode

Enable debug logging:
```bash
export DEBUG=true
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"
```

---

## CI/CD Pipeline

### GitHub Actions Example

Create `.github/workflows/build-and-test.yml`:

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: password
          MYSQL_DATABASE: grocery_ecom
      redis:
        image: redis:7.0-alpine
    
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Build with Maven
        run: mvn clean install
      - name: Run tests
        run: mvn test
```

---

## Security Considerations

1. **Database**
   - Use strong passwords
   - Enable MySQL user-specific privileges
   - Use SSL for connections

2. **Redis**
   - Set requirepass in production
   - Use ACL (Redis 6+)
   - Restrict network access

3. **RabbitMQ**
   - Change default guest credentials
   - Use SSL/TLS for connections
   - Implement virtual host isolation

4. **Application**
   - Rotate JWT secrets regularly
   - Implement rate limiting
   - Use HTTPS/TLS
   - Implement CORS properly
   - Validate and sanitize all inputs

---

## Next Steps

1. Implement database schema
2. Create User module (authentication)
3. Create Product module (catalog)
4. Create Order module
5. Implement payment gateway integration
6. Add comprehensive error handling
7. Write unit and integration tests
8. Configure CI/CD pipeline
9. Set up monitoring & alerting
10. Deploy to staging/production

---

## Support & Documentation

- **Architecture Design**: See `ARCHITECTURE.md`
- **API Documentation**: http://localhost:8080/api/swagger-ui.html
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **MySQL Docs**: https://dev.mysql.com/doc/
- **Redis Docs**: https://redis.io/docs/
- **RabbitMQ Docs**: https://www.rabbitmq.com/documentation.html

