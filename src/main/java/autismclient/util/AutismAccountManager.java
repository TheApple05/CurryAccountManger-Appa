package autismclient.util;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

public final class AutismAccountManager extends PersistentNbtManager<AutismAccount> implements Iterable<AutismAccount> {
   private static final AutismAccountManager INSTANCE = new AutismAccountManager();

   private AutismAccountManager() {
   }

   public static AutismAccountManager get() {
      INSTANCE.ensureLoaded();
      AutismMeteorImport.ensureImported();
      INSTANCE.ensureStableIds();
      return INSTANCE;
   }

   private synchronized void ensureStableIds() {
      boolean var1 = false;

      for (AutismAccount var3 : this.items) {
         if (var3 != null) {
            var3.stableId();
            var1 |= var3.generatedStableId;
            var3.generatedStableId = false;
         }
      }

      if (var1) {
         this.save();
      }
   }

   public synchronized AutismAccount findById(String var1) {
      if (var1 != null && !var1.isBlank()) {
         for (AutismAccount var3 : this.items) {
            if (var1.equals(var3.stableId())) {
               return var3;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public synchronized void applyResolvedCredentials(AutismAccount var1) {
      if (var1 != null && var1.id != null) {
         AutismAccount var2 = this.findById(var1.id);
         if (var2 != null) {
            var2.label = var1.label;
            var2.token = var1.token;
            var2.sessionToken = var1.sessionToken;
            var2.username = var1.username;
            var2.uuid = var1.uuid;
            var2.sessionTokenExpiresAt = var1.sessionTokenExpiresAt;
            this.save();
         }
      }
   }

   public synchronized boolean rename(String var1, String var2) {
      if (var2 != null && !var2.isBlank()) {
         AutismAccount var3 = this.findById(var1);
         if (var3 != null && var3.type == AutismAccountType.Cracked) {
            String var4 = var2.trim();
            if (var4.equals(var3.label)) {
               return true;
            }

            for (AutismAccount var6 : this.items) {
               if (var6 != var3 && var4.equalsIgnoreCase(var6.label)) {
                  return false;
               }
            }

            var3.label = var4;
            var3.username = var4;
            var3.uuid = UUIDUtil.createOfflinePlayerUUID(var4).toString();
            this.save();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public synchronized void invalidateSessionToken(String var1) {
      AutismAccount var2 = this.findById(var1);
      if (var2 != null && var2.type == AutismAccountType.Microsoft) {
         var2.token = "";
         var2.sessionTokenExpiresAt = 0L;
         this.save();
      }
   }

   @Override
   protected File saveFile() {
      return new File(Minecraft.getInstance().gameDirectory, "autism-accounts.nbt");
   }

   @Override
   protected String listKey() {
      return "accounts";
   }

   protected AutismAccount fromTag(CompoundTag var1) {
      return new AutismAccount().fromTag(var1);
   }

   protected CompoundTag toTag(AutismAccount var1) {
      return var1.toTag();
   }

   @Override
   protected String describe() {
      return "Autism accounts";
   }

   public synchronized void add(AutismAccount var1) {
      if (var1 != null) {
         this.items.add(var1);
         this.save();
      }
   }

   public synchronized int addAll(List<AutismAccount> var1) {
      if (var1 != null && !var1.isEmpty()) {
         int var2 = 0;

         for (AutismAccount var4 : var1) {
            if (var4 != null) {
               this.items.add(var4);
               var2++;
            }
         }

         if (var2 > 0) {
            this.save();
         }

         return var2;
      } else {
         return 0;
      }
   }

   public synchronized void remove(AutismAccount var1) {
      if (this.items.remove(var1)) {
         this.save();
      }
   }

   public synchronized int removeExpired() {
      int var1 = 0;
      Iterator var2 = this.items.iterator();

      while (var2.hasNext()) {
         if (((AutismAccount)var2.next()).checkStatus == AutismAccount.CheckStatus.EXPIRED) {
            var2.remove();
            var1++;
         }
      }

      if (var1 > 0) {
         this.save();
      }

      return var1;
   }

   public void login(AutismAccount var1) {
      if (var1 != null) {
         Thread var2 = new Thread(() -> {
            if (var1.fetchInfo() && var1.login()) {
               this.save();
               AutismClientMessaging.sendPrefixed("Logged in as " + var1.displayName() + ".");
            } else {
               AutismClientMessaging.sendPrefixed("Failed to login account: " + var1.displayName() + var1.failureSuffix());
            }
         }, "Autism-Account-Login");
         var2.setDaemon(true);
         var2.start();
      }
   }

   public void loginMicrosoft(AutismAccount var1) {
      if (var1 != null && var1.type == AutismAccountType.Microsoft) {
         AutismMicrosoftLogin.getRefreshToken(var2 -> {
            if (var2 == null) {
               AutismClientMessaging.sendPrefixed("Microsoft login cancelled or failed.");
            } else {
               var1.label = var2;
               Thread var3 = new Thread(() -> {
                  if (var1.fetchInfo() && var1.login()) {
                     synchronized (this) {
                        if (!this.items.contains(var1)) {
                           this.items.add(var1);
                        }
                     }

                     this.save();
                     AutismClientMessaging.sendPrefixed("Logged in as " + var1.displayName() + ".");
                  } else {
                     AutismClientMessaging.sendPrefixed("Failed to login Microsoft account" + var1.failureSuffix() + ".");
                  }
               }, "Autism-Microsoft-Login");
               var3.setDaemon(true);
               var3.start();
            }
         });
      }
   }

   @Override
   public Iterator<AutismAccount> iterator() {
      return this.all().iterator();
   }
}
