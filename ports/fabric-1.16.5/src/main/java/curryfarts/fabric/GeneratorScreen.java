package curryfarts.fabric;

import curryfarts.legacy.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

public final class GeneratorScreen extends Screen {
    private final AccountsScreen parent;
    private PaletteField count,password;
    private boolean fromList,setPassword=true,sharedPassword,listFocused;
    private String listText="",result="",savedCount="10",savedPassword="";
    private int x,y;
    private static final SecureRandom RANDOM=new SecureRandom();
    public GeneratorScreen(AccountsScreen parent){super(new TextComponent("Generate accounts"));this.parent=parent;loadSettings();}
    @Override protected void init(){
        if(count!=null){savedCount=count.getValue();savedPassword=password.getValue();}
        x=width/2-160;y=height/2-128;
        addButton(new PaletteButton(x+12,y+34,130,20,"Random",!fromList?Palette.PRIMARY:Palette.PANEL,Palette.BORDER,b->{fromList=false;rebuild();}));
        addButton(new PaletteButton(x+148,y+34,160,20,"From list",fromList?Palette.PRIMARY:Palette.PANEL,Palette.BORDER,b->{fromList=true;rebuild();}));
        count=new PaletteField(font,x+70,y+66,60,20,"Count");count.setValue(savedCount);count.visible=!fromList;addButton(count);
        password=new PaletteField(font,x+12,y+147,296,20,"Shared password");password.setValue(savedPassword);password.visible=setPassword&&sharedPassword;addButton(password);
        addButton(new PaletteButton(x+12,y+96,140,20,setPassword?"Password: on":"Password: off",setPassword?Palette.PRIMARY:Palette.PANEL,Palette.BORDER,b->{setPassword=!setPassword;rebuild();}));
        addButton(new PaletteButton(x+158,y+96,150,20,sharedPassword?"Set for all":"Generate password",Palette.PANEL,Palette.BORDER,b->{sharedPassword=!sharedPassword;rebuild();}));
        addButton(new PaletteButton(x+12,y+214,130,20,"Add accounts",Palette.PANEL,Palette.BORDER,b->generate()));
        addButton(new PaletteButton(x+220,y+214,88,20,"Close",Palette.PANEL,Palette.BORDER,b->onClose()));
    }
    private void rebuild(){init(minecraft,width,height);}
    @Override public void tick(){count.tick();password.tick();}
    @Override public void onClose(){savedPassword=password.getValue();saveSettings();minecraft.setScreen(parent);}
    private void generate(){
        LinkedHashSet<String> names=new LinkedHashSet<String>();int skipped=0;
        Set<String> existing=new HashSet<String>();for(Account a:CurryfartsClient.ACCOUNTS.all())existing.add(AccountsScreen.name(a).toLowerCase(Locale.ROOT));
        if(fromList){for(String token:listText.split("[\\s,;]+")){String name=token.split(":",2)[0].trim();if(!name.matches("[A-Za-z0-9_]{1,16}")||existing.contains(name.toLowerCase(Locale.ROOT))||!names.add(name))skipped++;}}
        else {int requested;try{requested=Integer.parseInt(count.getValue());}catch(Exception error){result="Enter a number from 1 to 5000.";return;}requested=Math.max(1,Math.min(5000,requested));while(names.size()<requested){String name="Curry"+Integer.toUnsignedString(RANDOM.nextInt(),36);if(name.length()>16)name=name.substring(0,16);if(!existing.contains(name.toLowerCase(Locale.ROOT)))names.add(name);}}
        List<Account> generated=new ArrayList<Account>();for(String name:names){Account a=new Account();a.type="Generated";a.label=name;if(setPassword)a.password=sharedPassword?password.getValue():randomPassword();if(Auth.resolve(a))generated.add(a);}
        int added=CurryfartsClient.ACCOUNTS.addAll(generated);result="Added "+added+" accounts"+(skipped>0?", skipped "+skipped:"")+".";saveSettings();parent.toast(result,Palette.TEXT);
    }
    private static String randomPassword(){String chars="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";StringBuilder result=new StringBuilder();for(int n=9+RANDOM.nextInt(8);n>0;n--)result.append(chars.charAt(RANDOM.nextInt(chars.length())));return result.toString();}
    @Override public void render(PoseStack pose,int mx,int my,float delta){
        fill(pose,0,0,width,height,Palette.BACKGROUND);parent.panel(pose,x,y,320,250,Palette.PANEL);font.draw(pose,"Generate accounts",x+12,y+12,Palette.TEXT);
        if(!fromList)font.draw(pose,"Count:",x+12,y+72,Palette.MUTED);
        else {fill(pose,x+12,y+63,x+308,y+91,listFocused?Palette.FOCUS:Palette.BORDER);fill(pose,x+13,y+64,x+307,y+90,Palette.FIELD);String display=listText.isEmpty()?"Paste names, one per line or name:password":listText.replace('\n',' ');font.draw(pose,font.plainSubstrByWidth(display,286),x+17,y+72,listText.isEmpty()?Palette.MUTED:Palette.TEXT);}
        font.draw(pose,"List accepts spaces, commas or line breaks.",x+12,y+125,Palette.MUTED);
        if(!result.isEmpty())font.draw(pose,font.plainSubstrByWidth(result,296),x+12,y+183,Palette.TEXT);
        super.render(pose,mx,my,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){listFocused=fromList&&mx>x+12&&mx<x+308&&my>y+63&&my<y+91;if(listFocused){setFocused(null);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean charTyped(char value,int modifiers){if(listFocused&&value>=32&&listText.length()<100000){listText+=value;return true;}return super.charTyped(value,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(listFocused){if(Screen.isPaste(key)){listText+=minecraft.keyboardHandler.getClipboard();return true;}if(key==259&&!listText.isEmpty()){listText=listText.substring(0,listText.length()-1);return true;}if(key==257){listText+='\n';return true;}if(Screen.isSelectAll(key)){listText="";return true;}}return super.keyPressed(key,scan,modifiers);}
    private File settingsFile(){return new File(CurryfartsClient.CONFIG,"generator-1.16.5.json");}
    private void loadSettings(){File file=settingsFile();if(!file.isFile())file=new File(CurryfartsClient.CONFIG,"config.json");if(!file.isFile())return;try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){JsonObject o=new JsonParser().parse(reader).getAsJsonObject();if(o.has("accountGenSetPassword"))setPassword=o.get("accountGenSetPassword").getAsBoolean();if(o.has("accountGenPasswordMode"))sharedPassword="Set For All".equalsIgnoreCase(o.get("accountGenPasswordMode").getAsString());if(o.has("accountGenSharedPassword"))savedPassword=o.get("accountGenSharedPassword").getAsString();}catch(Exception ignored){}}
    private void saveSettings(){try{CurryfartsClient.CONFIG.mkdirs();JsonObject o=new JsonObject();o.addProperty("accountGenSetPassword",setPassword);o.addProperty("accountGenPasswordMode",sharedPassword?"Set For All":"Generate");o.addProperty("accountGenSharedPassword",password==null?savedPassword:password.getValue());try(Writer writer=new OutputStreamWriter(new FileOutputStream(settingsFile()),StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(o,writer);}}catch(IOException ignored){}}
}
