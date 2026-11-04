package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Account feedback without the inactive full client's command/history hooks. */
public final class AutismClientMessaging {
    private AutismClientMessaging() {}

    public static void sendPrefixed(String text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.gui.chatListener().handleSystemMessage(Component.literal("[AUTISM] " + text), false);
            } else {
                AutismNotifications.show(text, 0xFFFF5555);
            }
        });
    }
}
