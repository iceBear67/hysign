package io.ib67.hysign.auth;

import com.google.gson.annotations.SerializedName;

public final class RespRefresh {
    @SerializedName("expires_in")
    private final int expiresIn;

    @SerializedName("access_token")
    private final String accessToken;

    @SerializedName("refresh_token")
    private final String refreshToken;

    public RespRefresh(int expiresIn, String accessToken, String refreshToken) {
        this.expiresIn = expiresIn;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
