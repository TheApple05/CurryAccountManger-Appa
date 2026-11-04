package autismclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class AutismUiScale {
   private static final double[] ALLOWED_OVERLAY_SCALES = new double[]{0.5, 0.85, 1.0, 1.2, 1.5, 2.0};
   private static int overlayScaleDepth = 0;
   private static volatile AutismUiScale.ScreenMetrics metrics;
   private static volatile AutismUiScale.MultiplierCache multiplierCache;

   private AutismUiScale() {
   }

   public static double toVirtual(double var0) {
      return var0 / getOverlayDrawScale();
   }

   public static int toVirtualInt(double var0) {
      double var2 = toVirtual(var0);
      return var2 < 0.0 ? (int)Math.floor(var2) : (int)Math.round(var2);
   }

   private static AutismUiScale.ScreenMetrics currentMetrics(Minecraft var0) {
      int var1 = var0.getWindow().getWidth();
      int var2 = var0.getWindow().getHeight();
      double var3 = getOverlayScaleMultiplier();
      AutismUiScale.ScreenMetrics var5 = metrics;
      if (var5 != null && var5.rawW == var1 && var5.rawH == var2 && var5.multiplier == var3) {
         return var5;
      }

      double var6 = 2.0 * var3;
      int var8 = (int)(var1 / var6);
      if (var1 / var6 > var8) {
         var8++;
      }

      int var9 = (int)(var2 / var6);
      if (var2 / var6 > var9) {
         var9++;
      }

      var5 = new AutismUiScale.ScreenMetrics(var1, var2, var3, var8, var9);
      metrics = var5;
      return var5;
   }

   public static int getVirtualScreenWidth() {
      Minecraft var0 = Minecraft.getInstance();
      return var0 != null && var0.getWindow() != null ? currentMetrics(var0).virtualW : 0;
   }

   public static int getVirtualScreenHeight() {
      Minecraft var0 = Minecraft.getInstance();
      return var0 != null && var0.getWindow() != null ? currentMetrics(var0).virtualH : 0;
   }

   public static float getOverlayDrawScale() {
      Minecraft var0 = Minecraft.getInstance();
      return var0 != null && var0.getWindow() != null && var0.getWindow().getGuiScale() > 0 ? (float)getFixedGuiScale() / var0.getWindow().getGuiScale() : 1.0F;
   }

   public static double getOverlayScaleMultiplier() {
      try {
         double var0 = AutismConfig.getGlobal().overlayScale;
         AutismUiScale.MultiplierCache var2 = multiplierCache;
         if (var2 == null || var2.overlayScale != var0) {
            var2 = new AutismUiScale.MultiplierCache(var0, nearestAllowedOverlayScale(var0));
            multiplierCache = var2;
         }

         return var2.multiplier;
      } catch (Throwable var3) {
         return 1.0;
      }
   }

   public static double nearestAllowedOverlayScale(double var0) {
      if (!(Math.abs(var0 - 0.75) < 1.0E-6) && !(Math.abs(var0 - 0.9) < 1.0E-6)) {
         double var2 = 1.0;
         double var4 = Double.MAX_VALUE;

         for (double var9 : ALLOWED_OVERLAY_SCALES) {
            double var11 = Math.abs(var0 - var9);
            if (var11 < var4) {
               var2 = var9;
               var4 = var11;
            }
         }

         return var2;
      } else {
         return 0.85;
      }
   }

   public static double nextOverlayScaleMultiplier() {
      return adjacentOverlayScaleMultiplier(1);
   }

   public static double previousOverlayScaleMultiplier() {
      return adjacentOverlayScaleMultiplier(-1);
   }

   private static double adjacentOverlayScaleMultiplier(int var0) {
      double var1 = getOverlayScaleMultiplier();

      for (int var3 = 0; var3 < ALLOWED_OVERLAY_SCALES.length; var3++) {
         if (Math.abs(var1 - ALLOWED_OVERLAY_SCALES[var3]) < 1.0E-6) {
            return ALLOWED_OVERLAY_SCALES[Math.floorMod(var3 + var0, ALLOWED_OVERLAY_SCALES.length)];
         }
      }

      return 1.0;
   }

   public static String formatOverlayScale(double var0) {
      double var2 = nearestAllowedOverlayScale(var0);
      return Math.abs(var2 - Math.rint(var2)) < 1.0E-6 ? Integer.toString((int)Math.rint(var2)) + "x" : Double.toString(var2) + "x";
   }

   public static void setOverlayScaleMultiplier(double var0) {
      double var2 = nearestAllowedOverlayScale(var0);
      AutismConfig var4 = AutismConfig.getGlobal();
      if (!(Math.abs(var4.overlayScale - var2) < 1.0E-6)) {
         var4.overlayScale = var2;
         var4.save();


      }
   }

   private static double getFixedGuiScale() {
      return 2.0 * getOverlayScaleMultiplier();
   }

   public static boolean isOverlayScaleActive() {
      return overlayScaleDepth > 0;
   }

   public static void pushOverlayScale(GuiGraphics var0) {
      if (var0 != null) {
         var0.pose().pushMatrix();
         if (overlayScaleDepth == 0) {
            float var1 = getOverlayDrawScale();
            var0.pose().scale(var1, var1);
         }

         overlayScaleDepth++;
      }
   }

   public static void popOverlayScale(GuiGraphics var0) {
      if (var0 != null) {
         if (overlayScaleDepth > 0) {
            overlayScaleDepth--;
         }

         var0.pose().popMatrix();
      }
   }

   public static void enableOverlayScissor(GuiGraphics var0, int var1, int var2, int var3, int var4) {
      if (var0 != null) {
         if (var3 > var1 && var4 > var2) {
            int var5 = overlayScissorExpansionForEndpoint(var1, var3);
            int var6 = overlayScissorExpansionForEndpoint(var2, var4);
            int var7 = Math.max(0, getVirtualScreenWidth());
            int var8 = Math.max(0, getVirtualScreenHeight());
            int var9 = clamp(var1, 0, Math.max(0, var7));
            int var10 = clamp(var2, 0, Math.max(0, var8));
            int var11 = clamp(var3 + var5, var9, Math.max(var9, var7 + var5));
            int var12 = clamp(var4 + var6, var10, Math.max(var10, var8 + var6));
            var0.enableScissor(var9, var10, var11, var12);
         } else {
            var0.enableScissor(var1, var2, var1, var2);
         }
      }
   }

   private static int overlayScissorExpansionForEndpoint(int var0, int var1) {
      int var2 = var1 - var0;
      if (isOverlayScaleActive() && var2 > 0) {
         double var3 = getOverlayDrawScale();
         if (var3 <= 0.0) {
            return 0;
         }

         int var5 = (int)Math.floor(var0 * var3 + 1.0E-6);
         int var6 = (int)Math.ceil(var1 * var3 - 1.0E-6);
         int var7 = Math.max(0, var6 - var5);
         int var8 = (int)Math.floor(var2 * var3 + 1.0E-6);
         if (var8 >= var7) {
            return 0;
         }

         int var9 = (int)Math.ceil((var7 - 1.0E-6) / var3);
         return Math.max(1, var9 - var2);
      } else {
         return 0;
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var0, var2));
   }

   private record MultiplierCache(double overlayScale, double multiplier) {
   }

   private record ScreenMetrics(int rawW, int rawH, double multiplier, int virtualW, int virtualH) {
   }
}
