package com.groceryecom.modules.identity.domain;

/**
 * Who is asking, as far as the network can tell. Recorded on a session so a user can
 * recognise their own devices, and so an unexpected one stands out.
 *
 * @param ipAddress the client address, already resolved from trusted proxy headers
 * @param userAgent the raw User-Agent header, truncated to fit the column
 */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_LENGTH = 45;

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

    public ClientInfo {
        ipAddress = truncate(ipAddress, MAX_IP_LENGTH);
        userAgent = truncate(userAgent, MAX_USER_AGENT_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
