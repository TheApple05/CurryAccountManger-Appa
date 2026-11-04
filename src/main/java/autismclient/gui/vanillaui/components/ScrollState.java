package autismclient.gui.vanillaui.components;

public final class ScrollState {
   private int targetOffset = 0;

   public void restore(int var1) {
      this.targetOffset = Math.max(0, var1);
   }

   public void setTarget(int var1, int var2) {
      this.targetOffset = clamp(var1, var2);
   }

   public void jumpTo(int var1, int var2) {
      this.targetOffset = clamp(var1, var2);
   }

   public void nudge(double var1, float var3, int var4) {
      this.setTarget(this.targetOffset - Math.round((float)var1 * var3), var4);
   }

   public void setFromThumb(CompactScrollbar.Metrics var1, double var2, int var4) {
      if (var1 == null) {
         this.jumpTo(0, 0);
      } else {
         this.setTarget(CompactScrollbar.scrollFromThumb(var1, var2, var4), var1.maxScroll());
      }
   }

   public void setFromThumbStepped(CompactScrollbar.Metrics var1, double var2, int var4, int var5) {
      if (var1 == null) {
         this.jumpTo(0, 0);
      } else {
         int var6 = CompactScrollbar.scrollFromThumb(var1, var2, var4);
         int var7 = var6 / var5 * var5;
         this.setTarget(var7, var1.maxScroll());
      }
   }

   public int tick(float var1, int var2) {
      this.targetOffset = clamp(this.targetOffset, var2);
      return this.targetOffset;
   }

   public int targetOffset() {
      return this.targetOffset;
   }

   public int visualOffsetInt() {
      return this.targetOffset;
   }

   private static int clamp(int var0, int var1) {
      return Math.max(0, Math.min(var1, var0));
   }
}
