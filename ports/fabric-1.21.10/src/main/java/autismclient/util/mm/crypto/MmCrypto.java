package autismclient.util.mm.crypto;

import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MmCrypto {
   public static final SecureRandom RNG = new SecureRandom();

   private MmCrypto() {
   }

   public static byte[] randomBytes(int var0) {
      byte[] var1 = new byte[var0];
      RNG.nextBytes(var1);
      return var1;
   }

   public static byte[] aesGcmSeal(byte[] var0, byte[] var1, byte[] var2, byte[] var3) {
      try {
         Cipher var4 = Cipher.getInstance("AES/GCM/NoPadding");
         var4.init(1, new SecretKeySpec(var0, "AES"), new GCMParameterSpec(128, var1));
         if (var3 != null) {
            var4.updateAAD(var3);
         }

         return var4.doFinal(var2);
      } catch (Exception var5) {
         throw new MmCrypto.MmCryptoException("aesGcmSeal", var5);
      }
   }

   public static byte[] aesGcmOpen(byte[] var0, byte[] var1, byte[] var2, byte[] var3) {
      try {
         Cipher var4 = Cipher.getInstance("AES/GCM/NoPadding");
         var4.init(2, new SecretKeySpec(var0, "AES"), new GCMParameterSpec(128, var1));
         if (var3 != null) {
            var4.updateAAD(var3);
         }

         return var4.doFinal(var2);
      } catch (AEADBadTagException var5) {
         return null;
      } catch (Exception var6) {
         return null;
      }
   }

   public static final class MmCryptoException extends RuntimeException {
      public MmCryptoException(String var1, Throwable var2) {
         super(var1, var2);
      }
   }
}
