package autismclient;

import net.fabricmc.api.ClientModInitializer;

public final class AutismAccountManagerMod implements ClientModInitializer {
   public void onInitializeClient() {
      AutismClientAddon.FOLDER.mkdirs();
   }
}
