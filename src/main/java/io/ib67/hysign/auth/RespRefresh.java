package io.ib67.hysign.auth;

import com.google.gson.annotations.SerializedName;

public record RespRefresh(@SerializedName("expires_in") int expiresIn,
                          @SerializedName("access_token") String accessToken,
                          @SerializedName("refresh_token") String refreshToken) {

    @Override
    public int expiresIn() {
        return expiresIn;
    }

    @Override
    public String refreshToken() {
        return refreshToken;
    }

    @Override
    public String accessToken() {
        return accessToken;
    }
}
