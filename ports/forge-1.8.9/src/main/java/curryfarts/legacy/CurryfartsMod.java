package curryfarts.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid="curryfarts",name="Curryfarts Account Manager",version="5.0-port.3+1.8.9",clientSideOnly=true,acceptableRemoteVersions="*")
public final class CurryfartsMod {
    public static final AccountStore STORE=new AccountStore();
    private static boolean loaded;
    @Mod.EventHandler public void init(FMLInitializationEvent event) { MinecraftForge.EVENT_BUS.register(this); }
    public static synchronized AccountStore store() {
        if(!loaded){STORE.load(Minecraft.getMinecraft().mcDataDir);loaded=true;}
        return STORE;
    }
    @SubscribeEvent public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if(event.gui instanceof GuiMultiplayer)
            event.buttonList.add(new PaletteButton(781122,event.gui.width-64,4,60,20,"Accounts",Palette.PANEL,Palette.BORDER));
    }
    @SubscribeEvent public void action(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if(event.gui instanceof GuiMultiplayer&&event.button.id==781122)
            Minecraft.getMinecraft().displayGuiScreen(new CurryAccountsScreen(event.gui));
    }
    // Preview name tags assume a joined-world camera; the account UI labels it itself.
    @SubscribeEvent public void previewName(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre event) {
        if(LegacyPreview.isPreview(event.entity))event.setCanceled(true);
    }
}
