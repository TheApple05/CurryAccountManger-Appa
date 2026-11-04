package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class PaletteButton extends GuiButton {
    private int fill;private final int border;
    public PaletteButton(int id,int x,int y,int width,int height,String label,int fill,int border) {
        super(id,x,y,width,height,label);this.fill=fill;this.border=border;
    }
    public void setFill(int color){fill=color;}
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks) {
        if(!visible)return;
        hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        int edge=enabled&&hovered?Palette.HOVER:border;
        GuiScreen.drawRect(x,y,x+width,y+height,edge);
        GuiScreen.drawRect(x+1,y+1,x+width-1,y+height-1,enabled?fill:Palette.PANEL);
        drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+(height-8)/2,enabled?Palette.TEXT:Palette.DISABLED);
    }
}
