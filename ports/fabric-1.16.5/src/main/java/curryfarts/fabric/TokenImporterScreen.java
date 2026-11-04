package curryfarts.fabric;

import curryfarts.legacy.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native batch importer; the input preview hides tokens and all network work runs off-thread. */
public final class TokenImporterScreen extends Screen {
    private static final AtomicBoolean CHOOSER=new AtomicBoolean();
    private final AccountsScreen parent;
    private TokenImportBatch.Authenticator auth=TokenImportBatch.real();
    private String input="",message="Paste tokens or open a UTF-8 TXT file. Tokens stay hidden.";
    private volatile boolean busy,closed;
    private boolean importing;
    private int generation,left,panelWidth,scroll;
    private Thread worker;
    private AtomicBoolean cancel=new AtomicBoolean();
    private TokenImportBatch.Progress progress;
    public TokenImporterScreen(AccountsScreen parent){super(new TextComponent("Session Token Importer"));this.parent=parent;}
    @Override protected void init(){closed=false;panelWidth=Math.min(584,width-24);left=(width-panelWidth)/2;rebuild();}
    private void add(int x,int y,int w,String title,int color,boolean active,Runnable action){PaletteButton b=new PaletteButton(x,y,w,18,title,color,Palette.BORDER,ignored->action.run());b.active=active;addButton(b);}
    private void rebuild(){buttons.clear();children.clear();int x=left+10,w=panelWidth-20,half=(w-4)/2;add(x,6,half,"Session Token Switcher",Palette.PANEL,true,this::onClose);add(x+half+4,6,half,"Session Token Importer",Palette.PRIMARY,true,()->{});int third=(w-8)/3;add(x,64,third,"Paste tokens",Palette.PANEL,!busy,()->loadInput(minecraft.keyboardHandler.getClipboard()));add(x+third+4,64,third,"Open TXT",Palette.PANEL,!busy,this::chooseFile);add(x+2*(third+4),64,w-2*(third+4),"Clear",Palette.PANEL,!busy,()->{input="";message="Input cleared.";});add(x,134,w-100,busy?"Validating...":"Validate & Import",Palette.PRIMARY,!busy,this::startImport);add(x+w-96,134,96,"Cancel",Palette.PANEL,busy,this::cancelImport);}
    public void loadInput(String text){if(busy)return;if(text.length()>TokenImportBatch.MAX_INPUT){message="Input exceeds 1 MiB. Use a smaller batch.";return;}input=text;message="Input loaded. Click Validate & Import to check it.";}
    public void startImport(){if(busy)return;if(input.trim().isEmpty()){message="Paste tokens or open a TXT file first.";return;}final String text=input;input="";final AtomicBoolean guard=new AtomicBoolean();cancel=guard;final int ticket=++generation;busy=true;importing=true;progress=null;scroll=0;message="Checking tokens. Working accounts are saved automatically.";rebuild();worker=new Thread(()->{try{TokenImportBatch.Progress result=TokenImportBatch.run(text,auth,(v,c)->CurryfartsClient.ACCOUNTS.importVerified(v,c),guard::get,p->minecraft.execute(()->{if(closed||ticket!=generation||guard.get())return;progress=p;scroll=Math.max(0,p.results.size()-visibleRows());}));minecraft.execute(()->{if(closed||ticket!=generation)return;progress=result;busy=false;importing=false;message=result.cancelled?"Cancelled. Completed imports remain saved.":result.processed<result.total?"Rate limited. Retry the remaining entries later.":result.total==0?"No entries found. Paste tokens or choose a TXT file.":result.imported==0&&result.duplicates==0?"Finished. No working accounts imported; review the results.":result.failed>0?"Finished. Some entries failed; review the results.":"Finished. Working accounts are ready in the switcher.";rebuild();});}catch(Throwable ignored){minecraft.execute(()->{if(closed||ticket!=generation)return;busy=false;importing=false;message="Import failed. Use at most 1,000 entries / 1 MiB.";rebuild();});}},"Curryfarts-Token-Import");worker.setDaemon(true);worker.start();}
    private void chooseFile(){if(!CHOOSER.compareAndSet(false,true)){message="A file chooser is already open.";return;}busy=true;importing=false;final int ticket=++generation;message="Choose a UTF-8 TXT file. Then click Validate & Import.";rebuild();worker=new Thread(()->{FileDialog chooser=null;try{chooser=new FileDialog((Frame)null,"Import Minecraft tokens",FileDialog.LOAD);chooser.setFile("*.txt");chooser.setVisible(true);final String text=chooser.getFile()==null?null:TokenImportBatch.readFile(Paths.get(chooser.getDirectory(),chooser.getFile()));minecraft.execute(()->{if(closed||ticket!=generation)return;busy=false;if(text!=null)input=text;message=text==null?"File selection cancelled.":"File loaded. Click Validate & Import.";rebuild();});}catch(Throwable ignored){fileFailed(ticket);}finally{if(chooser!=null)chooser.dispose();CHOOSER.set(false);}},"Curryfarts-Token-File");worker.setDaemon(true);worker.start();}
    private void fileFailed(int ticket){minecraft.execute(()->{if(closed||ticket!=generation)return;busy=false;message="Choose a readable UTF-8 TXT file up to 1 MiB.";rebuild();});}
    @Override public void onFilesDrop(List<Path> paths){if(busy||paths.size()!=1)return;busy=true;importing=false;final int ticket=++generation;rebuild();worker=new Thread(()->{try{final String text=TokenImportBatch.readFile(paths.get(0));minecraft.execute(()->{if(closed||ticket!=generation)return;busy=false;input=text;message="File loaded. Click Validate & Import.";rebuild();});}catch(Throwable ignored){fileFailed(ticket);}},"Curryfarts-Token-File");worker.setDaemon(true);worker.start();}
    private void cancelImport(){cancel.set(true);if(!importing)generation++;if(worker!=null)worker.interrupt();busy=false;message="Cancelled. Accounts already imported remain saved.";rebuild();}
    private int visibleRows(){return Math.max(0,(height-220)/16);}
    @Override public void render(PoseStack pose,int x,int y,float delta){fill(pose,0,0,width,height,Palette.BACKGROUND);fill(pose,left,30,left+panelWidth,height-10,Palette.BORDER);fill(pose,left+1,31,left+panelWidth-1,height-11,Palette.PANEL);text(pose,"Import working Minecraft accounts",left+10,40,Palette.TEXT);fill(pose,left+10,90,left+panelWidth-10,126,Palette.BORDER);fill(pose,left+11,91,left+panelWidth-11,125,Palette.FIELD);text(pose,input.isEmpty()?"Paste multiple tokens, username:token, or REFRESHTOKEN / MCTOKEN fields.":"Input loaded: "+input.length()+" characters. Tokens are hidden.",left+16,98,Palette.MUTED);text(pose,"Ctrl+V pastes a batch. Type or press Enter to add lines.",left+16,113,Palette.MUTED);text(pose,message,left+10,160,Palette.MUTED);if(progress!=null){text(pose,progress.processed+" / "+progress.total+" checked | "+progress.imported+" imported | "+progress.duplicates+" duplicates",left+10,176,Palette.TEXT);text(pose,progress.invalid+" invalid | "+progress.expired+" expired | "+progress.failed+" failed (scroll for results)",left+10,190,Palette.MUTED);for(int i=scroll;i<progress.results.size()&&i-scroll<visibleRows();i++){TokenImportBatch.Result r=progress.results.get(i);text(pose,"Line "+r.line+"  "+r.status+"  "+r.message,left+12,210+(i-scroll)*16,r.status==TokenImportBatch.Status.IMPORTED?Palette.FOCUS:Palette.MUTED);}}super.render(pose,x,y,delta);}
    private void text(PoseStack p,String s,int x,int y,int color){font.draw(p,font.plainSubstrByWidth(s,Math.max(1,left+panelWidth-10-x)),x,y,color);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){onClose();return true;}if(busy)return true;if(Screen.hasControlDown()&&key==86){loadInput(minecraft.keyboardHandler.getClipboard());return true;}if(key==259){if(!input.isEmpty())input=input.substring(0,input.length()-1);return true;}if(key==257&&input.length()<TokenImportBatch.MAX_INPUT){input+="\n";return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean charTyped(char c,int modifiers){if(!busy&&c>=32&&c!=127&&input.length()<TokenImportBatch.MAX_INPUT){input+=c;return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(progress!=null){scroll=Math.max(0,Math.min(Math.max(0,progress.results.size()-visibleRows()),scroll+(delta<0?3:-3)));return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){cancelImport();closed=true;generation++;input="";super.removed();}
}
