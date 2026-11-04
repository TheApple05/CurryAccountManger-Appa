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
      this.screenScrim = 1711276032;
      this.window = -670364656;
      this.windowStrong = -301331955;
      this.header = -266855904;
      this.headerHover = -14407630;
      this.row = -1340992486;
      this.rowAlt = -1810490847;
      this.rowHover = -920837589;
      this.field = -653126631;
      this.fieldFocused = -400941530;
      this.border = AutismTheme.recolor(-3258039, AutismTheme.Channel.OUTLINE);
      this.borderSoft = AutismTheme.recolor(-861980102, AutismTheme.Channel.OUTLINE);
      this.buttonBorder = AutismTheme.recolor(-525836706, AutismTheme.Channel.OUTLINE);
      this.hairline = 738197503;
      this.hoverVeil = 285212671;
      this.accent = AutismTheme.recolor(-50373, AutismTheme.Channel.ACCENT);
      this.accentDark = AutismTheme.recolor(-7397596, AutismTheme.Channel.ACCENT);
      this.accentSoft = AutismTheme.recolor(1157577531, AutismTheme.Channel.ACCENT);
      this.success = AutismTheme.recolor(-12588930, AutismTheme.Channel.SUCCESS);
      this.successSoft = AutismTheme.recolor(1715464318, AutismTheme.Channel.SUCCESS);
      this.text = AutismTheme.recolor(-791321, AutismTheme.Channel.TEXT);
      this.muted = AutismTheme.recolor(-4743522, AutismTheme.Channel.TEXT);
      this.disabled = AutismTheme.recolor(-9016726, AutismTheme.Channel.TEXT);
      this.bad = AutismTheme.recolor(-43691, AutismTheme.Channel.DANGER);
   }
}
