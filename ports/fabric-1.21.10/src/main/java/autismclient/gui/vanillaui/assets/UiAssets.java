package autismclient.gui.vanillaui.assets;

import net.minecraft.resources.ResourceLocation;

public final class UiAssets {
   public static final ResourceLocation FONT_BODY = ResourceLocation.fromNamespaceAndPath("autismclient", "pack_ui_body");
   public static final ResourceLocation FONT_LABEL = ResourceLocation.fromNamespaceAndPath("autismclient", "pack_ui_label");
   public static final ResourceLocation FONT_TITLE = ResourceLocation.fromNamespaceAndPath("autismclient", "pack_ui_title");
   public static final ResourceLocation ICON_KEYBINDS = icon("keybinds");
   public static final ResourceLocation ICON_LANSYNC = icon("lansync");
   public static final ResourceLocation ICON_MACROS = icon("macros");
   public static final ResourceLocation ICON_FABRICATOR = icon("fabricator");
   public static final ResourceLocation ICON_FILTER = icon("filter");
   public static final ResourceLocation ICON_PACKET_LOGGER = icon("packetlogger");
   public static final ResourceLocation ICON_PACKET_Q_EDITOR = icon("packetqeditor");
   public static final ResourceLocation ICON_SERVER_INFO = icon("serverinfo");
   public static final ResourceLocation ICON_MAIN_MENU_CATEGORY = icon("mainmenucategory");
   public static final ResourceLocation ICON_PACKET_CATEGORY = icon("packetcategory");
   public static final ResourceLocation ICON_SCREEN_CATEGORY = icon("screencategory");
   public static final ResourceLocation ICON_CHAT_CATEGORY = icon("chatcategory");
   public static final ResourceLocation ICON_MATCHMAKING = icon("matchmaking");
   public static final ResourceLocation ICON_MULTI = icon("multi");
   public static final ResourceLocation ICON_PROFILES = icon("profiles");

   private UiAssets() {
   }

   private static ResourceLocation icon(String var0) {
      return ResourceLocation.fromNamespaceAndPath("autismclient", "textures/gui/vanillaui/icons/" + var0 + ".png");
   }
}
