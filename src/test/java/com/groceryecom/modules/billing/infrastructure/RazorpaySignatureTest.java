package com.groceryecom.modules.billing.infrastructure;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only thing standing between "a payment succeeded" and anyone on the internet
 * granting themselves a free licence by posting JSON at a public endpoint.
 */
class RazorpaySignatureTest {

    private static final String SECRET = "whsec_test_value";
    private static final String BODY = """
            {"event":"order.paid","payload":{"order":{"entity":{"id":"order_ABC123"}}}}""";

    @Test
    void razorpaysOwnSignatureIsAccepted() {
        assertThat(RazorpaySignature.isValid(BODY, sign(BODY, SECRET), SECRET)).isTrue();
    }

    @Test
    void aBodyChangedAfterSigningIsRefused() {
        String signature = sign(BODY, SECRET);
        String tampered = BODY.replace("order_ABC123", "order_SOMEONE_ELSE");

        assertThat(RazorpaySignature.isValid(tampered, signature, SECRET)).isFalse();
    }

    @Test
    void aSignatureFromAnotherSecretIsRefused() {
        assertThat(RazorpaySignature.isValid(BODY, sign(BODY, "someone-elses-secret"), SECRET)).isFalse();
    }

    @Test
    void noSignatureAtAllIsRefused() {
        assertThat(RazorpaySignature.isValid(BODY, null, SECRET)).isFalse();
        assertThat(RazorpaySignature.isValid(BODY, "", SECRET)).isFalse();
    }

    /** Garbage in the header is an invalid signature, not a 500. */
    @Test
    void aSignatureThatIsNotHexIsRefusedQuietly() {
        assertThat(RazorpaySignature.isValid(BODY, "not-a-hex-signature", SECRET)).isFalse();
    }

    /** No webhook secret configured means nothing can be verified, so nothing is trusted. */
    @Test
    void withoutASecretNothingIsValid() {
        assertThat(RazorpaySignature.isValid(BODY, sign(BODY, SECRET), null)).isFalse();
        assertThat(RazorpaySignature.isValid(BODY, sign(BODY, SECRET), "  ")).isFalse();
    }

    /** What Razorpay does on its side: HMAC-SHA256 over the raw body, hex encoded. */
    static String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
