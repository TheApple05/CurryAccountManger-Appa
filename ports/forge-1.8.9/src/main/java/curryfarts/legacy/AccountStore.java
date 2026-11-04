package curryfarts.legacy;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
        if(file.isFile())try {NBTTagCompound root=CompressedStreamTools.read(file);if(root!=null){NBTTagList list=root.getTagList("accounts",10);for(int i=0;i<list.tagCount();i++)accounts.add(fromTag(list.getCompoundTagAt(i)));}}catch(Exception error){throw new IllegalStateException("Could not read saved accounts; file preserved",error);}
        importMeteor();
    }
    private void importMeteor(){
        File source=new File(gameDir,"meteor-client/accounts.nbt");if(!source.isFile())return;
        File stateFile=new File(gameDir,"autism-meteor-import.nbt");
        try{
            NBTTagCompound state=stateFile.isFile()?CompressedStreamTools.read(stateFile):new NBTTagCompound();if(state==null)state=new NBTTagCompound();
            String signature=source.length()+":"+source.lastModified();if(signature.equals(state.getString("accountsSig")))return;
            Set<String> imported=new LinkedHashSet<String>();NBTTagList old=state.getTagList("importedAccounts",8);for(int i=0;i<old.tagCount();i++)imported.add(old.getStringTagAt(i));
            NBTTagCompound root=CompressedStreamTools.read(source);List<Account> found=new ArrayList<Account>();if(root!=null){NBTTagList list=root.getTagList("accounts",10);for(int i=0;i<list.tagCount();i++){
                NBTTagCompound t=list.getCompoundTagAt(i),cache=t.getCompoundTag("cache");Account a=new Account();a.type=t.getString("type");if(!Arrays.asList("Cracked","Microsoft","Session","TheAltening").contains(a.type))continue;
                a.label=t.getString("name");a.username=cache.getString("username");a.uuid=cache.getString("uuid");a.token=t.getString("token");
                if("Cracked".equals(a.type)&&a.username.isEmpty())a.username=a.label;if("TheAltening".equals(a.type)&&a.token.isEmpty())a.token=a.label;
                String key=a.type+(!a.uuid.isEmpty()?"|u|"+a.uuid.toLowerCase(Locale.ROOT):"|n|"+a.displayName().toLowerCase(Locale.ROOT));if(imported.add(key))found.add(a);
            }}
            addAll(found);NBTTagList keys=new NBTTagList();for(String key:imported)keys.appendTag(new net.minecraft.nbt.NBTTagString(key));state.setTag("importedAccounts",keys);state.setString("accountsSig",signature);CompressedStreamTools.write(state,stateFile);
        }catch(Exception error){System.err.println("[Curryfarts] Meteor account import could not complete; source file preserved.");}
    }
    public synchronized List<Account> all(){return new ArrayList<Account>(accounts);}
    public synchronized Account findById(String id){for(Account a:accounts)if(a.stableId().equals(id))return a;return null;}
    public synchronized boolean add(Account account){if(account==null)return false;for(Account a:accounts)if(a.type.equals(account.type)&&a.displayName().equalsIgnoreCase(account.displayName()))return false;accounts.add(account);save();return true;}
    public synchronized int addAll(List<Account> incoming){int added=0;Set<String> seen=new HashSet<String>();for(Account a:accounts)seen.add(a.type+":"+a.displayName().toLowerCase(Locale.ROOT));for(Account a:incoming)if(a!=null&&seen.add(a.type+":"+a.displayName().toLowerCase(Locale.ROOT))){accounts.add(a);added++;}if(added>0)save();return added;}
    public synchronized void remove(Account account){if(accounts.remove(account))save();}
    public synchronized boolean rename(String id,String name){Account a=findById(id);if(a==null||!a.offline()||name==null||!name.matches("[A-Za-z0-9_]{1,16}"))return false;for(Account other:accounts)if(other!=a&&other.displayName().equalsIgnoreCase(name))return false;a.label=name;a.username=name;a.uuid=Account.offlineUuid(name).toString();save();return true;}
    public synchronized void save() {
        if(file==null)throw new IllegalStateException("Store not loaded");NBTTagCompound root=new NBTTagCompound();NBTTagList list=new NBTTagList();for(Account a:accounts)list.appendTag(toTag(a));root.setTag("accounts",list);
        File temp=new File(gameDir,file.getName()+".tmp");try{CompressedStreamTools.write(root,temp);if(file.isFile())Files.copy(file.toPath(),new File(gameDir,file.getName()+".bak").toPath(),StandardCopyOption.REPLACE_EXISTING);try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}}catch(IOException error){throw new IllegalStateException("Could not save accounts",error);}
    }
    private static String text(NBTTagCompound t,String key){return t.hasKey(key)?t.getString(key):"";}
    private static Account fromTag(NBTTagCompound t){Account a=new Account();a.id=text(t,"id");a.stableId();a.type=text(t,"type");if(a.type.isEmpty())a.type="Cracked";a.username=text(t,"username");a.uuid=text(t,"uuid");a.token=readSecret(t,"encToken","token");a.sessionToken=readSecret(t,"encSessionToken","sessionToken");a.password=readSecret(t,"encPassword","password");a.label=readSecret(t,"encLabel","label");if(a.label.isEmpty())a.label=text(t,"name");a.sessionTokenExpiresAt=t.getLong("sessionExpiresAt");return a;}
    private static NBTTagCompound toTag(Account a){NBTTagCompound t=new NBTTagCompound();t.setString("id",a.stableId());t.setString("type",a.type);t.setString("username",safe(a.username));t.setString("uuid",safe(a.uuid));t.setString("encToken",secret(a.token));t.setString("encSessionToken",secret(a.sessionToken));t.setString("encPassword",secret(a.password));if("Microsoft".equals(a.type))t.setString("encLabel",secret(a.label));else t.setString("label",safe(a.label));t.setLong("sessionExpiresAt",a.sessionTokenExpiresAt);return t;}
    private static String safe(String s){return s==null?"":s;}
    private static String secret(String s){return s==null||s.isEmpty()?"":Base64.getEncoder().encodeToString(CryptoSeal.seal(s.getBytes(StandardCharsets.UTF_8)));}
    private static String readSecret(NBTTagCompound t,String encrypted,String legacy){String value=text(t,encrypted);if(value.isEmpty())return text(t,legacy);try{byte[] bytes=CryptoSeal.unseal(Base64.getDecoder().decode(value));if(bytes==null)throw new IllegalStateException("Saved credentials cannot be decrypted on this user/machine");return new String(bytes,StandardCharsets.UTF_8);}catch(Exception error){throw new IllegalStateException("Could not decrypt saved account",error);}}
}
