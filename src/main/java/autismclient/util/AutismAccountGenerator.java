package autismclient.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;

public final class AutismAccountGenerator {
   private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
   private static final String[] ADJECTIVES = new String[]{
      "Swift",
      "Silent",
      "Dark",
      "Lucky",
      "Wild",
      "Rapid",
      "Clever",
      "Mighty",
      "Tiny",
      "Brave",
      "Calm",
      "Eager",
      "Fuzzy",
      "Golden",
      "Happy",
      "Jolly",
      "Keen",
      "Lazy",
      "Merry",
      "Noble",
      "Proud",
      "Quiet",
      "Sneaky",
      "Witty"
   };
   private static final String[] NOUNS = new String[]{
      "Fox",
      "Wolf",
      "Bear",
      "Hawk",
      "Tiger",
      "Panda",
      "Otter",
      "Raven",
      "Cobra",
      "Falcon",
      "Badger",
      "Lynx",
      "Moose",
      "Bison",
      "Viper",
      "Heron",
      "Gecko",
      "Llama",
      "Mantis",
      "Newt",
      "Osprey",
      "Puma",
      "Sloth",
      "Wren"
   };

   private AutismAccountGenerator() {
   }

   public static List<String> randomNames(int var0) {
      int var1 = Math.max(1, Math.min(5000, var0));
      Set var2 = existingNameKeys();
      LinkedHashSet var3 = new LinkedHashSet();
      Random var4 = new Random();
      int var5 = var1 * 30;

      while (var3.size() < var1 && var5-- > 0) {
         String var6 = ADJECTIVES[var4.nextInt(ADJECTIVES.length)];
         String var7 = NOUNS[var4.nextInt(NOUNS.length)];
         int var8 = var4.nextInt(4);

         String var9 = switch (var8) {
            case 0 -> var6 + var7 + digits(var4, 2, 3);
            case 1 -> var6 + var7;
            case 2 -> var7 + digits(var4, 2, 4);
            default -> var6 + var7 + "_" + digits(var4, 1, 2);
         };
         if (var9.length() > 16) {
            var9 = var9.substring(0, 16);
         }

         if (VALID_NAME.matcher(var9).matches()) {
            String var10 = var9.toLowerCase(Locale.ROOT);
            if (!var2.contains(var10) && !var3.contains(var9)) {
               var3.add(var9);
               var2.add(var10);
            }
         }
      }

      return new ArrayList<>(var3);
   }

   public static AutismAccountGenerator.ParseResult parseNameList(String var0) {
      ArrayList var1 = new ArrayList();
      if (var0 != null && !var0.isBlank()) {
         Set var2 = existingNameKeys();
         LinkedHashSet var3 = new LinkedHashSet();
         int var4 = 0;
         int var5 = 0;

         for (String var9 : var0.split("[\\s,;]+")) {
            if (!var9.isBlank()) {
               String var10 = var9;
               int var11 = var10.indexOf(58);
               if (var11 >= 0) {
                  var10 = var10.substring(0, var11);
               }

               var10 = var10.trim();
               if (!var10.isEmpty() && VALID_NAME.matcher(var10).matches()) {
                  String var12 = var10.toLowerCase(Locale.ROOT);
                  if (!var2.contains(var12) && var3.add(var12)) {
                     var1.add(var10);
                  } else {
                     var5++;
                  }
               } else {
                  var4++;
               }
            }
         }

         return new AutismAccountGenerator.ParseResult(var1, var4, var5);
      } else {
         return new AutismAccountGenerator.ParseResult(var1, 0, 0);
      }
   }

   public static int addGeneratedAccounts(List<String> var0) {
      if (var0 != null && !var0.isEmpty()) {
         if (var0.size() > 5000) {
            var0 = var0.subList(0, 5000);
         }

         AutismAccountManager var1 = AutismAccountManager.get();
         AutismConfig var2 = AutismConfig.getGlobal();
         boolean var3 = var2.accountGenSetPassword;
         boolean var4 = "Set For All".equalsIgnoreCase(var2.accountGenPasswordMode);
         String var5 = var2.accountGenSharedPassword == null ? "" : var2.accountGenSharedPassword.trim();
         ArrayList var6 = new ArrayList();

         for (String var8 : var0) {
            if (var8 != null && !var8.isBlank()) {
               AutismAccount var9 = new AutismAccount();
               var9.type = AutismAccountType.Generated;
               var9.label = var8;
               var9.username = var8;
               var9.uuid = UUIDUtil.createOfflinePlayerUUID(var8).toString();
               if (var3) {
                  var9.password = var4 ? var5 : generatePassword();
               }

               var6.add(var9);
            }
         }

         return var1.addAll(var6);
      } else {
         return 0;
      }
   }

   private static Set<String> existingNameKeys() {
      LinkedHashSet var0 = new LinkedHashSet();

      for (AutismAccount var2 : AutismAccountManager.get().all()) {
         String var3 = var2.displayName();
         if (var3 != null && !var3.isBlank()) {
            var0.add(var3.toLowerCase(Locale.ROOT));
         }
      }

      return var0;
   }

   private static String digits(Random var0, int var1, int var2) {
      int var3 = var1 + var0.nextInt(var2 - var1 + 1);
      StringBuilder var4 = new StringBuilder(var3);

      for (int var5 = 0; var5 < var3; var5++) {
         var4.append(var0.nextInt(10));
      }

      return var4.toString();
   }

   public record ParseResult(List<String> names, int skipped, int duplicates) {
   }

   public static String generatePassword() {
      SecureRandom var0 = new SecureRandom();
      String var1 = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
      int var2 = 9 + var0.nextInt(8);
      StringBuilder var3 = new StringBuilder(var2);

      for (int var4 = 0; var4 < var2; var4++) {
         var3.append(var1.charAt(var0.nextInt(var1.length())));
      }

      return var3.toString();
   }
}
