package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class SectionPanel {
   private SectionPanel() {
   }

   public static void renderBody(UiContext var0, UiBounds var1, int var2) {
      UiRenderer.frame(var0.graphics(), var1, var2, var0.theme().colors().borderSoft);
   }
}
