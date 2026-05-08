package dev.dreamyfx.accountswap.auth;

public class AuthResult {

    private final boolean success;
    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String refreshToken;
    private final long expiry;
    private final String error;

    private AuthResult(boolean success, String username, String uuid,
                       String accessToken, String refreshToken, long expiry, String error) {
        this.success = success;
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiry = expiry;
        this.error = error;
    }

    public static AuthResult success(String username, String uuid, String accessToken, String refreshToken, long expiry) {
        return new AuthResult(true, username, uuid, accessToken, refreshToken, expiry, null);
    }

    public static AuthResult failure(String error) {
        return new AuthResult(false, null, null, null, null, 0, error);
    }

    public boolean isSuccess() { return success; }
    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public long getExpiry() { return expiry; }
    public String getError() { return error; }
}
