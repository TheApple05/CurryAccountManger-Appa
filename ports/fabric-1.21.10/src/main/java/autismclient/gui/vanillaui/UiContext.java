package autismclient.gui.vanillaui;

import net.minecraft.client.gui.GuiGraphics;

public final class UiContext {
   private final GuiGraphics graphics;
   private final UiTheme theme;
   private final UiTextRenderer text;
   private final int screenWidth;
   private final int screenHeight;
   private final int mouseX;
   private final int mouseY;
   private final float delta;

   public UiContext(GuiGraphics var1, UiTheme var2, UiTextRenderer var3, int var4, int var5, int var6, int var7, float var8) {
      this.graphics = var1;
      this.theme = var2;
      this.text = var3;
      this.screenWidth = var4;
      this.screenHeight = var5;
      this.mouseX = var6;
      this.mouseY = var7;
      this.delta = var8;
   }

   public GuiGraphics graphics() {
      return this.graphics;
   }

   public UiTheme theme() {
      return this.theme;
   }

   public UiTextRenderer text() {
      return this.text;
   }

   public int screenWidth() {
      return this.screenWidth;
   }

   public int screenHeight() {
      return this.screenHeight;
   }

   public int mouseX() {
      return this.mouseX;
   }

   public int mouseY() {
      return this.mouseY;
   }

   public float delta() {
      return this.delta;
   }
}
