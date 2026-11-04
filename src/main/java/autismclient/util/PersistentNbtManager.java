package autismclient.util;

import autismclient.AutismClientAddon;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

public abstract class PersistentNbtManager<T> {
   protected final List<T> items = new ArrayList<>();
   private boolean loaded;
   private long changeRevision;

   public final synchronized long changeRevision() {
      return this.changeRevision;
   }

   protected abstract File saveFile();

   protected abstract String listKey();

   protected abstract T fromTag(CompoundTag var1);

   protected abstract CompoundTag toTag(T var1);

   protected void readExtra(CompoundTag var1) {
   }

   protected void writeExtra(CompoundTag var1) {
   }

   protected abstract String describe();

   protected final synchronized void ensureLoaded() {
      if (!this.loaded) {
         this.loaded = true;
         this.changeRevision++;
         File var1 = this.saveFile();
         if (var1.exists()) {
            try {
               CompoundTag var2 = NbtIo.read(var1.toPath());
               if (var2 == null) {
                  return;
               }

               this.readExtra(var2);
               this.items.clear();

               for (Tag var5 : var2.getListOrEmpty(this.listKey())) {
                  if (var5 instanceof CompoundTag var6) {
                     this.items.add(this.fromTag(var6));
                  }
               }
            } catch (Exception var7) {
               AutismClientAddon.LOG.error("Failed to load " + this.describe(), var7);
            }
         }
      }
   }

   public void save() {
      CompoundTag var1 = new CompoundTag();
      ArrayList var2;
      File var3;
      synchronized (this) {
         this.changeRevision++;
         this.writeExtra(var1);
         var2 = new ArrayList<>(this.items);
         var3 = this.saveFile();
      }

      ListTag var4 = new ListTag();

      for (Object var6 : var2) {
         var4.add(this.toTag((T)var6));
      }

      var1.put(this.listKey(), var4);
      String var8 = "nbt:" + var3.getAbsolutePath();
      File var9 = var3;
      SaveCoordinator.enqueueLatest(var8, () -> this.writeSnapshot(var1, var9));
   }

   private void writeSnapshot(CompoundTag var1, File var2) {
      File var3 = var2.getParentFile();
      if (var3 == null) {
         var3 = new File(".");
      }

      if (var3 != null) {
         var3.mkdirs();
      }

      File var4 = new File(var3, var2.getName() + ".tmp");
      File var5 = new File(var3, var2.getName() + ".bak");

      try {
         NbtIo.write(var1, var4.toPath());
      } catch (Exception var11) {
         AutismClientAddon.LOG.error("Failed to save " + this.describe(), var11);
         var4.delete();
         return;
      }

      try {
         if (var2.exists()) {
            Files.copy(var2.toPath(), var5.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var10) {
         AutismClientAddon.LOG.warn("Failed to back up " + this.describe(), var10);
      }

      try {
         Files.move(var4.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception var9) {
         try {
            Files.move(var4.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception var8) {
            AutismClientAddon.LOG.error("Failed to swap in " + this.describe(), var8);
         }
      }
   }

   public synchronized List<T> all() {
      return new ArrayList<>(this.items);
   }

   public synchronized int size() {
      return this.items.size();
   }

   public synchronized boolean contains(T var1) {
      return this.items.contains(var1);
   }
}
