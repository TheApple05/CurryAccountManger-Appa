package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactScrollbar {
   private CompactScrollbar() {
   }

   public static CompactScrollbar.Metrics compute(int var0, int var1, int var2, int var3, int var4, int var5, int var6) {
      return compute(var0, var1, var2, var3, var4, var5, (float)var6);
   }

   public static CompactScrollbar.Metrics compute(int var0, int var1, int var2, int var3, int var4, int var5, float var6) {
      int var7 = var2 + var4;
      byte var14 = 6;
      var2 = var7 - var14;
      if (var0 > 0 && var1 > 0 && var5 > 0) {
         int var8 = Math.max(0, var0 - var1);
         if (var8 <= 0) {
            return new CompactScrollbar.Metrics(var2, var3, var14, var5, var3, var5, 0);
         }

         int var9 = Math.max(12, (int)Math.round(var5 * ((double)var1 / Math.max(var1, var0))));
         var9 = Math.min(var5, var9);
         int var10 = Math.max(1, var5 - var9);
         float var11 = Math.max(0.0F, Math.min(var6, var8));
         int var12 = var3 + Math.round(var11 / var8 * var10);
         return new CompactScrollbar.Metrics(var2, var3, var14, var5, var12, var9, var8);
      } else {
         return new CompactScrollbar.Metrics(var2, var3, var14, var5, var3, var5, 0);
      }
   }

   public static void draw(GuiGraphicsExtractor var0, CompactScrollbar.Metrics var1, boolean var2, boolean var3) {
      if (var1 != null && var1.hasScroll()) {
         Scrollbar.render(
            UiContexts.overlay(var0, Minecraft.getInstance().font, -1, -1),
            new Scrollbar.Metrics(
               UiBounds.of(var1.trackX(), var1.trackY(), var1.trackWidth(), var1.trackHeight()),
               UiBounds.of(var1.trackX(), var1.thumbY(), var1.trackWidth(), var1.thumbHeight()),
               var1.maxScroll()
            ),
            var2,
            var3
         );
      }
   }

   public static int scrollFromThumb(CompactScrollbar.Metrics var0, double var1, int var3) {
      if (var0 != null && var0.hasScroll()) {
         int var4 = Math.max(1, var0.trackHeight() - var0.thumbHeight());
         int var5 = (int)Math.round(var1) - var3;
         var5 = Math.max(var0.trackY(), Math.min(var0.trackY() + var4, var5));
         float var6 = (float)(var5 - var0.trackY()) / var4;
         return Math.max(0, Math.min(var0.maxScroll(), Math.round(var6 * var0.maxScroll())));
      } else {
         return 0;
      }
   }

   public record Metrics(int trackX, int trackY, int trackWidth, int trackHeight, int thumbY, int thumbHeight, int maxScroll) {
      public boolean hasScroll() {
         return this.maxScroll > 0;
      }

      public boolean contains(double var1, double var3) {
         return var1 >= this.trackX && var1 < this.trackX + this.trackWidth && var3 >= this.trackY && var3 < this.trackY + this.trackHeight;
      }

      public boolean overThumb(double var1, double var3) {
         return this.hasScroll() && var1 >= this.trackX && var1 < this.trackX + this.trackWidth && var3 >= this.thumbY && var3 < this.thumbY + this.thumbHeight;
      }
   }
}
