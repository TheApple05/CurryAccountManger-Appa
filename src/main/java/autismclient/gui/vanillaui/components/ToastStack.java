package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ToastStack {
   private final List<ToastStack.ToastEntry> toasts = new CopyOnWriteArrayList<>();
   private volatile boolean maybeVisible;
   private final long lifetimeMs;
   private final float enterMs;
   private final float exitMs;
   private final int maxVisible;
   private final int gap;
   private final int height;

   public ToastStack() {
      this(1800L, 140.0F, 220.0F, 4, 4, 18);
   }

   public ToastStack(long var1, float var3, float var4, int var5, int var6, int var7) {
      this.lifetimeMs = Math.max(1L, var1);
      this.enterMs = Math.max(1.0F, var3);
      this.exitMs = Math.max(1.0F, var4);
      this.maxVisible = Math.max(1, var5);
      this.gap = Math.max(0, var6);
      this.height = Math.max(1, var7);
   }

   public void show(String var1, int var2) {
      if (var1 != null && !var1.isBlank()) {
         long var3 = System.nanoTime();
         this.prune(var3);
         if (this.toasts.size() >= this.maxVisible) {
            this.toasts.remove(0);
         }

         this.toasts.add(new ToastStack.ToastEntry(var1, var3, var2));
         this.maybeVisible = true;
      }
   }

   public boolean hasVisibleToasts() {
      if (!this.maybeVisible) {
         return false;
      } else {
         this.prune(System.nanoTime());
         if (this.toasts.isEmpty()) {
            this.maybeVisible = false;
            return false;
         } else {
            return true;
         }
      }
   }

   public void clear() {
      this.toasts.clear();
      this.maybeVisible = false;
   }

   public void render(UiContext var1, int var2, int var3, int var4) {
      if (var1 != null && var4 > 0) {
         long var5 = System.nanoTime();
         this.prune(var5);
         if (this.toasts.isEmpty()) {
            this.maybeVisible = false;
         } else {
            int var7 = Math.max(1, Math.min(var4, 260));
            byte var8 = 9;
            byte var9 = 10;
            int var10 = Math.max(4, this.height - var9);
            int var11 = Math.max(1, var7 - var8 * 2);
            int var12 = var3;

            for (int var13 = this.toasts.size() - 1; var13 >= 0; var13--) {
               ToastStack.ToastEntry var14 = this.toasts.get(var13);
               float var15 = Math.max(0.0F, (float)(var5 - var14.shownAtNanos()) / 1000000.0F);
               float var16 = Math.min(clamp01(var15 / this.enterMs), clamp01(((float)this.lifetimeMs - var15) / this.exitMs));
               if (!(var16 <= 0.001F)) {
                  List<String> var17 = var1.text().wrapFully(var14.message(), var11);
                  int var18 = 0;

                  for (String var20 : var17) {
                     var18 = Math.max(var18, var1.text().width(var20));
                  }

                  int var33 = Math.min(var7, Math.max(Math.min(48, var7), var18 + var8 * 2));
                  int var34 = var17.size() * var9;
                  int var21 = Math.max(this.height, var34 + var10);
                  int var22 = var2 + Math.max(0, (var4 - var33) / 2);
                  float var23 = clamp01(var15 / this.enterMs);
                  float var24 = 1.0F - (1.0F - var23) * (1.0F - var23) * (1.0F - var23);
                  int var25 = var12 - Math.round((1.0F - var24) * (var21 + this.gap + 2));
                  UiBounds var26 = UiBounds.of(var22, var25, var33, var21);
                  int var27 = tintToward(AccountPalette.DEEP_NAVY & 0xFFFFFF, var14.accentColor(), 0.22F);
                  UiRenderer.roundFrame(
                     var1.graphics(), var26, 5, UiRenderer.applyAlpha(-704643072 | var27, var16), UiRenderer.applyAlpha(var14.accentColor(), var16)
                  );
                  int var28 = UiRenderer.applyAlpha(AccountPalette.TEXT, var16);
                  int var29 = var25 + Math.max(2, (var21 - var34 + 1) / 2 + 1);

                  for (int var30 = 0; var30 < var17.size(); var30++) {
                     String var31 = (String)var17.get(var30);
                     int var32 = var22 + Math.max(var8, (var33 - var1.text().width(var31)) / 2);
                     var1.text().draw(var1.graphics(), var31, var32, var29 + var30 * var9, var28);
                  }

                  var12 += var21 + this.gap;
               }
            }
         }
      }
   }

   private void prune(long var1) {
      this.toasts.removeIf(var3 -> (var1 - var3.shownAtNanos()) / 1000000L >= this.lifetimeMs);
   }

   private static float clamp01(float var0) {
      return Math.max(0.0F, Math.min(1.0F, var0));
   }

   private static int tintToward(int var0, int var1, float var2) {
      int var3 = var0 >> 16 & 0xFF;
      int var4 = var0 >> 8 & 0xFF;
      int var5 = var0 & 0xFF;
      int var6 = var1 >> 16 & 0xFF;
      int var7 = var1 >> 8 & 0xFF;
      int var8 = var1 & 0xFF;
      var3 += Math.round((var6 - var3) * var2);
      var4 += Math.round((var7 - var4) * var2);
      var5 += Math.round((var8 - var5) * var2);
      return var3 << 16 | var4 << 8 | var5;
   }

   private record ToastEntry(String message, long shownAtNanos, int accentColor) {
   }
}
