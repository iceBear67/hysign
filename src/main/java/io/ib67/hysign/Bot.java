package io.ib67.hysign;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.inventory.ClickItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerActionType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundOpenBookPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.github.steveice10.mc.protocol.MinecraftConstants.SESSION_SERVICE_KEY;
import static java.util.concurrent.CompletableFuture.delayedExecutor;

public final class Bot extends SessionAdapter implements Runnable {
    private static final Logger log = Logger.getLogger("bot");
    private static final Pattern PLAYER_CHAT = Pattern.compile("([a-zA-Z_0-9§+\\[\\] ]+): .+");

    private final String accessToken;
    private final GameProfile selectedProfile;
    private static final Executor DELAYED_EXEC = delayedExecutor(2, TimeUnit.SECONDS);
    private final HttpClient http;
    private TcpClientSession client;
    private int windowId = -1;
    private int stateId;

    public Bot(String accessToken, GameProfile selectedProfile, HttpClient httpClient) {
        this.accessToken = accessToken;
        this.selectedProfile = selectedProfile;
        this.http = httpClient;
    }

    public void run() {
        var protocol = new MinecraftProtocol(selectedProfile, accessToken);
        var session = new SessionService();
        client = new TcpClientSession("mc.hypixel.net", 25565, protocol);
        client.setFlag(SESSION_SERVICE_KEY, session);
        client.addListener(this);
        client.connect(true);
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundPlayerChatPacket chat) {
            //  var content = chat.getUnsignedContent() == null ? chat.getSignedContent() : chat.getUnsignedContent();
            // System.out.println("[PLAYER] " + chat.getSenderName() + ": " + PlainTextComponentSerializer.plainText().serialize(content));
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            if (windowId == -1) {
                return; // uninitialized
            }
            var message = PlainTextComponentSerializer.plainText().serialize(chat.getContent());
            if (PLAYER_CHAT.matcher(message).find()
                    || message.contains("进入了大厅")
                    || message.contains("找到了")
            ) {
                return; // ignore spams
            }
            System.out.println("[SERVER] " + message);
        } else if (packet instanceof ClientboundLoginPacket login) {
            log.info("We're logged in! Signing up..");
            runDelayed(this::signUp);
        } else if (packet instanceof ClientboundOpenBookPacket openBook) {
            log.warning("It seems that you're in a resource-pack required minigame server, which is possible to break autosign bot.");
        } else if (packet instanceof ClientboundOpenScreenPacket window) {
            windowId = window.getContainerId();
            System.out.println("Window: " + PlainTextComponentSerializer.plainText().serialize(window.getTitle()) + " id: " + windowId);
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            if (slot.getContainerId() == 0 || slot.getContainerId() == 255) {
                return; // ignore player slots
            }
            if (windowId == -1) {
                log.warning("WindowId hasn't been initialized!! recv: " + slot.getContainerId());
            }
            //System.out.println("Recv: "+slot.getContainerId()+" Excpt: "+windowId);
            if (slot.getContainerId() == windowId) {
                stateId = slot.getStateId();
                if (slot.getSlot() == 33) {
                    runDelayed(this::clickDaliy);
                }
            }
        } else if (packet instanceof ClientboundLoginDisconnectPacket loginDisconnectPacket) {
            var reason = loginDisconnectPacket.getReason();
            log.warning("Can't connect to server! " + PlainTextComponentSerializer.plainText().serialize(reason));
        } else if (packet instanceof ClientboundDisconnectPacket disconnectPacket) {
            var reason = disconnectPacket.getReason();
            log.warning("Disconnected from server! " + PlainTextComponentSerializer.plainText().serialize(reason));
        }
    }

    private void clickDaliy() {
        client.send(new ServerboundContainerClickPacket(
                windowId,
                stateId,
                33,
                ContainerActionType.CLICK_ITEM,
                ClickItemAction.LEFT_CLICK,
                null,
                Map.of()
        ));
        log.info("Clicked! Will quit after 2 seconds.");
        runDelayed(this::quit);
    }

    private void quit() {
        client.disconnect("My mission is completed.");
        System.exit(0);
    }

    private void runDelayed(Runnable runnable) {
        CompletableFuture.runAsync(runnable, DELAYED_EXEC);
    }

    private void signUp() {
        client.send(new ServerboundChatPacket("/delivery", System.currentTimeMillis(), 0, new byte[0], false));
    }
}
