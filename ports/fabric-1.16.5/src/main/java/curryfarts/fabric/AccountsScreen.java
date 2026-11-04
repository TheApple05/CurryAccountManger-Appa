package curryfarts.fabric;

import curryfarts.legacy.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import java.util.*;

/** Native 1.16.5 GUI; account operations and data schema match the supplied client. */
public final class AccountsScreen extends Screen {
    private final Screen parent;
    private final SkinPreview preview=new SkinPreview();
    private PaletteField nameField,tokenField,searchField;
    private String type="Cracked",filter="All",query="",nameValue="",tokenValue="";
    private Account selected,renaming;
    private int offset;
    private boolean dragging;
    private double dragX;
    private final List<Region> regions=new ArrayList<Region>();
    public volatile boolean busy;
    public String notice="";
    private int noticeColor=Palette.GOLD;
    private long noticeUntil;
    private final List<PaletteButton> operations=new ArrayList<PaletteButton>();
    private int left,mainWidth,formWidth,listTop;
    public AccountsScreen(Screen parent) {super(new TextComponent("Curryfarts Accounts"));this.parent=parent;}

    @Override protected void init() {
        Sessions.original();
        if(nameField!=null){nameValue=nameField.getValue();tokenValue=tokenField.getValue();query=searchField.getValue();}
        if(!StoreState.loaded){CurryfartsClient.ACCOUNTS.load(minecraft.gameDirectory);StoreState.loaded=true;}
        operations.clear();left=Math.max(12,(width-Math.min(600,width-24))/2);mainWidth=Math.min(600,width-24);formWidth=mainWidth-206;listTop=200;
        addButton(new PaletteButton(10,6,72,20,"Back",Palette.NAVY,Palette.BORDER,b->onClose()));
        int typeW=(formWidth-24)/4;
        String[] names={"Cracked","TheAltening","Session","Microsoft"};
        for(int i=0;i<names.length;i++){final String kind=names[i];PaletteButton button=new PaletteButton(left+12+i*typeW,50,typeW-3,18,"TheAltening".equals(kind)?"Altening":kind,type.equals(kind)?Palette.TEAL:Palette.NAVY,type.equals(kind)?Palette.GOLD:Palette.BORDER,b->{type=kind;renaming=null;rebuild();});addButton(button);}
        nameField=new PaletteField(font,left+12,77,formWidth-24,20,"Username");nameField.setValue(nameValue);nameField.setMaxLength(256);addButton(nameField);
        tokenField=new PaletteField(font,left+12,103,formWidth-24,20,"Session token / Altening token");tokenField.setValue(tokenValue);addButton(tokenField);
        tokenField.visible="Session".equals(type)||"TheAltening".equals(type);nameField.visible=!"Microsoft".equals(type);
        PaletteButton add=new PaletteButton(left+12,129,150,20,renaming!=null?"Save name":("Microsoft".equals(type)?"Microsoft login":"Add / login"),Palette.BLUE,Palette.BLUE,b->addAccount());op(add);
        addButton(new PaletteButton(left+168,129,62,20,"Clear",Palette.NAVY,Palette.BORDER,b->{nameField.setValue("");tokenField.setValue("");renaming=null;}));
        addButton(new PaletteButton(left+236,129,Math.max(65,formWidth-248),20,"Generate",Palette.NAVY,Palette.BORDER,b->minecraft.setScreen(new GeneratorScreen(this))));
        searchField=new PaletteField(font,left+12,159,formWidth-24,20,"Search accounts...");searchField.setValue(query);searchField.setResponder(value->{query=value;offset=0;});addButton(searchField);
        addButton(new PaletteButton(left+formWidth+16,161,mainWidth-formWidth-28,20,"Restore launcher",Palette.NAVY,Palette.BORDER,b->{if(Sessions.restore())toast("Launcher session restored.",Palette.GOLD);else toast(Sessions.lastError,Palette.ORANGE);}));
        op(new PaletteButton(left+mainWidth-62,204,54,18,"Check",Palette.NAVY,Palette.BORDER,b->checkAccounts()));
        op(new PaletteButton(left+mainWidth-146,204,78,18,"Clear expired",Palette.DANGER,Palette.ORANGE,b->clearExpired()));
        op(new PaletteButton(left+mainWidth-216,204,64,18,"Clear list",Palette.DANGER,Palette.ORANGE,b->minecraft.setScreen(new ConfirmScreen(this))));
        addButton(new PaletteButton(left+118,204,Math.max(70,mainWidth-340),18,filter,Palette.NAVY,Palette.BORDER,b->cycleFilter()));
    }
    private void op(PaletteButton b){operations.add(b);addButton(b);}
    public void rebuild(){if(minecraft!=null)init(minecraft,width,height);}
    @Override public void onClose(){Auth.cancelMicrosoft();minecraft.setScreen(parent);}
    @Override public void tick(){nameField.tick();tokenField.tick();searchField.tick();for(PaletteButton b:operations)b.active=!busy;}
    public void toast(String message,int color){notice=message==null?"":message;noticeColor=color;noticeUntil=System.currentTimeMillis()+6000;}
    private List<Account> visibleAccounts(){List<Account> result=new ArrayList<Account>();String search=query.toLowerCase(Locale.ROOT);for(Account a:CurryfartsClient.ACCOUNTS.all())if(("All".equals(filter)||filter.equals(a.type))&&name(a).toLowerCase(Locale.ROOT).contains(search))result.add(a);return result;}
    private void cycleFilter(){String[] types={"All","Cracked","Microsoft","Session","TheAltening","Generated"};for(int i=0;i<types.length;i++)if(types[i].equals(filter)){filter=types[(i+1)%types.length];break;}offset=0;rebuild();}
    public static String name(Account a){return a.username!=null&&!a.username.trim().isEmpty()?a.username:(a.offline()?a.label:"(unresolved)");}
    public Account defaultAccount(){User u=Sessions.original();Account a=new Account();a.id="launcher";a.type="Launcher";a.username=u.getName();a.uuid=u.getUuid();return a;}
    public void addAccount(){
        if(busy)return;
        if(renaming!=null){boolean current=renaming.username.equals(minecraft.getUser().getName());if(CurryfartsClient.ACCOUNTS.rename(renaming.id,nameField.getValue())){Account a=renaming;renaming=null;if(current)login(a,false);else toast("Account renamed.",Palette.GOLD);rebuild();}else toast("Enter a unique Minecraft username.",Palette.ORANGE);return;}
        if("Microsoft".equals(type)){busy=true;toast("Complete Microsoft login in your browser.",Palette.GOLD);Auth.beginMicrosoft(refresh->{if(refresh==null){minecraft.execute(()->{busy=false;toast("Microsoft login cancelled.",Palette.ORANGE);});return;}Account a=new Account();a.type="Microsoft";a.label=refresh;resolveAndLogin(a,true);});return;}
        Account a=new Account();a.type=type;a.label=nameField.getValue().trim();a.token=tokenField.getValue().trim();if("Session".equals(type)&&a.token.isEmpty()){a.token=a.label;a.label="";}login(a,true);
    }
    public void login(Account a,boolean add){if(busy)return;busy=true;resolveAndLogin(a,add);}
    private void resolveAndLogin(final Account a,final boolean add){work(()->{boolean good=Auth.resolve(a);if(good&&add)good=CurryfartsClient.ACCOUNTS.add(a);if(good)good=Sessions.login(a);final boolean success=good;Minecraft.getInstance().execute(()->{busy=false;if(success){selected=a;CurryfartsClient.ACCOUNTS.save();toast("Logged in as "+name(a)+".",Palette.GREEN);nameField.setValue("");tokenField.setValue("");}else toast(a.lastError.isEmpty()?(Sessions.lastError.isEmpty()?"Duplicate account or login failed.":Sessions.lastError):a.lastError,Palette.ORANGE);});});}
    public void checkAccounts(){if(busy)return;busy=true;work(()->{int valid=0,expired=0;for(Account a:CurryfartsClient.ACCOUNTS.all()){if(a.offline())continue;a.checkStatus="CHECKING";boolean okay=Auth.resolve(a);a.checkStatus=okay?"VALID":"EXPIRED";if(okay)valid++;else expired++;}CurryfartsClient.ACCOUNTS.save();final String message=valid+" valid, "+expired+" expired.";minecraft.execute(()->{busy=false;toast(message,Palette.GOLD);});});}
    private void clearExpired(){for(Account a:CurryfartsClient.ACCOUNTS.all())if("EXPIRED".equals(a.checkStatus))CurryfartsClient.ACCOUNTS.remove(a);toast("Expired accounts removed.",Palette.GOLD);}
    public void clearAll(){for(Account a:CurryfartsClient.ACCOUNTS.all())CurryfartsClient.ACCOUNTS.remove(a);selected=null;offset=0;toast("Saved accounts cleared.",Palette.GOLD);}
    public static void work(Runnable work){Thread thread=new Thread(()->{try{work.run();}catch(Throwable error){Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().screen instanceof AccountsScreen){AccountsScreen s=(AccountsScreen)Minecraft.getInstance().screen;s.busy=false;s.toast("Operation failed: "+error.getClass().getSimpleName(),Palette.ORANGE);}});}},"Curryfarts-Account-Worker");thread.setDaemon(true);thread.start();}

    @Override public void render(PoseStack pose,int mouseX,int mouseY,float delta){
        fill(pose,0,0,width,height,Palette.BACKGROUND);panel(pose,left,28,formWidth,160,Palette.PANEL);panel(pose,left+formWidth+8,28,mainWidth-formWidth-8,160,Palette.NAVY);
        text(pose,"Accounts",left+12,35,Palette.TEXT);text(pose,"Skin preview",left+formWidth+20,35,Palette.TEXT);
        if("Microsoft".equals(type)){text(pose,"Sign in securely in your browser.",left+12,82,Palette.MUTED);text(pose,"No Microsoft password is stored.",left+12,98,Palette.MUTED);}
        Account show=selected!=null?selected:defaultAccount();preview.render(pose,show,left+formWidth+(mainWidth-formWidth)/2,70,50);text(pose,trim(name(show),mainWidth-formWidth-25),left+formWidth+20,148,isCurrent(show)?Palette.GOLD:Palette.TEXT);
        panel(pose,left,listTop,mainWidth,Math.max(52,height-listTop-14),Palette.PANEL);text(pose,"Accounts ("+CurryfartsClient.ACCOUNTS.all().size()+")",left+10,210,Palette.TEXT);
        regions.clear();List<Account> accounts=visibleAccounts();int rows=Math.max(1,(height-listTop-44)/24);offset=Math.min(offset,Math.max(0,accounts.size()+1-rows));
        List<Account> all=new ArrayList<Account>();all.add(defaultAccount());all.addAll(accounts);
        for(int i=offset;i<all.size()&&i-offset<rows;i++){Account a=all.get(i);int y=listTop+28+(i-offset)*24;boolean current=isCurrent(a);fill(pose,left+8,y,left+mainWidth-8,y+22,current?Palette.CURRENT:(a==selected?Palette.NAVY:Palette.PANEL));if(current)fill(pose,left+8,y,left+10,y+22,Palette.GOLD);
            text(pose,trim(name(a),160),left+16,y+7,current?Palette.GOLD:Palette.TEXT);text(pose,a.type,left+180,y+7,Palette.MUTED);
            int bx=left+mainWidth-176;rowButton(pose,bx,y+2,48,"launcher".equals(a.id)?(current?"Active":"Restore"):(current?"Active":"Login"),current?Palette.GREEN:Palette.BLUE,current?Palette.GREEN:Palette.BLUE,mouseX,mouseY,()->{if("launcher".equals(a.id)){Sessions.restore();toast("Launcher session restored.",Palette.GOLD);}else login(a,false);});
            if(!"launcher".equals(a.id)){if(a.offline())rowButton(pose,bx+52,y+2,48,"Rename",Palette.NAVY,Palette.BORDER,mouseX,mouseY,()->{selected=a;renaming=a;type="Cracked";nameValue=name(a);nameField.setValue(name(a));rebuild();});else rowButton(pose,bx+52,y+2,48,"Copy",Palette.NAVY,Palette.BORDER,mouseX,mouseY,()->{minecraft.keyboardHandler.setClipboard("TheAltening".equals(a.type)?a.sessionToken:a.token);toast("Session token copied.",Palette.GOLD);});
                rowButton(pose,bx+104,y+2,54,"Delete",Palette.DANGER,Palette.ORANGE,mouseX,mouseY,()->{CurryfartsClient.ACCOUNTS.remove(a);if(selected==a)selected=null;toast("Account deleted.",Palette.GOLD);});}
            regions.add(new Region(left+8,y,mainWidth-190,22,()->selected=a));
        }
        if(all.size()>rows){int h=Math.max(8,(height-listTop-44)*rows/all.size());int y=listTop+28+(height-listTop-44-h)*offset/Math.max(1,all.size()-rows);fill(pose,left+mainWidth-5,y,left+mainWidth-2,y+h,Palette.BROWN);}
        if(accounts.isEmpty()&&offset==0)text(pose,"No saved accounts match this view.",left+16,listTop+64,Palette.MUTED);
        super.render(pose,mouseX,mouseY,delta);
        if(System.currentTimeMillis()<noticeUntil){int w=Math.min(width-24,font.width(notice)+20);panel(pose,(width-w)/2,height-25,w,19,Palette.FIELD);fill(pose,(width-w)/2,height-25,(width-w)/2+2,height-6,noticeColor);text(pose,trim(notice,w-14),(width-w)/2+7,height-20,Palette.TEXT);}
    }
    private boolean isCurrent(Account a){return name(a).equals(minecraft.getUser().getName());}
    private void rowButton(PoseStack p,int x,int y,int w,String title,int fill,int border,int mx,int my,Runnable action){PaletteButton b=new PaletteButton(x,y,w,18,title,fill,border,unused->action.run());b.active=!busy&&!"Active".equals(title);b.render(p,mx,my,0);regions.add(new Region(x,y,w,18,()->{if(b.active)action.run();}));}
    public void panel(PoseStack p,int x,int y,int w,int h,int color){fill(p,x,y,x+w,y+h,Palette.TEAL);fill(p,x+1,y+1,x+w-1,y+h-1,color);}
    public void text(PoseStack p,String value,int x,int y,int color){font.draw(p,value,x,y,color);}
    private String trim(String s,int w){return font.plainSubstrByWidth(s,Math.max(1,w));}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){for(Region r:regions)if(r.hit(x,y)){r.action.run();return true;}if(x>left+formWidth+8&&x<left+mainWidth&&y>48&&y<148){dragging=true;dragX=x;return true;}}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging){preview.rotation+=(float)(x-dragX);dragX=x;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double amount){if(y>listTop){offset=Math.max(0,offset+(amount>0?-1:1));return true;}return super.mouseScrolled(x,y,amount);}
    private static class Region{final int x,y,w,h;final Runnable action;Region(int x,int y,int w,int h,Runnable a){this.x=x;this.y=y;this.w=w;this.h=h;action=a;}boolean hit(double px,double py){return px>=x&&px<x+w&&py>=y&&py<y+h;}}
    private static class StoreState{static boolean loaded;}
}
