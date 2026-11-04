package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismText;
import autismclient.util.AutismTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CompactOverlayButton {
   private static final CompactTheme THEME = new CompactTheme();
   public boolean active = true;
   public boolean visible = true;
   private int x;
   private int y;
   private int width;
   private int height;
   private Component message;
   private CompactOverlayButton.Variant variant = CompactOverlayButton.Variant.SECONDARY;
   private UiTone tone = UiTone.BODY;
   private Boolean toggleState;
   private String animationKey;
   private Identifier icon;
   private Boolean selected;
   private final CompactOverlayButton.PressAction primaryAction;
   private final CompactOverlayButton.PressAction secondaryAction;

   private static Identifier resolvedFont(int var0) {
      return THEME.fontFor(UiTone.BODY);
   }

   private static int fittedWidth(int var0, int var1, Component var2) {
      if (var0 > 0) {
         return var0;
      }

      Minecraft var3 = Minecraft.getInstance();
      Font var4 = var3 == null ? null : var3.font;
      if (var4 == null) {
         return var0;
      }

      String var5 = AutismText.sanitizeUiLabel(var2 == null ? "" : var2.getString());
      Identifier var6 = resolvedFont(var1);
      int var7 = UiSizing.fitTextWidthInt(var4, var5, var6, THEME.color(UiTone.BODY), 4, var0, Integer.MAX_VALUE);
      return Math.max(var0, var7);
   }

   private CompactOverlayButton(
      int var1, int var2, int var3, int var4, Component var5, CompactOverlayButton.PressAction var6, CompactOverlayButton.PressAction var7
   ) {
      this.x = var1;
      this.y = var2;
      this.width = var3;
      this.height = var4;
      this.message = (Component)(var5 == null ? Component.empty() : var5);
      this.primaryAction = var6;
      this.secondaryAction = var7;
   }

   public static CompactOverlayButton create(int var0, int var1, int var2, int var3, Component var4, CompactOverlayButton.PressAction var5) {
      return new CompactOverlayButton(var0, var1, fittedWidth(var2, var3, var4), var3, var4, var5, null);
   }

   public static CompactOverlayButton create(
      int var0, int var1, int var2, int var3, Component var4, CompactOverlayButton.PressAction var5, CompactOverlayButton.PressAction var6
   ) {
      return new CompactOverlayButton(var0, var1, fittedWidth(var2, var3, var4), var3, var4, var5, var6);
   }

   public CompactOverlayButton setTone(UiTone var1) {
      this.tone = var1 == null ? UiTone.BODY : var1;
      return this;
   }

   public CompactOverlayButton setToggleState(boolean var1) {
      this.toggleState = var1;
      return this;
   }

   public CompactOverlayButton setAnimationKey(String var1) {
      this.animationKey = var1;
      return this;
   }

   public CompactOverlayButton setIcon(Identifier var1) {
      this.icon = var1;
      return this;
   }

   public CompactOverlayButton setSelected(boolean var1) {
      this.selected = var1;
      return this;
   }

   public static boolean fireIfHit(CompactOverlayButton var0, double var1, double var3, int var5) {
      if (var0 != null && var0.visible && var0.active) {
         if ((var5 == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || var5 == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) && var0.contains(var1, var3)) {
            CompactOverlayButton.PressAction var6 = var5 == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT ? var0.secondaryAction : var0.primaryAction;
            if (var6 == null) {
               return false;
            }

            var6.onPress(var0);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void renderStyled(GuiGraphicsExtractor var0, Font var1, CompactOverlayButton var2, int var3, int var4) {
      if (var2 != null && var2.visible) {
         if (var2.icon != null) {
            boolean var17 = var2.contains(var3, var4);
            CompactSymbolButton.renderIcon(
               var0,
               var1,
               UiBounds.of(var2.x, var2.y, var2.width, var2.height),
               var2.icon,
               var17,
               var2.active,
               var2.variant == CompactOverlayButton.Variant.DANGER
            );
         } else {
            String var5 = AutismText.sanitizeUiLabel(var2.message.getString());
            String var6 = var5.toUpperCase();
            boolean var7 = "x".equalsIgnoreCase(var5) || var5.toLowerCase().contains("delete") || var5.toLowerCase().contains("clear");
            boolean var8 = var5.startsWith("[x]")
               || var5.startsWith("[X]")
               || var5.startsWith("[✓]")
               || var6.equals("ON")
               || var6.endsWith(": ON")
               || var6.equals("MODE: ENABLE")
               || var6.equals("ENABLE")
               || var6.equals("ENABLED")
               || var6.endsWith(": ENABLED");
            boolean var9 = var5.startsWith("[ ]")
               || var6.equals("OFF")
               || var6.endsWith(": OFF")
               || var6.equals("MODE: DISABLE")
               || var6.equals("DISABLE")
               || var6.equals("DISABLED")
               || var6.endsWith(": DISABLED");
            CompactOverlayButton.Variant var10 = var2.variant;
            if (var10 == CompactOverlayButton.Variant.SECONDARY && var7) {
               var10 = CompactOverlayButton.Variant.DANGER;
            }

            if (var10 == CompactOverlayButton.Variant.SECONDARY && var8) {
               var10 = CompactOverlayButton.Variant.SUCCESS;
            }

            if (var10 == CompactOverlayButton.Variant.SECONDARY && var9) {
               var10 = CompactOverlayButton.Variant.DANGER;
            }

            boolean var11 = var2.contains(var3, var4);
            UiContext var12 = UiContexts.overlay(var0, var1, var3, var4);
            UiBounds var13 = UiBounds.of(var2.x, var2.y, var2.width, var2.height);
            boolean var14 = var2.variant != CompactOverlayButton.Variant.FILTER_ON && var2.variant != CompactOverlayButton.Variant.FILTER_OFF;
            Boolean var15 = var2.toggleState != null ? var2.toggleState : (var14 ? (var8 ? Boolean.TRUE : (var9 ? Boolean.FALSE : null)) : null);
            if (var15 != null) {
               AnimatedToggleButton.render(
                  var12, var13, var5, var15, var11 && var2.active, var2.animationKey == null ? stableVisualKey(var2) : var2.animationKey
               );
            } else {
               boolean var10000;
               if (var2.selected != null) {
                  var10000 = var2.selected;
               } else {
                  switch (var10) {
                     case DANGER:
                     case SUCCESS:
                     case PRIMARY:
                     case FILTER_ON:
                        var10000 = true;
                        break;
                     case GHOST:
                     default:
                        var10000 = false;
                  }
               }

               boolean var16 = var10000;
               Button.render(var12, var13, var5, buttonTone(var10), var11 && var2.active, var16);
            }

            if (!var2.active && !Boolean.TRUE.equals(var2.selected)) {
               UiRenderer.frame(var0, var13, AccountPalette.FIELD, AccountPalette.BORDER);
               var12.text().drawCentered(var0, var5, var13, AccountPalette.TEXT_DISABLED);
            }
         }
      }
   }

   public int getX() {
      return this.x;
   }

   public void setX(int var1) {
      this.x = var1;
   }

   public int getY() {
      return this.y;
   }

   public void setY(int var1) {
      this.y = var1;
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int var1) {
      this.width = var1;
   }

   public int getHeight() {
      return this.height;
   }

   public CompactOverlayButton setVariant(CompactOverlayButton.Variant var1) {
      this.variant = var1 == null ? CompactOverlayButton.Variant.SECONDARY : var1;
      return this;
   }

   private boolean contains(double var1, double var3) {
      return var1 >= this.x && var1 < this.x + this.width && var3 >= this.y && var3 < this.y + this.height;
   }

   private static Button.Tone buttonTone(CompactOverlayButton.Variant var0) {
      return switch (var0) {
         case SECONDARY, GHOST, FILTER_OFF -> Button.Tone.NORMAL;
         case DANGER -> Button.Tone.DANGER;
         case SUCCESS -> Button.Tone.SUCCESS;
         case FILTER_ON -> Button.Tone.SELECTED;
         case PRIMARY -> Button.Tone.PRIMARY;
      };
   }

   private static String stableVisualKey(CompactOverlayButton var0) {
      return "overlay-toggle:" + var0.x + ":" + var0.y + ":" + var0.width + ":" + var0.height + ":" + var0.tone;
   }

   @FunctionalInterface
   public interface PressAction {
      void onPress(CompactOverlayButton var1);
   }

   public enum Variant {
      SECONDARY,
      DANGER,
      SUCCESS,
      PRIMARY,
      GHOST,
      FILTER_ON,
      FILTER_OFF;
   }
}
