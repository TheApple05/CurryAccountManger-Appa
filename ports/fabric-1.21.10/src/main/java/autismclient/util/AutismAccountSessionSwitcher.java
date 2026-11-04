package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.mixin.accessor.AutismAccountMinecraftAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.Util;

public final class AutismAccountSessionSwitcher {
   private static User originalUser;
   private static String lastError = "";

   private AutismAccountSessionSwitcher() {
   }

   public static User getOriginalUser() {
      if (originalUser == null) {
         originalUser = Minecraft.getInstance().getUser();
      }

      return originalUser;
   }

   public static boolean setSession(User var0) {
      return setSession(var0, new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy()));
   }

   public static boolean setSession(User var0, YggdrasilAuthenticationService var1) {
      lastError = "";

      try {
         Minecraft var2 = Minecraft.getInstance();
         if (originalUser == null) {
            originalUser = var2.getUser();
         }

         AutismAccountMinecraftAccessor var3 = (AutismAccountMinecraftAccessor)var2;
         YggdrasilAuthenticationService var4 = new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());
         Services var5 = Services.create(var1, var2.gameDirectory);
         UserApiService var6 = var4.createUserApiService(var0.getAccessToken());
         Path var9 = var2.gameDirectory.toPath().resolve("assets").resolve("skins");
         var3.autism$setServices(var5);
         var3.autism$setUser(var0);
         var3.autism$setUserApiService(var6);
         var3.autism$setPlayerSocialManager(new PlayerSocialManager(var2, var6));
         var3.autism$setProfileKeyPairManager(ProfileKeyPairManager.create(var6, var0, var2.gameDirectory.toPath()));
         var3.autism$setReportingContext(ReportingContext.create(ReportEnvironment.local(), var6));
         var3.autism$setProfileFuture(
            CompletableFuture.supplyAsync(() -> var2.services().sessionService().fetchProfile(var2.getUser().getProfileId(), true), Util.nonCriticalIoPool())
         );
         var3.autism$setSkinManager(
            new SkinManager(var9, var5, new SkinTextureDownloader(AutismAuthNetwork.directProxy(), var2.getTextureManager(), var2), var2)
         );
         return true;
      } catch (Exception var10) {
         lastError = shortError(var10);
         AutismClientAddon.LOG.error("Failed to switch Autism account session", var10);
         return false;
      }
   }

   public static String lastError() {
      return lastError == null ? "" : lastError;
   }

   private static String shortError(Throwable var0) {
      if (var0 == null) {
         return "unknown error";
      }

      String var1 = var0.getClass().getSimpleName();
      String var2 = var0.getMessage();
      if (var2 != null && !var2.isBlank()) {
         var2 = var2.replace('\n', ' ').replace('\r', ' ').trim();
         if (var2.length() > 120) {
            var2 = var2.substring(0, 117) + "...";
         }

         return var1 + ": " + var2;
      } else {
         return var1;
      }
   }
}
