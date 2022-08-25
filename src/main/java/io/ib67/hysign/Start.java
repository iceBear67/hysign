package io.ib67.hysign;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ib67.hysign.auth.RespRefresh;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.ib67.hysign.Env.*;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class Start {
    private static final Logger log = Logger.getLogger("MAIN");
    private static final HttpClient http = HttpClient.newBuilder().build();
    public static final Gson GSON = new Gson();
    // files
    public static final String REFRESH_TOKEN = ".retoken";
    public static final String XBOX_ACCES_TOKEN = ".accToken";
    public static final String MINECRAFT_ACCES_TOKEN = ".mcAccess";

    public static final String URL_TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    public static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_AUTHENTICATE = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MINECRAFT_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MINECRAFT_PROFILES = "https://api.minecraftservices.com/minecraft/profile";
    public static final JsonParser JSON = new JsonParser();

    public static void main(String[] args) {
        log.info("HySign is initializing...");
        new Start().run();
    }

    private void run() {
        Path mcAcces = Path.of(MINECRAFT_ACCES_TOKEN);
        if(Files.exists(mcAcces)){
            try {
                var jo = JSON.parse(Files.readString(mcAcces)).getAsJsonObject();
                accessToken = jo.get("token").getAsString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if(!checkProfile(accessToken)){
            auth();
        }
        log.info("Logging into Hypixel...");
        new Bot(accessToken, new GameProfile(fixUUID(mc_uuid), mc_usrName));
    }

    private String fixUUID(String mc_uuid) {
        return mc_uuid.substring(0, 8) +
                '-' +
                mc_uuid.substring(8, 8 + 4) +
                '-' +
                mc_uuid.substring(8 + 4, 8 + 4 + 4) +
                '-' +
                mc_uuid.substring(8 + 4 + 4, 8 + 4 + 4 + 4) +
                '-' +
                mc_uuid.substring(8 + 4 + 4 + 4, 8 + 4 + 4 + 4 + 12);
    }

    private void auth(){
        // load mc token
        readXBOXAccessTokenFromCache();
        if (!checkXBOXAccessToken(accessToken)) {
            if (Env.refreshToken == null) {
                // update refreshToken.
                log.info("Can't find any valid accessToken, looking for refreshToken...");
                Env.refreshToken = ofNullable(System.getProperty("hysign.refreshToken"))
                        .orElseGet(this::readRefreshToken);
            }
            try {
                updateXBOXAccessToken(refreshToken);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Can't update access token", e);
            }
        }
        System.out.println(accessToken);
        log.info("Authenticating with XBox Live (1/3)");
        // do login
        auth_with_xbl:
        {
            var payload = new JsonObject();
            payload.addProperty("TokenType", "JWT");
            payload.addProperty("RelyingParty", "http://auth.xboxlive.com");
            var prop = new JsonObject();
            prop.addProperty("AuthMethod", "RPS");
            prop.addProperty("SiteName", "user.auth.xboxlive.com");
            prop.addProperty("RpsTicket", "d=" + accessToken);
            payload.add("Properties", prop);
            var req = HttpRequest.newBuilder(URI.create(XBL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            // send.
            try {
                var result = http.send(req, ofString());
                var resp = JSON.parse(result.body());
                Env.xblToken = resp.getAsJsonObject().get("Token").getAsString();
                Env.userHash = resp.getAsJsonObject().get("DisplayClaims")
                        .getAsJsonObject().getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Can't fetch XBL token: " + e);
            }
        }
        log.info("Authenticating with XSTS (2/3)");
        xsts:
        {
            var payload = new JsonObject();
            payload.addProperty("TokenType", "JWT");
            payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            var prop = new JsonObject();
            prop.addProperty("SandboxId", "RETAIL");
            var usrTks = new JsonArray();
            usrTks.add(Env.xblToken);
            prop.add("UserTokens", usrTks);
            payload.add("Properties", prop);

            var req = HttpRequest.newBuilder(URI.create(XSTS_AUTHENTICATE))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            try {
                var resp = JSON.parse(http.send(req, ofString()).body()).getAsJsonObject();
                if (resp.has("XErr")) {
                    var errId = resp.get("XErr").getAsLong();
                    log.warning("Failed to get XSTS Token! Error: (" + errId + ") " + XErr.MESSAGE.get(errId));
                    return;
                }
                Env.xstsToken = resp.get("Token").getAsString();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Can't fetch XSTS Token: " + e);
            }
        }
        log.info("Authenticating with Minecraft! (3/3)");
        // auth mc
        authenticate_with_minecraft:
        {
            var payload = new JsonObject();
            payload.addProperty("identityToken", "XBL3.0 x=" + Env.userHash + ";" + Env.xstsToken);
            var req = HttpRequest.newBuilder(URI.create(MINECRAFT_LOGIN))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            try {
                var resp = JSON.parse(http.send(req, ofString()).body()).getAsJsonObject();
                accessToken = resp.get("access_token").getAsString();
                checkGameOwnership(accessToken);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Can't login to Minecraft! " + e);
            }
        }
        log.info("Checking for profiles...");
        if(!checkProfile(accessToken)){
            throw new RuntimeException("Can't fetch profile! Did you purchased minecraft yet?");
        }
    }
    private boolean checkProfile(String accessToken){
        if (accessToken == null){
            return false;
        }
        var req = HttpRequest.newBuilder(URI.create(MINECRAFT_PROFILES))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        try {
            var resp = JSON.parse(http.send(req, ofString()).body()).getAsJsonObject();
            var name = resp.get("name").getAsString();
            var id = resp.get("id").getAsString();

            mc_uuid = id;
            mc_usrName = name;

            log.info("[" + name + "/" + id + "] Logged in!");
            var jo = new JsonObject();
            jo.addProperty("token",accessToken);
            jo.addProperty("time",System.currentTimeMillis());
            Files.writeString(Path.of(MINECRAFT_ACCES_TOKEN), jo.toString());
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
    private void checkGameOwnership(String accessToken) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/entitlements/mcstore"))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        var resp = http.send(req, ofString()).body();
        if (!resp.contains("game_minecraft"))
            throw new IOException("This account haven't purchase Minecraft yet! Dump: " + resp);
    }

    private void readXBOXAccessTokenFromCache() {
        final var file = Path.of(XBOX_ACCES_TOKEN);
        if (Files.exists(file)) {
            try {
                log.info("AccessToken was found in cache!");
                accessToken = Files.readString(file);
            } catch (IOException ignored) {

            }
        }
    }

    private void updateXBOXAccessToken(String refreshToken) throws IOException, InterruptedException {
        var query = Map.of(
                "grant_type", "refresh_token",
                "client_id", "6a3728d6-27a3-4180-99bb-479895b8f88e", // borrowed from HMCL, will delete if abused
                "refresh_token", refreshToken
        );
        var request = HttpRequest.newBuilder(URI.create(URL_TOKEN))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(asForm(query)))
                .build();
        var body = http.send(request, ofString()).body();
        var resp = requireNonNull(GSON.fromJson(body, RespRefresh.class));
        requireNonNull(Env.accessToken = resp.getAccessToken());
        Files.writeString(Path.of(XBOX_ACCES_TOKEN), accessToken);
    }

    private String asForm(Map<String, String> query) {
        return query.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String readRefreshToken() {
        var file = Path.of(REFRESH_TOKEN);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Can't find refreshToken file! It should be `.retoken` and under same directory.");
        }
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkXBOXAccessToken(String accessToken) {
        return accessToken != null;
    }
}
