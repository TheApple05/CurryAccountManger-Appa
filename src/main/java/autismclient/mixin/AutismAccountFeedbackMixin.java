package autismclient.mixin;

import autismclient.util.AutismNotifications;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class AutismAccountFeedbackMixin {
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void autism$renderAccountNotifications(GuiGraphicsExtractor var1, int var2, int var3, float var4, CallbackInfo var5) {
      if (AutismNotifications.hasVisible()) {
         var1.nextStratum();
         AutismUiScale.pushOverlayScale(var1);

         try {
            AutismNotifications.render(var1);
         } finally {
            AutismUiScale.popOverlayScale(var1);
         }
      }
   }
}
