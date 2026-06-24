package fun.lumis.common.discord.callbacks;

import com.sun.jna.Callback;
import fun.lumis.common.discord.utils.DiscordUser;

public interface ReadyCallback extends Callback {
    void apply(DiscordUser var1);
}
