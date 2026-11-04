package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

public final class PaletteButton extends GuiButton {
    private final int fill, border;
    public PaletteButton(int id,int x,int y,int width,int height,String label,int fill,int border) {
        super(id,x,y,width,height,label);this.fill=fill;this.border=border;
    }
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks) {
        if(!visible)return;
        hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        int edge=enabled?border:Palette.TEAL;
        GuiScreen.drawRect(x,y,x+width,y+height,edge);
        GuiScreen.drawRect(x+1,y+1,x+width-1,y+height-1,enabled?fill:Palette.FIELD);
        if(hovered&&enabled)GuiScreen.drawRect(x+1,y+1,x+width-1,y+height-1,0x0CFFFFFF);
        drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+(height-8)/2,enabled?Palette.TEXT:Palette.DISABLED);
    }
}
