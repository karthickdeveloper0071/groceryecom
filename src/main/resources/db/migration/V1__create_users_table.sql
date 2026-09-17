-- Users table for the user module (matches modules/user/model/User.java)
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6),
    is_active      BIT(1),
    is_deleted     BIT(1),
    username       VARCHAR(50)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    phone_number   VARCHAR(20),
    role           VARCHAR(20)  NOT NULL,
    email_verified BIT(1),
    phone_verified BIT(1),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_email (email),
    INDEX idx_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
