package autismclient.gui.vanillaui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiTextRenderer {
   private final Font font;
   private final Map<String, Integer> widthCache = new LinkedHashMap<String, Integer>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, Integer> var1) {
         return this.size() > 2048;
      }
   };
   private final Map<UiTextRenderer.TrimKey, String> trimCache = new LinkedHashMap<UiTextRenderer.TrimKey, String>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<UiTextRenderer.TrimKey, String> var1) {
         return this.size() > 2048;
      }
   };
   private final Map<UiTextRenderer.WrapKey, List<String>> wrapCache = new LinkedHashMap<UiTextRenderer.WrapKey, List<String>>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<UiTextRenderer.WrapKey, List<String>> var1) {
         return this.size() > 2048;
      }
   };

   public UiTextRenderer(Font var1) {
      this.font = var1;
   }

   public Font font() {
      return this.font;
   }

   public int width(String var1) {
      String var2 = var1 == null ? "" : var1;
      Integer var3 = this.widthCache.get(var2);
      if (var3 != null) {
         return var3;
      }

      int var4 = this.font.width(var2);
      this.widthCache.put(var2, var4);
      return var4;
   }

   public String trim(String var1, int var2) {
      String var3 = var1 == null ? "" : var1;
      if (var2 <= 0) {
         return "";
      }

      if (this.width(var3) <= var2) {
         return var3;
      }

      UiTextRenderer.TrimKey var4 = new UiTextRenderer.TrimKey(var3, var2);
      String var5 = this.trimCache.get(var4);
      if (var5 != null) {
         return var5;
      }

      String var6 = this.font.plainSubstrByWidth(var3, var2);
      this.trimCache.put(var4, var6);
      return var6;
   }

   public String trimEllipsis(String var1, int var2) {
      String var3 = var1 == null ? "" : var1;
      if (var2 > 0 && !var3.isEmpty()) {
         if (this.width(var3) <= var2) {
            return var3;
         }

         String var4 = "...";
         int var5 = this.width(var4);
         if (var2 <= var5) {
            return this.trim(var3, var2);
         }

         String var6 = this.font.plainSubstrByWidth(var3, Math.max(0, var2 - var5));

         while (!var6.isEmpty() && this.width(var6 + var4) > var2) {
            var6 = var6.substring(0, var6.length() - 1);
         }

         return var6.isEmpty() ? var4 : var6 + var4;
      } else {
         return "";
      }
   }

   public void draw(GuiGraphics var1, String var2, int var3, int var4, int var5) {
      var1.drawString(this.font, var2 == null ? "" : var2, var3, var4, var5, false);
   }

   public void drawFitted(GuiGraphics var1, String var2, int var3, int var4, int var5, int var6) {
      this.drawFitted(var1, var2, var3, var4, var5, var6, 0.75F);
   }

   public void drawFitted(GuiGraphics var1, String var2, int var3, int var4, int var5, int var6, float var7) {
      String var8 = var2 == null ? "" : var2;
      if (!var8.isEmpty() && var5 > 0) {
         int var9 = this.width(var8);
         if (var9 <= var5) {
            this.draw(var1, var8, var3, var4, var6);
         } else {
            float var10 = Math.min(1.0F, (float)var5 / Math.max(1, var9));
            float var11 = var10;
            var1.pose().pushMatrix();

            try {
               var1.pose().scale(var11, var11);
               this.draw(var1, var8, Math.round(var3 / var11), Math.round(var4 / var11), var6);
            } finally {
               var1.pose().popMatrix();
            }
         }
      }
   }

   public void drawEllipsized(GuiGraphics var1, String var2, int var3, int var4, int var5, int var6) {
      String var7 = this.trimEllipsis(var2, var5);
      if (!var7.isEmpty()) {
         this.draw(var1, var7, var3, var4, var6);
      }
   }

   public List<String> wrapFully(String var1, int var2) {
      String var3 = var1 == null ? "" : var1;
      int var4 = Math.max(1, var2);
      UiTextRenderer.WrapKey var5 = new UiTextRenderer.WrapKey(var3, var4);
      List var6 = this.wrapCache.get(var5);
      if (var6 != null) {
         return var6;
      }

      ArrayList var7 = new ArrayList();

      for (TextWrapLayout.Line var9 : TextWrapLayout.layout(var3, var4, (var2x, var3x) -> this.width(var3.substring(var2x, var3x)))) {
         var7.add(var3.substring(var9.start(), var9.renderEnd()));
      }

      if (var7.isEmpty()) {
         var7.add("");
      }

      List var10 = List.copyOf(var7);
      this.wrapCache.put(var5, var10);
      return var10;
   }

   public void drawCentered(GuiGraphics var1, String var2, UiBounds var3, int var4) {
      this.drawCentered(var1, var2, var3, var4, 0);
   }

   public void drawCentered(GuiGraphics var1, String var2, UiBounds var3, int var4, int var5) {
      String var6 = var2 == null ? "" : var2;
      int var7 = Math.max(0, var3.width() - 4);
      float var8 = this.width(var6) <= var7 ? 1.0F : Math.min(1.0F, (float)var7 / Math.max(1, this.width(var6)));
      int var9 = Math.round(this.width(var6) * var8);
      int var10 = var3.x() + Math.max(2, (var3.width() - var9 + 1) / 2);
      int var11 = var3.x() + 1;
      int var12 = Math.max(var11, var3.right() - var9 - 1);
      int var13 = Math.max(var11, Math.min(var12, var10 + var5));
      int var14 = this.centeredY(var3);
      this.drawFitted(var1, var6, var13, var14, var7, var4);
   }

   public int centeredY(UiBounds var1) {
      int var2 = Math.max(0, var1.height() - 9);
      return var1.y() + Math.max(1, (var2 + 1) / 2 + 1);
   }

   private record TrimKey(String text, int maxWidth) {
      private TrimKey(String text, int maxWidth) {
         Objects.requireNonNull(text);
         this.text = text;
         this.maxWidth = maxWidth;
      }
   }

   private record WrapKey(String text, int maxWidth) {
      private WrapKey(String text, int maxWidth) {
         Objects.requireNonNull(text);
         this.text = text;
         this.maxWidth = maxWidth;
      }
   }
}
