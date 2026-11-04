package curryfarts.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.TextComponent;

public final class PaletteField extends EditBox {
    private final String hint;
    public PaletteField(Font font,int x,int y,int w,int h,String hint) {
        super(font,x,y,w,h,new TextComponent(hint));this.hint=hint;setTextColor(Palette.TEXT);setTextColorUneditable(Palette.DISABLED);setMaxLength(16384);
    }
    @Override public void renderButton(PoseStack pose,int mx,int my,float delta) {
        int px=x,py=y,pw=width;boolean border=true;
        fill(pose,x,y,x+width,y+height,isFocused()?Palette.GOLD:Palette.BORDER);
        fill(pose,x+1,y+1,x+width-1,y+height-1,Palette.FIELD);
        setBordered(false);x+=4;y+=(height-8)/2;width-=8;
        try { super.renderButton(pose,mx,my,delta);if(getValue().isEmpty()&&!isFocused())MinecraftFont.draw(pose,hint,x,y); }
        finally {x=px;y=py;width=pw;setBordered(border);}
    }
    private static class MinecraftFont {static void draw(PoseStack pose,String s,int x,int y){net.minecraft.client.Minecraft.getInstance().font.draw(pose,s,x,y,Palette.MUTED);}}
}
