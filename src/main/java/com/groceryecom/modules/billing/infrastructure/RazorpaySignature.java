package com.groceryecom.modules.billing.infrastructure;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Proves that a webhook really came from Razorpay.
 *
 * <p>The endpoint is public - Razorpay cannot hold a token - so the signature is the only
 * thing between "a payment succeeded" and anyone on the internet granting themselves a
 * free licence by posting JSON at it. Razorpay signs the <b>raw request body</b> with
 * HMAC-SHA256 using the webhook secret and sends the result in {@code X-Razorpay-Signature}.
 *
 * <p>Two details that are easy to get wrong and expensive to get wrong:
 * <ul>
 *   <li>the signature covers the bytes as sent. Parsing the body to an object and
 *       re-serialising it changes whitespace and key order, and the signature then never
 *       matches. The controller therefore takes a {@code String} and hands it here
 *       untouched;</li>
 *   <li>comparison is constant-time. {@code String.equals} returns early on the first
 *       differing character, and that timing difference is enough to guess a signature
 *       byte by byte.</li>
 * </ul>
 */
final class RazorpaySignature {

    private static final String ALGORITHM = "HmacSHA256";

    private RazorpaySignature() {
    }

    /**
     * @param rawBody   the request body exactly as received
     * @param signature the value of the X-Razorpay-Signature header
     * @param secret    the webhook secret configured in the Razorpay dashboard
     */
    static boolean isValid(String rawBody, String signature, String secret) {
        if (rawBody == null || signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(expected, decodeHex(signature));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A malformed signature is an invalid signature, not an error worth a 500
            return false;
        }
    }

    private static byte[] decodeHex(String signature) {
        return HexFormat.of().parseHex(signature.trim());
    }
}
