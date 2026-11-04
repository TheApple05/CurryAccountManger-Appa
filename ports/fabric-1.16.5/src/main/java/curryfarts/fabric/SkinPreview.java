package curryfarts.fabric;

import curryfarts.legacy.Account;
import curryfarts.legacy.Auth;
import curryfarts.legacy.Http;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.*;

/** Standalone player model works in menus without constructing a fake world. */
public final class SkinPreview {
    private static final Map<String,SkinInfo> SKINS=new ConcurrentHashMap<String,SkinInfo>();
    private static final Set<String> REQUESTED=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private final PlayerModel wide=new PlayerModel(0f,false),slim=new PlayerModel(0f,true);
    public float rotation=205;
    public ResourceLocation skin(final Account a) {
        UUID id;
        try{id=Auth.uuid(a.uuid);}catch(Exception e){id=Account.offlineUuid(a.displayName());}
        SkinInfo cached=SKINS.get(a.stableId());
        if(cached!=null)return cached.texture;
        if(REQUESTED.add(a.stableId())) {
            final UUID fallback=id;final String key=a.stableId();final String name=a.displayName();
            Thread thread=new Thread(()->{
                try {
                    UUID resolved=fallback;
                    if(a.offline()) {
                        JsonObject profile=Http.get("https://api.mojang.com/users/profiles/minecraft/"+name,null);
                        if(profile==null||!profile.has("id"))return;
                        resolved=Auth.uuid(profile.get("id").getAsString());
                    }
                    final GameProfile profile=Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(new GameProfile(resolved,name),false);
                    Minecraft.getInstance().execute(()->Minecraft.getInstance().getSkinManager().registerSkins(profile,(type,texture,info)->{
                        if(type==MinecraftProfileTexture.Type.SKIN)cacheSkin(key,texture,info);
                    },false));
                }catch(Exception ignored){}
            },"Curryfarts-Skin-Lookup");thread.setDaemon(true);thread.start();
        }
        return DefaultPlayerSkin.getDefaultSkin(id);
    }
    private static void cacheSkin(String key,ResourceLocation texture,MinecraftProfileTexture info) {
        // Resolved skins declare slim arms through texture metadata. Missing
        // metadata means the classic model, irrespective of the UUID default.
        SKINS.put(key,new SkinInfo(texture,info!=null&&"slim".equals(info.getMetadata("model"))));
    }
    private PlayerModel selectModel(Account account) {
        SkinInfo cached=SKINS.get(account.stableId());
        if(cached!=null)return cached.slim?slim:wide;
        UUID id;
        try{id=Auth.uuid(account.uuid);}catch(Exception e){id=Account.offlineUuid(account.displayName());}
        return "slim".equals(DefaultPlayerSkin.getSkinModelName(id))?slim:wide;
    }
    private static final class SkinInfo {
        final ResourceLocation texture;
        final boolean slim;
        SkinInfo(ResourceLocation texture,boolean slim){this.texture=texture;this.slim=slim;}
    }
    public void render(PoseStack pose,Account account,int x,int y,int scale) {
        Minecraft mc=Minecraft.getInstance();ResourceLocation skin=skin(account);
        PlayerModel model=selectModel(account);
        pose.pushPose();pose.translate(x,y,100);pose.scale(scale,scale,scale);
        pose.mulPose(Vector3f.XP.rotationDegrees(-8));pose.mulPose(Vector3f.YP.rotationDegrees(rotation));
        RenderSystem.enableDepthTest();RenderSystem.enableBlend();
        model.young=false;model.riding=false;model.setAllVisible(true);MultiBufferSource.BufferSource buffer=mc.renderBuffers().bufferSource();
        model.renderToBuffer(pose,buffer.getBuffer(RenderType.entityTranslucent(skin)),15728880,OverlayTexture.NO_OVERLAY,1f,1f,1f,1f);
        buffer.endBatch();pose.popPose();RenderSystem.disableDepthTest();
    }
}
