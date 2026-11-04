package autismclient.util.mm.crypto;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.Platform;

/** Java 21 compatible DPAPI bridge; preserves the original on-disk format. */
final class WinDpapi {
    private WinDpapi() {}
    static byte[] protect(byte[] data) {
        if (!Platform.isWindows() || data == null) return null;
        try { return Crypt32Util.cryptProtectData(data, 1); }
        catch (Throwable failure) { return null; }
    }
    static byte[] unprotect(byte[] data) {
        if (!Platform.isWindows() || data == null) return null;
        try { return Crypt32Util.cryptUnprotectData(data, 1); }
        catch (Throwable failure) { return null; }
    }
}
