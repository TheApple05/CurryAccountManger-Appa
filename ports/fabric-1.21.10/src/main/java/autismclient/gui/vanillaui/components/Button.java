package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.StateFades;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiColors;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismThemeTextures;
import net.minecraft.resources.ResourceLocation;

public final class Button {
   private Button() {
   }

   public static void render(UiContext var0, UiBounds var1, String var2, Button.Tone var3, boolean var4, boolean var5) {
      render(var0, var1, var2, null, var3, var4, var5, StateFades.get(StateFades.key(var1), var5));
   }

   public static void renderIcon(UiContext var0, UiBounds var1, String var2, ResourceLocation var3, Button.Tone var4, boolean var5, boolean var6, float var7) {
      render(var0, var1, var2, var3, var4, var5, var6, var7);
   }

   private static void render(UiContext var0, UiBounds var1, String var2, ResourceLocation var3, Button.Tone var4, boolean var5, boolean var6, float var7) {
      UiColors var8 = var0.theme().colors();
      int background = var4 == Button.Tone.PRIMARY || var4 == Button.Tone.SELECTED
         ? AccountPalette.PRIMARY : AccountPalette.SURFACE;
      int border = var5 ? AccountPalette.HOVER : AccountPalette.BORDER;
      UiRenderer.frame(var0.graphics(), var1, background, border);

      if (var3 == null) {
         var0.text().drawCentered(var0.graphics(), var2, var1, var8.text);
      } else {
         int var18 = Math.min(12, Math.max(8, var1.height() - 5));
         int var13 = var0.text().width(var2);
         int var14 = var18 + 4 + var13;
         int var15 = var1.x() + Math.max(3, (var1.width() - var14) / 2);
         int var16 = var1.y() + Math.max(1, (var1.height() - var18) / 2);
         var0.graphics()
            .blit(AutismThemeTextures.recolored(var3, AutismTheme.Channel.ACCENT), var15, var16, var15 + var18, var16 + var18, 0.0F, 1.0F, 0.0F, 1.0F);
         var0.text()
            .drawFitted(var0.graphics(), var2, var15 + var18 + 4, var0.text().centeredY(var1), Math.max(1, var1.right() - (var15 + var18 + 4) - 4), var8.text);
      }
   }

   public enum Tone {
      NORMAL,
      SECONDARY,
      PRIMARY,
      SELECTED,
      SUCCESS,
      DANGER;
   }
}
