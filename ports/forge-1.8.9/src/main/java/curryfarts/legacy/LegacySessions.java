package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import com.mojang.authlib.minecraft.MinecraftSessionService;

/** Applies an already resolved account on the Minecraft client thread. */
public final class LegacySessions {
    private static Session original;
    private static MinecraftSessionService originalService;
    private LegacySessions() {}
    public static Session original() {
        if(original==null){original=Minecraft.getMinecraft().getSession();originalService=Minecraft.getMinecraft().getSessionService();}
        return original;
    }
    public static void restore() { set(original());service(originalService); }
    public static void apply(Account account) {
        original();
        String token=("TheAltening".equals(account.type)?account.sessionToken:account.token);
        boolean offline="Cracked".equals(account.type)||"Generated".equals(account.type);
        set(new Session(account.username,account.uuid,offline?"":token,offline?"legacy":"mojang"));
        service("TheAltening".equals(account.type)?new AlteningSessionService(originalService):originalService);
    }
    private static void set(Session session) {
        ObfuscationReflectionHelper.setPrivateValue(Minecraft.class,Minecraft.getMinecraft(),session,"session","field_71449_j");
    }
    private static void service(MinecraftSessionService service) { ObfuscationReflectionHelper.setPrivateValue(Minecraft.class,Minecraft.getMinecraft(),service,"sessionService","field_152355_az"); }
    public static boolean current(Account account) {
        Session user=Minecraft.getMinecraft().getSession();
        return account!=null&&account.username!=null&&account.username.equals(user.getUsername())
            &&account.uuid!=null&&account.uuid.replace("-","").equalsIgnoreCase(user.getPlayerID().replace("-",""));
    }
}
