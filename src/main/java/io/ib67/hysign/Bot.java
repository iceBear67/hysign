package io.ib67.hysign;

import com.github.steveice10.mc.auth.data.GameProfile;

public final class Bot {
    private final String accessToken;
    private final GameProfile selectedProfile;

    public Bot(String accessToken, GameProfile selectedProfile) {
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
    }
}
