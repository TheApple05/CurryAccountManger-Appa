package curryfarts.legacy;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/** Renders an actual player model even when the account manager opens at the title screen. */
final class LegacyPreview {
    static boolean isPreview(Object entity){return entity instanceof Player;}
    private WorldClient world;private Player player;private String key="";
    void render(Account account,int x,int bottom,int scale,int mouseX,int mouseY) {
        Minecraft mc=Minecraft.getMinecraft();LegacySkins.Skin skin=LegacySkins.get(account);String next=skin.uuid+":"+account.displayName();
        if(world==null)world=new WorldClient(null,new WorldSettings(0L,GameType.SURVIVAL,false,false,WorldType.DEFAULT),0,EnumDifficulty.NORMAL,mc.mcProfiler);
        if(player==null||!next.equals(key)){key=next;player=new Player(world,new GameProfile(skin.uuid,account.displayName()),skin);}
        float lookX=Math.max(-18f,Math.min(18f,x-mouseX));
        float lookY=Math.max(-12f,Math.min(12f,bottom-scale-mouseY));
        // Title-screen GUIs have depth testing disabled; without it rear cube faces
        // overwrite the face/chest of the preview player regardless of its yaw.
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.enableDepth();GlStateManager.depthMask(true);
        try {GuiInventory.drawEntityOnScreen(x,bottom,scale,lookX,lookY,player);}
        finally {GlStateManager.depthMask(depthMask);if(!depth)GlStateManager.disableDepth();}
    }
    private static final class Player extends EntityOtherPlayerMP {
        private final LegacySkins.Skin skin;
        Player(WorldClient world,GameProfile profile,LegacySkins.Skin skin){super(world,profile);this.skin=skin;}
        @Override public boolean isSpectator(){return false;}
        @Override protected net.minecraft.client.network.NetworkPlayerInfo getPlayerInfo(){return null;}
        @Override public ResourceLocation getLocationSkin(){return skin.location;}
        @Override public String getSkinType(){return skin.model;}
    }
}
