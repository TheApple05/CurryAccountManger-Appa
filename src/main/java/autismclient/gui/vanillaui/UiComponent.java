package autismclient.gui.vanillaui;

public interface UiComponent {
   UiBounds bounds();

   void setBounds(UiBounds var1);

   default UiBounds hitBounds() {
      return this.bounds();
   }

   default void render(UiContext var1) {
   }

   default UiInputResult mouseClicked(int var1, int var2, int var3) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseReleased(int var1, int var2, int var3) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseDragged(int var1, int var2, int var3, double var4, double var6) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult mouseScrolled(int var1, int var2, double var3) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult keyPressed(int var1, int var2, int var3) {
      return UiInputResult.IGNORED;
   }

   default UiInputResult charTyped(char var1) {
      return UiInputResult.IGNORED;
   }
}
