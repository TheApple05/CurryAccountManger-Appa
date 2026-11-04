package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class PaletteButton extends GuiButton {
    private int fill;private final int border;
    public PaletteButton(int id,int xPosition,int yPosition,int width,int height,String label,int fill,int border) {
        super(id,xPosition,yPosition,width,height,label);this.fill=fill;this.border=border;
    }
    public void setFill(int color){fill=color;}
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY) {
        if(!visible)return;
        hovered=mouseX>=xPosition&&mouseY>=yPosition&&mouseX<xPosition+width&&mouseY<yPosition+height;
        int edge=enabled&&hovered?Palette.HOVER:border;
        GuiScreen.drawRect(xPosition,yPosition,xPosition+width,yPosition+height,edge);
        GuiScreen.drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,enabled?fill:Palette.PANEL);
        drawCenteredString(mc.fontRendererObj,displayString,xPosition+width/2,yPosition+(height-8)/2,enabled?Palette.TEXT:Palette.DISABLED);
    }
}
