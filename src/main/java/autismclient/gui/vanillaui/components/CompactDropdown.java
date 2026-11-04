package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiInputResult;
import autismclient.util.AutismText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactDropdown {
   private static boolean suppressUnderlyingPointerUntilRelease;
   public boolean active = true;
   public boolean visible = true;
   private final Dropdown dropdown;
   private int x;
   private int y;
   private int width;
   private int height;
   private List<String> options;
   private List<String> displayOptions;
   private IntConsumer onSelect;
   private int selectedIndex;
   private String buttonLabelOverride = "";

   public CompactDropdown(int var1, int var2, int var3, int var4, List<String> var5, int var6, IntConsumer var7) {
      this.x = var1;
      this.y = var2;
      this.width = var3;
      this.height = var4;
      this.options = copy(var5);
      this.displayOptions = displayOptions(this.options);
      this.selectedIndex = clampIndex(var6, this.options.size());
      this.onSelect = var7;
      this.dropdown = new Dropdown(this.bounds(), this.displayOptions, this.selectedDisplay(), this::selectDisplayValue);
   }

   public CompactDropdown setBounds(int var1, int var2, int var3, int var4) {
      this.x = var1;
      this.y = var2;
      this.width = var3;
      this.height = var4;
      this.dropdown.setBounds(this.bounds());
      return this;
   }

   public CompactDropdown setOptions(List<String> var1) {
      List var2 = copy(var1);
      if (this.options.equals(var2)) {
         return this;
      }

      this.options = var2;
      this.displayOptions = displayOptions(var2);
      this.selectedIndex = clampIndex(this.selectedIndex, var2.size());
      this.dropdown.setOptions(this.displayOptions);
      this.dropdown.setSelected(this.selectedDisplay());
      return this;
   }

   public CompactDropdown setSelectedIndex(int var1) {
      this.selectedIndex = clampIndex(var1, this.options.size());
      this.dropdown.setSelected(this.selectedDisplay());
      return this;
   }

   public CompactDropdown setOnSelect(IntConsumer var1) {
      this.onSelect = var1;
      return this;
   }

   public CompactDropdown setButtonLabelOverride(String var1) {
      this.buttonLabelOverride = var1 == null ? "" : var1;
      return this;
   }

   public static void renderButtons(GuiGraphicsExtractor var0, Font var1, List<CompactDropdown> var2, int var3, int var4) {
      if (var0 != null && var1 != null && var2 != null) {
         UiContext var5 = context(var0, var1, var3, var4);
         boolean var6 = isMenuOpen(var2);
         boolean var7 = var6 || shouldSuppressUnderlyingPointer();

         for (CompactDropdown var9 : var2) {
            if (var9 != null && var9.visible) {
               boolean var10 = var9.active && !var7 && var9.bounds().contains(var3, var4);
               Dropdown.renderControl(var5, var9.bounds(), var9.buttonLabel(), var10, var9.dropdown.isOpen(), var9.active);
            }
         }
      }
   }

   public static void renderOpenMenu(GuiGraphicsExtractor var0, Font var1, List<CompactDropdown> var2, int var3, int var4) {
      if (var0 != null && var1 != null && var2 != null) {
         for (CompactDropdown var6 : var2) {
            if (var6 != null && var6.visible && var6.dropdown.isOpen()) {
               var6.dropdown.render(context(var0, var1, var3, var4));
               return;
            }
         }
      }
   }

   public static boolean isMenuOpen(List<CompactDropdown> var0) {
      if (var0 == null) {
         suppressUnderlyingPointerUntilRelease = false;
         return false;
      }

      for (CompactDropdown var2 : var0) {
         if (var2 != null && var2.visible && var2.dropdown.isOpen()) {
            return true;
         }
      }

      suppressUnderlyingPointerUntilRelease = false;
      return false;
   }

   public static boolean isInsideOpenMenu(List<CompactDropdown> var0, double var1, double var3) {
      if (var0 == null) {
         return false;
      }

      for (CompactDropdown var6 : var0) {
         if (var6 != null && var6.visible && var6.dropdown.containsMenu((int)var1, (int)var3)) {
            return true;
         }
      }

      return false;
   }

   public static boolean shouldSuppressUnderlyingPointer() {
      return suppressUnderlyingPointerUntilRelease;
   }

   public static boolean closeOpenMenu(List<CompactDropdown> var0) {
      if (var0 == null) {
         return false;
      }

      boolean var1 = false;

      for (CompactDropdown var3 : var0) {
         if (var3 != null && var3.dropdown.isOpen()) {
            var3.close();
            var1 = true;
         }
      }

      return var1;
   }

   public static boolean mouseClicked(List<CompactDropdown> var0, double var1, double var3, int var5) {
      if (var0 == null) {
         return false;
      }

      CompactDropdown var6 = openDropdown(var0);
      if (var6 != null) {
         if (var5 == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            suppressUnderlyingPointerUntilRelease = true;
         }

         var6.dropdown.mouseClicked((int)var1, (int)var3, var5);
         if (var5 != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !var6.bounds().contains((int)var1, (int)var3) && !var6.dropdown.containsMenu((int)var1, (int)var3)) {
            var6.close();
         }

         suppressUnderlyingPointerUntilRelease = var6.dropdown.isOpen();
         return true;
      } else {
         if (var5 != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
         }

         for (int var7 = var0.size() - 1; var7 >= 0; var7--) {
            CompactDropdown var8 = (CompactDropdown)var0.get(var7);
            if (var8 != null && var8.visible && var8.active && var8.bounds().contains((int)var1, (int)var3)) {
               closeOpenMenu(var0);
               var8.dropdown.open();
               suppressUnderlyingPointerUntilRelease = true;
               return true;
            }
         }

         return false;
      }
   }

   public static boolean mouseScrolled(List<CompactDropdown> var0, double var1, double var3, double var5) {
      CompactDropdown var7 = openDropdown(var0);
      if (var7 == null) {
         return false;
      }

      if (!var7.dropdown.containsMenu((int)var1, (int)var3)) {
         return false;
      }

      var7.dropdown.mouseScrolled((int)var1, (int)var3, var5);
      return true;
   }

   public static boolean mouseDragged(List<CompactDropdown> var0, double var1, double var3, int var5) {
      CompactDropdown var6 = openDropdown(var0);
      if (var6 == null) {
         return false;
      }

      var6.dropdown.mouseDragged((int)var1, (int)var3, var5, 0.0, 0.0);
      return true;
   }

   public static boolean mouseReleased(List<CompactDropdown> var0) {
      boolean var1 = suppressUnderlyingPointerUntilRelease || isMenuOpen(var0);
      suppressUnderlyingPointerUntilRelease = false;
      if (var0 == null) {
         return var1;
      }

      for (CompactDropdown var3 : var0) {
         if (var3 != null && var3.dropdown.mouseReleased(0, 0, 0) == UiInputResult.HANDLED) {
            var1 = true;
         }
      }

      return var1;
   }

   public void close() {
      this.dropdown.close();
      suppressUnderlyingPointerUntilRelease = false;
   }

   private UiBounds bounds() {
      return UiBounds.of(this.x, this.y, this.width, this.height);
   }

   private String buttonLabel() {
      return this.buttonLabelOverride.isBlank() ? this.selectedDisplay() : this.buttonLabelOverride;
   }

   private String selectedDisplay() {
      return this.displayOptions.isEmpty() ? "" : this.displayOptions.get(clampIndex(this.selectedIndex, this.displayOptions.size()));
   }

   private void selectDisplayValue(String var1) {
      int var2 = this.displayOptions.indexOf(var1);
      if (var2 >= 0) {
         this.selectedIndex = var2;
         if (this.onSelect != null) {
            this.onSelect.accept(var2);
         }
      }
   }

   private static CompactDropdown openDropdown(List<CompactDropdown> var0) {
      if (var0 == null) {
         return null;
      }

      for (CompactDropdown var2 : var0) {
         if (var2 != null && var2.visible && var2.dropdown.isOpen()) {
            return var2;
         }
      }

      return null;
   }

   private static UiContext context(GuiGraphicsExtractor var0, Font var1, int var2, int var3) {
      return UiContexts.overlay(var0, var1, var2, var3);
   }

   private static List<String> copy(List<String> var0) {
      return var0 == null ? List.of() : List.copyOf(var0);
   }

   private static List<String> displayOptions(List<String> var0) {
      ArrayList var1 = new ArrayList(var0.size());

      for (String var3 : var0) {
         var1.add(prettify(var3));
      }

      return List.copyOf(var1);
   }

   private static String prettify(String var0) {
      String var1 = AutismText.sanitizeUiLabel(var0);
      if (var1 != null && !var1.isEmpty()) {
         if (var1.length() <= 3 && var1.chars().anyMatch(Character::isDigit)) {
            return var1;
         }

         boolean var2 = true;

         for (int var3 = 0; var3 < var1.length(); var3++) {
            char var4 = var1.charAt(var3);
            if (var4 != '_' && var4 != ' ' && !Character.isDigit(var4) && (!Character.isLetter(var4) || var4 != Character.toUpperCase(var4))) {
               var2 = false;
               break;
            }
         }

         if (!var2) {
            return var1;
         }

         StringBuilder var7 = new StringBuilder(var1.length());
         boolean var8 = true;

         for (int var5 = 0; var5 < var1.length(); var5++) {
            char var6 = var1.charAt(var5);
            if (var6 != '_' && var6 != ' ') {
               if (var8) {
                  var7.append(Character.toUpperCase(var6));
                  var8 = false;
               } else {
                  var7.append(Character.toLowerCase(var6));
               }
            } else {
               if (!var7.isEmpty() && var7.charAt(var7.length() - 1) != ' ') {
                  var7.append(' ');
               }

               var8 = true;
            }
         }

         return var7.toString().trim();
      } else {
         return "";
      }
   }

   private static int clampIndex(int var0, int var1) {
      return var1 <= 0 ? 0 : Math.max(0, Math.min(var1 - 1, var0));
   }
}
