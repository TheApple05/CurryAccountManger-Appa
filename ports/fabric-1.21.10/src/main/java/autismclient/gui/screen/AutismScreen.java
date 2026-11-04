package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.SectionPanel;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class AutismScreen extends Screen {
   protected AutismScreen(Component var1) {
      super(var1);
   }

   protected static String safeTrim(String var0) {
      return var0 == null ? "" : var0.trim();
   }

   protected static MouseButtonEvent virtualEvent(MouseButtonEvent var0) {
      return new MouseButtonEvent(AutismUiScale.toVirtual(var0.x()), AutismUiScale.toVirtual(var0.y()), new MouseButtonInfo(var0.button(), var0.modifiers()));
   }

   protected int screenWidth() {
      int var1 = AutismUiScale.getVirtualScreenWidth();
      return var1 <= 0 ? this.width : var1;
   }

   protected int screenHeight() {
      int var1 = AutismUiScale.getVirtualScreenHeight();
      return var1 <= 0 ? this.height : var1;
   }

   protected void drawPanel(GuiGraphics var1, int var2, int var3, int var4, int var5, int var6) {
      SectionPanel.renderBody(UiContexts.overlay(var1, this.font, -10000, -10000), UiBounds.of(var2, var3, var4, var5), var6);
   }

   protected void toast(String var1, int var2) {
      if (var1 != null && !var1.isBlank() && this.minecraft != null) {
         this.minecraft.execute(() -> AutismNotifications.show(var1, var2));
      }
   }
}
