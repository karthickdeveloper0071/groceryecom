# 🔐 Complete Authentication & Authorization Implementation Guide

## Overview

This guide covers the **production-ready authentication and authorization system** implemented for GroceryEcom.

---

## Architecture Components

### 1. **JWT Token Provider** (`JwtTokenProvider.java`)
- Generates access tokens (15-minute expiration)
- Generates refresh tokens (7-day expiration)
- Validates tokens
- Extracts claims (userId, username, email, roles)
- Handles token expiration and signature verification

### 2. **JWT Authentication Filter** (`JwtAuthenticationFilter.java`)
- Intercepts all incoming requests
- Extracts JWT token from Authorization header
- Validates token
- Sets Spring Security authentication context
- Allows request to proceed without authentication if token is invalid (handled by controller)

### 3. **Security Configuration** (`WebSecurityConfig.java`)
- Stateless session management (no cookies)
- CORS configuration
- Authorization rules (public vs protected endpoints)
- OAuth2 resource server configuration
- Exception handling for authentication/authorization failures

### 4. **Authentication Service** (`AuthServiceImpl.java`)
- User registration with validation
- User login with password verification
- Token refresh
- Password management (change, reset, forgot)
- Email verification

### 5. **Authentication Controller** (`AuthController.java`)
- REST endpoints for all auth operations
- Input validation
- Security annotations for endpoint protection

---

## API Endpoints

### Public Endpoints (No Authentication Required)

#### 1. **Register User**
```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "role": "CUSTOMER"
}

Response (201 Created):
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": 1,
      "username": "john_doe",
      "email": "john@example.com",
      "role": "CUSTOMER",
      ...
    }
  },
  "message": "User registered successfully"
}
```

#### 2. **Login User**
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john_doe",  # Can also be email
  "password": "SecurePass123!"
}

Response (200 OK):
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": { ... }
  },
  "message": "Login successful"
}
```

#### 3. **Refresh Access Token**
```bash
POST /api/v1/auth/refresh-token
Authorization: Bearer {REFRESH_TOKEN}

Response (200 OK):
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",  # New access token
    "refreshToken": "eyJhbGc...",  # New refresh token
    "tokenType": "Bearer",
    "expiresIn": 900
  },
  "message": "Token refreshed successfully"
}
```

#### 4. **Forgot Password**
```bash
POST /api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "john@example.com"
}

Response (200 OK):
{
  "success": true,
  "message": "If the email exists, you will receive a password reset link"
}
```

#### 5. **Reset Password**
```bash
POST /api/v1/auth/reset-password
Content-Type: application/json

{
  "resetToken": "abc123xyz",  # Token from email link
  "newPassword": "NewSecurePass456!",
  "confirmPassword": "NewSecurePass456!"
}

Response (200 OK):
{
  "success": true,
  "message": "Password reset successfully"
}
```

#### 6. **Verify Email**
```bash
POST /api/v1/auth/verify-email?token=abc123xyz

Response (200 OK):
{
  "success": true,
  "message": "Email verified successfully"
}
```

---

### Protected Endpoints (Authentication Required)

#### 1. **Get Current User**
```bash
GET /api/v1/auth/me
Authorization: Bearer {ACCESS_TOKEN}

Response (200 OK):
{
  "success": true,
  "data": "john_doe",
  "message": "Current user retrieved successfully"
}
```

#### 2. **Logout User**
```bash
POST /api/v1/auth/logout
Authorization: Bearer {ACCESS_TOKEN}

Response (200 OK):
{
  "success": true,
  "message": "Logout successful"
}
```

#### 3. **Change Password**
```bash
POST /api/v1/auth/{userId}/change-password
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json

{
  "oldPassword": "SecurePass123!",
  "newPassword": "NewSecurePass456!",
  "confirmPassword": "NewSecurePass456!"
}

Response (200 OK):
{
  "success": true,
  "message": "Password changed successfully"
}
```

---

## Authentication Flow Diagram

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       │ 1. POST /register or /login
       ▼
┌──────────────────────────┐
│  AuthController          │
│  - Validate input        │
│  - Call AuthService      │
└──────┬───────────────────┘
       │
       │ 2. Register/Login
       ▼
┌──────────────────────────┐
│  AuthServiceImpl          │
│  - Check user exists     │
│  - Verify password       │
│  - Generate tokens       │
└──────┬───────────────────┘
       │
       │ 3. Generate tokens
       ▼
┌──────────────────────────┐
│  JwtTokenProvider        │
│  - Create access token   │
│  - Create refresh token  │
└──────┬───────────────────┘
       │
       │ 4. Return tokens
       ▼
┌──────────────────────────┐
│  Response to Client      │
│  {accessToken, ...}      │
└──────────────────────────┘
       │
       │ 5. Future requests with Authorization header
       ▼
┌──────────────────────────┐
│  JwtAuthenticationFilter │
│  - Extract token         │
│  - Validate token        │
│  - Set SecurityContext   │
└──────┬───────────────────┘
       │
       │ 6. Request continues to Controller
       ▼
┌──────────────────────────┐
│  Protected Endpoint      │
│  - Access granted        │
└──────────────────────────┘
```

---

## Token Structure

### Access Token
```
Header:
{
  "alg": "HS512",
  "typ": "JWT"
}

Payload:
{
  "sub": "john_doe",
  "userId": 1,
  "email": "john@example.com",
  "roles": ["CUSTOMER"],
  "tokenType": "ACCESS",
  "iat": 1691683200,
  "exp": 1691684100
}

Signature:
HMACSHA512(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
```

### Refresh Token
```
Header:
{
  "alg": "HS512",
  "typ": "JWT"
}

Payload:
{
  "sub": "john_doe",
  "userId": 1,
  "tokenType": "REFRESH",
  "iat": 1691683200,
  "exp": 1698921600
}

Signature:
HMACSHA512(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
```

---

## Authorization Rules

### Public Endpoints (No Role Required)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh-token`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `POST /api/v1/auth/verify-email`

### Authenticated Endpoints (Any Role)
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/{userId}/change-password`

### Admin-Only Endpoints
- `GET /api/v1/admin/users` - List all users
- `PUT /api/v1/admin/users/{id}/approve` - Approve user

### Vendor-Only Endpoints
- `POST /api/v1/vendors/{id}/products` - Create product
- `GET /api/v1/vendors/{id}/metrics` - View metrics

---

## Security Features

### 1. **Password Security**
- ✅ BCrypt hashing with strength 12
- ✅ Minimum 8 characters required
- ✅ Password not returned in responses
- ✅ Constant-time comparison to prevent timing attacks

### 2. **Token Security**
- ✅ HS512 signature algorithm
- ✅ Access token: 15-minute expiration
- ✅ Refresh token: 7-day expiration
- ✅ Tokens contain user roles for authorization
- ✅ Refresh tokens cannot access protected resources

### 3. **Request Security**
- ✅ CSRF disabled (stateless API)
- ✅ CORS configured for specific origins
- ✅ Input validation on all endpoints
- ✅ SQL injection prevention (ORM)
- ✅ Rate limiting ready

### 4. **Authorization**
- ✅ Role-based access control (RBAC)
- ✅ Method-level security (@PreAuthorize)
- ✅ User can only modify own data (except admin)
- ✅ Clear separation of permissions by role

### 5. **Error Handling**
- ✅ Generic error messages (don't leak user info)
- ✅ Proper HTTP status codes
- ✅ Detailed logging for debugging
- ✅ No sensitive data in responses

---

## Usage Examples

### Using Access Token
```bash
# Any protected endpoint requires access token
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### Refreshing Expired Token
```bash
# When access token expires (15 minutes), use refresh token
curl -X POST http://localhost:8080/api/v1/auth/refresh-token \
  -H "Authorization: Bearer {REFRESH_TOKEN}"

# Response contains new access and refresh tokens
```

### Changing Password
```bash
# Requires current password for security
curl -X POST http://localhost:8080/api/v1/auth/{userId}/change-password \
  -H "Authorization: Bearer {ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "oldPassword": "CurrentPassword123",
    "newPassword": "NewPassword456",
    "confirmPassword": "NewPassword456"
  }'
```

---

## Configuration

### JWT Configuration (`application.properties`)
```properties
# JWT Secret (change in production!)
jwt.secret=your-super-secret-key-change-this-in-production-minimum-256-bits

# Access token expiration (15 minutes in milliseconds)
jwt.expiration=900000

# Refresh token expiration (7 days in milliseconds)
jwt.refresh-token-expiration=604800000
```

### Security Configuration (`WebSecurityConfig.java`)
```java
// Session management
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

// CORS configuration
.cors(cors -> cors.configurationSource(corsConfigurationSource()))

// Authorization rules
.authorizeHttpRequests(authz -> authz
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
    // ... more rules
    .anyRequest().authenticated()
)

// JWT filter
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

---

## Production Considerations

### 1. **Token Blacklist** (Logout Implementation)
```java
// For production logout, implement token blacklist in Redis
@Service
public class TokenBlacklistService {
    private final RedisTemplate<String, String> redisTemplate;
    
    public void blacklistToken(String token, Long expirationTime) {
        redisTemplate.opsForValue().set(
            "blacklist:" + token, 
            "true", 
            Duration.ofMillis(expirationTime)
        );
    }
    
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey("blacklist:" + token)
        );
    }
}
```

### 2. **Refresh Token Rotation**
- Generate new refresh token on each refresh
- Keep track of token family for security
- Invalidate entire family if suspicious activity detected

### 3. **Email Verification**
- Send verification token via email on registration
- Require email verification before account activation
- Implement token expiration (24 hours)

### 4. **Password Reset Security**
- Send reset token via email (not in response)
- Token should be one-time use
- Token should expire (15-30 minutes)
- Implement rate limiting on reset attempts

### 5. **OAuth2 Integration** (Future)
- Google OAuth2
- GitHub OAuth2
- Facebook OAuth2
- Use authorization code flow
- Store provider info in User entity

### 6. **Rate Limiting**
```java
// Implement rate limiting for auth endpoints
@RateLimiter(limit = 5, window = 60) // 5 attempts per minute
@PostMapping("/login")
public ResponseEntity<?> login(...) { ... }
```

### 7. **Two-Factor Authentication** (Future)
- TOTP-based 2FA using Google Authenticator
- Email-based 2FA
- SMS-based 2FA

### 8. **Security Headers**
```java
// Add security headers
http.headers(headers -> headers
    .contentSecurityPolicy("default-src 'self'")
    .xssProtection()
    .frameOptions().deny()
);
```

---

## Testing Examples

### Unit Test
```java
@SpringBootTest
class AuthServiceImplTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    @InjectMocks
    private AuthServiceImpl authService;
    
    @Test
    void testLoginWithValidCredentials() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("john");
        user.setPasswordHash("$2a$12$...");
        user.setRole(User.UserRole.CUSTOMER);
        
        when(userRepository.findByUsernameOrEmail("john", "john"))
            .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$12$..."))
            .thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(...))
            .thenReturn("token");
        
        // Act
        UserLoginDTO loginDTO = new UserLoginDTO("john", "password123");
        AuthTokenDTO result = authService.login(loginDTO);
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getAccessToken());
    }
}
```

### Integration Test
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    private RestTemplate restTemplate;
    
    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
    }
    
    @Test
    void testLoginFlow() {
        // Register
        UserRegistrationDTO registration = new UserRegistrationDTO(...);
        ResponseEntity<ApiResponse<AuthTokenDTO>> registerResponse = 
            restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/register",
                registration,
                ApiResponse.class
            );
        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());
        
        // Login
        UserLoginDTO login = new UserLoginDTO("john", "password123");
        ResponseEntity<ApiResponse<AuthTokenDTO>> loginResponse = 
            restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login",
                login,
                ApiResponse.class
            );
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
    }
}
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| 401 Unauthorized | Invalid/expired token | Refresh token using refresh token endpoint |
| 401 Unauthorized | Missing Authorization header | Add `Authorization: Bearer {TOKEN}` header |
| 403 Forbidden | Insufficient permissions | Use account with required role |
| 400 Bad Request | Invalid input | Check request body validation |
| 500 Internal Server | JWT secret not configured | Set `jwt.secret` in application.properties |

---

## Best Practices

1. **Never log or expose tokens** - They're credentials
2. **Always use HTTPS in production** - Tokens in plain HTTP are vulnerable
3. **Store tokens securely on client** - Use httpOnly cookies or secure storage
4. **Implement token rotation** - Refresh frequently
5. **Monitor failed attempts** - Log and alert on suspicious activity
6. **Use strong JWT secret** - Minimum 256 bits
7. **Implement rate limiting** - Prevent brute force attacks
8. **Validate all input** - Even in authenticated endpoints
9. **Use CORS carefully** - Whitelist specific origins only
10. **Keep dependencies updated** - Security patches matter

---

## Summary

This authentication system provides:
- ✅ Secure JWT-based authentication
- ✅ Role-based authorization
- ✅ Stateless API design
- ✅ Refresh token mechanism
- ✅ Password management
- ✅ Production-ready security
- ✅ Extensible for OAuth2

**Next Step**: Implement token blacklist in Redis and add email verification!

