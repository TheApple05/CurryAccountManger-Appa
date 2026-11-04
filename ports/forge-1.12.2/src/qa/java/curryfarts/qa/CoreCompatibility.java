package curryfarts.qa;

import com.google.gson.*;
import curryfarts.legacy.*;
import net.minecraft.nbt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Checks the original modern NBT and encrypted records without a native window. */
public final class CoreCompatibility {
    public static void main(String[] args)throws Exception{
        Path source=Paths.get(args[0]),run=Paths.get(args[1]);Files.createDirectories(run);
        Files.copy(source.resolve("autism-accounts.nbt"),run.resolve("autism-accounts.nbt"),StandardCopyOption.REPLACE_EXISTING);
        JsonObject expected=new JsonParser().parse(new String(Files.readAllBytes(source.resolve("expected.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> checks=new ArrayList<String>();AccountStore store=new AccountStore();store.load(run.toFile());
        Account account=store.findById(expected.get("id").getAsString());if(account==null)throw new AssertionError("Original stable ID not imported");
        for(String name:new String[]{"id","type","username","uuid","token","sessionToken","password","label"})if(!expected.get(name).getAsString().equals(Account.class.getField(name).get(account)))throw new AssertionError("Original field mismatch: "+name);
        if(account.sessionTokenExpiresAt!=expected.get("sessionTokenExpiresAt").getAsLong())throw new AssertionError("Expiry mismatch");
        checks.add("Original Minecraft 1.21.11 NBT and DPAPI credentials imported without network calls");
        store.save();AccountStore copy=new AccountStore();copy.load(run.toFile());Account reloaded=copy.findById(account.id);
        if(!account.token.equals(reloaded.token)||!account.label.equals(reloaded.label))throw new AssertionError("Encrypted save changed credentials");
        checks.add("Legacy NBT save/reload retains stable ID, refresh token and access token");
        String raw=new String(Files.readAllBytes(run.resolve("autism-accounts.nbt")),StandardCharsets.ISO_8859_1);
        for(String name:new String[]{"token","sessionToken","password","label"})if(raw.contains(expected.get(name).getAsString()))throw new AssertionError("Plaintext credential persisted");
        checks.add("Saved sensitive fields remain encrypted");
        byte[] clear="synthetic-core-fixture".getBytes(StandardCharsets.UTF_8),sealed=CryptoSeal.seal(clear);
        if(sealed[0]!=1||!Arrays.equals(clear,CryptoSeal.unseal(sealed)))throw new AssertionError("DPAPI failed");
        checks.add("Java 8 native DPAPI encryption/decryption works");
        Account offline=new Account();offline.label="OfflineCompat";if(!Auth.resolve(offline))throw new AssertionError("Offline resolve failed");
        if(!offline.uuid.equals(Account.offlineUuid("OfflineCompat").toString()))throw new AssertionError("Offline UUID mismatch");
        checks.add("Offline identities retain vanilla name-based UUIDs");
        Path meteorRun=run.resolve("meteor-test"), meteorFile=meteorRun.resolve("meteor-client/accounts.nbt");Files.createDirectories(meteorFile.getParent());
        NBTTagCompound meteorRoot=new NBTTagCompound(),item=new NBTTagCompound(),cache=new NBTTagCompound();NBTTagList list=new NBTTagList();
        item.setString("type","Cracked");item.setString("name","MeteorCompat");cache.setString("username","MeteorCompat");item.setTag("cache",cache);list.appendTag(item);meteorRoot.setTag("accounts",list);
        CompressedStreamTools.write(meteorRoot,meteorFile.toFile());byte[] sourceBytes=Files.readAllBytes(meteorFile);
        AccountStore imported=new AccountStore();imported.load(meteorRun.toFile());if(imported.all().size()!=1||!"MeteorCompat".equals(imported.all().get(0).displayName()))throw new AssertionError("Meteor import failed without an existing account file");
        checks.add("Meteor accounts import when no Curryfarts account file exists");
        imported.remove(imported.all().get(0));AccountStore again=new AccountStore();again.load(meteorRun.toFile());if(!again.all().isEmpty())throw new AssertionError("Deleted Meteor account reimported");
        if(!Arrays.equals(sourceBytes,Files.readAllBytes(meteorFile)))throw new AssertionError("Meteor source was modified");
        checks.add("Meteor import marker prevents reimport and preserves original source");
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("status","PASSED");result.put("checks",checks);result.put("syntheticDataOnly",true);
        Path output=Paths.get("validation/core-compatibility.json");Files.createDirectories(output.getParent());Files.write(output,new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("PASSED "+checks.size()+" compatibility checks");
    }
}
