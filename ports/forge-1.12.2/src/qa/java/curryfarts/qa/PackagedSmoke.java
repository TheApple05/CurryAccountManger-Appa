package curryfarts.qa;

import com.google.gson.GsonBuilder;
import curryfarts.legacy.*;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

/** Separate QA mod, excluded from the installable artifact; synthetic records only. */
@Mod(modid="curryfartsqa",name="Curryfarts Packaged QA",version="1",clientSideOnly=true,acceptableRemoteVersions="*",dependencies="required-after:curryfarts")
public final class PackagedSmoke {
    private int ticks,stage,stageTick,originalGuiScale;private Session original;private CurryAccountsScreen accounts;
    private final List<String>checks=new ArrayList<String>();private final Map<String,Object>result=new LinkedHashMap<String,Object>();
    private String id,oldClipboard="";
    @Mod.EventHandler public void init(FMLInitializationEvent event){if(Boolean.getBoolean("curryfarts.qa"))MinecraftForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END||stage<0)return;try{run(Minecraft.getMinecraft());}catch(Throwable error){finish(error);}}
    private void run(Minecraft mc)throws Exception{
        ticks++;if(ticks>3000)throw new AssertionError("Timeout at stage "+stage);if(ticks-stageTick<40)return;
        switch(stage){
        case 0:
            if(!(mc.currentScreen instanceof GuiMainMenu))return;
            require(!FMLLaunchHandler.isDeobfuscatedEnvironment(),"production Forge environment");
            File source=Loader.instance().getIndexedModList().get("curryfarts").getSource();
            Path expected=Paths.get(System.getProperty("curryfarts.qa.jar"));
            String sha=hash(Files.readAllBytes(source.toPath()));require(sha.equals(hash(Files.readAllBytes(expected))),"exact reobfuscated JAR loaded");
            result.put("jarSha256",sha);result.put("origin",source.toString());originalGuiScale=mc.gameSettings.guiScale;original=mc.getSession();oldClipboard=GuiScreen.getClipboardString();
            mc.displayGuiScreen(new GuiMultiplayer(mc.currentScreen));next();break;
        case 1:
            GuiButton button=find(mc.currentScreen,781122);require(button!=null,"Accounts button injected into multiplayer");
            capture(mc,"00-multiplayer.png");MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Post(mc.currentScreen,button,buttons(mc.currentScreen)));
            require(mc.currentScreen instanceof CurryAccountsScreen,"Accounts button opens native manager");accounts=(CurryAccountsScreen)mc.currentScreen;next();break;
        case 2:
            require(((GuiScreen)accounts).width>=600&&((GuiScreen)accounts).height>=340,"Auto GUI scale retains readable account layout");capture(mc,"01-account-manager.png");((GuiTextField)field(accounts,"label")).setText("CurryQa112");click(accounts,20);next();break;
        case 3:
            if(!mc.getSession().getUsername().equals("CurryQa112"))return;
            require(CurryfartsMod.store().all().size()==1,"UI adds and logs into offline account");
            capture(mc,"02-offline-account.png");Account a=CurryfartsMod.store().all().get(0);id=a.id;
            require(CurryfartsMod.store().rename(id,"CurryRenamed112"),"offline rename preserves ID");
            AccountStore reloaded=new AccountStore();reloaded.load(mc.gameDir);require(reloaded.findById(id)!=null&&reloaded.findById(id).displayName().equals("CurryRenamed112"),"NBT record reloads from disk");
            byte[] clear="synthetic-crypto-fixture".getBytes(StandardCharsets.UTF_8),sealed=CryptoSeal.seal(clear);
            require(sealed[0]==1&&Arrays.equals(clear,CryptoSeal.unseal(sealed)),"Windows DPAPI round trip on Java 8");
            LegacySessions.restore();require(mc.getSession().getUsername().equals(original.getUsername()),"launcher session restored");
            mc.displayGuiScreen(new CurryAccountsScreen(new GuiMultiplayer(new GuiMainMenu())));accounts=(CurryAccountsScreen)mc.currentScreen;next();break;
        case 4:
            capture(mc,"03-renamed-and-restored.png");
            Account record=CurryfartsMod.store().findById(id);record.token="synthetic-clipboard-fixture";CurryfartsMod.store().save();
            click(accounts,102);require(GuiScreen.getClipboardString().equals(record.token),"UI copies selected account token");GuiScreen.setClipboardString(oldClipboard);
            click(accounts,41);next();break;
        case 5:
            require(mc.currentScreen.getClass().getName().contains("GeneratorScreen"),"generator opens");capture(mc,"04-generator.png");
            ((GuiTextField)field(mc.currentScreen,"count")).setText("3");click(mc.currentScreen,2);
            require(CurryfartsMod.store().all().size()==4,"generator creates requested offline accounts");click(mc.currentScreen,3);accounts=(CurryAccountsScreen)mc.currentScreen;next();break;
        case 6:
            click(accounts,40);next();break;
        case 7:
            capture(mc,"05-confirmation.png");require(mc.currentScreen.getClass().getName().contains("ConfirmScreen"),"destructive action requires confirmation");click(mc.currentScreen,1);
            require(CurryfartsMod.store().all().size()==4,"cancel preserves saved accounts");accounts=(CurryAccountsScreen)mc.currentScreen;click(accounts,103);require(CurryfartsMod.store().findById(id)==null,"UI deletes selected account");
            next();break;
        case 8:
            capture(mc,"06-generated-accounts.png");click(accounts,81);require(mc.currentScreen instanceof TokenImporterScreen,"adjacent importer tab opens native screen");next();break;
        case 9:
            capture(mc,"07-token-importer.png");ImporterPackagedChecks.prepare(mc.currentScreen);((TokenImporterScreen)mc.currentScreen).startImport();next();break;
        case 10:
            if((Boolean)field(mc.currentScreen,"busy"))return;ImporterPackagedChecks.verify(mc.currentScreen,CurryfartsMod.store(),mc.gameDir,checks::add);capture(mc,"08-token-import-results.png");mc.displayGuiScreen(accounts);for(Account record2:CurryfartsMod.store().all())CurryfartsMod.store().remove(record2);
            require(CurryfartsMod.store().all().isEmpty(),"cleanup leaves isolated test store empty");mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));require(mc.gameSettings.guiScale==originalGuiScale,"original GUI scale restored after leaving manager");finish(null);break;
        default:throw new AssertionError("Unknown stage");}
    }
    private void next(){stage++;stageTick=ticks;}
    private void require(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @SuppressWarnings("unchecked") private List<GuiButton> buttons(GuiScreen screen)throws Exception{for(Field f:GuiScreen.class.getDeclaredFields())if(List.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object v=f.get(screen);if(v instanceof List){List<?>list=(List<?>)v;if(!list.isEmpty()&&list.get(0) instanceof GuiButton)return(List<GuiButton>)list;}}throw new IllegalStateException("Button list unavailable");}
    private GuiButton find(GuiScreen screen,int id)throws Exception{for(GuiButton b:buttons(screen))if(b.id==id)return b;return null;}
    private void click(GuiScreen screen,int id)throws Exception{GuiButton b=find(screen,id);if(b==null)throw new AssertionError("Missing button "+id);for(Method m:screen.getClass().getDeclaredMethods())if(m.getParameterTypes().length==1&&m.getParameterTypes()[0]==GuiButton.class){m.setAccessible(true);m.invoke(screen,b);return;}throw new AssertionError("Action handler unavailable");}
    private void capture(Minecraft mc,String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    private static String hash(byte[] data)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))out.append(String.format("%02x",b&255));return out.toString();}
    private void finish(Throwable error){stage=-1;try{GuiScreen.setClipboardString(oldClipboard);result.put("status",error==null?"PASSED":"FAILED");result.put("checks",checks);if(error!=null){result.put("error",error.toString());error.printStackTrace();}Files.write(Paths.get(Minecraft.getMinecraft().gameDir.toString(),"qa-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception failed){failed.printStackTrace();}Minecraft.getMinecraft().shutdown();}
}
