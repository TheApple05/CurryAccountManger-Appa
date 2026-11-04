package autismclient.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class AutismUiIcons {
   public static final Identifier X = icon("x");
   public static final Identifier DOT = icon("dot");
   public static final Identifier CHECK = icon("check");
   public static final Identifier CHEVRON_DOWN = icon("chevron_down");
   public static final Identifier CHEVRON_UP = icon("chevron_up");
   public static final Identifier CHEVRON_RIGHT = icon("chevron_right");
   public static final Identifier PILL = icon("pill_track");
   public static final Identifier PLAY = icon("play");
   public static final Identifier STOP = icon("stop");
   public static final Identifier TRASH = icon("trash");
   public static final Identifier EDIT = icon("edit");
   public static final Identifier DUPLICATE = icon("duplicate");
   public static final Identifier PLUS = icon("plus");

   private AutismUiIcons() {
   }

   private static Identifier icon(String var0) {
      return Identifier.fromNamespaceAndPath("autismclient", "textures/gui/icons/" + var0 + ".png");
   }

   public static void blit(GuiGraphicsExtractor var0, Identifier var1, int var2, int var3, int var4, int var5) {
      var0.blit(RenderPipelines.GUI_TEXTURED, var1, var2, var3, 0.0F, 0.0F, var4, var4, 512, 512, 512, 512, var5);
   }

   public static void blit(GuiGraphicsExtractor var0, Identifier var1, int var2, int var3, int var4, int var5, int var6) {
      var0.blit(RenderPipelines.GUI_TEXTURED, var1, var2, var3, 0.0F, 0.0F, var4, var5, 512, 512, 512, 512, var6);
   }

   public static void blitSliced(GuiGraphicsExtractor var0, int var1, int var2, int var3, int var4, int var5) {
      if (var3 > 0 && var4 > 0) {
         int var6 = Math.min(var4 / 2, Math.max(1, var3 / 2));
         short var7 = 512;
         var var8 = RenderPipelines.GUI_TEXTURED;
         var0.blit(var8, PILL, var1, var2, 0.0F, 0.0F, var6, var4, var7, 1024, 2048, 1024, var5);
         if (var3 - 2 * var6 > 0) {
            var0.blit(var8, PILL, var1 + var6, var2, var7, 0.0F, var3 - 2 * var6, var4, 2048 - 2 * var7, 1024, 2048, 1024, var5);
         }

         var0.blit(var8, PILL, var1 + var3 - var6, var2, 2048 - var7, 0.0F, var6, var4, var7, 1024, 2048, 1024, var5);
      }
   }
}
