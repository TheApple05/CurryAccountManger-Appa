package autismclient.util.mm.crypto;

import autismclient.AutismClientAddon;
import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;

public final class AtRestSeal {
   private AtRestSeal() {
   }

   private static File keyFile() {
      try {
         if (AutismClientAddon.FOLDER != null) {
            return new File(AutismClientAddon.FOLDER, "autism-seal.key");
         }
      } catch (Throwable var1) {
      }

      File var0 = new File(System.getProperty("java.io.tmpdir", "."), "autismclient-seal");
      return new File(var0, "autism-seal.key");
   }

   private static File keyTmp() {
      File var0 = keyFile();
      return new File(var0.getParentFile(), var0.getName() + ".tmp");
   }

   public static byte[] seal(byte[] var0) {
      if (var0 == null) {
         var0 = new byte[0];
      }

      byte[] var1 = WinDpapi.protect(var0);
      if (var1 != null) {
         return selectEncryptedResult(var1, null);
      }

      byte[] var2 = aesSeal(var0);
      return selectEncryptedResult(null, var2);
   }

   /** New records must be encrypted. Marker 0 remains readable only for legacy data. */
   static byte[] selectEncryptedResult(byte[] dpapi, byte[] aes) {
      if (dpapi != null) {
         return prepend((byte)1, dpapi);
      }
      if (aes != null) {
         return prepend((byte)2, aes);
      }
      throw new IllegalStateException("Could not encrypt account credentials. No plaintext record was created.");
   }

   public static byte[] unseal(byte[] var0) {
      if (var0 != null && var0.length >= 1) {
         byte[] var1 = Arrays.copyOfRange(var0, 1, var0.length);

         return switch (var0[0]) {
            case 0 -> var1;
            case 1 -> WinDpapi.unprotect(var1);
            case 2 -> aesOpen(var1);
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static byte[] prepend(byte var0, byte[] var1) {
      byte[] var2 = new byte[var1.length + 1];
      var2[0] = var0;
      System.arraycopy(var1, 0, var2, 1, var1.length);
      return var2;
   }

   private static byte[] aesSeal(byte[] var0) {
      try {
         byte[] var1 = getOrCreateAesKey();
         if (var1 == null) {
            return null;
         }

         byte[] var2 = MmCrypto.randomBytes(12);
         byte[] var3 = MmCrypto.aesGcmSeal(var1, var2, var0, null);
         byte[] var4 = new byte[12 + var3.length];
         System.arraycopy(var2, 0, var4, 0, 12);
         System.arraycopy(var3, 0, var4, 12, var3.length);
         return var4;
      } catch (Throwable var5) {
         return null;
      }
   }

   private static byte[] aesOpen(byte[] var0) {
      try {
         if (var0 != null && var0.length >= 28) {
            byte[] var1 = readAesKey();
            if (var1 == null) {
               return null;
            }

            byte[] var2 = Arrays.copyOfRange(var0, 0, 12);
            byte[] var3 = Arrays.copyOfRange(var0, 12, var0.length);
            return MmCrypto.aesGcmOpen(var1, var2, var3, null);
         } else {
            return null;
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   private static byte[] getOrCreateAesKey() {
      byte[] var0 = readAesKey();
      if (var0 != null) {
         return var0;
      }

      try {
         File var1 = keyFile();
         File var2 = keyTmp();
         byte[] var3 = MmCrypto.randomBytes(32);
         File var4 = var1.getParentFile();
         if (var4 != null) {
            var4.mkdirs();
         }

         Files.write(var2.toPath(), var3);
         if (!restrictToOwner(var2)) {
            Files.deleteIfExists(var2.toPath());
            return null;
         }

         try {
            Files.move(var2.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var6) {
            Files.move(var2.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         return !restrictToOwner(var1) ? null : var3;
      } catch (Throwable var7) {
         return null;
      }
   }

   private static byte[] readAesKey() {
      try {
         File var0 = keyFile();
         if (!var0.exists()) {
            return null;
         }

         if (!restrictToOwner(var0)) {
            return null;
         }

         byte[] var1 = Files.readAllBytes(var0.toPath());
         return var1 != null && var1.length == 32 ? var1 : null;
      } catch (Throwable var2) {
         return null;
      }
   }

   private static boolean restrictToOwner(File var0) {
      try {
         PosixFileAttributeView var1 = Files.getFileAttributeView(var0.toPath(), PosixFileAttributeView.class);
         if (var1 != null) {
            EnumSet var2 = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(var0.toPath(), var2);
            return Files.getPosixFilePermissions(var0.toPath()).equals(var2);
         } else {
            var0.setReadable(false, false);
            var0.setWritable(false, false);
            return var0.setReadable(true, true) && var0.setWritable(true, true);
         }
      } catch (Throwable var3) {
         return false;
      }
   }
}
