package com.groceryecom.modules.billing.domain;

import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One payment gateway's keys, as an admin pasted them.
 *
 * <p>The two secret fields hold ciphertext and nothing else. Nothing in this class
 * decrypts: that is {@code GatewayCredentialsService}'s job, so the number of places a
 * live key can exist in memory stays countable.
 *
 * <p>There is deliberately no {@code toString}: Lombok's would print the ciphertext, and
 * an entity that ends up in a log line should not carry anything worth reading.
 */
@Entity
@Table(name = "payment_gateway_credentials")
@Getter
@Setter
@NoArgsConstructor
public class PaymentGatewayCredential extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "provider", nullable = false, unique = true, updatable = false, length = 30)
    private String provider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "mode", nullable = false, length = 10)
    private GatewayMode mode;

    /** Public by design: the browser needs it to open the gateway's checkout. */
    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(name = "key_secret_cipher", nullable = false, columnDefinition = "text")
    private String keySecretCipher;

    @Column(name = "webhook_secret_cipher", columnDefinition = "text")
    private String webhookSecretCipher;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    public boolean isUsable() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * The last four characters of the key id, for an admin screen. Enough to tell which
     * key is installed, useless to anybody who reads it.
     */
    public String keyIdHint() {
        return keyId.length() <= 4 ? "****" : "****" + keyId.substring(keyId.length() - 4);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    /**
     * Test keys move no money. Keeping the mode explicit means an admin screen can say
     * so, because "why has nothing settled?" is nearly always test keys left in place
     * after launch.
     */
    public enum GatewayMode {
        TEST,
        LIVE
    }
}
