package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/** Keeps controls readable at Auto/Large GUI scale and restores the user's setting on exit. */
abstract class LegacyScreen extends GuiScreen {
    private int originalScale=-1;
    @Override public void setWorldAndResolution(Minecraft client,int ignoredWidth,int ignoredHeight){
        if(originalScale<0)originalScale=client.gameSettings.guiScale;
        int maximum=Math.max(1,Math.min(client.displayWidth/640,client.displayHeight/360));
        client.gameSettings.guiScale=Math.min(originalScale==0?Integer.MAX_VALUE:originalScale,maximum);
        ScaledResolution resolution=new ScaledResolution(client);
        super.setWorldAndResolution(client,resolution.getScaledWidth(),resolution.getScaledHeight());
    }
    @Override public void onGuiClosed(){
        if(originalScale>=0){mc.gameSettings.guiScale=originalScale;originalScale=-1;}
        super.onGuiClosed();
    }
}
