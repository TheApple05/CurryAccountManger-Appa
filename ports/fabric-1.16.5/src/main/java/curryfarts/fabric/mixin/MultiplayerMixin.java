package curryfarts.fabric.mixin;

import curryfarts.fabric.AccountsScreen;
import curryfarts.fabric.Palette;
import curryfarts.fabric.PaletteButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerMixin extends Screen {
    protected MultiplayerMixin(Component text){super(text);}
    @Inject(method="init",at=@At("TAIL"))
    private void curryfarts$accounts(CallbackInfo ci) {
        addButton(new PaletteButton(width-68,4,64,20,"Accounts",Palette.PANEL,Palette.BORDER,
            b->minecraft.setScreen(new AccountsScreen(this))));
    }
}
