package curryfarts.qa;

import curryfarts.fabric.*;
import curryfarts.legacy.*;
import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class PackagedSmoke implements ClientModInitializer {
    private int ticks,stage,wait;
    private User original;
    private Object originalService,originalSkinManager,originalSocial;
    private AccountsScreen screen;
    private Account account;
    private final List<String> checks=new ArrayList<String>();
    private final Map<String,Object> result=new LinkedHashMap<String,Object>();
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(mc->{try{tick(mc);}catch(Throwable error){finish(mc,error);}});}
    private void tick(Minecraft mc)throws Exception {
        if(stage<0)return;if(++ticks>2400)throw new AssertionError("Timeout at stage "+stage);if(mc.getOverlay()!=null||ticks-wait<35)return;
        switch(stage){
            case 0:
                if(!(mc.screen instanceof TitleScreen)){mc.setScreen(new TitleScreen());return;}
                require(!FabricLoader.getInstance().isDevelopmentEnvironment(),"production environment");
                Path origin=FabricLoader.getInstance().getModContainer("curryfarts").get().getOrigin().getPaths().get(0);
                String sha=hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(origin)));result.put("modOrigin",origin.toString());result.put("sha256",sha);
                require(sha.equals(System.getProperty("curryfarts.qa.sha256")),"exact installable JAR loaded");
                original=mc.getUser();originalService=mc.getMinecraftSessionService();originalSkinManager=mc.getSkinManager();originalSocial=mc.getPlayerSocialManager();
                mc.setScreen(new JoinMultiplayerScreen(mc.screen));next();break;
            case 1:
                Button b=button(mc,"Accounts");require(b!=null,"multiplayer Accounts button injected");capture(mc,"00-multiplayer.png");b.onPress();require(mc.screen instanceof AccountsScreen,"Accounts button opens screen");screen=(AccountsScreen)mc.screen;hoverPrimary(mc);next();break;
            case 2:
                capture(mc,"01-empty.png");PaletteField input=(PaletteField)field(screen,"nameField");input.setValue("CurryQA116");input.mouseClicked(input.x+5,input.y+5,0);require(input.getCursorPosition()<=1,"themed field retains mouse caret position");screen.addAccount();next();break;
            case 3:
                if(screen.busy)return;require(mc.getUser().getName().equals("CurryQA116"),"offline login updates active identity");
                require(CurryfartsClient.ACCOUNTS.all().size()==1,"offline account added to store");account=CurryfartsClient.ACCOUNTS.all().get(0);capture(mc,"02-offline-login.png");
                verifySkinModels();
                require(CurryfartsClient.ACCOUNTS.rename(account.id,"CurryRenamed"),"offline rename preserves stable ID");
                byte[] clear="synthetic-port-test".getBytes(StandardCharsets.UTF_8),sealed=CryptoSeal.seal(clear);
                require(sealed[0]==1&&Arrays.equals(clear,CryptoSeal.unseal(sealed)),"Java 8 Windows DPAPI round trip");
                mc.setScreen(new GeneratorScreen(screen));next();break;
            case 4:
                GeneratorScreen generator=(GeneratorScreen)mc.screen;set(generator,"fromList",true);set(generator,"listText","AlphaQA116\nBetaQA116:ignored");set(generator,"sharedPassword",true);generator.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());((PaletteField)field(generator,"password")).setValue("synthetic-password");button(mc,"Add accounts").onPress();require(CurryfartsClient.ACCOUNTS.all().size()==3,"list generator adds valid accounts");next();break;
            case 5:
                capture(mc,"03-generator.png");mc.screen.onClose();CurryfartsClient.ACCOUNTS.load(mc.gameDirectory);require(CurryfartsClient.ACCOUNTS.findById(account.id)!=null,"NBT reload retains stable ID");require(CurryfartsClient.ACCOUNTS.findById(account.id).username.equals("CurryRenamed"),"renamed account survives disk reload");
                require(CurryfartsClient.ACCOUNTS.all().get(1).password.equals("synthetic-password"),"generated password survives encrypted persistence");
                require(Sessions.restore(),"launcher session restore succeeds");require(mc.getUser().getName().equals(original.getName()),"launcher username restored");
                require(mc.getMinecraftSessionService()==originalService&&mc.getSkinManager()==originalSkinManager&&mc.getPlayerSocialManager()==originalSocial,"launcher session/skin/social services restored");next();break;
            case 6:
                capture(mc,"04-restored.png");mc.setScreen(new ConfirmScreen(screen));next();break;
            case 7:
                capture(mc,"05-clear-confirmation.png");button(mc,"Cancel").onPress();require(mc.screen==screen&&CurryfartsClient.ACCOUNTS.all().size()==3,"cancel clear preserves accounts");button(mc,"Microsoft").onPress();next();break;
            case 8:
                capture(mc,"06-microsoft-category.png");require(!((PaletteField)field(screen,"nameField")).visible,"Microsoft category hides username field");
                screen.clearAll();CurryfartsClient.ACCOUNTS.load(mc.gameDirectory);require(CurryfartsClient.ACCOUNTS.all().isEmpty(),"clear/delete persists to disk");finish(mc,null);break;
        }
    }
    private static Button button(Minecraft mc,String name){for(Object child:mc.screen.children())if(child instanceof Button&&((Button)child).getMessage().getString().equals(name))return (Button)child;return null;}
    private static void hoverPrimary(Minecraft mc){Button b=button(mc,"Add Cracked");if(b!=null)org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),(b.x+10d)*mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth(),(b.y+10d)*mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight());}
    private static Object field(Object instance,String name)throws Exception{Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    private static void set(Object instance,String name,Object value)throws Exception{Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}
    private void require(boolean value,String name){if(!value)throw new AssertionError(name);checks.add(name);}
    private void next(){stage++;wait=ticks;}
    private void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getWindow().getWidth(),mc.getWindow().getHeight(),mc.getMainRenderTarget(),unused->{});}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    private void verifySkinModels()throws Exception {
        SkinPreview preview=new SkinPreview();Account a=new Account();a.id="qa-resolved-model";a.label="ModelQA";a.username=a.label;a.uuid=Account.offlineUuid(a.label).toString();
        net.minecraft.resources.ResourceLocation texture=net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(Auth.uuid(a.uuid));
        Method cache=SkinPreview.class.getDeclaredMethod("cacheSkin",String.class,net.minecraft.resources.ResourceLocation.class,com.mojang.authlib.minecraft.MinecraftProfileTexture.class);cache.setAccessible(true);
        Method select=SkinPreview.class.getDeclaredMethod("selectModel",Account.class);select.setAccessible(true);
        Map<String,String> metadata=new HashMap<String,String>();metadata.put("model","slim");
        cache.invoke(null,a.id,texture,new com.mojang.authlib.minecraft.MinecraftProfileTexture("https://textures.minecraft.net/texture/qa",metadata));
        require(select.invoke(preview,a)==field(preview,"slim"),"resolved slim skin metadata selects slim model");
        cache.invoke(null,a.id,texture,new com.mojang.authlib.minecraft.MinecraftProfileTexture("https://textures.minecraft.net/texture/qa",Collections.<String,String>emptyMap()));
        require(select.invoke(preview,a)==field(preview,"wide"),"resolved classic skin metadata selects wide model");
    }
    private void finish(Minecraft mc,Throwable error){stage=-1;try{result.put("status",error==null?"PASSED":"FAILED");result.put("checks",checks);if(error!=null){result.put("error",error.toString());error.printStackTrace();}Files.write(Paths.get(System.getProperty("curryfarts.qa.result")),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception e){e.printStackTrace();}mc.stop();}
}
