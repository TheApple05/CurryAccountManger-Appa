package curryfarts.legacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Native Java 8 screen using the supplied palette, with all account categories. */
public final class CurryAccountsScreen extends LegacyScreen {
    private final GuiScreen parent;
    private String type="Cracked",filter="",message="",renameId="";
    private final String[] types={"Cracked","TheAltening","Session","Microsoft"};
    private GuiTextField label,token,search;
    private int left,top,panelWidth,listTop,scroll;
    private volatile boolean busy;
    private volatile int operation;
    private volatile Thread operationThread;
    private Account selected;
    private final LegacyPreview previewModel=new LegacyPreview();
    private final List<Account> rows=new ArrayList<Account>();
    private long revision;
    public CurryAccountsScreen(GuiScreen parent){this.parent=parent;LegacySessions.original();}
    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);buttonList.clear();
        panelWidth=Math.min(520,width-24);left=(width-panelWidth)/2;top=20;listTop=height<300?160:204;
        label=new GuiTextField(1,fontRenderer,left+10,top+68,Math.max(100,panelWidth-216),18);
        token=new GuiTextField(2,fontRenderer,left+10,top+68,Math.max(100,panelWidth-216),18);
        search=new GuiTextField(3,fontRenderer,left+10,top+120,Math.max(100,panelWidth-216),18);
        label.setMaxStringLength(256);token.setMaxStringLength(4096);search.setMaxStringLength(64);
        for(GuiTextField f:new GuiTextField[]{label,token,search}){f.setEnableBackgroundDrawing(false);f.setTextColor(Palette.TEXT);f.setDisabledTextColour(Palette.DISABLED);}
        rebuild();
    }
    private void add(int id,int x,int y,int w,String text,int fill,int edge){buttonList.add(new PaletteButton(id,x,y,w,18,text,fill,edge));}
    public void rebuild() {
        buttonList.clear();add(0,10,6,52,"Back",Palette.PANEL,Palette.BORDER);int navX=Math.max(70,left),navWidth=(width-navX-12)/2;add(80,navX,6,navWidth-2,"Session Token Switcher",Palette.PRIMARY,Palette.BORDER);add(81,navX+navWidth+2,6,navWidth-2,"Session Token Importer",Palette.PANEL,Palette.BORDER);
        int editWidth=Math.max(100,panelWidth-216),tabWidth=Math.max(25,(editWidth-6)/4);
        for(int i=0;i<4;i++)add(10+i,left+10+i*(tabWidth+2),top+38,tabWidth,types[i].equals("TheAltening")?"Altening":types[i],types[i].equals(type)?Palette.PRIMARY:Palette.PANEL,Palette.BORDER);
        add(20,left+10,top+94,Math.max(90,editWidth-74),renameId.isEmpty()?("Microsoft".equals(type)?"Microsoft login":"Add "+("TheAltening".equals(type)?"Altening":type)):"Save rename",Palette.PRIMARY,Palette.BORDER);
        add(21,left+editWidth-56,top+94,66,"Clear",Palette.PANEL,Palette.BORDER);
        add(22,left+panelWidth-196,top+132,112,"Restore launcher",Palette.PANEL,Palette.BORDER);
        add(23,left+panelWidth-80,top+132,68,"Copy token",Palette.PANEL,Palette.BORDER);
        if(busy)add(24,left+10,top+94,editWidth,"Cancel operation",Palette.PANEL,Palette.BORDER);
        int fw=Math.max(35,(panelWidth-24)/5);String[]filters={"Cracked","Microsoft","Session","TheAltening","Generated"};
        for(int i=0;i<filters.length;i++)add(30+i,left+10+i*(fw+1),listTop-24,fw,filters[i].equals("TheAltening")?"Altening":filters[i],filters[i].equals(filter)?Palette.PRIMARY:Palette.PANEL,Palette.BORDER);
        add(40,left+panelWidth-304,listTop+4,74,"Clear list",Palette.PANEL,Palette.BORDER);
        add(41,left+panelWidth-226,listTop+4,70,"Generate",Palette.PANEL,Palette.BORDER);
        add(42,left+panelWidth-152,listTop+4,82,"Clear expired",Palette.PANEL,Palette.BORDER);
        add(43,left+panelWidth-66,listTop+4,56,"Check",Palette.PANEL,Palette.BORDER);
        rows.clear();String query=search==null?"":search.getText().toLowerCase(Locale.ROOT);
        for(Account a:CurryfartsMod.store().all())if((filter.isEmpty()||filter.equals(a.type))&&a.displayName().toLowerCase(Locale.ROOT).contains(query))rows.add(a);
        int maxRows=Math.max(1,(height-listTop-42)/25);scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-maxRows)));
        for(int i=scroll;i<rows.size()&&i<scroll+maxRows;i++){
            Account a=rows.get(i);int y=listTop+29+(i-scroll)*25;boolean current=LegacySessions.current(a);
            add(100+i*4,left+panelWidth-182,y,54,current?"Active":"Login",Palette.PANEL,Palette.BORDER);
            add(101+i*4,left+panelWidth-124,y,32,"Edit",Palette.PANEL,Palette.BORDER);
            add(102+i*4,left+panelWidth-88,y,36,"Copy",Palette.PANEL,Palette.BORDER);
            add(103+i*4,left+panelWidth-48,y,38,"Del",Palette.PANEL,Palette.BORDER);
        }
        for(GuiButton b:buttonList)if(b.id!=0&&b.id!=24)b.enabled=!busy;
        revision=System.nanoTime();
    }
    private void field(GuiTextField field,String hint,int mouseX,int mouseY,float partial) {
        int x=field.x,y=field.y,w=field.width;
        drawRect(x-2,y-3,x+w+2,y+15,field.isFocused()?Palette.FOCUS:(mouseX>=x-2&&mouseX<x+w+2&&mouseY>=y-3&&mouseY<y+15?Palette.HOVER:Palette.BORDER));
        drawRect(x-1,y-2,x+w+1,y+14,Palette.FIELD);field.drawTextBox();
        if(field.getText().isEmpty()&&!field.isFocused())fontRenderer.drawString(hint,x+2,y+2,Palette.MUTED);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        GlStateManager.disableDepth();
        drawRect(0,0,width,height,Palette.BACKGROUND);
        panel(left,top,panelWidth,Math.max(140,listTop-top-32),Palette.PANEL);
        panel(left,listTop,panelWidth,Math.max(45,height-listTop-12),Palette.PANEL);
        fontRenderer.drawString("Accounts",left+10,top+10,Palette.TEXT);
        fontRenderer.drawString("Accounts",left+10,listTop+10,Palette.TEXT);
        if(!"Microsoft".equals(type))field("Cracked".equals(type)?label:token,"Cracked".equals(type)?"Username":"Access token",mouseX,mouseY,partial);
        field(search,"Search accounts...",mouseX,mouseY,partial);
        int previewX=left+panelWidth-196;
        fontRenderer.drawString("Skin preview",previewX,top+11,Palette.TEXT);
        Account preview=selected;
        if(preview==null){preview=new Account();preview.username=mc.getSession().getUsername();preview.uuid=mc.getSession().getPlayerID();preview.type="Launcher";}
        previewModel.render(preview,previewX+90,top+105,35,mouseX,mouseY);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(preview.displayName(),180),previewX,top+110,LegacySessions.current(preview)?Palette.FOCUS:Palette.TEXT);
        int maxRows=Math.max(1,(height-listTop-42)/25);
        for(int i=scroll;i<rows.size()&&i<scroll+maxRows;i++){
            Account a=rows.get(i);int y=listTop+27+(i-scroll)*25;boolean current=LegacySessions.current(a);
            drawRect(left+8,y,left+panelWidth-8,y+23,current?Palette.CURRENT:(a==selected?Palette.PANEL:Palette.PANEL));
            if(current)drawRect(left+8,y,left+10,y+23,Palette.FOCUS);
            drawHead(a,left+14,y+3,16);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(a.displayName(),Math.max(40,panelWidth-236)),left+36,y+8,current?Palette.FOCUS:Palette.TEXT);
            if(panelWidth>450)fontRenderer.drawString(a.type,left+panelWidth-232,y+8,Palette.MUTED);
        }
        if(rows.isEmpty())fontRenderer.drawString("No matching saved accounts.",left+12,listTop+36,Palette.MUTED);
        if(!message.isEmpty()){
            int w=Math.min(width-16,fontRenderer.getStringWidth(message)+16);drawRect((width-w)/2,height-31,(width+w)/2,height-10,Palette.PANEL);
            drawCenteredString(fontRenderer,fontRenderer.trimStringToWidth(message,w-10),width/2,height-24,Palette.TEXT);
        }
        if(busy)fontRenderer.drawString("Working...",left+10,top+54,Palette.TEXT);
        super.drawScreen(mouseX,mouseY,partial);
    }
    private void panel(int x,int y,int w,int h,int fill){drawRect(x,y,x+w,y+h,Palette.BORDER);drawRect(x+1,y+1,x+w-1,y+h-1,fill);}
    private void drawHead(Account account,int x,int y,int size) {
        UUID uuid;
        try{String s=account.uuid.replace("-","");uuid=UUID.fromString(s.length()==32?s.substring(0,8)+"-"+s.substring(8,12)+"-"+s.substring(12,16)+"-"+s.substring(16,20)+"-"+s.substring(20):account.uuid);}catch(Exception ignored){uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+account.displayName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        ResourceLocation skin=LegacySkins.get(account).location;
        mc.getTextureManager().bindTexture(skin);GlStateManager.color(1,1,1,1);
        drawScaledCustomSizeModalRect(x,y,8,8,8,8,size,size,64,64);
        drawScaledCustomSizeModalRect(x,y,40,8,8,8,size,size,64,64);
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==81){mc.displayGuiScreen(new TokenImporterScreen(this));return;}
        if(button.id==0){cancelOperation();mc.displayGuiScreen(parent);return;}
        if(button.id>=10&&button.id<14){type=types[button.id-10];renameId="";rebuild();return;}
        if(button.id==21){label.setText("");token.setText("");renameId="";rebuild();return;}
        if(button.id==22){LegacySessions.restore();message="Launcher account restored.";rebuild();return;}
        if(button.id==23){copyToken(LegacySessions.original().getToken());return;}
        if(button.id==24){cancelOperation();return;}
        if(button.id>=30&&button.id<35){String t=new String[]{"Cracked","Microsoft","Session","TheAltening","Generated"}[button.id-30];filter=filter.equals(t)?"":t;scroll=0;rebuild();return;}
        if(button.id==20){addAccount();return;}
        if(button.id==41){mc.displayGuiScreen(new GeneratorScreen(this));return;}
        if(button.id==40||button.id==42){mc.displayGuiScreen(new ConfirmScreen(this,button.id==42));return;}
        if(button.id==43){checkAccounts();return;}
        if(button.id>=100){int index=(button.id-100)/4,action=(button.id-100)%4;if(index>=rows.size())return;Account a=rows.get(index);selected=a;
            if(action==0)login(a,false);else if(action==1){if("Cracked".equals(a.type)||"Generated".equals(a.type)){renameId=a.id;type="Cracked";label.setText(a.displayName());message="Edit the username, then Save rename.";rebuild();}else message="Only offline accounts can be renamed.";}else if(action==2){copyToken("TheAltening".equals(a.type)?a.sessionToken:a.token);}else{CurryfartsMod.store().remove(a);message="Deleted "+a.displayName();rebuild();}
        }
    }
    private void addAccount() {
        if(!renameId.isEmpty()){if(CurryfartsMod.store().rename(renameId,label.getText().trim())){message="Account renamed.";renameId="";label.setText("");}else message="Name is empty or already used.";rebuild();return;}
        if("Microsoft".equals(type)){busy=true;rebuild();Auth.beginMicrosoft(refresh->{if(refresh==null){mc.addScheduledTask(()->{busy=false;message="Microsoft login cancelled.";rebuild();});return;}Account a=new Account();a.type="Microsoft";a.label=refresh;login(a,true);});return;}
        Account a=new Account();a.type=type;a.label=label.getText().trim();a.token=token.getText().trim();if("Cracked".equals(type)&&a.label.isEmpty()){message="Enter a username first.";return;}login(a,true);
    }
    private void login(final Account account,final boolean add) {
        busy=true;final int ticket=++operation;rebuild();Thread worker=new Thread(()->{
            boolean ok=Auth.resolve(account);mc.addScheduledTask(()->{if(ticket!=operation)return;try{if(ok){LegacySessions.apply(account);if(add)CurryfartsMod.store().add(account);else CurryfartsMod.store().save();selected=account;message="Logged in as "+account.displayName();label.setText("");token.setText("");}else message="Login failed: "+account.lastError;}catch(Exception error){message="Session switch failed: "+error.getClass().getSimpleName();}busy=false;rebuild();});
        },"Curryfarts-Account-Login");worker.setDaemon(true);operationThread=worker;worker.start();
    }
    private void checkAccounts() {
        busy=true;final int ticket=++operation;rebuild();Thread worker=new Thread(()->{int valid=0,expired=0;for(Account a:CurryfartsMod.store().all()){if(ticket!=operation)return;if("Cracked".equals(a.type)||"Generated".equals(a.type))continue;a.checkStatus="CHECKING";if(Auth.resolve(a)){a.checkStatus="VALID";valid++;}else{a.checkStatus=a.lastFailure==TokenRefresher.FailureKind.NETWORK||a.lastFailure==TokenRefresher.FailureKind.RATE_LIMITED?"UNKNOWN":"EXPIRED";if("EXPIRED".equals(a.checkStatus))expired++;}}final String text=valid+" valid, "+expired+" expired.";CurryfartsMod.store().save();mc.addScheduledTask(()->{if(ticket!=operation)return;busy=false;message=text;rebuild();});},"Curryfarts-Account-Check");worker.setDaemon(true);operationThread=worker;worker.start();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        super.mouseClicked(x,y,button);label.mouseClicked(x,y,button);token.mouseClicked(x,y,button);search.mouseClicked(x,y,button);
        if(y>=listTop+27&&x>=left&&x<left+panelWidth){int i=(y-listTop-27)/25+scroll;if(i<rows.size())selected=rows.get(i);}
    }
    @Override protected void keyTyped(char c,int key)throws IOException {
        String before=search.getText();search.textboxKeyTyped(c,key);if(!before.equals(search.getText())){scroll=0;rebuild();}
        if(!"Microsoft".equals(type)){if("Cracked".equals(type))label.textboxKeyTyped(c,key);else token.textboxKeyTyped(c,key);}
        if(key==Keyboard.KEY_RETURN&&!busy)addAccount();else if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);
    }
    @Override public void handleMouseInput()throws IOException {super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){scroll+=wheel<0?1:-1;rebuild();}}
    @Override public void updateScreen(){label.updateCursorCounter();token.updateCursorCounter();search.updateCursorCounter();}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    private void copyToken(String token){if(token==null||token.isEmpty())message="This account has no session token.";else{GuiScreen.setClipboardString(token);message="Session token copied to clipboard.";}}
    private void cancelOperation(){operation++;busy=false;Thread worker=operationThread;if(worker!=null)worker.interrupt();Auth.cancelMicrosoft();message="Operation cancelled.";rebuild();}
    public void notice(String text){message=text;rebuild();}
    private static final class ConfirmScreen extends LegacyScreen {
        private final CurryAccountsScreen parent;private final boolean expired;
        ConfirmScreen(CurryAccountsScreen parent,boolean expired){this.parent=parent;this.expired=expired;}
        @Override public void initGui(){buttonList.add(new PaletteButton(0,width/2-120,height/2+20,100,20,"Clear",Palette.PANEL,Palette.BORDER));buttonList.add(new PaletteButton(1,width/2+20,height/2+20,100,20,"Cancel",Palette.PANEL,Palette.BORDER));}
        @Override public void drawScreen(int x,int y,float p){parent.drawScreen(-1,-1,p);drawRect(0,0,width,height,Palette.SCRIM);drawRect(width/2-140,height/2-55,width/2+140,height/2+55,Palette.PANEL);drawCenteredString(fontRenderer,expired?"Clear expired accounts?":"Clear all saved accounts?",width/2,height/2-35,Palette.TEXT);drawCenteredString(fontRenderer,"This cannot be undone.",width/2,height/2-12,Palette.MUTED);super.drawScreen(x,y,p);}
        @Override protected void actionPerformed(GuiButton b){if(b.id==0){for(Account a:CurryfartsMod.store().all())if(!expired||"EXPIRED".equals(a.checkStatus))CurryfartsMod.store().remove(a);}mc.displayGuiScreen(parent);parent.notice(b.id==0?"Accounts cleared.":"");}
    }
    private static final class GeneratorScreen extends LegacyScreen {
        private final CurryAccountsScreen parent;private GuiTextField count,password;private boolean listMode,setPassword=true;private String result="";
        GeneratorScreen(CurryAccountsScreen parent){this.parent=parent;}
        @Override public void initGui(){int x=width/2-130,y=height/2-75;count=new GuiTextField(1,fontRenderer,x,y+32,100,18);count.setText("10");count.setMaxStringLength(4);password=new GuiTextField(2,fontRenderer,x,y+64,260,18);password.setMaxStringLength(64);for(GuiTextField f:new GuiTextField[]{count,password}){f.setEnableBackgroundDrawing(false);f.setTextColor(Palette.TEXT);f.setDisabledTextColour(Palette.DISABLED);}buttonList.add(new PaletteButton(0,x,y,125,20,"Random",listMode?Palette.PANEL:Palette.PRIMARY,Palette.BORDER));buttonList.add(new PaletteButton(1,x+135,y,125,20,"From clipboard",listMode?Palette.PRIMARY:Palette.PANEL,Palette.BORDER));buttonList.add(new PaletteButton(4,x,y+98,260,20,"Set password: ON",setPassword?Palette.PRIMARY:Palette.PANEL,Palette.BORDER));buttonList.add(new PaletteButton(2,x,y+130,125,20,"Generate",Palette.PRIMARY,Palette.BORDER));buttonList.add(new PaletteButton(3,x+135,y+130,125,20,"Close",Palette.PANEL,Palette.BORDER));}
        @Override public void drawScreen(int x,int y,float p){GlStateManager.disableDepth();drawRect(0,0,width,height,Palette.BACKGROUND);drawRect(width/2-145,height/2-110,width/2+145,height/2+110,Palette.PANEL);drawCenteredString(fontRenderer,"Generate offline accounts",width/2,height/2-95,Palette.TEXT);drawString(fontRenderer,listMode?"Paste name list using Generate":"Count",width/2-130,height/2-55,Palette.MUTED);parent.field(count,"Count",x,y,p);if(setPassword)parent.field(password,"Shared password",x,y,p);drawString(fontRenderer,"Shared password (empty = random)",width/2-130,height/2-24,Palette.MUTED);drawString(fontRenderer,result,width/2-130,height/2+88,Palette.MUTED);super.drawScreen(x,y,p);}
        private void refreshModes(){for(GuiButton button:buttonList){if(button instanceof PaletteButton){if(button.id==0)((PaletteButton)button).setFill(listMode?Palette.PANEL:Palette.PRIMARY);else if(button.id==1)((PaletteButton)button).setFill(listMode?Palette.PRIMARY:Palette.PANEL);else if(button.id==4)((PaletteButton)button).setFill(setPassword?Palette.PRIMARY:Palette.PANEL);}}}
        @Override protected void actionPerformed(GuiButton b){if(b.id==4){setPassword=!setPassword;b.displayString="Set password: "+(setPassword?"ON":"OFF");refreshModes();return;}if(b.id==0){listMode=false;refreshModes();}if(b.id==1){listMode=true;refreshModes();}if(b.id==3){mc.displayGuiScreen(parent);parent.notice(result);return;}if(b.id==2){List<String>names=new ArrayList<String>();if(listMode){for(String name:GuiScreen.getClipboardString().split("[\\s,;]+")){int colon=name.indexOf(':');if(colon>=0)name=name.substring(0,colon);if(name.matches("[A-Za-z0-9_]{1,16}"))names.add(name);}}else{int n=10;try{n=Integer.parseInt(count.getText());}catch(Exception ignored){}for(int i=0;i<Math.min(5000,Math.max(1,n));i++)names.add("Curry_"+UUID.randomUUID().toString().replace("-","").substring(0,10));}int added=0;for(String name:names){Account a=new Account();a.type="Generated";a.label=name;a.password=!setPassword?"":(password.getText().isEmpty()?UUID.randomUUID().toString().replace("-","").substring(0,12):password.getText());if(Auth.resolve(a)&&CurryfartsMod.store().add(a))added++;}result="Added "+added+" generated accounts.";}}
        @Override protected void keyTyped(char c,int key)throws IOException{count.textboxKeyTyped(c,key);password.textboxKeyTyped(c,key);if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);}
        @Override protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);count.mouseClicked(x,y,b);password.mouseClicked(x,y,b);}
    }
}
