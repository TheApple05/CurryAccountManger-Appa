import curryfarts.legacy.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.*;

/** Reads the parent's synthetic upstream NBT fixture; never accesses real accounts. */
public final class StoreCompatibility {
    public static void main(String[] args)throws Exception {
        Path fixture=Paths.get(args[0]),game=Paths.get(args[1]);Files.createDirectories(game);
        Files.copy(fixture.resolve("autism-accounts.nbt"),game.resolve("autism-accounts.nbt"),StandardCopyOption.REPLACE_EXISTING);
        JsonObject expected=new JsonParser().parse(new String(Files.readAllBytes(fixture.resolve("expected.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        AccountStore store=new AccountStore();store.load(game.toFile());check(store.all().size()==1,"fixture count");compare(store.all().get(0),expected);
        store.save();store.load(game.toFile());compare(store.all().get(0),expected);
        CompoundTag tag=NbtIo.read(game.resolve("autism-accounts.nbt").toFile()).getList("accounts",10).getCompound(0);
        check(!tag.contains("token")&&!tag.contains("password")&&!tag.contains("label"),"no plaintext secrets written");
        check(tag.contains("encToken")&&tag.contains("encPassword")&&tag.contains("encLabel"),"upstream encrypted fields retained");
        System.out.println("{\"passed\":true,\"java\":\"8\",\"upstreamNbtRead\":true,\"saveReloadPreservesAllFields\":true,\"encryptedFieldsRetained\":true}");
    }
    private static void compare(Account a,JsonObject expected)throws Exception {for(String key:Arrays.asList("id","type","username","uuid","token","sessionToken","password","label"))check(String.valueOf(Account.class.getField(key).get(a)).equals(expected.get(key).getAsString()),"account field "+key);check(a.sessionTokenExpiresAt==expected.get("sessionTokenExpiresAt").getAsLong(),"session expiration");}
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
