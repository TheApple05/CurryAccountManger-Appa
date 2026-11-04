package curryfarts.fabric;

import curryfarts.fabric.mixin.MinecraftAccessor;
import curryfarts.legacy.Account;
import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.SocialInteractionsService;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import java.io.File;
import java.net.Proxy;
import java.util.UUID;

public final class Sessions {
    private static User original;
    private static MinecraftSessionService originalService;
    private static SkinManager originalSkins;
    private static PlayerSocialManager originalSocial;
    private static SocialInteractionsService originalSocialService;
    private static PropertyMap originalProperties;
    public static String lastError="";
    public static synchronized User original() {
        Minecraft mc=Minecraft.getInstance();
        if(original==null){original=mc.getUser();originalService=mc.getMinecraftSessionService();originalSkins=mc.getSkinManager();originalSocial=mc.getPlayerSocialManager();originalSocialService=((MinecraftAccessor)mc).curryfarts$getSocialService();originalProperties=new PropertyMap();originalProperties.putAll(((MinecraftAccessor)mc).curryfarts$getProfileProperties());}
        return original;
    }
    public static boolean login(Account account) {
        Minecraft mc=Minecraft.getInstance();original();
        try {
            YggdrasilAuthenticationService auth;
            if("TheAltening".equals(account.type)) {
                Environment env=Environment.create("http://authserver.thealtening.com","http://api.thealtening.com","http://sessionserver.thealtening.com","https://api.minecraftservices.com","The Altening");
                auth=new YggdrasilAuthenticationService(Proxy.NO_PROXY,env);
            } else auth=new YggdrasilAuthenticationService(Proxy.NO_PROXY);
            final MinecraftSessionService service=auth.createMinecraftSessionService();
            final User user=new User(account.username,account.uuid,
                "TheAltening".equals(account.type)?account.sessionToken:account.token,"mojang");
            SocialInteractionsService offline=new SocialInteractionsService(){
                public boolean serversAllowed(){return true;}public boolean realmsAllowed(){return true;}public boolean chatAllowed(){return true;}public boolean isBlockedPlayer(UUID uuid){return false;}
            };
            final SocialInteractionsService social=("Microsoft".equals(account.type)||"Session".equals(account.type))
                ?auth.createSocialInteractionsService(account.token):offline;
            final SkinManager skins=new SkinManager(mc.getTextureManager(),new File(mc.gameDirectory,"assets/skins"),service);
            Runnable apply=()->{MinecraftAccessor access=(MinecraftAccessor)mc;access.curryfarts$getProfileProperties().clear();access.curryfarts$setUser(user);access.curryfarts$setSessionService(service);access.curryfarts$setSocialService(social);access.curryfarts$setSkinManager(skins);access.curryfarts$setPlayerSocialManager(new PlayerSocialManager(mc,social));};
            if(mc.isSameThread())apply.run();else mc.submit(apply).get();
            lastError="";return true;
        }catch(Exception error){lastError=error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());return false;}
    }
    public static boolean restore() {
        Minecraft mc=Minecraft.getInstance();original();
        Runnable apply=()->{MinecraftAccessor access=(MinecraftAccessor)mc;access.curryfarts$getProfileProperties().clear();access.curryfarts$getProfileProperties().putAll(originalProperties);access.curryfarts$setUser(original);access.curryfarts$setSessionService(originalService);access.curryfarts$setSocialService(originalSocialService);access.curryfarts$setSkinManager(originalSkins);access.curryfarts$setPlayerSocialManager(originalSocial);};
        try{if(mc.isSameThread())apply.run();else mc.submit(apply).get();return true;}catch(Exception e){lastError=e.toString();return false;}
    }
}
