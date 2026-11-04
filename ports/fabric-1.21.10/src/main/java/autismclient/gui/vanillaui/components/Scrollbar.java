package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class Scrollbar {
   private Scrollbar() {
   }

   public static Scrollbar.Metrics metrics(UiBounds var0, int var1, int var2, int var3) {
      int var4 = Math.max(0, var1 - Math.max(0, var2));
      if (var4 > 0 && var0.height() > 0) {
         int var5 = Math.max(12, (int)Math.round(var0.height() * ((double)var2 / Math.max(var2, var1))));
         var5 = Math.min(var0.height(), var5);
         int var6 = var0.y() + (int)Math.round((var0.height() - var5) * ((double)var3 / var4));
         return new Scrollbar.Metrics(var0, UiBounds.of(var0.x(), var6, var0.width(), var5), var4);
      } else {
         return new Scrollbar.Metrics(var0, UiBounds.of(var0.x(), var0.y(), var0.width(), var0.height()), 0);
      }
   }

   public static void render(UiContext var0, Scrollbar.Metrics var1, boolean var2, boolean var3) {
      if (var1 != null && var1.maxScroll() > 0) {
         UiRenderer.rect(var0.graphics(), var1.track(), AccountPalette.FIELD);
         UiBounds var4 = var1.thumb();
         boolean var5 = var2 || var3;
         int var6 = var5 ? var4.width() : Math.max(2, var4.width() - 2);
         int var7 = var4.x() + (var4.width() - var6) / 2;
         UiRenderer.rect(var0.graphics(), UiBounds.of(var7, var4.y(), var6, var4.height()), var5 ? AccountPalette.FOCUS : AccountPalette.BORDER);
      }
   }

   public static int scrollFromMouse(Scrollbar.Metrics var0, int var1, int var2) {
      if (var0 != null && var0.maxScroll() > 0) {
         int var3 = Math.max(1, var0.track().height() - var0.thumb().height());
         int var4 = Math.max(0, Math.min(var3, var1 - var2 - var0.track().y()));
         return (int)Math.round(var0.maxScroll() * ((double)var4 / var3));
      } else {
         return 0;
      }
   }

   public record Metrics(UiBounds track, UiBounds thumb, int maxScroll) {
      public boolean overTrack(int var1, int var2) {
         return this.track.contains(var1, var2);
      }

      public boolean overThumb(int var1, int var2) {
         return this.thumb.contains(var1, var2);
      }
   }
}
