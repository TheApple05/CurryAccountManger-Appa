package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.HoverFades;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiColors;
import autismclient.gui.vanillaui.UiComponent;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismUiScale;
import java.util.List;
import java.util.function.Consumer;

public final class Dropdown implements UiComponent {
   private UiBounds bounds;
   private UiBounds menuBounds = UiBounds.of(0, 0, 0, 0);
   private List<String> options;
   private String selected;
   private final Consumer<String> onSelect;
   private int scroll;
   private int visibleRows;
   private boolean open;
   private long openedAtNanos;
   private boolean draggingScrollbar;
   private int scrollbarGrabOffset;
   private Scrollbar.Metrics scrollbar;
   private boolean menuWidthDirty = true;
   private int cachedMenuWidth = 1;
   private int cachedMenuScreenWidth = -1;
   private int lastScreenWidth = -1;
   private int lastScreenHeight = -1;
   private int lastRowHeight = -1;

   private static int lerpColor(int var0, int var1, float var2) {
      int var3 = var0 >> 16 & 0xFF;
      int var4 = var0 >> 8 & 0xFF;
      int var5 = var0 & 0xFF;
      int var6 = var0 >>> 24 & 0xFF;
      int var7 = var1 >> 16 & 0xFF;
      int var8 = var1 >> 8 & 0xFF;
      int var9 = var1 & 0xFF;
      int var10 = var1 >>> 24 & 0xFF;
      int var11 = Math.round(var3 + (var7 - var3) * var2);
      int var12 = Math.round(var4 + (var8 - var4) * var2);
      int var13 = Math.round(var5 + (var9 - var5) * var2);
      int var14 = Math.round(var6 + (var10 - var6) * var2);
      return var14 << 24 | var11 << 16 | var12 << 8 | var13;
   }

   public Dropdown(UiBounds var1, List<String> var2, String var3, Consumer<String> var4) {
      this.bounds = var1 == null ? UiBounds.of(0, 0, 0, 0) : var1;
      this.options = var2 == null ? List.of() : List.copyOf(var2);
      this.selected = var3 == null ? "" : var3;
      this.onSelect = var4 == null ? var0 -> {} : var4;
   }

   @Override
   public UiBounds bounds() {
      return this.bounds;
   }

   @Override
   public UiBounds hitBounds() {
      return this.open ? this.bounds.union(this.menuBounds) : this.bounds;
   }

   @Override
   public void setBounds(UiBounds var1) {
      if (var1 != null) {
         if (this.bounds.width() != var1.width()) {
            this.menuWidthDirty = true;
         }

         this.bounds = var1;
      }
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOptions(List<String> var1) {
      List var2 = var1 == null ? List.of() : List.copyOf(var1);
      if (!this.options.equals(var2)) {
         this.options = var2;
         this.menuWidthDirty = true;
         if (!this.options.contains(this.selected)) {
            this.selected = this.options.isEmpty() ? "" : this.options.get(0);
         }

         this.scroll = clamp(this.scroll, 0, this.maxScroll());
         this.ensureSelectedVisible();
      }
   }

   public void setSelected(String var1) {
      this.selected = var1 == null ? "" : var1;
      this.ensureSelectedVisible();
   }

   public boolean containsMenu(int var1, int var2) {
      this.prepareInputLayout();
      return this.open && this.menuBounds.contains(var1, var2);
   }

   public void open() {
      this.open = true;
      this.openedAtNanos = System.nanoTime();
      this.ensureSelectedVisible();
      this.prepareInputLayout();
   }

   public void close() {
      this.open = false;
      this.openedAtNanos = 0L;
      this.draggingScrollbar = false;
   }

   public static void renderControl(UiContext var0, UiBounds var1, String var2, boolean var3, boolean var4) {
      renderControl(var0, var1, var2, var3, var4, false, true);
   }

   public static void renderControl(UiContext var0, UiBounds var1, String var2, boolean var3, boolean var4, boolean var5) {
      renderControl(var0, var1, var2, var3, var4, false, var5);
   }

   private static void renderControl(UiContext var0, UiBounds var1, String var2, boolean var3, boolean var4, boolean var5, boolean var6) {
      UiColors var7 = var0.theme().colors();
      int var8 = !var6 ? var7.field : (var4 ? var7.fieldFocused : (var3 ? var7.rowHover : var7.field));
      UiRenderer.frame(var0.graphics(), var1, var8, var4 && var6 ? var7.accent : var7.borderSoft);
      int var9 = var6 ? var7.text : var7.disabled;
      if (var5) {
         var0.text().drawCentered(var0.graphics(), var2, var1, var9);
      } else {
         var0.text().drawFitted(var0.graphics(), var2, var1.x() + 5, var0.text().centeredY(var1), Math.max(1, var1.width() - 18), var9);
      }

      UiRenderer.chevron(var0.graphics(), UiBounds.of(var1.right() - 10, var1.y() + Math.max(0, (var1.height() - 8) / 2), 8, 8), var4, var9);
   }

   @Override
   public void render(UiContext var1) {
      if (this.open && !this.options.isEmpty()) {
         byte var2 = 15;
         this.lastScreenWidth = var1.screenWidth();
         this.lastScreenHeight = var1.screenHeight();
         this.lastRowHeight = var2;
         this.visibleRows = this.visibleRows(var1.screenHeight(), var2);
         int var3 = this.menuWidth(var1);
         this.layoutMenu(var1.screenWidth(), var1.screenHeight(), var2, var3);
         UiColors var4 = var1.theme().colors();
         float var5 = this.openedAtNanos == 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, (float)(System.nanoTime() - this.openedAtNanos) / 7.0E7F));
         UiRenderer.frame(var1.graphics(), this.menuBounds, UiRenderer.applyAlpha(var4.windowStrong, var5), UiRenderer.applyAlpha(var4.borderSoft, var5));
         int var6 = this.options.size() > this.visibleRows ? this.menuBounds.width() - 6 - 3 : this.menuBounds.width() - 2;

         for (int var7 = 0; var7 < this.visibleRows; var7++) {
            int var8 = this.scroll + var7;
            if (var8 >= this.options.size()) {
               break;
            }

            String var9 = this.options.get(var8);
            UiBounds var10 = UiBounds.of(this.menuBounds.x() + 1, this.menuBounds.y() + 1 + var7 * var2, Math.max(1, var6), var2);
            boolean var11 = var10.contains(var1.mouseX(), var1.mouseY());
            boolean var12 = var9.equals(this.selected);
            float var13 = HoverFades.get("dropdown:" + var9, var11);
            int var14 = var12 ? var4.accentSoft : lerpColor(var4.row, var4.rowHover, var13);
            UiRenderer.rect(var1.graphics(), var10, UiRenderer.applyAlpha(var14, var5));
            var1.text()
               .drawFitted(
                  var1.graphics(), var9, var10.x() + 4, var1.text().centeredY(var10), Math.max(1, var10.width() - 18), UiRenderer.applyAlpha(var4.text, var5)
               );
            if (var12) {
               UiRenderer.check(
                  var1.graphics(),
                  UiBounds.of(var10.right() - 11, var10.y() + Math.max(0, (var10.height() - 8) / 2), 8, 8),
                  UiRenderer.applyAlpha(var4.accent, var5)
               );
            }
         }

         if (this.options.size() > this.visibleRows) {
            UiBounds var15 = UiBounds.of(this.menuBounds.right() - 6 - 1, this.menuBounds.y() + 1, 6, Math.max(1, this.menuBounds.height() - 2));
            this.scrollbar = Scrollbar.metrics(var15, this.options.size() * var2, this.visibleRows * var2, this.scroll * var2);
            Scrollbar.render(var1, this.scrollbar, this.scrollbar.track().contains(var1.mouseX(), var1.mouseY()), this.draggingScrollbar);
         } else {
            this.scrollbar = null;
         }
      }
   }

   @Override
   public UiInputResult mouseClicked(int var1, int var2, int var3) {
      this.prepareInputLayout();
      if (var3 != 0) {
         if (!this.open) {
            return UiInputResult.IGNORED;
         }

         if (!this.bounds.contains(var1, var2) && !this.menuBounds.contains(var1, var2)) {
            this.close();
         }

         return UiInputResult.HANDLED;
      } else if (this.open) {
         if (this.menuBounds.contains(var1, var2)) {
            if (this.scrollbar != null && this.scrollbar.track().contains(var1, var2)) {
               this.scrollbarGrabOffset = this.scrollbar.thumb().contains(var1, var2) ? var2 - this.scrollbar.thumb().y() : this.scrollbar.thumb().height() / 2;
               this.draggingScrollbar = true;
               this.updateScrollbar(var2);
               return UiInputResult.HANDLED;
            }

            int var4 = this.scroll + Math.max(0, (var2 - this.menuBounds.y() - 1) / Math.max(1, this.rowHeight()));
            if (var4 >= 0 && var4 < this.options.size()) {
               this.selected = this.options.get(var4);
               this.onSelect.accept(this.selected);
            }

            this.close();
            return UiInputResult.HANDLED;
         } else if (this.bounds.contains(var1, var2)) {
            this.close();
            return UiInputResult.HANDLED;
         } else {
            this.close();
            return UiInputResult.HANDLED;
         }
      } else if (this.bounds.contains(var1, var2)) {
         this.open();
         return UiInputResult.HANDLED;
      } else {
         return UiInputResult.IGNORED;
      }
   }

   @Override
   public UiInputResult mouseReleased(int var1, int var2, int var3) {
      this.prepareInputLayout();
      if (!this.draggingScrollbar) {
         return this.open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
      }

      this.draggingScrollbar = false;
      return UiInputResult.HANDLED;
   }

   @Override
   public UiInputResult mouseDragged(int var1, int var2, int var3, double var4, double var6) {
      this.prepareInputLayout();
      if (this.draggingScrollbar && var3 == 0) {
         this.updateScrollbar(var2);
         return UiInputResult.HANDLED;
      } else {
         return this.open ? UiInputResult.HANDLED : UiInputResult.IGNORED;
      }
   }

   @Override
   public UiInputResult mouseScrolled(int var1, int var2, double var3) {
      this.prepareInputLayout();
      if (!this.open) {
         return UiInputResult.IGNORED;
      }

      if (this.menuBounds.contains(var1, var2)) {
         this.scroll = clamp(this.scroll + (var3 < 0.0 ? 1 : -1), 0, this.maxScroll());
      }

      return UiInputResult.HANDLED;
   }

   private int menuWidth(UiContext var1) {
      if (!this.menuWidthDirty && this.cachedMenuScreenWidth == var1.screenWidth()) {
         return this.cachedMenuWidth;
      }

      int var2 = Math.max(1, this.bounds.width());

      for (String var4 : this.options) {
         var2 = Math.max(var2, var1.text().width(var4) + 12);
      }

      this.cachedMenuWidth = Math.min(var2, Math.max(1, var1.screenWidth() - 8));
      this.cachedMenuScreenWidth = var1.screenWidth();
      this.menuWidthDirty = false;
      return this.cachedMenuWidth;
   }

   private void prepareInputLayout() {
      if (this.open) {
         int var1 = this.lastScreenWidth > 0 ? this.lastScreenWidth : Math.max(1, AutismUiScale.getVirtualScreenWidth());
         int var2 = this.lastScreenHeight > 0 ? this.lastScreenHeight : Math.max(1, AutismUiScale.getVirtualScreenHeight());
         int var3 = this.lastRowHeight > 0 ? this.lastRowHeight : 16;
         this.visibleRows = this.visibleRows(var2, var3);
         int var4 = Math.max(1, Math.min(this.cachedMenuWidth > 1 ? this.cachedMenuWidth : this.bounds.width(), var1 - 8));
         this.layoutMenu(var1, var2, var3, var4);
      }
   }

   private void layoutMenu(int var1, int var2, int var3, int var4) {
      this.visibleRows = Math.max(1, Math.min(this.options.size(), this.visibleRows <= 0 ? this.visibleRows(var2, var3) : this.visibleRows));
      int var5 = Math.max(1, this.visibleRows * var3 + 2);
      int var6 = this.bounds.x();
      int var7 = this.bounds.bottom() + 1;
      int var8 = this.bounds.y() - var5 - 1;
      int var9 = var2 - 4 - var7;
      int var10 = this.bounds.y() - 4;
      int var11 = var9 < var5 && var9 < var10 ? var8 : var7;
      this.menuBounds = UiBounds.of(clamp(var6, 4, Math.max(4, var1 - 4 - var4)), clamp(var11, 4, Math.max(4, var2 - 4 - var5)), var4, var5);
   }

   private int visibleRows(int var1, int var2) {
      int var3 = Math.max(0, (var1 - 4 - this.bounds.bottom() - 1) / Math.max(1, var2));
      int var4 = Math.max(0, (this.bounds.y() - 4 - 1) / Math.max(1, var2));
      return Math.max(1, Math.min(this.options.size(), Math.max(var3, var4)));
   }

   private int rowHeight() {
      return this.visibleRows <= 0 ? 1 : Math.max(1, (this.menuBounds.height() - 2) / this.visibleRows);
   }

   private int maxScroll() {
      return Math.max(0, this.options.size() - Math.max(1, this.visibleRows));
   }

   private void ensureSelectedVisible() {
      int var1 = this.options.indexOf(this.selected);
      if (var1 >= 0) {
         if (var1 < this.scroll) {
            this.scroll = var1;
         }

         if (this.visibleRows > 0 && var1 >= this.scroll + this.visibleRows) {
            this.scroll = var1 - this.visibleRows + 1;
         }

         this.scroll = clamp(this.scroll, 0, this.maxScroll());
      }
   }

   private void updateScrollbar(int var1) {
      if (this.scrollbar != null && this.scrollbar.maxScroll() > 0) {
         int var2 = Scrollbar.scrollFromMouse(this.scrollbar, var1, this.scrollbarGrabOffset);
         this.scroll = clamp(Math.round((float)var2 / Math.max(1, this.rowHeight())), 0, this.maxScroll());
      }
   }

   private static int clamp(int var0, int var1, int var2) {
      return Math.max(var1, Math.min(var0, var2));
   }
}
