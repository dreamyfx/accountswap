package dev.dreamyfx.accountswap.auth;

public class DeviceCodeResponse {

    public String deviceCode;
    public String userCode;
    public String verificationUri;
    public int expiresIn;
    public int interval;
    public String message;

    public boolean isValid() {
        return deviceCode != null && userCode != null && verificationUri != null;
    }
}
