package curryfarts.fabric;

import java.io.File;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import curryfarts.legacy.AccountStore;

public final class CurryfartsClient implements ClientModInitializer {
    public static final File CONFIG = FabricLoader.getInstance().getConfigDir().resolve("autism").toFile();
    public static final AccountStore ACCOUNTS = new AccountStore();
    @Override public void onInitializeClient() { CONFIG.mkdirs(); }
}
