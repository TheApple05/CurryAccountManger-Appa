package curryfarts.legacy;

import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads and writes the original uncompressed autism-accounts.nbt schema. */
public final class AccountStore {
    private final List<Account> accounts=new ArrayList<Account>();
    private File gameDir, file;
    public synchronized void load(File gameDirectory) {
        gameDir=gameDirectory;file=new File(gameDir,"autism-accounts.nbt");CryptoSeal.configure(new File(gameDir,"config/autism"));accounts.clear();
        if(file.isFile())try {CompoundTag root=NbtIo.read(file);if(root!=null){ListTag list=root.getList("accounts",10);for(int i=0;i<list.size();i++)accounts.add(fromTag(list.getCompound(i)));}}catch(Exception error){throw new IllegalStateException("Could not read saved accounts; file preserved",error);}
        importMeteor();
    }
    private void importMeteor(){
        File source=new File(gameDir,"meteor-client/accounts.nbt");if(!source.isFile())return;
        File stateFile=new File(gameDir,"autism-meteor-import.nbt");
        try{
            CompoundTag state=stateFile.isFile()?NbtIo.read(stateFile):new CompoundTag();if(state==null)state=new CompoundTag();
            String signature=source.length()+":"+source.lastModified();if(signature.equals(state.getString("accountsSig")))return;
            Set<String> imported=new LinkedHashSet<String>();ListTag old=state.getList("importedAccounts",8);for(int i=0;i<old.size();i++)imported.add(old.getString(i));
            CompoundTag root=NbtIo.read(source);List<Account> found=new ArrayList<Account>();if(root!=null){ListTag list=root.getList("accounts",10);for(int i=0;i<list.size();i++){
                CompoundTag t=list.getCompound(i),cache=t.getCompound("cache");Account a=new Account();a.type=t.getString("type");if(!Arrays.asList("Cracked","Microsoft","Session","TheAltening").contains(a.type))continue;
                a.label=t.getString("name");a.username=cache.getString("username");a.uuid=cache.getString("uuid");a.token=t.getString("token");
                if("Cracked".equals(a.type)&&a.username.isEmpty())a.username=a.label;if("TheAltening".equals(a.type)&&a.token.isEmpty())a.token=a.label;
                String key=a.type+(!a.uuid.isEmpty()?"|u|"+a.uuid.toLowerCase(Locale.ROOT):"|n|"+a.displayName().toLowerCase(Locale.ROOT));if(imported.add(key))found.add(a);
            }}
            addAll(found);ListTag keys=new ListTag();for(String key:imported)keys.add(net.minecraft.nbt.StringTag.valueOf(key));state.put("importedAccounts",keys);state.putString("accountsSig",signature);NbtIo.write(state,stateFile);
        }catch(Exception error){System.err.println("[Curryfarts] Meteor account import could not complete; source file preserved.");}
    }
    public synchronized List<Account> all(){return new ArrayList<Account>(accounts);}
    public synchronized Account findById(String id){for(Account a:accounts)if(a.stableId().equals(id))return a;return null;}
    public synchronized boolean add(Account account){if(account==null)return false;for(Account a:accounts)if(a.type.equals(account.type)&&a.displayName().equalsIgnoreCase(account.displayName()))return false;accounts.add(account);save();return true;}
    public synchronized int addAll(List<Account> incoming){int added=0;Set<String> seen=new HashSet<String>();for(Account a:accounts)seen.add(a.type+":"+a.displayName().toLowerCase(Locale.ROOT));for(Account a:incoming)if(a!=null&&seen.add(a.type+":"+a.displayName().toLowerCase(Locale.ROOT))){accounts.add(a);added++;}if(added>0)save();return added;}
    /** Verified UUID is authoritative; duplicate credentials update atomically and preserve stable IDs. */
    public synchronized TokenImportBatch.Stored importVerified(TokenRefresher.Verified verified,java.util.function.BooleanSupplier cancelled){
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())return TokenImportBatch.Stored.CANCELLED;
        int duplicate=-1;for(int i=0;i<accounts.size();i++){Account old=accounts.get(i);if(("Session".equals(old.type)||"Microsoft".equals(old.type))&&(old.uuid.replace("-","").equalsIgnoreCase(verified.uuid.replace("-",""))||verified.accessToken.equals(old.token))){duplicate=i;break;}}
        Account previous=duplicate>=0?accounts.get(duplicate):null;Account candidate=previous==null?new Account():previous.copy();
        boolean refresh=verified.refreshToken!=null&&!verified.refreshToken.isEmpty();if(refresh){candidate.type="Microsoft";candidate.tokenRefresherAuth=true;candidate.label=verified.refreshToken;}else if(previous==null){candidate.type="Session";candidate.label=verified.username;}
        candidate.username=verified.username;candidate.uuid=verified.uuid;candidate.token=verified.accessToken;candidate.sessionToken=verified.accessToken;candidate.sessionTokenExpiresAt=verified.expiresAt;candidate.checkStatus="VALID";
        toTag(candidate); // Encryption must succeed before changing the in-memory library.
        if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())return TokenImportBatch.Stored.CANCELLED;
        if(duplicate>=0)accounts.set(duplicate,candidate);else accounts.add(candidate);
        try{save();}catch(RuntimeException error){if(duplicate>=0)accounts.set(duplicate,previous);else accounts.remove(candidate);throw new IllegalStateException("Could not save verified account.");}
        return duplicate>=0?TokenImportBatch.Stored.DUPLICATE:TokenImportBatch.Stored.IMPORTED;
    }
    public synchronized void remove(Account account){if(accounts.remove(account))save();}
    public synchronized boolean rename(String id,String name){Account a=findById(id);if(a==null||!a.offline()||name==null||!name.matches("[A-Za-z0-9_]{1,16}"))return false;for(Account other:accounts)if(other!=a&&other.displayName().equalsIgnoreCase(name))return false;a.label=name;a.username=name;a.uuid=Account.offlineUuid(name).toString();save();return true;}
    public synchronized void save() {
        if(file==null)throw new IllegalStateException("Store not loaded");CompoundTag root=new CompoundTag();ListTag list=new ListTag();for(Account a:accounts)list.add(toTag(a));root.put("accounts",list);
        File temp=new File(gameDir,file.getName()+".tmp");try{NbtIo.write(root,temp);if(file.isFile())Files.copy(file.toPath(),new File(gameDir,file.getName()+".bak").toPath(),StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}}catch(IOException error){throw new IllegalStateException("Could not save accounts",error);}
    }
    private static String text(CompoundTag t,String key){return t.contains(key)?t.getString(key):"";}
    private static Account fromTag(CompoundTag t){Account a=new Account();a.id=text(t,"id");a.stableId();a.type=text(t,"type");if(a.type.isEmpty())a.type="Cracked";a.username=text(t,"username");a.uuid=text(t,"uuid");a.token=readSecret(t,"encToken","token");a.sessionToken=readSecret(t,"encSessionToken","sessionToken");a.password=readSecret(t,"encPassword","password");a.label=readSecret(t,"encLabel","label");if(a.label.isEmpty())a.label=text(t,"name");a.sessionTokenExpiresAt=t.getLong("sessionExpiresAt");a.tokenRefresherAuth=t.getBoolean("tokenRefresherAuth");return a;}
    private static CompoundTag toTag(Account a){CompoundTag t=new CompoundTag();t.putString("id",a.stableId());t.putString("type",a.type);t.putString("username",safe(a.username));t.putString("uuid",safe(a.uuid));t.putString("encToken",secret(a.token));t.putString("encSessionToken",secret(a.sessionToken));t.putString("encPassword",secret(a.password));if("Microsoft".equals(a.type))t.putString("encLabel",secret(a.label));else t.putString("label",safe(a.label));t.putLong("sessionExpiresAt",a.sessionTokenExpiresAt);t.putBoolean("tokenRefresherAuth",a.tokenRefresherAuth);return t;}
    private static String safe(String s){return s==null?"":s;}
    private static String secret(String s){return s==null||s.isEmpty()?"":Base64.getEncoder().encodeToString(CryptoSeal.seal(s.getBytes(StandardCharsets.UTF_8)));}
    private static String readSecret(CompoundTag t,String encrypted,String legacy){String value=text(t,encrypted);if(value.isEmpty())return text(t,legacy);try{byte[] bytes=CryptoSeal.unseal(Base64.getDecoder().decode(value));if(bytes==null)throw new IllegalStateException("Saved credentials cannot be decrypted on this user/machine");return new String(bytes,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Could not decrypt saved account",error);}}
}
