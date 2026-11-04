import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Run once against each JAR to verify old/new encrypted records interoperate. */
public final class DpapiCompatibility {
    public static void main(String[] args) throws Exception {
        byte[] clear = "synthetic-legacy-compatibility-fixture".getBytes(StandardCharsets.UTF_8);
        Class<?> bridge = Class.forName("autismclient.util.mm.crypto.WinDpapi");
        Method protect = bridge.getDeclaredMethod("protect", byte[].class);
        Method unprotect = bridge.getDeclaredMethod("unprotect", byte[].class);
        protect.setAccessible(true);
        unprotect.setAccessible(true);
        if (args[0].equals("write")) {
            byte[] encrypted = (byte[]) protect.invoke(null, (Object) clear);
            if (encrypted == null || Arrays.equals(clear, encrypted)) throw new AssertionError("Encryption failed");
            Files.write(Path.of(args[1]), encrypted);
            System.out.println("Synthetic DPAPI fixture created");
        } else {
            byte[] decrypted = (byte[]) unprotect.invoke(null, (Object) Files.readAllBytes(Path.of(args[1])));
            if (!Arrays.equals(clear, decrypted)) throw new AssertionError("Cross-version decryption failed");
            System.out.println("Cross-version DPAPI decryption passed");
        }
    }
}
