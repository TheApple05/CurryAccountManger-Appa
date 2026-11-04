package curryfarts.legacy;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

/** Asynchronous Mojang skin lookup with deterministic default-skin fallback. */
final class LegacySkins {
    static final class Skin {
        final UUID uuid;volatile ResourceLocation location;volatile String model;
        Skin(UUID id){uuid=id;location=DefaultPlayerSkin.getDefaultSkin(id);model=DefaultPlayerSkin.getSkinType(id);}
    }
    private static final ConcurrentHashMap<String,Skin>CACHE=new ConcurrentHashMap<String,Skin>();
    static Skin get(final Account account) {
        final String key=account.type+":"+account.uuid+":"+account.displayName();Skin cached=CACHE.get(key);if(cached!=null)return cached;
        UUID uuid;try{uuid=Auth.uuid(account.uuid);}catch(Exception ignored){uuid=Account.offlineUuid(account.displayName());}
        final Skin skin=new Skin(uuid);Skin raced=CACHE.putIfAbsent(key,skin);if(raced!=null)return raced;
        final String name=account.displayName();final boolean offline=account.offline();
        Thread thread=new Thread(()->{try{
            UUID profileId=skin.uuid;
            if(offline){JsonObject p=Http.get("https://api.mojang.com/minecraft/profile/lookup/name/"+java.net.URLEncoder.encode(name,"UTF-8"),null);if(p==null||!p.has("id"))return;profileId=Auth.uuid(p.get("id").getAsString());}
            final GameProfile profile=Minecraft.getMinecraft().getSessionService().fillProfileProperties(new GameProfile(profileId,name),false);
            Minecraft.getMinecraft().addScheduledTask(()->Minecraft.getMinecraft().getSkinManager().loadProfileTextures(profile,(type,location,texture)->{if(type==MinecraftProfileTexture.Type.SKIN){skin.location=location;String model=texture.getMetadata("model");skin.model=model==null?"default":model;}},false));
        }catch(Exception ignored){}},"Curryfarts-Skin-Lookup");thread.setDaemon(true);thread.start();return skin;
    }
}
