package curryfarts.legacy;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Native in-game batch import UI; credentials are deliberately hidden in its input preview. */
public final class TokenImporterScreen extends LegacyScreen {
    private static final AtomicBoolean CHOOSER=new AtomicBoolean();
    private final CurryAccountsScreen parent;
    private TokenImportBatch.Authenticator auth=TokenImportBatch.real();
    private String input="",message="Paste tokens or open a UTF-8 TXT file. Tokens stay hidden.";
    private volatile boolean busy,closed;
    private boolean importing;
    private int generation,left,panelWidth,scroll;
    private Thread worker;
    private AtomicBoolean cancel=new AtomicBoolean();
    private TokenImportBatch.Progress progress;
    public TokenImporterScreen(CurryAccountsScreen parent){this.parent=parent;}
    @Override public void initGui(){closed=false;Keyboard.enableRepeatEvents(true);panelWidth=Math.min(584,width-24);left=(width-panelWidth)/2;rebuild();}
    private void add(int id,int x,int y,int w,String title,int color){buttonList.add(new PaletteButton(id,x,y,w,18,title,color,Palette.BORDER));}
    private void rebuild(){buttonList.clear();int x=left+10,w=panelWidth-20,half=(w-4)/2;add(0,x,6,half,"Session Token Switcher",Palette.PANEL);add(1,x+half+4,6,half,"Session Token Importer",Palette.PRIMARY);int third=(w-8)/3;add(2,x,64,third,"Paste tokens",Palette.PANEL);add(3,x+third+4,64,third,"Open TXT",Palette.PANEL);add(4,x+2*(third+4),64,w-2*(third+4),"Clear",Palette.PANEL);add(5,x,134,w-100,busy?"Validating...":"Validate & Import",Palette.PRIMARY);add(6,x+w-96,134,96,"Cancel",Palette.PANEL);for(GuiButton b:buttonList)if(b.id>=2&&b.id<=5)b.enabled=!busy;else if(b.id==6)b.enabled=busy;}
    @Override protected void actionPerformed(GuiButton b){if(b.id==0){mc.displayGuiScreen(parent);return;}if(b.id==2)loadInput(GuiScreen.getClipboardString());else if(b.id==3)chooseFile();else if(b.id==4){input="";message="Input cleared.";}else if(b.id==5)startImport();else if(b.id==6)cancelImport();}
    public void loadInput(String text){if(busy)return;if(text.length()>TokenImportBatch.MAX_INPUT){message="Input exceeds 1 MiB. Use a smaller batch.";return;}input=text;message="Input loaded. Click Validate & Import to check it.";}
    public void startImport(){if(busy)return;if(input.trim().isEmpty()){message="Paste tokens or open a TXT file first.";return;}final String text=input;input="";final AtomicBoolean guard=new AtomicBoolean();cancel=guard;final int ticket=++generation;busy=true;importing=true;progress=null;scroll=0;message="Checking tokens. Working accounts are saved automatically.";rebuild();worker=new Thread(()->{try{TokenImportBatch.Progress result=TokenImportBatch.run(text,auth,(v,c)->CurryfartsMod.store().importVerified(v,c),guard::get,p->mc.addScheduledTask(()->{if(closed||ticket!=generation||guard.get())return;progress=p;scroll=Math.max(0,p.results.size()-visibleRows());}));mc.addScheduledTask(()->{if(closed||ticket!=generation)return;progress=result;busy=false;importing=false;message=result.cancelled?"Cancelled. Completed imports remain saved.":result.processed<result.total?"Rate limited. Retry the remaining entries later.":result.total==0?"No entries found. Paste tokens or choose a TXT file.":result.imported==0&&result.duplicates==0?"Finished. No working accounts imported; review the results.":result.failed>0?"Finished. Some entries failed; review the results.":"Finished. Working accounts are ready in the switcher.";rebuild();});}catch(Throwable ignored){mc.addScheduledTask(()->{if(closed||ticket!=generation)return;busy=false;importing=false;message="Import failed. Use at most 1,000 entries / 1 MiB.";rebuild();});}},"Curryfarts-Token-Import");worker.setDaemon(true);worker.start();}
    private void chooseFile(){if(!CHOOSER.compareAndSet(false,true)){message="A file chooser is already open.";return;}busy=true;importing=false;final int ticket=++generation;message="Choose a UTF-8 TXT file. Validation starts after clicking Import.";rebuild();worker=new Thread(()->{FileDialog chooser=null;try{chooser=new FileDialog((Frame)null,"Import Minecraft tokens",FileDialog.LOAD);chooser.setFile("*.txt");chooser.setVisible(true);final String text=chooser.getFile()==null?null:TokenImportBatch.readFile(Paths.get(chooser.getDirectory(),chooser.getFile()));mc.addScheduledTask(()->{if(closed||ticket!=generation)return;busy=false;if(text!=null)input=text;message=text==null?"File selection cancelled.":"File loaded. Click Validate & Import.";rebuild();});}catch(Throwable ignored){mc.addScheduledTask(()->{if(closed||ticket!=generation)return;busy=false;message="Choose a readable UTF-8 TXT file up to 1 MiB.";rebuild();});}finally{if(chooser!=null)chooser.dispose();CHOOSER.set(false);}},"Curryfarts-Token-File");worker.setDaemon(true);worker.start();}
    private void cancelImport(){cancel.set(true);if(!importing)generation++;if(worker!=null)worker.interrupt();busy=false;message="Cancelled. Accounts already imported remain saved.";rebuild();}
    private int visibleRows(){return Math.max(0,(height-220)/16);}
    @Override public void drawScreen(int x,int y,float delta){drawRect(0,0,width,height,Palette.BACKGROUND);drawRect(left,30,left+panelWidth,height-10,Palette.BORDER);drawRect(left+1,31,left+panelWidth-1,height-11,Palette.PANEL);text("Import working Minecraft accounts",left+10,40,Palette.TEXT);drawRect(left+10,90,left+panelWidth-10,126,Palette.BORDER);drawRect(left+11,91,left+panelWidth-11,125,Palette.FIELD);text(input.isEmpty()?"Paste multiple tokens, username:token, or REFRESHTOKEN / MCTOKEN fields.":"Input loaded: "+input.length()+" characters. Tokens are hidden.",left+16,98,Palette.MUTED);text("Ctrl+V pastes a batch. Type or press Enter to add lines.",left+16,113,Palette.MUTED);text(message,left+10,160,Palette.MUTED);if(progress!=null){text(progress.processed+" / "+progress.total+" checked | "+progress.imported+" imported | "+progress.duplicates+" duplicates",left+10,176,Palette.TEXT);text(progress.invalid+" invalid | "+progress.expired+" expired | "+progress.failed+" failed (scroll for results)",left+10,190,Palette.MUTED);for(int i=scroll;i<progress.results.size()&&i-scroll<visibleRows();i++){TokenImportBatch.Result r=progress.results.get(i);text("Line "+r.line+"  "+r.status+"  "+r.message,left+12,210+(i-scroll)*16,r.status==TokenImportBatch.Status.IMPORTED?Palette.FOCUS:Palette.MUTED);}}super.drawScreen(x,y,delta);}
    private void text(String s,int x,int y,int color){fontRenderer.drawString(fontRenderer.trimStringToWidth(s,Math.max(1,left+panelWidth-10-x)),x,y,color);}
    @Override protected void keyTyped(char c,int key)throws IOException {if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(busy)return;if(GuiScreen.isCtrlKeyDown()&&key==Keyboard.KEY_V){loadInput(GuiScreen.getClipboardString());return;}if(key==Keyboard.KEY_BACK){if(!input.isEmpty())input=input.substring(0,input.length()-1);}else if(key==Keyboard.KEY_RETURN){if(input.length()<TokenImportBatch.MAX_INPUT)input+="\n";}else if(c>=32&&c!=127&&input.length()<TokenImportBatch.MAX_INPUT)input+=c;}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0&&progress!=null)scroll=Math.max(0,Math.min(Math.max(0,progress.results.size()-visibleRows()),scroll+(wheel<0?3:-3)));}
    @Override public void onGuiClosed(){cancelImport();closed=true;generation++;input="";Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
}
