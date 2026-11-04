package autismclient.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public final class AutismUiIcons {
   public static final ResourceLocation X = icon("x");
   public static final ResourceLocation DOT = icon("dot");
   public static final ResourceLocation CHECK = icon("check");
   public static final ResourceLocation CHEVRON_DOWN = icon("chevron_down");
   public static final ResourceLocation CHEVRON_UP = icon("chevron_up");
   public static final ResourceLocation CHEVRON_RIGHT = icon("chevron_right");
   public static final ResourceLocation PILL = icon("pill_track");
   public static final ResourceLocation PLAY = icon("play");
   public static final ResourceLocation STOP = icon("stop");
   public static final ResourceLocation TRASH = icon("trash");
   public static final ResourceLocation EDIT = icon("edit");
   public static final ResourceLocation DUPLICATE = icon("duplicate");
   public static final ResourceLocation PLUS = icon("plus");

   private AutismUiIcons() {
   }

   private static ResourceLocation icon(String var0) {
      return ResourceLocation.fromNamespaceAndPath("autismclient", "textures/gui/icons/" + var0 + ".png");
   }

   public static void blit(GuiGraphics var0, ResourceLocation var1, int var2, int var3, int var4, int var5) {
      var0.blit(RenderPipelines.GUI_TEXTURED, var1, var2, var3, 0.0F, 0.0F, var4, var4, 512, 512, 512, 512, var5);
   }

   public static void blit(GuiGraphics var0, ResourceLocation var1, int var2, int var3, int var4, int var5, int var6) {
      var0.blit(RenderPipelines.GUI_TEXTURED, var1, var2, var3, 0.0F, 0.0F, var4, var5, 512, 512, 512, 512, var6);
   }

   public static void blitSliced(GuiGraphics var0, int var1, int var2, int var3, int var4, int var5) {
      if (var3 > 0 && var4 > 0) {
         int var6 = Math.min(var4 / 2, Math.max(1, var3 / 2));
         short var7 = 512;
         RenderPipeline var8 = RenderPipelines.GUI_TEXTURED;
         var0.blit(var8, PILL, var1, var2, 0.0F, 0.0F, var6, var4, var7, 1024, 2048, 1024, var5);
         if (var3 - 2 * var6 > 0) {
            var0.blit(var8, PILL, var1 + var6, var2, var7, 0.0F, var3 - 2 * var6, var4, 2048 - 2 * var7, 1024, 2048, 1024, var5);
         }

         var0.blit(var8, PILL, var1 + var3 - var6, var2, 2048 - var7, 0.0F, var6, var4, var7, 1024, 2048, 1024, var5);
      }
   }
}
