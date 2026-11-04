package autismclient.util;

import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.ToastStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class AutismNotifications {
   private static final ToastStack TOASTS = new ToastStack(2100L, 140.0F, 220.0F, 4, 4, 18);

   private AutismNotifications() {
   }

   public static void copied(String var0) {
      show(var0 != null && !var0.isBlank() ? var0 : "Copied.", -13248397);
   }

   public static void success(String var0) {
      show(var0, -13248397);
   }

   public static void warning(String var0) {
      show(var0, -14249);
   }

   public static void error(String var0) {
      show(var0, -42149);
   }

   public static void show(String var0, int var1) {
      TOASTS.show(var0, var1);
   }

   public static void render(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var0 != null && var1 != null && var1.font != null) {
         if (TOASTS.hasVisibleToasts()) {
            int var2 = Math.min(320, Math.max(120, AutismUiScale.getVirtualScreenWidth() - 16));
            int var3 = Math.max(8, (AutismUiScale.getVirtualScreenWidth() - var2) / 2);
            TOASTS.render(UiContexts.overlay(var0, var1.font, -1, -1), var3, 8, var2);
         }
      }
   }

   public static boolean hasVisible() {
      return TOASTS.hasVisibleToasts();
   }

   public static void clear() {
      TOASTS.clear();
   }
}
