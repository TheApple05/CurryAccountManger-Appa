package curryfarts.legacy;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/** QA-only fixtures; never added to installable JARs and never use real credentials. */
public final class ImporterPackagedChecks {
    public static final String ACCESS="eyJhbGciOiJub25lIn0.eyJleHAiOjE5MDAwMDAwMDB9.c3ludGhldGlj";
    private static final String UUID="12345678-1234-1234-1234-123456789abc";
    public static void prepare(Object screen)throws Exception {Field auth=screen.getClass().getDeclaredField("auth");auth.setAccessible(true);auth.set(screen,new TokenImportBatch.Authenticator(){public TokenRefresher.Verified validate(String token){return new TokenRefresher.Verified("ImportedQA",UUID,token,"",1900000000000L);}public TokenRefresher.Verified refresh(String token){throw new AssertionError("No live refresh in UI fixture");}});screen.getClass().getMethod("loadInput",String.class).invoke(screen,ACCESS+"\nSpoofed:"+ACCESS+"\ninvalid fixture");}
    public static void verify(Object screen,AccountStore store,File directory,Consumer<String> checked)throws Exception {
        Field progress=screen.getClass().getDeclaredField("progress");progress.setAccessible(true);TokenImportBatch.Progress p=(TokenImportBatch.Progress)progress.get(screen);require(p!=null&&p.imported==1&&p.duplicates==1&&p.invalid==1,"importer async valid/duplicate/invalid results",checked);
        Field input=screen.getClass().getDeclaredField("input");input.setAccessible(true);require(input.get(screen).equals(""),"importer clears credential input after dispatch",checked);
        Account imported=null;for(Account a:store.all())if(a.uuid.equals(UUID))imported=a;require(imported!=null&&imported.username.equals("ImportedQA"),"verified identity immediately available in switcher store",checked);String stable=imported.id;
        TokenRefresher.Verified refreshed=new TokenRefresher.Verified("ImportedQA",UUID,"synthetic-new-session","M.Crotated-packaged-fixture",1900000001000L);
        require(store.importVerified(refreshed,()->false)==TokenImportBatch.Stored.DUPLICATE,"verified duplicate updates credentials without adding row",checked);Account updated=store.findById(stable);require(updated!=null&&updated.tokenRefresherAuth&&updated.label.equals(refreshed.refreshToken)&&updated.token.equals(refreshed.accessToken),"duplicate refresh rotation retained with stable ID",checked);
        AccountStore reloaded=new AccountStore();reloaded.load(directory);Account saved=reloaded.findById(stable);require(saved!=null&&saved.tokenRefresherAuth&&saved.label.equals(refreshed.refreshToken)&&saved.token.equals(refreshed.accessToken),"imported refresh client and encrypted credentials survive disk reload",checked);
        String disk=new String(Files.readAllBytes(new File(directory,"autism-accounts.nbt").toPath()),StandardCharsets.ISO_8859_1);require(!disk.contains(refreshed.accessToken)&&!disk.contains(refreshed.refreshToken)&&!disk.contains(ACCESS),"NBT contains no plaintext imported credentials",checked);
        int size=store.all().size();require(store.importVerified(new TokenRefresher.Verified("CancelledQA","bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",ACCESS,"",1900000000000L),()->true)==TokenImportBatch.Stored.CANCELLED&&store.all().size()==size,"cancelled verified result cannot reach saved library",checked);
        Path fixture=new File(directory,"synthetic-token-input.txt").toPath();Files.write(fixture,("\uFEFFName:"+ACCESS).getBytes(StandardCharsets.UTF_8));require(TokenImportBatch.parse(TokenImportBatch.readFile(fixture)).get(0).token.equals(ACCESS),"UTF8 TXT import parser works in packaged runtime",checked);Files.delete(fixture);
    }
    private static void require(boolean value,String message,Consumer<String> checked){if(!value)throw new AssertionError(message);checked.accept(message);}
}
