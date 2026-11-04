package autismclient.gui.vanillaui.direct;

public record DirectUiInsets(int left, int top, int right, int bottom) {
   public static final DirectUiInsets NONE = new DirectUiInsets(0, 0, 0, 0);

   public static DirectUiInsets all(int var0) {
      return new DirectUiInsets(var0, var0, var0, var0);
   }

   public static DirectUiInsets symmetric(int var0, int var1) {
      return new DirectUiInsets(var0, var1, var0, var1);
   }

   public int horizontal() {
      return this.left + this.right;
   }

   public int vertical() {
      return this.top + this.bottom;
   }
}
