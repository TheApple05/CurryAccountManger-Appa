package autismclient.gui.vanillaui.components;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class UiText {
   private static final Map<UiText.TrimKey, String> TRIM_CACHE = Collections.synchronizedMap(new LinkedHashMap<UiText.TrimKey, String>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<UiText.TrimKey, String> var1) {
         return this.size() > 2048;
      }
   });
   private static volatile int reloadGeneration;

   public static int reloadGeneration() {
      return reloadGeneration;
   }

   private UiText() {
   }

   public static MutableComponent literal(String var0, ResourceLocation var1, int var2) {
      return Component.literal(var0 == null ? "" : var0).setStyle(Style.EMPTY.withColor(var2));
   }

   public static int width(Font var0, String var1, ResourceLocation var2, int var3) {
      return var0.width(var1 == null ? "" : var1);
   }

   public static int fontHeight(ResourceLocation var0) {
      return 9;
   }

   public static String trimToWidth(Font var0, String var1, int var2, ResourceLocation var3, int var4) {
      String var5 = var1 == null ? "" : var1;
      UiText.TrimKey var6 = new UiText.TrimKey(var5, var2, var3);
      String var7 = TRIM_CACHE.get(var6);
      if (var7 != null) {
         return var7;
      } else if (var5.isEmpty()) {
         return "";
      } else {
         int var9 = var2 + 4;
         if (width(var0, var5, var3, var4) <= var9) {
            TRIM_CACHE.put(var6, var5);
            return var5;
         } else {
            String var8 = var0.plainSubstrByWidth(var5, Math.max(0, var9));
            TRIM_CACHE.put(var6, var8);
            return var8;
         }
      }
   }

   public static String trimToWidthEllipsis(Font var0, String var1, int var2, ResourceLocation var3, int var4) {
      String var5 = var1 == null ? "" : var1;
      if (!var5.isEmpty() && var2 > 0) {
         if (width(var0, var5, var3, var4) <= var2) {
            return var5;
         }

         String var6 = "...";
         int var7 = width(var0, var6, var3, var4);
         if (var2 <= var7) {
            return trimToWidth(var0, var5, var2, var3, var4);
         }

         String var8 = var0.plainSubstrByWidth(var5, Math.max(0, var2 - var7));

         while (!var8.isEmpty() && width(var0, var8 + var6, var3, var4) > var2) {
            var8 = var8.substring(0, var8.length() - 1);
         }

         return var8.isEmpty() ? var6 : var8 + var6;
      } else {
         return "";
      }
   }

   public static void draw(GuiGraphics var0, Font var1, String var2, ResourceLocation var3, int var4, int var5, int var6, boolean var7) {
      var0.drawString(var1, var2 == null ? "" : var2, var5, var6, var4, var7);
   }

   public static void drawFitted(GuiGraphics var0, Font var1, String var2, ResourceLocation var3, int var4, int var5, int var6, int var7, boolean var8) {
      String var9 = var2 == null ? "" : var2;
      if (!var9.isEmpty() && var7 > 0) {
         int var10 = width(var1, var9, var3, var4);
         if (var10 <= var7) {
            draw(var0, var1, var9, var3, var4, var5, var6, var8);
         } else {
            float var11 = Math.min(1.0F, (float)var7 / Math.max(1, var10));
            var0.pose().pushMatrix();

            try {
               var0.pose().scale(var11, var11);
               draw(var0, var1, var9, var3, var4, Math.round(var5 / var11), Math.round(var6 / var11), var8);
            } finally {
               var0.pose().popMatrix();
            }
         }
      }
   }

   public static void fill(GuiGraphics var0, int var1, int var2, int var3, int var4, int var5) {
      var0.fill(var1, var2, var3, var4, var5);
   }

   private record TrimKey(String value, int maxWidth, ResourceLocation fontId) {
   }
}
