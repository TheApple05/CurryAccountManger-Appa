package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.HoverFades;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiColors;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class AnimatedToggleButton {
   private static final Map<String, AnimatedToggleButton.State> STATES = new HashMap<>();
   private static long lastPruneNanos;

   private AnimatedToggleButton() {
   }

   public static void render(UiContext var0, UiBounds var1, String var2, boolean var3, boolean var4, String var5) {
      long var6 = System.nanoTime();
      AnimatedToggleButton.State var8 = STATES.get(var5);
      if (var8 == null) {
         var8 = new AnimatedToggleButton.State(var3 ? 1.0F : 0.0F, var3, var6);
         STATES.put(var5, var8);
      } else if (var8.targetEnabled != var3) {
         var8.startProgress = var8.progress(var6, 200000000L);
         var8.targetEnabled = var3;
         var8.changedNanos = var6;
      }

      var8.lastSeenNanos = var6;
      renderStateButton(var0, var1, var2, var4, var8.progress(var6, 200000000L));
      pruneIfNeeded(var6);
   }

   private static void renderStateButton(UiContext var0, UiBounds var1, String var2, boolean var3, float var4) {
      UiColors var5 = var0.theme().colors();
      float var6 = clamp01(var4);
      UiRenderer.frame(var0.graphics(), var1, -1071766483, var5.buttonBorder);
      if (var6 > 0.001F) {
         UiRenderer.rect(var0.graphics(), var1.inset(1), UiRenderer.applyAlpha(AutismTheme.recolor(-736144845, AutismTheme.Channel.SUCCESS), var6));
         UiRenderer.outline(var0.graphics(), var1, UiRenderer.applyAlpha(var5.success, var6));
      }

      float var7 = HoverFades.get(HoverFades.key(var1), var3);
      if (var7 > 0.001F) {
         UiRenderer.rect(var0.graphics(), var1.inset(1), Math.round(20.0F * var7) << 24 | 16777215);
      }

      if (var2 != null && !var2.isEmpty()) {
         var0.text().drawCentered(var0.graphics(), var2, var1, var5.text);
      }
   }

   private static void pruneIfNeeded(long var0) {
      if (var0 - lastPruneNanos >= 30000000000L) {
         lastPruneNanos = var0;
         Iterator var2 = STATES.values().iterator();

         while (var2.hasNext()) {
            if (var0 - ((AnimatedToggleButton.State)var2.next()).lastSeenNanos > 30000000000L) {
               var2.remove();
            }
         }
      }
   }

   private static float clamp01(float var0) {
      return Math.max(0.0F, Math.min(1.0F, var0));
   }

   private static final class State {
      private float startProgress;
      private boolean targetEnabled;
      private long changedNanos;
      private long lastSeenNanos;

      private State(float var1, boolean var2, long var3) {
         this.startProgress = var1;
         this.targetEnabled = var2;
         this.changedNanos = var3;
         this.lastSeenNanos = var3;
      }

      private float progress(long var1, long var3) {
         float var5 = Math.min(1.0F, (float)(var1 - this.changedNanos) / (float)Math.max(1L, var3));
         float var6 = var5 * var5 * (3.0F - 2.0F * var5);
         float var7 = this.targetEnabled ? 1.0F : 0.0F;
         return this.startProgress + (var7 - this.startProgress) * var6;
      }
   }
}
