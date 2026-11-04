package autismclient.gui.vanillaui;

import autismclient.util.AutismTheme;

public final class UiColors {
   public int screenScrim;
   public int window;
   public int windowStrong;
   public int header;
   public int headerHover;
   public int row;
   public int rowAlt;
   public int rowHover;
   public int field;
   public int fieldFocused;
   public int border;
   public int borderSoft;
   public int buttonBorder;
   public int hairline;
   public int hoverVeil;
   public int accent;
   public int accentDark;
   public int accentSoft;
   public int success;
   public int successSoft;
   public int text;
   public int muted;
   public int disabled;
   public int bad;

   public UiColors() {
      this.recompute();
   }

   public void recompute() {
      this.screenScrim = AccountPalette.MODAL_SCRIM;
      this.window = AccountPalette.SURFACE;
      this.windowStrong = AccountPalette.SURFACE_RAISED;
      this.header = AccountPalette.DEEP_NAVY;
      this.headerHover = AccountPalette.BOWL_TEAL;
      this.row = AccountPalette.SURFACE;
      this.rowAlt = AccountPalette.SURFACE_RAISED;
      this.rowHover = AccountPalette.ROW_HOVER;
      this.field = AccountPalette.FIELD;
      this.fieldFocused = AccountPalette.FIELD_FOCUSED;
      this.border = AccountPalette.BORDER;
      this.borderSoft = AccountPalette.BORDER;
      this.buttonBorder = AccountPalette.CONTROL_BORDER;
      this.hairline = AccountPalette.BOWL_TEAL;
      this.hoverVeil = 0x22036E99;
      this.accent = AccountPalette.GOLDEN_YELLOW;
      this.accentDark = AccountPalette.ELECTRIC_BLUE;
      this.accentSoft = AccountPalette.SELECTED_ROW;
      this.success = AccountPalette.HERB_GREEN;
      this.successSoft = AccountPalette.HERB_GREEN;
      this.text = AccountPalette.TEXT;
      this.muted = AccountPalette.TEXT_SECONDARY;
      this.disabled = AccountPalette.TEXT_DISABLED;
      this.bad = AccountPalette.SPICE_ORANGE;
   }
}
