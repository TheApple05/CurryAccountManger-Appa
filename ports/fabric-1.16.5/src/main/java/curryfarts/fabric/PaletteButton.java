package curryfarts.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.TextComponent;

public final class PaletteButton extends Button {
    public int fillColor, borderColor;
    public PaletteButton(int x,int y,int w,int h,String title,int fill,int border,OnPress press) {
        super(x,y,w,h,new TextComponent(title),press);fillColor=fill;borderColor=border;
    }
    @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float delta) {
        boolean badge="Active".equals(getMessage().getString())&&fillColor==Palette.GREEN;
        int fill=active||badge?fillColor:Palette.FIELD,border=active||badge?borderColor:Palette.TEAL;
        fill(pose,x,y,x+width,y+height,border);fill(pose,x+1,y+1,x+width-1,y+height-1,fill);
        if(active&&isHovered())fill(pose,x+1,y+1,x+width-1,y+height-1,0x0CFFFFFF);
        if(isFocused()) { fill(pose,x,y,x+width,y+1,Palette.GOLD);fill(pose,x,y+height-1,x+width,y+height,Palette.GOLD); }
        Minecraft mc=Minecraft.getInstance();String title=mc.font.plainSubstrByWidth(getMessage().getString(),Math.max(1,width-6));
        mc.font.draw(pose,title,x+(width-mc.font.width(title))/2f,y+(height-8)/2f,active||badge?Palette.TEXT:Palette.DISABLED);
    }
}
