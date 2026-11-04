import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic cross-version fixture only; no saved real account is accessed. */
public final class CryptoCompatibility {
    public static void main(String[] args)throws Exception {
        boolean original=args[0].equals("original"),write=args[1].equals("write");
        byte[] clear="synthetic-curryfarts-compatibility".getBytes(StandardCharsets.UTF_8);
        Class<?> type=Class.forName(original?"autismclient.util.mm.crypto.WinDpapi":"curryfarts.legacy.CryptoSeal");
        Method method=type.getDeclaredMethod(original?(write?"protect":"unprotect"):(write?"seal":"unseal"),byte[].class);method.setAccessible(true);
        if(write){byte[] sealed=(byte[])method.invoke(null,(Object)clear);if(sealed==null)throw new AssertionError("Encryption failed");if(original){byte[] record=new byte[sealed.length+1];record[0]=1;System.arraycopy(sealed,0,record,1,sealed.length);sealed=record;}if(sealed[0]!=1)throw new AssertionError("Expected Windows DPAPI");Files.write(Paths.get(args[2]),sealed);System.out.println("Synthetic DPAPI record written");}
        else{byte[] sealed=Files.readAllBytes(Paths.get(args[2]));if(original)sealed=Arrays.copyOfRange(sealed,1,sealed.length);byte[] opened=(byte[])method.invoke(null,(Object)sealed);if(!Arrays.equals(clear,opened))throw new AssertionError("Cross-version decryption failed");System.out.println("Cross-version DPAPI decryption passed");}
    }
}
