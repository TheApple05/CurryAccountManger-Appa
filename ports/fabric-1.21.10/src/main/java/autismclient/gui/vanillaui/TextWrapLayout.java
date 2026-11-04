package autismclient.gui.vanillaui;

import java.util.ArrayList;
import java.util.List;

public final class TextWrapLayout {
   private TextWrapLayout() {
   }

   public static List<TextWrapLayout.Line> layout(String var0, int var1, TextWrapLayout.RangeWidth var2) {
      String var3 = var0 == null ? "" : var0;
      int var4 = Math.max(1, var1);
      ArrayList var5 = new ArrayList();
      if (var3.isEmpty()) {
         var5.add(new TextWrapLayout.Line(0, 0, 0));
         return var5;
      }

      byte var6 = 0;

      while (var6 < var3.length()) {
         int var7 = var3.indexOf(10, var6);
         int var8 = var7 >= 0 ? var7 : var3.length();
         if (var6 == var8) {
            var5.add(new TextWrapLayout.Line(var6, var7 + 1, var6));
         } else {
            int var9 = var6;

            while (var9 < var8) {
               int var10 = nextLineEnd(var3, var9, var8, var4, var2);
               int var11 = trimRenderEnd(var3, var9, var10);
               var5.add(new TextWrapLayout.Line(var9, var10, var11));
               var9 = var10;
            }

            if (var7 >= 0) {
               TextWrapLayout.Line var12 = (TextWrapLayout.Line)var5.remove(var5.size() - 1);
               var5.add(new TextWrapLayout.Line(var12.start(), var7 + 1, var12.renderEnd()));
            }
         }

         var6 = (byte)(var7 >= 0 ? var7 + 1 : var3.length());
      }

      if (var3.endsWith("\n")) {
         var5.add(new TextWrapLayout.Line(var3.length(), var3.length(), var3.length()));
      }

      return var5;
   }

   public static int nextLineEnd(String var0, int var1, int var2, int var3, TextWrapLayout.RangeWidth var4) {
      if (var0 != null && !var0.isEmpty()) {
         int var5 = floorCodePointBoundary(var0, Math.max(0, Math.min(var1, var0.length())));
         int var6 = floorCodePointBoundary(var0, Math.max(var5, Math.min(var2, var0.length())));
         if (var5 >= var6) {
            return var6;
         }

         int var7 = Math.max(1, var3);
         int var8 = 1;
         int var9 = var0.codePointCount(var5, var6);
         int var10 = 0;

         while (var8 <= var9) {
            int var11 = var8 + (var9 - var8 >>> 1);
            int var12 = var0.offsetByCodePoints(var5, var11);
            if (var4.width(var5, var12) <= var7) {
               var10 = var11;
               var8 = var11 + 1;
            } else {
               var9 = var11 - 1;
            }
         }

         int var14 = var0.offsetByCodePoints(var5, var10);
         if (var14 >= var6) {
            return var6;
         }

         if (var14 == var5) {
            return var0.offsetByCodePoints(var5, 1);
         }

         int var15 = var14;

         while (var15 > var5) {
            int var13 = var0.offsetByCodePoints(var15, -1);
            if (isPreferredBreak(var0.codePointAt(var13))) {
               return var15;
            }

            var15 = var13;
         }

         return var14;
      } else {
         return 0;
      }
   }

   private static int trimRenderEnd(String var0, int var1, int var2) {
      int var3;
      for (var3 = var2; var3 > var1; var3--) {
         char var4 = var0.charAt(var3 - 1);
         if (var4 != ' ' && var4 != '\t' && var4 != '\r' && var4 != '\n') {
            break;
         }
      }

      return var3;
   }

   private static int floorCodePointBoundary(String var0, int var1) {
      return var1 > 0 && var1 < var0.length() && Character.isHighSurrogate(var0.charAt(var1 - 1)) && Character.isLowSurrogate(var0.charAt(var1))
         ? var1 - 1
         : var1;
   }

   private static boolean isPreferredBreak(int var0) {
      return Character.isWhitespace(var0) || var0 == 44 || var0 == 59 || var0 == 58 || var0 == 47 || var0 == 41 || var0 == 93 || var0 == 125 || var0 == 62;
   }

   public record Line(int start, int end, int renderEnd) {
   }

   @FunctionalInterface
   public interface RangeWidth {
      int width(int var1, int var2);
   }
}
