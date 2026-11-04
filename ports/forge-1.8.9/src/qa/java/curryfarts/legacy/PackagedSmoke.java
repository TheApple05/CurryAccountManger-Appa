package curryfarts.legacy;

import com.google.gson.GsonBuilder;
import java.io.*;
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
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="curryfartsqa",name="Curryfarts isolated packaged QA",version="1",clientSideOnly=true,acceptableRemoteVersions="*")
public final class PackagedSmoke {
    private int tick,stage,last,originalGuiScale; private Session launcher; private CurryAccountsScreen screen; private String id;
    private final List<String>checks=new ArrayList<String>();private final Map<String,Object>result=new LinkedHashMap<String,Object>();
    @Mod.EventHandler public void init(FMLInitializationEvent event){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END||stage<0)return;try{run(Minecraft.getMinecraft());}catch(Throwable error){finish(Minecraft.getMinecraft(),error);}}
    private void run(Minecraft mc)throws Exception {
        tick++;if(tick>2400)throw new AssertionError("Timed out at stage "+stage);if(tick-last<35)return;
        switch(stage){
        case 0:
            if(!(mc.currentScreen instanceof GuiMainMenu))return;
            File origin=Loader.instance().getIndexedModList().get("curryfarts").getSource();
            result.put("modOrigin",origin.getAbsolutePath());result.put("sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(origin.toPath()))));
            require(result.get("sha256").equals(System.getProperty("curryfarts.qa.sha256")),"exact packaged JAR loaded");
            originalGuiScale=mc.gameSettings.guiScale;launcher=mc.getSession();mc.displayGuiScreen(new GuiMultiplayer(mc.currentScreen));next();break;
        case 1:
            List<GuiButton>buttons=buttons(mc.currentScreen);GuiButton entry=null;for(GuiButton b:buttons)if(b.displayString.equals("Accounts"))entry=b;
            require(entry!=null,"multiplayer Accounts button injected");snap(mc,"00-multiplayer.png");
            MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Post(mc.currentScreen,entry,buttons));
            require(mc.currentScreen instanceof CurryAccountsScreen,"injected Accounts button opens manager");screen=(CurryAccountsScreen)mc.currentScreen;next();break;
        case 2:
            require(((GuiScreen)screen).width>=600&&((GuiScreen)screen).height>=340,"Auto GUI scale retains readable account layout");snap(mc,"01-empty-accounts.png");((GuiTextField)field(screen,"label")).setText("Curry189QA");call(screen,"addAccount");next();break;
        case 3:
            if(!mc.getSession().getUsername().equals("Curry189QA")||(Boolean)field(screen,"busy"))return;
            Account account=CurryfartsMod.store().all().get(0);id=account.stableId();require(LegacySessions.current(account),"offline account login and current-session marker");
            require(CurryfartsMod.store().all().size()==1,"UI adds saved offline account");snap(mc,"02-offline-account.png");
            byte[] clear="synthetic-legacy-fixture".getBytes(StandardCharsets.UTF_8);byte[] encrypted=CryptoSeal.seal(clear);require(encrypted[0]==1,"Windows DPAPI uses existing legacy JNA");require(Arrays.equals(clear,CryptoSeal.unseal(encrypted)),"DPAPI ciphertext round trip on Java 8");
            Path fixture=mc.mcDataDir.toPath().resolve("legacy-fixture.bin");if(Files.isRegularFile(fixture)){byte[] old=Files.readAllBytes(fixture),framed=new byte[old.length+1];framed[0]=1;System.arraycopy(old,0,framed,1,old.length);require(new String(CryptoSeal.unseal(framed),StandardCharsets.UTF_8).equals("synthetic-legacy-compatibility-fixture"),"original Java25 ciphertext decrypts on Java8");}
            require(CurryfartsMod.store().rename(id,"Curry189Renamed"),"offline account rename");
            Account generated=new Account();generated.type="Generated";generated.label="Generated189QA";generated.password="synthetic-password";require(Auth.resolve(generated)&&CurryfartsMod.store().add(generated),"generated account credentials persist");
            AccountStore reloaded=new AccountStore();reloaded.load(mc.mcDataDir);require(reloaded.findById(id).displayName().equals("Curry189Renamed"),"NBT reload preserves stable ID and rename");require(reloaded.all().get(1).password.equals("synthetic-password"),"encrypted password survives NBT reload");
            LegacySessions.restore();require(mc.getSession().getUsername().equals(launcher.getUsername()),"launcher session restoration");mc.displayGuiScreen(new CurryAccountsScreen(new GuiMultiplayer(new GuiMainMenu())));screen=(CurryAccountsScreen)mc.currentScreen;next();break;
        case 4:
            snap(mc,"03-renamed-and-restored.png");action(screen,41);require(mc.currentScreen.getClass().getName().endsWith("GeneratorScreen"),"generator screen opens");next();break;
        case 5:
            snap(mc,"04-generator.png");mc.displayGuiScreen(screen);action(screen,40);require(mc.currentScreen.getClass().getName().endsWith("ConfirmScreen"),"clear confirmation opens");next();break;
        case 6:
            snap(mc,"05-clear-confirmation.png");mc.displayGuiScreen(screen);((GuiTextField)field(screen,"search")).setText("Renamed");screen.rebuild();require(((List<?>)field(screen,"rows")).size()==1,"search filters saved account rows");next();break;
        case 7:
            snap(mc,"06-search.png");action(screen,81);require(mc.currentScreen instanceof TokenImporterScreen,"adjacent importer tab opens native screen");next();break;
        case 8:
            snap(mc,"07-token-importer.png");ImporterPackagedChecks.prepare(mc.currentScreen);((TokenImporterScreen)mc.currentScreen).startImport();next();break;
        case 9:
            if((Boolean)field(mc.currentScreen,"busy"))return;ImporterPackagedChecks.verify(mc.currentScreen,CurryfartsMod.store(),mc.mcDataDir,checks::add);snap(mc,"08-token-import-results.png");mc.displayGuiScreen(screen);for(Account a:CurryfartsMod.store().all())CurryfartsMod.store().remove(a);require(CurryfartsMod.store().all().isEmpty(),"saved-account deletion");mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));require(mc.gameSettings.guiScale==originalGuiScale,"original GUI scale restored after leaving manager");finish(mc,null);break;
        }
    }
    private void next(){stage++;last=tick;}
    private void require(boolean pass,String message){if(!pass)throw new AssertionError(message);checks.add(message);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private static void action(GuiScreen screen,int id)throws Exception{for(Method m:screen.getClass().getDeclaredMethods())if(m.getParameterTypes().length==1&&m.getParameterTypes()[0]==GuiButton.class){m.setAccessible(true);for(GuiButton b:buttons(screen))if(b.id==id){m.invoke(screen,b);return;}}throw new AssertionError("Missing button "+id);}
    @SuppressWarnings("unchecked") private static List<GuiButton>buttons(GuiScreen screen)throws Exception{for(String name:new String[]{"buttonList","field_146292_n"})try{Field f=GuiScreen.class.getDeclaredField(name);f.setAccessible(true);return(List<GuiButton>)f.get(screen);}catch(NoSuchFieldException ignored){}throw new AssertionError("Cannot inspect screen buttons");}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v&255));return b.toString();}
    private void snap(Minecraft mc,String name){ScreenShotHelper.saveScreenshot(mc.mcDataDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    private void finish(Minecraft mc,Throwable error){stage=-1;try{result.put("status",error==null?"PASSED":"FAILED");result.put("checks",checks);if(error!=null){result.put("error",error.toString());error.printStackTrace();}Files.write(Paths.get(System.getProperty("curryfarts.qa.result")),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception e){e.printStackTrace();}mc.shutdown();}
}
