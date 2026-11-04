package autismclient.gui.vanillaui;

import autismclient.util.AutismUiScale;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiContexts {
   private static final UiTheme THEME = new UiTheme();
   private static final Map<Font, UiTextRenderer> TEXT_RENDERERS = new IdentityHashMap<>();
   private static volatile UiContexts.FontEntry lastTextRenderer;

   private UiContexts() {
   }

   public static UiContext overlay(GuiGraphicsExtractor var0, Font var1, int var2, int var3) {
      return new UiContext(
         var0,
         THEME,
         textRenderer(var1),
         Math.max(1, AutismUiScale.getVirtualScreenWidth()),
         Math.max(1, AutismUiScale.getVirtualScreenHeight()),
         var2,
         var3,
         0.0F
      );
   }

   public static UiTextRenderer textRenderer(Font var0) {
      UiContexts.FontEntry var1 = lastTextRenderer;
      if (var1 != null && var1.font() == var0) {
         return var1.renderer();
      }

      UiTextRenderer var2;
      synchronized (TEXT_RENDERERS) {
         var2 = TEXT_RENDERERS.computeIfAbsent(var0, UiTextRenderer::new);
      }

      lastTextRenderer = new UiContexts.FontEntry(var0, var2);
      return var2;
   }

   private record FontEntry(Font font, UiTextRenderer renderer) {
   }
}
