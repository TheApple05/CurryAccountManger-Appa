package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiSizing;
import autismclient.gui.vanillaui.components.UiTone;
import net.minecraft.client.gui.Font;

public final class DirectLayout {
   private DirectLayout() {
   }

   public static int contentWidth(int var0, int var1) {
      return Math.max(0, var0 - var1 * 2);
   }

   public static int reserveScrollbar(int var0, boolean var1, int var2) {
      return Math.max(0, var0 - (var1 ? var2 : 0));
   }

   public static int fitOverlayButtonWidth(Font var0, CompactTheme var1, UiTone var2, String var3, int var4, int var5, int var6) {
      return var0 != null && var1 != null
         ? UiSizing.fitTextWidthInt(
            var0, var3 == null ? "" : var3, var1.fontFor(var2 == null ? UiTone.BODY : var2), var1.color(var2 == null ? UiTone.BODY : var2), var4, var5, var6
         )
         : Math.max(var5, 0);
   }

   public static int rightAlign(int var0, int var1, int var2, int var3) {
      return var0 + Math.max(0, var1 - var2 - var3);
   }

   public static int fitPanelDimension(int var0, int var1, int var2) {
      int var3 = Math.max(1, var0 - Math.max(0, var1) * 2);
      return Math.max(1, Math.min(var2, var3));
   }

   public static int centerPanel(int var0, int var1, int var2) {
      int var3 = Math.max(0, var2);
      return Math.max(var3, Math.min((var0 - var1) / 2, Math.max(var3, var0 - var3 - var1)));
   }
}
