# User Module Implementation Guide

This guide shows how to implement your first module (User Module) following the architectural patterns.

## 📋 User Module Overview

The User Module handles:
- User registration
- User authentication (login/logout)
- JWT token management
- User profile management
- Role-based access control

## 📁 Module Structure

```
modules/user/
├── controller/
│   └── UserController.java
├── service/
│   ├── UserService.java
│   └── AuthService.java
├── repository/
│   └── UserRepository.java
├── model/
│   └── User.java
└── dto/
    ├── UserRegistrationDTO.java
    ├── UserLoginDTO.java
    ├── UserResponseDTO.java
    └── AuthTokenDTO.java
```

## 🔧 Step-by-Step Implementation

### Step 1: Create User Entity

File: `modules/user/model/User.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.GroceryEcom.GorceryEcom.common.util.BaseEntity;
import com.GroceryEcom.GorceryEcom.common.constants.AppConstants;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_role", columnList = "role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    private static final long serialVersionUID = 1L;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "phone_verified")
    private Boolean phoneVerified = false;

    // Enum for roles
    public enum UserRole {
        ADMIN, VENDOR, CUSTOMER
    }
}
```

### Step 2: Create DTOs

File: `modules/user/dto/UserRegistrationDTO.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationDTO {
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone number")
    private String phoneNumber;

    private String role; // ADMIN, VENDOR, CUSTOMER
}
```

File: `modules/user/dto/UserLoginDTO.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginDTO {
    
    @NotBlank(message = "Username or email is required")
    private String username; // Can accept email too

    @NotBlank(message = "Password is required")
    private String password;
}
```

File: `modules/user/dto/UserResponseDTO.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String role;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
```

File: `modules/user/dto/AuthTokenDTO.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserResponseDTO user;
}
```

### Step 3: Create Repository

File: `modules/user/repository/UserRepository.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.repository;

import com.GroceryEcom.GorceryEcom.modules.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    List<User> findByRole(User.UserRole role);
    
    Optional<User> findByUsernameOrEmail(String username, String email);
}
```

### Step 4: Create Service

File: `modules/user/service/UserService.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.common.util.BaseService;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserResponseDTO;
import com.GroceryEcom.GorceryEcom.modules.user.model.User;

public interface UserService extends BaseService<User, Long> {
    
    UserResponseDTO getUserById(Long id);
    
    UserResponseDTO getUserByUsername(String username);
    
    UserResponseDTO getUserByEmail(String email);
    
    void updateUserProfile(Long userId, UserResponseDTO userDTO);
    
    void changePassword(Long userId, String oldPassword, String newPassword);
    
    void deactivateUser(Long userId);
    
    void activateUser(Long userId);
}
```

File: `modules/user/service/UserServiceImpl.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.common.exception.ResourceNotFoundException;
import com.GroceryEcom.GorceryEcom.common.exception.ValidationException;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserResponseDTO;
import com.GroceryEcom.GorceryEcom.modules.user.model.User;
import com.GroceryEcom.GorceryEcom.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    @Override
    public User save(User user) {
        log.info("Saving user: {}", user.getUsername());
        return userRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User update(User user) {
        log.info("Updating user: {}", user.getId());
        if (!userRepository.existsById(user.getId())) {
            throw new ResourceNotFoundException("User", user.getId().toString());
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting user: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        user.setIsDeleted(true);
        userRepository.save(user);
    }

    @Override
    public boolean exists(Long id) {
        return userRepository.existsById(id);
    }

    @Override
    public long count() {
        return userRepository.count();
    }

    @Override
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        return modelMapper.map(user, UserResponseDTO.class);
    }

    @Override
    public UserResponseDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User with username: " + username));
        return modelMapper.map(user, UserResponseDTO.class);
    }

    @Override
    public UserResponseDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User with email: " + email));
        return modelMapper.map(user, UserResponseDTO.class);
    }

    @Override
    @Transactional
    public void updateUserProfile(Long userId, UserResponseDTO userDTO) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setPhoneNumber(userDTO.getPhoneNumber());
        
        userRepository.save(user);
        log.info("User profile updated: {}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("Old password is incorrect");
        }
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", userId);
    }

    @Override
    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User deactivated: {}", userId);
    }

    @Override
    @Transactional
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        user.setIsActive(true);
        userRepository.save(user);
        log.info("User activated: {}", userId);
    }
}
```

### Step 5: Create Authentication Service

File: `modules/user/service/AuthService.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.modules.user.dto.UserLoginDTO;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserRegistrationDTO;
import com.GroceryEcom.GorceryEcom.modules.user.dto.AuthTokenDTO;

public interface AuthService {
    
    AuthTokenDTO register(UserRegistrationDTO registrationDTO);
    
    AuthTokenDTO login(UserLoginDTO loginDTO);
    
    AuthTokenDTO refreshToken(String refreshToken);
    
    void logout(Long userId);
    
    String generateAccessToken(Long userId);
    
    String generateRefreshToken(Long userId);
}
```

### Step 6: Create Controller

File: `modules/user/controller/UserController.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.controller;

import com.GroceryEcom.GorceryEcom.common.util.ApiResponse;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserLoginDTO;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserRegistrationDTO;
import com.GroceryEcom.GorceryEcom.modules.user.dto.UserResponseDTO;
import com.GroceryEcom.GorceryEcom.modules.user.dto.AuthTokenDTO;
import com.GroceryEcom.GorceryEcom.modules.user.service.AuthService;
import com.GroceryEcom.GorceryEcom.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> register(
            @Valid @RequestBody UserRegistrationDTO registrationDTO) {
        log.info("User registration request for: {}", registrationDTO.getUsername());
        AuthTokenDTO authToken = authService.register(registrationDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(authToken, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> login(
            @Valid @RequestBody UserLoginDTO loginDTO) {
        log.info("User login request for: {}", loginDTO.getUsername());
        AuthTokenDTO authToken = authService.login(loginDTO);
        return ResponseEntity.ok(ApiResponse.success(authToken, "Login successful"));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        AuthTokenDTO authToken = authService.refreshToken(token);
        return ResponseEntity.ok(ApiResponse.success(authToken, "Token refreshed successfully"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserById(
            @PathVariable Long userId) {
        UserResponseDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UserResponseDTO userDTO) {
        userService.updateUserProfile(userId, userDTO);
        return ResponseEntity.ok(ApiResponse.success(null, "Profile updated successfully"));
    }

    @PostMapping("/{userId}/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long userId,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        userService.changePassword(userId, oldPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}
```

## 🗄️ Database Migration (Flyway)

File: `src/main/resources/db/migration/V1__Create_Users_Table.sql`

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    role VARCHAR(50) NOT NULL,
    email_verified BOOLEAN DEFAULT false,
    phone_verified BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 🧪 Unit Test Example

File: `src/test/java/com/GroceryEcom/GorceryEcom/modules/user/service/UserServiceImplTest.java`

```java
package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.modules.user.model.User;
import com.GroceryEcom.GorceryEcom.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashedpassword");
        testUser.setRole(User.UserRole.CUSTOMER);
    }

    @Test
    void testSaveUser() {
        when(userRepository.save(testUser)).thenReturn(testUser);
        
        User result = userService.save(testUser);
        
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void testFindUserById() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        
        Optional<User> result = userService.findById(1L);
        
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
    }
}
```

## 🚀 Run Instructions

1. Create the files in their respective directories
2. Run the database migration:
   ```bash
   mvn flyway:migrate
   ```
3. Start the application:
   ```bash
   mvn spring-boot:run
   ```
4. Test the endpoints:
   ```bash
   # Register
   curl -X POST http://localhost:8080/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{"username":"john","email":"john@example.com","password":"SecurePass123","firstName":"John","lastName":"Doe"}'

   # Login
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"john","password":"SecurePass123"}'
   ```

---

This gives you a complete working User Module! The same pattern can be applied to all other modules.

