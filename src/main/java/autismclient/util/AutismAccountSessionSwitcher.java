package autismclient.util;

import autismclient.AutismClientAddon;
import autismclient.mixin.accessor.AutismAccountMinecraftAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

public final class AutismAccountSessionSwitcher {
   private static volatile User originalUser;
   private static volatile String lastError = "";

   private AutismAccountSessionSwitcher() {}

   public static User getOriginalUser() {
      if (originalUser == null) originalUser = Minecraft.getInstance().getUser();
      return originalUser;
   }

   public static boolean setSession(User user) { return setSession(user, false); }

   public static boolean setTheAlteningSession(User user) { return setSession(user, true); }

   private static boolean setSession(User user, boolean altening) {
      lastError = "";
      PreparedServices prepared = null;
      try {
         Objects.requireNonNull(user, "user");
         Minecraft client = Minecraft.getInstance();
         getOriginalUser();
         prepared = AccountAuthPlatform.prepare(client, user, altening);
         PreparedServices ready = prepared;
         Services services = ready.services();
         UserApiService userApi = ready.userApiService();
         Path skinPath = client.gameDirectory.toPath().resolve("assets").resolve("skins");
         SkinManager skins = new SkinManager(skinPath, services,
            new SkinTextureDownloader(AutismAuthNetwork.directProxy(), client.getTextureManager(), client), client);
         ProfileKeyPairManager keys = ProfileKeyPairManager.create(userApi, user, client.gameDirectory.toPath());
         ReportingContext reporting = ReportingContext.create(ReportEnvironment.local(), userApi);
         // Capture this session: a later switch must not redirect an in-flight lookup.
         CompletableFuture<?> profile = user.getAccessToken().isBlank() ? CompletableFuture.completedFuture(null)
            : CompletableFuture.supplyAsync(() -> services.sessionService().fetchProfile(user.getProfileId(), true), Util.nonCriticalIoPool());
         CompletableFuture<UserApiService.UserProperties> properties = user.getAccessToken().isBlank()
            ? CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES)
            : CompletableFuture.supplyAsync(() -> {
               try { return userApi.fetchProperties(); }
               catch (Exception ignored) { return UserApiService.OFFLINE_PROPERTIES; }
            }, Util.nonCriticalIoPool());

         Runnable commit = () -> {
            AutismAccountMinecraftAccessor accessor = (AutismAccountMinecraftAccessor) client;
            RemoteFriendListUpdateHandler previousUpdates = accessor.autism$getRemoteFriendListUpdateHandler();
            PlayerSocialManager previousSocial = client.getPlayerSocialManager();
            accessor.autism$setServices(services);
            accessor.autism$setUser(user);
            accessor.autism$setUserApiService(userApi);
            accessor.autism$setUserPropertiesFuture(properties);
            accessor.autism$setRemoteFriendListUpdateHandler(ready.friendUpdates());
            accessor.autism$setPlayerSocialManager(ready.socialManager());
            accessor.autism$setProfileKeyPairManager(keys);
            accessor.autism$setReportingContext(reporting);
            accessor.autism$setProfileFuture(profile);
            accessor.autism$setSkinManager(skins);
            AccountAuthPlatform.applyProperties(ready.socialManager(), properties.getNow(UserApiService.OFFLINE_PROPERTIES));
            properties.thenAccept(value -> client.execute(() -> {
               if (client.getUser() == user && client.getPlayerSocialManager() == ready.socialManager()) {
                  AccountAuthPlatform.applyProperties(ready.socialManager(), value);
               }
            }));
            if (previousSocial != null) previousSocial.stopOnlineMode();
            if (previousUpdates != null) previousUpdates.close();
         };
         if (client.isSameThread()) commit.run();
         else client.executeBlocking(commit);
         return true;
      } catch (Exception failure) {
         if (prepared != null) prepared.friendUpdates().close();
         // Auth failures can contain server bodies or credentials; only retain the type.
         lastError = failure.getClass().getSimpleName();
         AutismClientAddon.LOG.error("Failed to switch Autism account session ({})", lastError);
         return false;
      }
   }

   public static String lastError() { return lastError; }

   public record PreparedServices(Services services, UserApiService userApiService,
      PlayerSocialManager socialManager, RemoteFriendListUpdateHandler friendUpdates) {}
}
