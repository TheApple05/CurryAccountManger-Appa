package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid="curryfarts",name="Curryfarts Account Manager",version="5.0-port.2+1.12.2",clientSideOnly=true,acceptableRemoteVersions="*")
public final class CurryfartsMod {
    public static final AccountStore STORE=new AccountStore();
    private static boolean loaded;
    @Mod.EventHandler public void init(FMLInitializationEvent event) { MinecraftForge.EVENT_BUS.register(this); }
    public static synchronized AccountStore store() {
        if(!loaded){STORE.load(Minecraft.getMinecraft().gameDir);loaded=true;}
        return STORE;
    }
    @SubscribeEvent public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if(event.getGui() instanceof GuiMultiplayer)
            event.getButtonList().add(new PaletteButton(781122,event.getGui().width-64,4,60,20,"Accounts",Palette.PANEL,Palette.BORDER));
    }
    @SubscribeEvent public void action(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if(event.getGui() instanceof GuiMultiplayer&&event.getButton().id==781122)
            Minecraft.getMinecraft().displayGuiScreen(new CurryAccountsScreen(event.getGui()));
    }
    // Preview name tags assume a joined-world camera; the account UI labels it itself.
    @SubscribeEvent public void previewName(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre event) {
        if(LegacyPreview.isPreview(event.getEntity()))event.setCanceled(true);
    }
}
