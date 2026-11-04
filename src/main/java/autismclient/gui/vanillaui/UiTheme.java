package autismclient.gui.vanillaui;

public final class UiTheme {
   private final UiColors colors = new UiColors();
   private final UiSpacing spacing = new UiSpacing();
   private final UiTypography typography = new UiTypography();

   public UiColors colors() {
      return this.colors;
   }

   public UiSpacing spacing() {
      return this.spacing;
   }
}
