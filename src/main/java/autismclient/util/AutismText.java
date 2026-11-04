package autismclient.util;

import autismclient.gui.vanillaui.assets.UiAssets;
import autismclient.gui.vanillaui.components.UiText;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class AutismText {
   private static final Map<Identifier, FontDescription> FONT_CACHE = new ConcurrentHashMap<>();

   private AutismText() {
   }

   public static int colorFor(AutismText.Tone var0) {
      return switch (var0) {
         case TITLE, LABEL, BODY -> AutismColors.textPrimary();
         case MUTED -> AutismColors.textMuted();
         case ACCENT -> AutismColors.accent();
      };
   }

   public static String sanitizeUiLabel(String var0) {
      return var0 != null && !var0.isEmpty()
         ? var0.replace("✓", "X")
            .replace("✔", "X")
            .replace("✕", "X")
            .replace("✖", "X")
            .replace("→", "->")
            .replace("←", "<-")
            .replace("—", "-")
            .replace("–", "-")
            .replace("·", "-")
            .replace("▼", "v")
            .replace("▾", "v")
            .replace("▲", "^")
            .replace("▴", "^")
            .replace("●", "X")
            .replace("○", "O")
            .replace("≡", "=")
            .replace("★", "*")
            .replace("∞", "INF")
            .replace("⚠", "WARN")
            .replace("⚡", "BURST")
            .trim()
         : "";
   }

   public static int width(Font var0, String var1, AutismText.Tone var2) {
      return UiText.width(var0, var1, fontIdFor(var2), colorFor(var2));
   }

   public static String trimToWidth(Font var0, String var1, int var2, AutismText.Tone var3) {
      return UiText.trimToWidth(var0, var1, var2, fontIdFor(var3), colorFor(var3));
   }

   public static void draw(GuiGraphicsExtractor var0, Font var1, String var2, AutismText.Tone var3, int var4, int var5, boolean var6) {
      UiText.draw(var0, var1, var2, fontIdFor(var3), colorFor(var3), var4, var5, var6);
   }

   public static void draw(GuiGraphicsExtractor var0, Font var1, String var2, int var3, int var4, int var5, boolean var6) {
      UiText.draw(var0, var1, var2, UiAssets.FONT_BODY, var3, var4, var5, var6);
   }

   private static Identifier fontIdFor(AutismText.Tone var0) {
      return switch (var0) {
         case TITLE -> UiAssets.FONT_TITLE;
         case LABEL -> UiAssets.FONT_LABEL;
         case BODY, MUTED, ACCENT -> UiAssets.FONT_BODY;
      };
   }

   public enum Tone {
      TITLE,
      LABEL,
      BODY,
      MUTED,
      ACCENT;
   }
}
