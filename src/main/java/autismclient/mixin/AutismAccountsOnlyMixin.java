package autismclient.mixin;

import autismclient.gui.screen.AutismAccountsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class AutismAccountsOnlyMixin extends Screen {
   @Unique
   private Button autism$accountsButton;

   protected AutismAccountsOnlyMixin(Component var1) {
      super(var1);
   }

   @Inject(method = "repositionElements", at = @At("TAIL"))
   private void autism$repositionElements(CallbackInfo var1) {
      if (this.autism$accountsButton == null) {
         this.autism$accountsButton = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Accounts"), var1x -> this.minecraft.setScreen(new AutismAccountsScreen(this))).bounds(0, 0, 60, 20).build()
         );
      }

      this.autism$accountsButton.setX(Math.max(4, this.width - 64));
      this.autism$accountsButton.setY(4);
   }
}
