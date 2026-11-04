package autismclient.util;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileResult;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.server.Services;

/** Authlib 10 no longer provides the legacy Yggdrasil authentication service. */
public final class AccountAuthPlatform {
   private AccountAuthPlatform() {}

   public static void openBrowser(String uri) {
      com.mojang.blaze3d.Blaze3D.openUri(URI.create(uri));
   }

   public static AutismAccountSessionSwitcher.PreparedServices prepare(Minecraft client, User user, boolean altening) {
      MinecraftServicesDiscoveryService discovery = user.getAccessToken().isBlank()
         ? MinecraftServicesDiscoveryService.createOffline(AutismAuthNetwork.directProxy())
         : MinecraftServicesDiscoveryService.create(AutismAuthNetwork.directProxy());
      Services services = Services.create(discovery, client.gameDirectory);
      if (altening) services = new Services(new TheAlteningSessionService(services.sessionService()),
         services.servicesKeySet(), services.profileRepository(), services.nameToIdCache(), services.profileResolver());
      UserApiService userApi = user.getAccessToken().isBlank() ? UserApiService.OFFLINE : discovery.createUserApiService(user.getAccessToken());
      FriendsService friends = discovery.createFriendsService(user.getAccessToken());
      RemoteFriendListUpdateHandler updates = new RemoteFriendListUpdateHandler(friends, client);
      try {
         PlayerSocialManager social = new PlayerSocialManager(client, userApi, friends, updates);
         return new AutismAccountSessionSwitcher.PreparedServices(services, userApi, social, updates);
      } catch (RuntimeException failure) {
         updates.close();
         throw failure;
      }
   }

   public static void applyProperties(PlayerSocialManager social, UserApiService.UserProperties properties) {
      social.setFriendListEnabled(properties.flag(UserApiService.UserFlag.FRIENDS_ENABLED));
      social.setAllowFriendRequests(properties.flag(UserApiService.UserFlag.ACCEPT_FRIEND_INVITES));
   }

   /** Retains the legacy server-join endpoint without inventing a discovery endpoint. */
   private record TheAlteningSessionService(SessionService vanilla) implements SessionService {
      private static final HttpClient HTTP = HttpClient.newBuilder()
         .proxy(AutismAuthNetwork.directProxySelector()).followRedirects(HttpClient.Redirect.NEVER)
         .connectTimeout(Duration.ofSeconds(15)).build();

      @Override public void joinServer(UUID profileId, String accessToken, String serverId) throws AuthenticationException {
         JsonObject body = new JsonObject();
         body.addProperty("accessToken", accessToken);
         body.addProperty("selectedProfile", profileId.toString().replace("-", ""));
         body.addProperty("serverId", serverId);
         HttpRequest request = HttpRequest.newBuilder(URI.create("http://sessionserver.thealtening.com/session/minecraft/join"))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
         try {
            int status = HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) throw new AuthenticationException("TheAltening session join failed (HTTP " + status + ").");
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException("TheAltening session join cancelled.");
         } catch (java.io.IOException unavailable) {
            throw new AuthenticationUnavailableException("TheAltening session service is unavailable.");
         }
      }

      @Override public ProfileResult hasJoinedServer(String name, String serverId, InetAddress address) throws AuthenticationUnavailableException {
         return vanilla.hasJoinedServer(name, serverId, address);
      }
      @Override public Property getPackedTextures(GameProfile profile) { return vanilla.getPackedTextures(profile); }
      @Override public MinecraftProfileTextures unpackTextures(Property property) { return vanilla.unpackTextures(property); }
      @Override public ProfileResult fetchProfile(UUID profileId, boolean secure) { return vanilla.fetchProfile(profileId, secure); }
      @Override public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException { return vanilla.getSecurePropertyValue(property); }
   }
}
