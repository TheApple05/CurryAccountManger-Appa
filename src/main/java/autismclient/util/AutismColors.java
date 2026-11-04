package autismclient.util;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactTheme;
import net.minecraft.client.gui.GuiGraphics;

public class AutismColors {
   private static final CompactTheme PACK_UI_THEME = new CompactTheme();
   private static final int SURFACE = PACK_UI_THEME.windowFill();
   private static final int SURFACE_ALT = PACK_UI_THEME.listFill();
   private static final int SURFACE_HOVER = PACK_UI_THEME.overlaySurface(2103837);
   private static final int HEADER = PACK_UI_THEME.headerFill();
   private static final int HEADER_ACTIVE = PACK_UI_THEME.headerFill();
   private static final int OUTER = PACK_UI_THEME.windowFillInactive();
   private static final int BORDER = PACK_UI_THEME.borderSoft();
   private static final int DANGER_BG = PACK_UI_THEME.dangerFill();
   private static final int SUCCESS_BG = PACK_UI_THEME.overlaySurface(2110506);
   private static final int TOOLTIP = PACK_UI_THEME.overlaySurface(526604);

   public static int lessTransparent30(int var0) {
      return boostAlpha(var0, 11, 10);
   }

   public static int lessTransparent60(int var0) {
      return boostAlpha(var0, 13, 10);
   }

   private static int boostAlpha(int var0, int var1, int var2) {
      int var3 = var0 >>> 24 & 0xFF;
      int var4 = Math.min(255, var3 * var1 / var2);
      return var4 << 24 | var0 & 16777215;
   }

   public static int accent() {
      return AutismTheme.recolor(-5093304, AutismTheme.Channel.ACCENT);
   }

   public static int secondary() {
      return SURFACE_ALT;
   }

   public static int sectionHeaderBg() {
      return lessTransparent30(857679146);
   }

   public static int dangerText() {
      return AutismTheme.recolor(-1938838, AutismTheme.Channel.DANGER);
   }

   public static int successText() {
      return AutismTheme.recolor(-11018872, AutismTheme.Channel.SUCCESS);
   }

   public static int packetGreen() {
      return -8592483;
   }

   public static int packetPink() {
      return -27948;
   }

   public static int packetOrange() {
      return -19091;
   }

   public static int packetBlue() {
      return -8340993;
   }

   public static int packetYellow() {
      return -10134;
   }

   public static int packetCyan() {
      return -9115393;
   }

   public static int packetWhite() {
      return -461586;
   }

   public static int packetGray() {
      return -5195582;
   }

   public static int packetLightYellow() {
      return -3921;
   }

   public static int listBg() {
      return SURFACE_ALT;
   }

   public static int rowNormal() {
      return PACK_UI_THEME.rowFillNormal();
   }

   public static int rowHover() {
      return PACK_UI_THEME.rowFillHovered();
   }

   public static int rowSelectedText() {
      return AutismTheme.recolor(-2828, AutismTheme.Channel.TEXT);
   }

   public static int packetRowBg(boolean var0, int var1, boolean var2) {
      if (var2) {
         return var0 ? lessTransparent30(1145068152) : lessTransparent30(1146173989);
      } else if (var0) {
         return (var1 & 1) == 0 ? lessTransparent30(539243587) : lessTransparent30(405355858);
      } else {
         return (var1 & 1) == 0 ? lessTransparent30(539632411) : lessTransparent30(405939741);
      }
   }

   public static int packetRowText(boolean var0, int var1) {
      if (var0) {
         return (var1 & 1) == 0 ? -6496513 : -8861448;
      } else {
         return (var1 & 1) == 0 ? -14193 : -20889;
      }
   }

   public static int packetRowSelectedBg(boolean var0) {
      return var0 ? PACK_UI_THEME.overlaySurface(5413731) : PACK_UI_THEME.overlaySurfaceSoft(4225104);
   }

   public static int packetRowSelectedText() {
      return -4787259;
   }

   public static int packetRowSelectedAccent() {
      return -9184882;
   }

   public static int packetRowBlockedBg(boolean var0) {
      return var0 ? lessTransparent30(-2000670660) : lessTransparent30(1721245482);
   }

   public static int packetRowBlockedAccent() {
      return -1549714;
   }

   public static int packetRowDivider() {
      return lessTransparent30(822083583);
   }

   public static int textPrimary() {
      return AutismTheme.recolor(-791058, AutismTheme.Channel.TEXT);
   }

   public static int textSecondary() {
      return AutismTheme.recolor(-3361096, AutismTheme.Channel.TEXT);
   }

   public static int textDim() {
      return AutismTheme.recolor(-6388344, AutismTheme.Channel.TEXT);
   }

   public static int textMuted() {
      return AutismTheme.recolor(-6388344, AutismTheme.Channel.TEXT);
   }

   public static int textLight() {
      return AutismTheme.recolor(-791058, AutismTheme.Channel.TEXT);
   }

   public static int subPanelBorder() {
      return PACK_UI_THEME.borderSoft();
   }

   public static int popupHover() {
      return lessTransparent60(822083583);
   }

   public static void drawInsetPanel(GuiGraphics var0, int var1, int var2, int var3, int var4, boolean var5) {
      UiRenderer.frame(var0, UiBounds.of(var1, var2, var3, var4), listBg(), var5 ? accent() : subPanelBorder());
   }
}
