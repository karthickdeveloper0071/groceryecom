package com.groceryecom.shared;

/**
 * Application-wide constants
 */
public class AppConstants {

    // Security
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final long JWT_EXPIRATION = 900000; // 15 minutes
    public static final long REFRESH_TOKEN_EXPIRATION = 604800000; // 7 days

    // Cache Keys
    public static final String CACHE_USER_PREFIX = "user:";
    public static final String CACHE_PRODUCT_PREFIX = "product:";
    public static final String CACHE_VENDOR_PREFIX = "vendor:";
    public static final String CACHE_CATEGORY_PREFIX = "category:";
    public static final String CACHE_CART_PREFIX = "cart:";
    public static final long CACHE_DEFAULT_TTL = 3600; // 1 hour

    // Pagination
    public static final int DEFAULT_PAGE_NUMBER = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Roles
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_VENDOR = "VENDOR";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    // Order Status
    public static final String ORDER_STATUS_PENDING = "PENDING";
    public static final String ORDER_STATUS_CONFIRMED = "CONFIRMED";
    public static final String ORDER_STATUS_PROCESSING = "PROCESSING";
    public static final String ORDER_STATUS_SHIPPED = "SHIPPED";
    public static final String ORDER_STATUS_DELIVERED = "DELIVERED";
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    public static final String ORDER_STATUS_RETURNED = "RETURNED";

    // Payment Status
    public static final String PAYMENT_STATUS_INITIATED = "INITIATED";
    public static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    public static final String PAYMENT_STATUS_FAILED = "FAILED";
    public static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";

    // Product Status
    public static final String PRODUCT_STATUS_ACTIVE = "ACTIVE";
    public static final String PRODUCT_STATUS_INACTIVE = "INACTIVE";
    public static final String PRODUCT_STATUS_OUT_OF_STOCK = "OUT_OF_STOCK";

    // Message Queue Topics
    public static final String QUEUE_ORDER_CREATED = "order.created";
    public static final String QUEUE_ORDER_CONFIRMED = "order.confirmed";
    public static final String QUEUE_PAYMENT_PROCESSED = "payment.processed";
    public static final String QUEUE_INVENTORY_UPDATED = "inventory.updated";
    public static final String QUEUE_NOTIFICATION_SEND = "notification.send";
    public static final String QUEUE_VENDOR_REGISTERED = "vendor.registered";

    // Timeouts
    public static final int DATABASE_TIMEOUT = 30000; // 30 seconds
    public static final int CACHE_TIMEOUT = 5000; // 5 seconds
    public static final int API_TIMEOUT = 10000; // 10 seconds

    // Rate Limiting
    public static final int RATE_LIMIT_REQUESTS = 100;
    public static final int RATE_LIMIT_WINDOW = 60; // seconds

    // Validation
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_USERNAME_LENGTH = 50;
    public static final int MIN_PRODUCT_NAME_LENGTH = 3;
    public static final int MAX_PRODUCT_DESCRIPTION_LENGTH = 2000;
    public static final double MIN_PRODUCT_PRICE = 0.0;
    public static final double MAX_PRODUCT_PRICE = 999999.99;

}

