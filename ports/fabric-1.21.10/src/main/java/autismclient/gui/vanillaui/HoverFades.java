package autismclient.gui.vanillaui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HoverFades {
   private static final Map<String, HoverFades.State> STATES = new HashMap<>();
   private static long lastPrune;

   private HoverFades() {
   }

   public static float get(String var0, boolean var1) {
      long var2 = System.nanoTime();
      HoverFades.State var4 = STATES.get(var0);
      if (var4 == null) {
         var4 = new HoverFades.State(var1 ? 1.0F : 0.0F, var1, var2);
         STATES.put(var0, var4);
         return var4.value;
      }

      if (var4.target != var1) {
         var4.start = var4.value(var2);
         var4.target = var1;
         var4.since = var2;
      }

      var4.lastSeen = var2;
      if (var2 - lastPrune > 30000000000L) {
         lastPrune = var2;
         Iterator var5 = STATES.values().iterator();

         while (var5.hasNext()) {
            if (var2 - ((HoverFades.State)var5.next()).lastSeen > 30000000000L) {
               var5.remove();
            }
         }
      }

      return var4.value(var2);
   }

   public static String key(UiBounds var0) {
      return var0.x() + ":" + var0.y() + ":" + var0.width() + ":" + var0.height();
   }

   private static final class State {
      float start;
      float value;
      boolean target;
      long since;
      long lastSeen;

      State(float var1, boolean var2, long var3) {
         this.value = var1;
         this.start = var1;
         this.target = var2;
         this.since = var3;
         this.lastSeen = var3;
      }

      float value(long var1) {
         float var3 = Math.min(1.0F, (float)(var1 - this.since) / 1.3E8F);
         float var4 = var3 * var3 * (3.0F - 2.0F * var3);
         this.value = this.start + ((this.target ? 1.0F : 0.0F) - this.start) * var4;
         return this.value;
      }
   }
}
