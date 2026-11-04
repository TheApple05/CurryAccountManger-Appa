package autismclient.gui.vanillaui.components;

import java.util.Collection;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

public final class UiSizing {
   private UiSizing() {
   }

   public static float clamp(float var0, float var1, float var2) {
      float var3 = Math.max(var1, var2);
      return Math.max(var1, Math.min(var0, var3));
   }

   public static float fitTextWidth(Font var0, String var1, Identifier var2, int var3, float var4, float var5, float var6) {
      float var7 = UiText.width(var0, var1 == null ? "" : var1, var2, var3);
      return clamp(var7 + var4 * 2.0F, var5, var6);
   }

   public static int fitTextWidthInt(Font var0, String var1, Identifier var2, int var3, int var4, int var5, int var6) {
      return Math.round(fitTextWidth(var0, var1, var2, var3, var4, var5, var6));
   }

   public static int measureWidestText(Font var0, Identifier var1, int var2, Collection<String> var3) {
      if (var0 != null && var3 != null && !var3.isEmpty()) {
         int var4 = 0;

         for (String var6 : var3) {
            var4 = Math.max(var4, UiText.width(var0, var6 == null ? "" : var6, var1, var2));
         }

         return var4;
      } else {
         return 0;
      }
   }

   public static int centerInside(int var0, int var1, int var2) {
      return var0 + Math.max(0, (var1 - var2) / 2);
   }

   public static int alignMiddle(int var0, int var1, int var2) {
      return centerInside(var0, var1, var2);
   }

   public static int alignTextY(int var0, int var1, int var2, int var3) {
      int var4 = Math.max(0, var1 - var2);
      return var0 + (var4 + 1) / 2 + var3;
   }

   public static int lerpColor(int var0, int var1, float var2) {
      float var3 = Math.max(0.0F, Math.min(1.0F, var2));
      int var4 = var0 >>> 24 & 0xFF;
      int var5 = var0 >>> 16 & 0xFF;
      int var6 = var0 >>> 8 & 0xFF;
      int var7 = var0 & 0xFF;
      int var8 = var1 >>> 24 & 0xFF;
      int var9 = var1 >>> 16 & 0xFF;
      int var10 = var1 >>> 8 & 0xFF;
      int var11 = var1 & 0xFF;
      int var12 = Math.round(var4 + (var8 - var4) * var3);
      int var13 = Math.round(var5 + (var9 - var5) * var3);
      int var14 = Math.round(var6 + (var10 - var6) * var3);
      int var15 = Math.round(var7 + (var11 - var7) * var3);
      return var12 << 24 | var13 << 16 | var14 << 8 | var15;
   }
}
