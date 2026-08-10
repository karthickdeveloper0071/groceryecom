# 🔐 Authorization & RBAC Implementation Guide

## Role-Based Access Control (RBAC)

This guide shows how to implement role-based authorization in your endpoints.

---

## Roles in GroceryEcom

```java
public enum UserRole {
    ADMIN,      // Full system access
    VENDOR,     // Vendor dashboard, product management
    CUSTOMER    // Order, browse products
}
```

---

## Method-Level Authorization Examples

### 1. Admin-Only Access

```java
@GetMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> getAllUsers() {
    // Only accessible to ADMIN role
}
```

### 2. Multiple Roles

```java
@GetMapping("/dashboard")
@PreAuthorize("hasRole('ADMIN') or hasRole('VENDOR')")
public ResponseEntity<?> getDashboard() {
    // Accessible to ADMIN or VENDOR
}
```

### 3. Authenticated Users Only

```java
@GetMapping("/profile")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> getProfile() {
    // Accessible to any authenticated user
}
```

### 4. User Can Only Access Own Data

```java
@GetMapping("/{userId}")
@PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or #userId.toString() == principal.name)")
public ResponseEntity<?> getUserProfile(@PathVariable Long userId) {
    // User can access own profile or ADMIN can access any profile
}
```

### 5. Vendor-Specific Access

```java
@PostMapping("/vendors/{vendorId}/products")
@PreAuthorize("hasRole('VENDOR') and (hasRole('ADMIN') or @vendorService.isVendorOwner(#vendorId, principal.name))")
public ResponseEntity<?> createProduct(@PathVariable Long vendorId, ...) {
    // Vendor can only create products for their own vendor, or ADMIN
}
```

### 6. Expression-Based Access

```java
@PreAuthorize("@permissionService.canAccessOrder(#orderId, principal.name)")
@GetMapping("/orders/{orderId}")
public ResponseEntity<?> getOrder(@PathVariable Long orderId) {
    // Custom permission check via bean
}
```

---

## Custom Permission Service

```java
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;

    /**
     * Check if user can access order
     */
    public boolean canAccessOrder(Long orderId, String username) {
        User user = userRepository.findByUsername(username)
            .orElse(null);
        
        if (user == null) return false;
        
        // Admin can access any order
        if (user.getRole() == User.UserRole.ADMIN) {
            return true;
        }
        
        // User can access their own orders
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return false;
        
        return order.getUser().getId().equals(user.getId());
    }

    /**
     * Check if vendor can manage product
     */
    public boolean canManageProduct(Long productId, Long vendorId, String username) {
        User user = userRepository.findByUsername(username)
            .orElse(null);
        
        if (user == null) return false;
        
        // Admin can manage any product
        if (user.getRole() == User.UserRole.ADMIN) {
            return true;
        }
        
        // Vendor can only manage their own products
        return isVendorOwner(vendorId, username);
    }

    /**
     * Check if user owns vendor
     */
    public boolean isVendorOwner(Long vendorId, String username) {
        User user = userRepository.findByUsername(username)
            .orElse(null);
        
        if (user == null) return false;
        
        Vendor vendor = vendorRepository.findById(vendorId).orElse(null);
        if (vendor == null) return false;
        
        return vendor.getUser().getId().equals(user.getId());
    }
}
```

---

## Securing Endpoints by Role

### Admin Endpoints

```java
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final VendorService vendorService;

    /**
     * Get all users (admin only)
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponseDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserResponseDTO> users = userService.getAllUsers(page, size);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Approve vendor (admin only)
     */
    @PutMapping("/vendors/{vendorId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveVendor(@PathVariable Long vendorId) {
        vendorService.approveVendor(vendorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Vendor approved"));
    }

    /**
     * Ban user (admin only)
     */
    @PutMapping("/users/{userId}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable Long userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User banned"));
    }

    /**
     * View system analytics (admin only)
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<?>> getAnalytics() {
        // Return system-wide analytics
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

### Vendor Endpoints

```java
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;
    private final ProductService productService;

    /**
     * Create product (vendor only)
     */
    @PostMapping("/{vendorId}/products")
    @PreAuthorize("hasRole('VENDOR') and (hasRole('ADMIN') or @permissionService.isVendorOwner(#vendorId, principal.name))")
    public ResponseEntity<ApiResponse<ProductDTO>> createProduct(
            @PathVariable Long vendorId,
            @Valid @RequestBody CreateProductDTO productDTO) {
        
        ProductDTO created = productService.createProduct(vendorId, productDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(created, "Product created"));
    }

    /**
     * Get vendor dashboard (vendor only)
     */
    @GetMapping("/{vendorId}/dashboard")
    @PreAuthorize("hasRole('VENDOR') and (hasRole('ADMIN') or @permissionService.isVendorOwner(#vendorId, principal.name))")
    public ResponseEntity<ApiResponse<?>> getDashboard(@PathVariable Long vendorId) {
        // Return vendor-specific dashboard
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Update vendor info (own vendor only)
     */
    @PutMapping("/{vendorId}")
    @PreAuthorize("hasRole('VENDOR') and (hasRole('ADMIN') or @permissionService.isVendorOwner(#vendorId, principal.name))")
    public ResponseEntity<ApiResponse<Void>> updateVendor(
            @PathVariable Long vendorId,
            @Valid @RequestBody UpdateVendorDTO updateDTO) {
        
        vendorService.updateVendor(vendorId, updateDTO);
        return ResponseEntity.ok(ApiResponse.success(null, "Vendor updated"));
    }
}
```

### Customer Endpoints

```java
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final OrderService orderService;
    private final WishlistService wishlistService;

    /**
     * Place order (customer authenticated)
     */
    @PostMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderDTO>> placeOrder(
            @Valid @RequestBody CreateOrderDTO orderDTO) {
        
        OrderDTO order = orderService.createOrder(orderDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(order, "Order placed"));
    }

    /**
     * Get own orders (customer authenticated)
     */
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OrderDTO>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<OrderDTO> orders = orderService.getUserOrders(page, size);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Add to wishlist (customer authenticated)
     */
    @PostMapping("/wishlist/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> addToWishlist(@PathVariable Long productId) {
        wishlistService.addToWishlist(productId);
        return ResponseEntity.ok(ApiResponse.success(null, "Added to wishlist"));
    }
}
```

---

## Getting Current User Information

### In Controller/Service

```java
// Option 1: Using Authentication object
@PostMapping("/update-profile")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> updateProfile(
        @RequestBody UpdateProfileDTO profileDTO,
        Authentication authentication) {
    
    String username = authentication.getName();
    Collection<? extends GrantedAuthority> roles = authentication.getAuthorities();
    
    // Get custom details (userId, etc.)
    JwtAuthenticationDetails details = (JwtAuthenticationDetails) authentication.getDetails();
    Long userId = details.getUserId();
    
    return ResponseEntity.ok("Profile updated");
}

// Option 2: Using SecurityContextHolder
@PostMapping("/update-profile")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileDTO profileDTO) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String username = authentication.getName();
    
    return ResponseEntity.ok("Profile updated");
}

// Option 3: Using @AuthenticationPrincipal (Recommended)
@PostMapping("/update-profile")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> updateProfile(
        @RequestBody UpdateProfileDTO profileDTO,
        @AuthenticationPrincipal String username) {
    
    return ResponseEntity.ok("Profile updated");
}
```

---

## Authorization with SpEL (Spring Expression Language)

### Advanced Examples

```java
// Check if user is admin or owns resource
@PreAuthorize("#user.id == principal.name or hasRole('ADMIN')")
public void updateUser(User user) { }

// Check multiple roles
@PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
public void accessDashboard() { }

// Permission-based access
@PreAuthorize("hasPermission(#resource, 'WRITE')")
public void updateResource(Resource resource) { }

// Custom permission evaluator
@PreAuthorize("@customPermissionEvaluator.canAccess(#resourceId, principal.name)")
public void accessResource(@PathVariable Long resourceId) { }
```

---

## Testing Authorization

### Unit Test Example

```java
@SpringBootTest
@WithMockUser(roles = "CUSTOMER")  // Mock authenticated user
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "CUSTOMER")  // Customer can place order
    void testPlaceOrderAsCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/customers/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": []}"))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "VENDOR")  // Vendor cannot place order
    void testPlaceOrderAsVendor_Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/customers/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": []}"))
            .andExpect(status().isForbidden());
    }

    @Test  // Unauthenticated user
    void testPlaceOrderUnauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/customers/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": []}"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Integration Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void testAdminCanAccessAdminEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Login as admin
        LoginRequest loginRequest = new LoginRequest("admin_user", "password");
        ResponseEntity<AuthTokenDTO> loginResponse = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/auth/login",
            loginRequest,
            AuthTokenDTO.class
        );
        
        String token = loginResponse.getBody().getAccessToken();
        
        // Access admin endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/admin/users",
            HttpMethod.GET,
            entity,
            String.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
```

---

## Security Best Practices for Authorization

1. **Principle of Least Privilege**
   - Grant minimum permissions required
   - Use role-based, not user-based access

2. **Deny by Default**
   - Require explicit authorization
   - Don't rely on absence of denial

3. **Consistent Authorization**
   - Check authorization at every layer (controller, service)
   - Don't trust the frontend

4. **Secure by Default**
   - Require authentication for most endpoints
   - Only explicitly allow public endpoints

5. **Regular Audits**
   - Log all access attempts
   - Monitor for suspicious patterns
   - Review authorization rules regularly

---

## Complete Authorization Checklist

- [ ] All protected endpoints require authentication
- [ ] Role-based access control implemented
- [ ] Users cannot access other users' data (except admin)
- [ ] Admin has comprehensive access
- [ ] Vendor access limited to own resources
- [ ] Customer access limited to own orders
- [ ] Authorization checks at service level
- [ ] Authorization logging implemented
- [ ] Tests cover authorization scenarios
- [ ] Documentation covers authorization rules

---

## Summary

This RBAC system provides:
- ✅ Role-based endpoint protection
- ✅ Method-level security annotations
- ✅ Custom permission evaluators
- ✅ User data isolation
- ✅ Admin oversight capabilities
- ✅ Clear authorization rules
- ✅ Testing support

**Use @PreAuthorize liberally and test authorization thoroughly!**

