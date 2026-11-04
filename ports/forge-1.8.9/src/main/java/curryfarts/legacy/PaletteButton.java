package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class PaletteButton extends GuiButton {
    private final int fill, border;
    public PaletteButton(int id,int xPosition,int yPosition,int width,int height,String label,int fill,int border) {
        super(id,xPosition,yPosition,width,height,label);this.fill=fill;this.border=border;
    }
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY) {
        if(!visible)return;
        hovered=mouseX>=xPosition&&mouseY>=yPosition&&mouseX<xPosition+width&&mouseY<yPosition+height;
        int edge=enabled?border:Palette.TEAL;
        GuiScreen.drawRect(xPosition,yPosition,xPosition+width,yPosition+height,edge);
        GuiScreen.drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,enabled?fill:Palette.FIELD);
        if(hovered&&enabled)GuiScreen.drawRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,0x0CFFFFFF);
        drawCenteredString(mc.fontRendererObj,displayString,xPosition+width/2,yPosition+(height-8)/2,enabled?Palette.TEXT:Palette.DISABLED);
    }
}
