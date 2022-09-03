package io.ib67.hysign.auth;

import com.google.gson.annotations.SerializedName;

public final class RespRefresh {
    @SerializedName("access_token")
    public String accessToken;
    @SerializedName("expires_in")
    int expiresIn;
    @SerializedName("refresh_token")
    String refreshToken;

}
