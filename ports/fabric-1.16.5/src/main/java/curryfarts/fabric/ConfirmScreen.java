package curryfarts.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public final class ConfirmScreen extends Screen {
    private final AccountsScreen parent;
    public ConfirmScreen(AccountsScreen parent){super(new TextComponent("Clear accounts?"));this.parent=parent;}
    @Override protected void init(){int x=width/2-150,y=height/2-55;addButton(new PaletteButton(x+14,y+75,110,20,"Clear accounts",Palette.DANGER,Palette.ORANGE,b->{parent.clearAll();onClose();}));addButton(new PaletteButton(x+180,y+75,106,20,"Cancel",Palette.NAVY,Palette.BORDER,b->onClose()));}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(PoseStack pose,int mx,int my,float delta){fill(pose,0,0,width,height,Palette.BACKGROUND);int x=width/2-150,y=height/2-55;parent.panel(pose,x,y,300,112,Palette.PANEL);font.draw(pose,"Clear saved accounts?",x+14,y+12,Palette.TEXT);font.draw(pose,CurryfartsClient.ACCOUNTS.all().size()+" accounts will be deleted.",x+14,y+34,Palette.GOLD);font.draw(pose,"This cannot be undone.",x+14,y+51,Palette.MUTED);super.render(pose,mx,my,delta);}
}
