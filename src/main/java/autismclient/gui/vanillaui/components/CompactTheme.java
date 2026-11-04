package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.direct.DirectUiInsets;
import autismclient.util.AutismTheme;
import net.minecraft.resources.Identifier;

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

   public Identifier fontFor(UiTone var1) {
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
         case TITLE, LABEL, BODY -> rc(-791321, AutismTheme.Channel.TEXT);
         case MUTED -> rc(-4743522, AutismTheme.Channel.TEXT);
         case ACCENT -> rc(-45747, AutismTheme.Channel.ACCENT);
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
      return this.overlaySurface(657932);
   }

   public int windowFillInactive() {
      return this.overlaySurfaceSoft(460553);
   }

   public int headerFill() {
      return this.overlaySurface(1118484);
   }

   public int headerFillInactive() {
      return this.overlaySurfaceSoft(657932);
   }

   public int headerAccent() {
      return rc(-50373, AutismTheme.Channel.HEADER);
   }

   public int borderColor() {
      return rc(-2996152, AutismTheme.Channel.OUTLINE);
   }

   public int borderSoft() {
      return rc(lessTransparent30(-1063699910), AutismTheme.Channel.OUTLINE);
   }

   public int dangerFill() {
      return rc(this.overlaySurface(2756114), AutismTheme.Channel.DANGER);
   }

   public int listFill() {
      return this.overlaySurface(1052692);
   }

   public int listFillFocused() {
      return this.overlaySurfaceStrong(1250328);
   }

   public int rowFillNormal() {
      return 639113758;
   }

   public int rowFillHovered() {
      return rc(1327766045, AutismTheme.Channel.HOVER);
   }

   public int rowFillSelected() {
      return rc(this.overlaySurface(2308139), AutismTheme.Channel.TOGGLE);
   }
}
