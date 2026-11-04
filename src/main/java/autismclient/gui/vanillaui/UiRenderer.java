package autismclient.gui.vanillaui;

import autismclient.util.AutismUiIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiRenderer {
   private UiRenderer() {
   }

   public static void rect(GuiGraphicsExtractor var0, UiBounds var1, int var2) {
      if (var1.width() > 0 && var1.height() > 0) {
         var0.fill(var1.x(), var1.y(), var1.right(), var1.bottom(), var2);
      }
   }

   public static void horizontalEdge(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4) {
      if (var3 > 0) {
         rect(var0, UiBounds.of(var1, var2, var3, 1), var4);
      }
   }

   public static void verticalEdge(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4) {
      if (var3 > 0) {
         rect(var0, UiBounds.of(var1, var2, 1, var3), var4);
      }
   }

   public static void outline(GuiGraphicsExtractor var0, UiBounds var1, int var2) {
      if (var1.width() > 0 && var1.height() > 0) {
         horizontalEdge(var0, var1.x(), var1.y(), var1.width(), var2);
         if (var1.height() > 1) {
            horizontalEdge(var0, var1.x(), var1.bottom() - 1, var1.width(), var2);
         }

         if (var1.height() > 2) {
            verticalEdge(var0, var1.x(), var1.y() + 1, var1.height() - 2, var2);
            if (var1.width() > 1) {
               verticalEdge(var0, var1.right() - 1, var1.y() + 1, var1.height() - 2, var2);
            }
         }
      }
   }

   public static void frame(GuiGraphicsExtractor var0, UiBounds var1, int var2, int var3) {
      rect(var0, var1, var2);
      outline(var0, var1, var3);
   }

   public static void popup(GuiGraphicsExtractor var0, UiBounds var1, int var2, int var3, int var4) {
      frame(var0, var1, var2, var3);
      horizontalEdge(var0, var1.x(), var1.y(), var1.width(), var4);
   }

   public static int applyAlpha(int var0, float var1) {
      int var2 = var0 >>> 24 & 0xFF;
      int var3 = Math.max(0, Math.min(255, Math.round(var2 * Math.max(0.0F, Math.min(1.0F, var1)))));
      return var3 << 24 | var0 & 16777215;
   }

   public static void cross(GuiGraphicsExtractor var0, UiBounds var1, int var2) {
      AutismUiIcons.blit(var0, AutismUiIcons.X, var1.x(), var1.y(), var1.width(), var1.height(), var2);
   }

   public static void roundRect(GuiGraphicsExtractor var0, UiBounds var1, int var2, int var3) {
      if (var1.width() > 0 && var1.height() > 0) {
         if (var2 <= 0) {
            rect(var0, var1, var3);
         } else {
            AutismUiIcons.blitSliced(var0, var1.x(), var1.y(), var1.width(), var1.height(), var3);
         }
      }
   }

   public static void disc(GuiGraphicsExtractor var0, float var1, float var2, float var3, int var4) {
      int var5 = Math.max(1, Math.round(var3 * 2.0F));
      AutismUiIcons.blit(var0, AutismUiIcons.DOT, Math.round(var1 - var3), Math.round(var2 - var3), var5, var5, var4);
   }

   public static void roundFrame(GuiGraphicsExtractor var0, UiBounds var1, int var2, int var3, int var4) {
      roundRect(var0, var1, var2, var4);
      roundRect(var0, var1.inset(1), Math.max(0, var2 - 1), var3);
   }

   public static void chevron(GuiGraphicsExtractor var0, UiBounds var1, boolean var2, int var3) {
      AutismUiIcons.blit(var0, var2 ? AutismUiIcons.CHEVRON_DOWN : AutismUiIcons.CHEVRON_RIGHT, var1.x(), var1.y(), var1.width(), var1.height(), var3);
   }

   public static void chevronUp(GuiGraphicsExtractor var0, UiBounds var1, int var2) {
      AutismUiIcons.blit(var0, AutismUiIcons.CHEVRON_UP, var1.x(), var1.y(), var1.width(), var1.height(), var2);
   }

   public static void play(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4) {
      for (int var5 = 0; var5 < var3; var5++) {
         int var6 = Math.min(var5 + 1, var3 - var5);
         var0.fill(var1, var2 + var5, var1 + var6, var2 + var5 + 1, var4);
      }
   }

   public static void check(GuiGraphicsExtractor var0, UiBounds var1, int var2) {
      AutismUiIcons.blit(var0, AutismUiIcons.CHECK, var1.x(), var1.y(), var1.width(), var1.height(), var2);
   }
}
