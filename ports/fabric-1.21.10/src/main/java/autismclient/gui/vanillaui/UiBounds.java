package autismclient.gui.vanillaui;

public record UiBounds(int x, int y, int width, int height) {
   public static UiBounds of(int var0, int var1, int var2, int var3) {
      return new UiBounds(var0, var1, Math.max(0, var2), Math.max(0, var3));
   }

   public int right() {
      return this.x + this.width;
   }

   public int bottom() {
      return this.y + this.height;
   }

   public boolean contains(int var1, int var2) {
      return var1 >= this.x && var1 < this.right() && var2 >= this.y && var2 < this.bottom();
   }

   public UiBounds inset(int var1) {
      return this.inset(var1, var1, var1, var1);
   }

   public UiBounds inset(int var1, int var2, int var3, int var4) {
      int var5 = this.x + var1;
      int var6 = this.y + var2;
      return of(var5, var6, this.width - var1 - var3, this.height - var2 - var4);
   }

   public UiBounds clampInside(int var1, int var2) {
      int var3 = Math.min(this.width, Math.max(0, var1));
      int var4 = Math.min(this.height, Math.max(0, var2));
      int var5 = clamp(this.x, 0, Math.max(0, var1 - var3));
      int var6 = clamp(this.y, 0, Math.max(0, var2 - var4));
      return of(var5, var6, var3, var4);
   }

   public UiBounds union(UiBounds var1) {
      if (var1 == null) {
         return this;
      }

      int var2 = Math.min(this.x, var1.x);
      int var3 = Math.min(this.y, var1.y);
      return of(var2, var3, Math.max(this.right(), var1.right()) - var2, Math.max(this.bottom(), var1.bottom()) - var3);
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var2, var0));
   }
}
