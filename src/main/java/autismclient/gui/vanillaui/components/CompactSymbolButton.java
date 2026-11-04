package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.HoverFades;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiColors;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismUiIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class CompactSymbolButton {
   private CompactSymbolButton() {
   }

   public static void render(GuiGraphics var0, Font var1, UiBounds var2, String var3, boolean var4, boolean var5, boolean var6) {
      if (var0 != null && var1 != null && var2 != null && var2.width() > 0 && var2.height() > 0) {
         UiContext var7 = UiContexts.overlay(var0, var1, -10000, -10000);
         UiColors var8 = var7.theme().colors();
         int var9 = var6 ? AccountPalette.DANGER_FILL : AccountPalette.SURFACE_RAISED;
         UiRenderer.frame(var0, var2, var9, var4 && var5 ? AccountPalette.HOVER : AccountPalette.BORDER);
         float var10 = HoverFades.get(HoverFades.key(var2), var4 && var5);
         if (var10 > 0.001F) {
            UiRenderer.outline(var0, var2, AccountPalette.HOVER);
         }

         if (!var5) {
            UiRenderer.rect(var0, var2.inset(1), 0x6606070B);
         }

         int var11 = var5 ? var8.text : var8.disabled;
         if ("X".equals(var3)) {
            UiRenderer.cross(var0, var2.inset(3), var11);
         } else if ("^".equals(var3) || "v".equals(var3)) {
            UiBounds var14 = centeredSquare(var2, 3);
            if ("^".equals(var3)) {
               UiRenderer.chevronUp(var0, var14, var11);
            } else {
               UiRenderer.chevron(var0, var14, true, var11);
            }
         } else if ("+".equals(var3)) {
            UiBounds var13 = centeredSquare(var2, 3);
            AutismUiIcons.blit(var0, AutismUiIcons.PLUS, var13.x(), var13.y(), var13.width(), var13.height(), var11);
         } else {
            String var12 = var3 == null ? "" : var3;
            var7.text().drawCentered(var0, var12, var2, var11);
         }
      }
   }

   private static UiBounds centeredSquare(UiBounds var0, int var1) {
      int var2 = Math.max(4, Math.min(var0.width(), var0.height()) - var1 * 2);
      int var3 = Math.min(var0.width(), var0.height());
      if ((var3 - var2 & 1) != 0) {
         var2--;
      }

      return UiBounds.of(var0.x() + (var0.width() - var2) / 2, var0.y() + (var0.height() - var2) / 2, Math.max(1, var2), Math.max(1, var2));
   }

   public static void renderIcon(GuiGraphics var0, Font var1, UiBounds var2, Identifier var3, boolean var4, boolean var5, boolean var6) {
      if (var0 != null && var1 != null && var2 != null && var2.width() > 0 && var2.height() > 0) {
         UiContext var7 = UiContexts.overlay(var0, var1, -10000, -10000);
         UiColors var8 = var7.theme().colors();
         int var9 = var6 ? AccountPalette.DANGER_FILL : AccountPalette.SURFACE_RAISED;
         int var10 = var4 && var5 ? AccountPalette.HOVER : AccountPalette.BORDER;
         UiRenderer.frame(var0, var2, var9, var10);
         float var11 = HoverFades.get(HoverFades.key(var2), var4 && var5);
         if (var11 > 0.001F) {
            UiRenderer.outline(var0, var2, AccountPalette.HOVER);
         }

         if (!var5) {
            UiRenderer.rect(var0, var2.inset(1), 0x6606070B);
         }

         int var12 = var5 ? AccountPalette.TEXT : AccountPalette.TEXT_DISABLED;
         int var13 = Math.max(4, Math.min(var2.width(), var2.height()) - 5);
         int var14 = Math.min(var2.width(), var2.height());
         if ((var14 - var13 & 1) != 0) {
            var13--;
         }

         AutismUiIcons.blit(var0, var3, var2.x() + (var2.width() - var13) / 2, var2.y() + (var2.height() - var13) / 2, var13, var12);
      }
   }

   public static Font minecraftFont() {
      Minecraft var0 = Minecraft.getInstance();
      return var0 == null ? null : var0.font;
   }
}
