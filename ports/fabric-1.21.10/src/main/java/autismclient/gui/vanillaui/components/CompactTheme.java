package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.util.AutismTheme;
import net.minecraft.resources.ResourceLocation;

public final class CompactTheme {
   private final int windowRadius = 0;

   private static int rc(int var0, AutismTheme.Channel var1) {
      return AutismTheme.recolor(var0, var1);
   }

   public static int lessTransparent30(int var0) {
      return boostAlpha(var0, 13, 10);
   }

   private static int boostAlpha(int var0, int var1, int var2) {
      int var3 = var0 >>> 24 & 0xFF;
      int var4 = Math.min(255, var3 * var1 / var2);
      return var4 << 24 | var0 & 16777215;
   }

   public ResourceLocation fontFor(UiTone var1) {
      return switch (var1) {
         case TITLE -> UiAssets.FONT_TITLE;
         case LABEL -> UiAssets.FONT_LABEL;
         case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
      };
   }

   public int headerHeight() {
      return Math.max(16, this.lineHeight(UiTone.LABEL, 3));
   }

   public int buttonHeight() {
      return Math.max(14, this.lineHeight(UiTone.BODY, 3));
   }

   public int scale(int var1) {
      return var1;
   }

   public float scale(float var1) {
      return var1;
   }

   public DirectUiInsets scale(DirectUiInsets var1) {
      return var1 == null
         ? DirectUiInsets.NONE
         : new DirectUiInsets(this.scale(var1.left()), this.scale(var1.top()), this.scale(var1.right()), this.scale(var1.bottom()));
   }

   public int fontHeight(UiTone var1) {
      int var2 = UiText.fontHeight(this.fontFor(var1));
      if (var2 > 0) {
         return var2;
      }

      return switch (var1) {
         case TITLE -> 13;
         case LABEL -> 12;
         case BODY, MUTED, ACCENT -> 11;
      };
   }

   public int lineHeight(UiTone var1, int var2) {
      byte var3 = switch (var1) {
         case TITLE -> 3;
         case LABEL -> 2;
         case BODY, MUTED, ACCENT -> 2;
      };
      return this.fontHeight(var1) + Math.max(this.scale(var2), var3);
   }

   public int bodyTextNudge() {
      return 1;
   }

   public int fieldTextNudge() {
      return 1;
   }

   public int color(UiTone var1) {
      return switch (var1) {
         case TITLE, LABEL, BODY -> AccountPalette.TEXT;
         case MUTED -> AccountPalette.TEXT_SECONDARY;
         case ACCENT -> AccountPalette.GOLDEN_YELLOW;
      };
   }

   public int overlaySurface(int var1) {
      return -1476395008 | var1 & 16777215;
   }

   public int overlaySurfaceSoft(int var1) {
      return -1610612736 | var1 & 16777215;
   }

   public int overlaySurfaceStrong(int var1) {
      return -1375731712 | var1 & 16777215;
   }

   public int windowFill() {
      return AccountPalette.SURFACE;
   }

   public int windowFillInactive() {
      return AccountPalette.BACKGROUND;
   }

   public int headerFill() {
      return AccountPalette.DEEP_NAVY;
   }

   public int headerFillInactive() {
      return AccountPalette.SURFACE;
   }

   public int headerAccent() {
      return AccountPalette.GOLDEN_YELLOW;
   }

   public int borderColor() {
      return AccountPalette.BORDER;
   }

   public int borderSoft() {
      return AccountPalette.BORDER;
   }

   public int dangerFill() {
      return AccountPalette.DANGER_FILL;
   }

   public int listFill() {
      return AccountPalette.SURFACE;
   }

   public int listFillFocused() {
      return AccountPalette.FIELD_FOCUSED;
   }

   public int rowFillNormal() {
      return AccountPalette.SURFACE;
   }

   public int rowFillHovered() {
      return AccountPalette.ROW_HOVER;
   }

   public int rowFillSelected() {
      return AccountPalette.SELECTED_ROW;
   }
}
