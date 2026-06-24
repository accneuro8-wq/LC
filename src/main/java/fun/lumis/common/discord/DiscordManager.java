package fun.lumis.common.discord;
import antidaunleak.api.UserProfile;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Identifier;
import fun.lumis.common.discord.utils.*;
import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.client.discord.Buffer;
import fun.lumis.lumis;
import java.io.IOException;
import java.util.Arrays;

@Setter
@Getter
public class DiscordManager implements QuickImports {
    private final DiscordDaemonThread discordDaemonThread = new DiscordDaemonThread();
    private boolean running = true;
    private DiscordInfo info = new DiscordInfo("Unknown", "", "");
    private Identifier avatarId;

    public void init() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            return;
        }

        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder()
                .ready((user) -> {
                    lumis.getInstance().getDiscordManager().setInfo(
                            new DiscordInfo(user.username,
                                    "https://cdn.discordapp.com/avatars/" + user.userId + "/" + user.avatar + ".png",
                                    user.userId));
                    DiscordlumisPresence lumisPresence = new DiscordlumisPresence.Builder()
                            .setStartTimestamp(System.currentTimeMillis() / 1000)
                            .setDetails("User: " + UserProfile.getInstance().profile("username"))
                            .setState("Uid: " + UserProfile.getInstance().profile("uid"))
                            .setLargeImage("https://i.postimg.cc/nznMWbhM/0001-0250.gif", "https://lumisclient.fun/")
                            .setSmallImage(lumis.getInstance().getDiscordManager().getInfo().avatarUrl, "https://lumisclient.fun/")
                            .setButtons(Arrays.asList(
                                    RPCButton.create("Телеграм", "https://t.me/lumisclientnew"),
                                    RPCButton.create("Дискорд", "https://discord.gg/zYctK4mjZZ")))
                            .build();
                    DiscordRPC.INSTANCE.Discord_UpdatePresence(lumisPresence);
                }).build();
        DiscordRPC.INSTANCE.Discord_Initialize("1419653405265105021", handlers, true, "");
        discordDaemonThread.start();
    }

    public void stopRPC() {
        DiscordRPC.INSTANCE.Discord_Shutdown();
        this.running = false;
    }

    public void load() throws IOException {
        if (avatarId == null && !info.avatarUrl.isEmpty()) {
            avatarId = Buffer.registerDynamicTexture("avatar-", Buffer.getHeadFromURL(info.avatarUrl));
        }
    }

    public Identifier getAvatarId() {
        return avatarId;
    }

    private class DiscordDaemonThread extends Thread {
        @Override
        public void run() {
            this.setName("Discord-RPC");
            try {
                while (lumis.getInstance().getDiscordManager().isRunning()) {
                    DiscordRPC.INSTANCE.Discord_RunCallbacks();
                    load();
                    Thread.sleep(15000);
                }
            } catch (Exception exception) {
                stopRPC();
            }
            super.run();
        }
    }

    public record DiscordInfo(String userName, String avatarUrl, String userId) {}
}
