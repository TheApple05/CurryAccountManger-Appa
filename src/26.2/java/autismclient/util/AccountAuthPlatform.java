package autismclient.util;

import com.mojang.authlib.Environment;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.server.Services;

/** Authlib 9 adapter; kept separate from 26.3's discovery-based Authlib 10. */
public final class AccountAuthPlatform {
   private AccountAuthPlatform() {}

   public static void openBrowser(String uri) {
      net.minecraft.util.Util.getPlatform().openUri(uri);
   }

   public static AutismAccountSessionSwitcher.PreparedServices prepare(Minecraft client, User user, boolean altening) {
      YggdrasilAuthenticationService normal = user.getAccessToken().isBlank()
         ? YggdrasilAuthenticationService.createOffline(AutismAuthNetwork.directProxy())
         : new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy());
      YggdrasilAuthenticationService sessions = altening
         ? new YggdrasilAuthenticationService(AutismAuthNetwork.directProxy(), new Environment(
            "http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening"))
         : normal;
      Services services = Services.create(sessions, client.gameDirectory);
      UserApiService userApi = user.getAccessToken().isBlank() ? UserApiService.OFFLINE : normal.createUserApiService(user.getAccessToken());
      FriendsService friends = normal.createFriendsService(user.getAccessToken());
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
}
