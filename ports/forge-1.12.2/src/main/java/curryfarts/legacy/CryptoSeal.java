package curryfarts.legacy;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/** Compatible flag-1 Windows DPAPI / flag-2 AES-GCM record format from the source. */
public final class CryptoSeal {
    private static File directory = new File("config/autism");
    private static final SecureRandom RANDOM = new SecureRandom();
    public static synchronized void configure(File configDir){directory=configDir;}
    public static byte[] seal(byte[] plain) {
        byte[] p=plain==null?new byte[0]:plain;
        byte[] nativeData=dpapi(true,p);if(nativeData!=null)return prefix(1,nativeData);
        try {byte[] nonce=new byte[12];RANDOM.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(true),"AES"),new GCMParameterSpec(128,nonce));byte[] enc=c.doFinal(p);byte[] data=new byte[12+enc.length];System.arraycopy(nonce,0,data,0,12);System.arraycopy(enc,0,data,12,enc.length);return prefix(2,data);}catch(Exception error){throw new IllegalStateException("Could not encrypt account data",error);}
    }
    public static byte[] unseal(byte[] sealed) {
        if(sealed==null||sealed.length==0)return null;byte[] data=Arrays.copyOfRange(sealed,1,sealed.length);
        if(sealed[0]==0)return data;
        if(sealed[0]==1)return dpapi(false,data);
        if(sealed[0]==2)try{if(data.length<28)return null;Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(false),"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(data,0,12)));return c.doFinal(Arrays.copyOfRange(data,12,data.length));}catch(Exception ignored){return null;}
        return null;
    }
    private static byte[] prefix(int type,byte[] data){byte[] result=new byte[data.length+1];result[0]=(byte)type;System.arraycopy(data,0,result,1,data.length);return result;}
    private static synchronized byte[] key(boolean create) throws IOException {
        File f=new File(directory,"autism-seal.key");if(f.isFile()){byte[] key=Files.readAllBytes(f.toPath());if(key.length!=32)throw new IOException("Invalid account key");return key;}
        if(!create)throw new FileNotFoundException("Account encryption key missing");directory.mkdirs();byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);File tmp=new File(directory,"autism-seal.key.tmp");Files.write(tmp.toPath(),bytes);tmp.setReadable(false,false);tmp.setWritable(false,false);tmp.setReadable(true,true);tmp.setWritable(true,true);Files.move(tmp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);return bytes;
    }
    public static class Blob extends Structure {
        public int cbData; public Pointer pbData;
        @Override protected List<String> getFieldOrder(){return Arrays.asList("cbData","pbData");}
    }
    public interface Crypt32 extends StdCallLibrary {int CryptProtectData(Blob in,Pointer description,Pointer entropy,Pointer reserved,Pointer prompt,int flags,Blob out);int CryptUnprotectData(Blob in,Pointer description,Pointer entropy,Pointer reserved,Pointer prompt,int flags,Blob out);}
    public interface Kernel32 extends StdCallLibrary {Pointer LocalFree(Pointer pointer);}
    private static byte[] dpapi(boolean encrypt,byte[] data) {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))return null;
        try {Crypt32 api=(Crypt32)Native.loadLibrary("Crypt32",Crypt32.class);Kernel32 kernel=(Kernel32)Native.loadLibrary("Kernel32",Kernel32.class);Memory memory=new Memory(Math.max(1,data.length));if(data.length>0)memory.write(0,data,0,data.length);Blob in=new Blob();in.cbData=data.length;in.pbData=memory;in.write();Blob out=new Blob();out.write();int ok=encrypt?api.CryptProtectData(in,null,null,null,null,1,out):api.CryptUnprotectData(in,null,null,null,null,1,out);out.read();if(ok==0||out.pbData==null)return null;try{return out.pbData.getByteArray(0,out.cbData);}finally{kernel.LocalFree(out.pbData);}}catch(Throwable unavailable){return null;}
    }
}
