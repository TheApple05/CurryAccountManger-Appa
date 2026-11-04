package autismclient.util;

import autismclient.AutismClientAddon;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class AutismMeteorImport {
   private static volatile boolean started;

   private AutismMeteorImport() {
   }

   public static void ensureImported() {
      if (!started) {
         Minecraft var0 = Minecraft.getInstance();
         if (var0 != null && var0.gameDirectory != null) {
            synchronized (AutismMeteorImport.class) {
               if (started) {
                  return;
               }

               started = true;
            }

            try {
               runImport(var0.gameDirectory);
            } catch (Throwable var3) {
               AutismClientAddon.LOG.warn("Meteor import failed", var3);
            }
         }
      }
   }

   private static void runImport(File var0) {
      File var1 = new File(var0, "meteor-client");
      if (var1.isDirectory()) {
         AutismMeteorImport.State var2 = loadState(var0);
         boolean var3 = var2.proxyImportVersion < 1;
         if (var3) {
            var2.proxiesSig = "";
         }

         boolean var4 = importAccounts(new File(var1, "accounts.nbt"), var2);

         if (var3) {
            var2.proxyImportVersion = 1;
            var4 = true;
         }

         if (var4) {
            saveState(var0, var2);
         }
      }
   }

   private static boolean importAccounts(File var0, AutismMeteorImport.State var1) {
      if (!var0.isFile()) {
         return false;
      }

      String var2 = fileSignature(var0);
      if (var2.equals(var1.accountsSig)) {
         return false;
      }

      try {
         CompoundTag var3 = NbtIo.read(var0.toPath());
         if (var3 != null) {
            for (Tag var5 : var3.getListOrEmpty("accounts")) {
               if (var5 instanceof CompoundTag var6) {
                  AutismAccount var7 = mapAccount(var6);
                  if (var7 != null && var1.importedAccounts.add(accountKey(var7))) {
                     AutismAccountManager.get().add(var7);
                  }
               }
            }
         }
      } catch (Exception var8) {
         AutismClientAddon.LOG.warn("Failed to read Meteor accounts", var8);
         return !var1.importedAccounts.isEmpty();
      }

      var1.accountsSig = var2;
      return true;
   }

   private static AutismAccount mapAccount(CompoundTag var0) {
      String var1 = var0.getStringOr("type", "");
      String var2 = var0.getStringOr("name", "");
      String var3 = var0.getStringOr("token", "");
      String var4 = "";
      String var5 = "";
      Optional var6 = var0.getCompound("cache");
      if (var6.isPresent()) {
         var4 = ((CompoundTag)var6.get()).getStringOr("username", "");
         var5 = ((CompoundTag)var6.get()).getStringOr("uuid", "");
      }

      AutismAccount var7 = new AutismAccount();
      var7.uuid = var5;
      var7.username = var4;
      switch (var1) {
         case "Cracked":
            var7.type = AutismAccountType.Cracked;
            var7.label = var2;
            if (var7.username.isBlank()) {
               var7.username = var2;
            }
            break;
         case "Microsoft":
            var7.type = AutismAccountType.Microsoft;
            var7.label = var2;
            break;
         case "Session":
            var7.type = AutismAccountType.Session;
            var7.label = var2;
            var7.token = var3;
            break;
         case "TheAltening":
            var7.type = AutismAccountType.TheAltening;
            var7.label = var2;
            var7.token = var3.isBlank() ? var2 : var3;
            break;
         default:
            return null;
      }

      boolean var10 = !var7.label.isBlank() || !var7.username.isBlank() || !var7.token.isBlank();
      return var10 ? var7 : null;
   }

   private static String accountKey(AutismAccount var0) {
      String var1 = var0.uuid == null ? "" : var0.uuid.trim().toLowerCase(Locale.ROOT);
      if (!var1.isEmpty()) {
         return var0.type.name() + "|u|" + var1;
      }

      String var2 = var0.username != null && !var0.username.isBlank() ? var0.username : var0.label;
      return var0.type.name() + "|n|" + (var2 == null ? "" : var2.trim().toLowerCase(Locale.ROOT));
   }









   private static String settingString(Map<String, CompoundTag> var0, String var1, String var2) {
      CompoundTag var3 = (CompoundTag)var0.get(var1);
      return var3 == null ? var2 : var3.getStringOr("value", var2);
   }

   private static int settingInt(Map<String, CompoundTag> var0, String var1, int var2) {
      CompoundTag var3 = (CompoundTag)var0.get(var1);
      return var3 == null ? var2 : var3.getIntOr("value", var2);
   }



   private static AutismMeteorImport.State loadState(File var0) {
      AutismMeteorImport.State var1 = new AutismMeteorImport.State();
      File var2 = new File(var0, "autism-meteor-import.nbt");
      if (!var2.isFile()) {
         return var1;
      }

      try {
         CompoundTag var3 = NbtIo.read(var2.toPath());
         if (var3 == null) {
            return var1;
         }

         var1.accountsSig = var3.getStringOr("accountsSig", "");
         var1.proxiesSig = var3.getStringOr("proxiesSig", "");
         var1.proxyImportVersion = var3.getIntOr("proxyImportVersion", 0);
         readKeys(var3.getListOrEmpty("importedAccounts"), var1.importedAccounts);
         readKeys(var3.getListOrEmpty("importedProxies"), var1.importedProxies);
      } catch (Exception var4) {
         AutismClientAddon.LOG.warn("Failed to read Meteor import state", var4);
      }

      return var1;
   }

   private static void saveState(File var0, AutismMeteorImport.State var1) {
      try {
         CompoundTag var2 = new CompoundTag();
         var2.putString("accountsSig", var1.accountsSig);
         var2.putString("proxiesSig", var1.proxiesSig);
         var2.putInt("proxyImportVersion", var1.proxyImportVersion);
         var2.put("importedAccounts", writeKeys(var1.importedAccounts));
         var2.put("importedProxies", writeKeys(var1.importedProxies));
         NbtIo.write(var2, new File(var0, "autism-meteor-import.nbt").toPath());
      } catch (Exception var3) {
         AutismClientAddon.LOG.warn("Failed to save Meteor import state", var3);
      }
   }

   private static void readKeys(ListTag var0, Set<String> var1) {
      for (Tag var3 : var0) {
         String var4 = var3.asString().orElse("");
         if (!var4.isEmpty()) {
            var1.add(var4);
         }
      }
   }

   private static ListTag writeKeys(Set<String> var0) {
      ListTag var1 = new ListTag();

      for (String var3 : var0) {
         var1.add(StringTag.valueOf(var3));
      }

      return var1;
   }

   private static String fileSignature(File var0) {
      return var0.lastModified() + ":" + var0.length();
   }

   private static final class State {
      String accountsSig = "";
      String proxiesSig = "";
      int proxyImportVersion = 0;
      final Set<String> importedAccounts = new LinkedHashSet<>();
      final Set<String> importedProxies = new LinkedHashSet<>();
   }
}
