package autismclient.util;

import autismclient.util.mm.crypto.AtRestSeal;
import autismclient.util.tokenimport.TokenRefresher;
import com.google.gson.JsonObject;
import com.mojang.authlib.Environment;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import de.florianreuth.waybackauthlib.InvalidCredentialsException;
import de.florianreuth.waybackauthlib.WaybackAuthLib;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

public class AutismAccount {
   private static final Environment ALTENING_ENVIRONMENT = new Environment(
      "http://sessionserver.thealtening.com", "http://authserver.thealtening.com", "https://api.mojang.com", "The Altening"
   );
   public String id = UUID.randomUUID().toString();
   public AutismAccountType type = AutismAccountType.Cracked;
   public String label = "";
   public String token = "";
   public String sessionToken = "";
   public String username = "";
   public String uuid = "";
   public String password = "";
   public long sessionTokenExpiresAt;
   public boolean tokenRefresherAuth;
   public transient volatile TokenRefresher.FailureKind tokenImportFailureKind;
   public transient volatile AutismAccount.CheckStatus checkStatus = AutismAccount.CheckStatus.UNKNOWN;
   private transient String lastError = "";
   private transient WaybackAuthLib alteningAuth;
   private transient String alteningAuthToken = "";
   private transient boolean suppressMessages;
   transient boolean generatedStableId;

   public String displayName() {
      if (this.username != null && !this.username.isBlank()) {
         return this.username;
      } else {
         return this.label == null ? "" : this.label;
      }
   }

   public boolean fetchInfo() {
      this.lastError = "";
      this.tokenImportFailureKind = null;

      return switch (this.type) {
         case Cracked, Generated -> this.fetchCracked();
         case Session -> this.fetchSession();
         case Microsoft -> this.fetchMicrosoft();
         case TheAltening -> this.fetchTheAltening();
      };
   }

   public boolean fetchInfoSilently() {
      this.suppressMessages = true;

      try {
         return this.fetchInfo();
      } finally {
         this.suppressMessages = false;
      }
   }

   public boolean login() {
      this.lastError = "";
      boolean var1 = this.username == null || this.username.isBlank() || this.type == AutismAccountType.Microsoft && !this.hasFreshSessionToken();
      if (var1 && !this.fetchInfo()) {
         return false;
      }

      return switch (this.type) {
         case Cracked, Generated -> {
            boolean var3 = AutismAccountSessionSwitcher.setSession(
               new User(this.username, UUIDUtil.createOfflinePlayerUUID(this.username), "", Optional.empty(), Optional.empty())
            );
            if (!var3) {
               this.lastError = AutismAccountSessionSwitcher.lastError();
            }

            yield var3;
         }
         case Session, Microsoft -> {
            if (this.token != null && !this.token.isBlank() && this.uuid != null && !this.uuid.isBlank()) {
               boolean var2 = AutismAccountSessionSwitcher.setSession(
                  new User(this.username, UndashedUuid.fromStringLenient(this.uuid), this.token, Optional.empty(), Optional.empty())
               );
               if (!var2) {
                  this.lastError = AutismAccountSessionSwitcher.lastError();
               }

               yield var2;
            } else {
               this.lastError = "missing token or uuid";
               yield false;
            }
         }
         case TheAltening -> this.loginTheAltening();
      };
   }

   private boolean fetchCracked() {
      if (this.label != null && !this.label.isBlank()) {
         this.username = this.label.trim();
         this.uuid = UUIDUtil.createOfflinePlayerUUID(this.username).toString();
         return true;
      } else {
         this.lastError = "missing username";
         return false;
      }
   }

   private boolean fetchSession() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            TokenRefresher.VerifiedAccount verified = new TokenRefresher().validateSession(this.token);
            this.uuid = verified.uuid().toString();
            this.username = verified.username();
            this.sessionToken = verified.accessToken();
            this.sessionTokenExpiresAt = verified.expiresAtMillis();
            return true;
         } catch (TokenRefresher.Failure failure) {
            this.tokenImportFailureKind = failure.kind();
            this.lastError = failure.getMessage();
            return false;
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            this.tokenImportFailureKind = TokenRefresher.FailureKind.NETWORK;
            this.lastError = "Session check cancelled.";
            return false;
         }
      } else {
         this.lastError = "missing access token";
         return false;
      }
   }

   public boolean hasFreshSessionToken() {
      return this.token != null
         && !this.token.isBlank()
         && this.username != null
         && !this.username.isBlank()
         && this.uuid != null
         && !this.uuid.isBlank()
         && this.sessionTokenExpiresAt > System.currentTimeMillis() + 300000L;
   }

   private boolean fetchMicrosoft() {
      if (this.label == null || this.label.isBlank()) {
         this.lastError = "missing refresh token";
         return false;
      } else if (this.hasFreshSessionToken()) {
         return true;
      } else if (this.tokenRefresherAuth) {
         try {
            TokenRefresher.VerifiedAccount verified = new TokenRefresher().refresh(this.label);
            this.token = verified.accessToken();
            this.sessionToken = verified.accessToken();
            this.uuid = verified.uuid().toString();
            this.username = verified.username();
            this.label = verified.refreshToken();
            this.sessionTokenExpiresAt = verified.expiresAtMillis();
            return true;
         } catch (TokenRefresher.Failure failure) {
            this.tokenImportFailureKind = failure.kind();
            this.lastError = failure.getMessage();
            return false;
         } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            this.tokenImportFailureKind = TokenRefresher.FailureKind.NETWORK;
            this.lastError = "Refresh cancelled.";
            return false;
         }
      } else {
         AutismMicrosoftLogin.LoginData var1 = AutismMicrosoftLogin.login(this.label);
         if (var1.isGood()) {
            this.token = var1.mcToken;
            this.uuid = var1.uuid;
            this.username = var1.username;
            this.label = var1.newRefreshToken;
            this.sessionTokenExpiresAt = var1.expiresAtEpochMs();
            return true;
         } else {
            this.lastError = var1.error != null && !var1.error.isBlank() ? var1.error : "Microsoft login failed";
            return false;
         }
      }
   }

   private boolean fetchTheAltening() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            WaybackAuthLib var1 = this.createTheAlteningAuth();
            var1.logIn();
            this.username = var1.getCurrentProfile().name();
            this.uuid = var1.getCurrentProfile().id().toString();
            this.sessionToken = var1.getAccessToken() == null ? "" : var1.getAccessToken();
            this.alteningAuth = var1;
            this.alteningAuthToken = this.token;
            return true;
         } catch (InvalidCredentialsException var2) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var2);
            if (!this.suppressMessages) {
               AutismClientMessaging.sendPrefixed("Invalid TheAltening credentials.");
            }

            return false;
         } catch (Exception var3) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var3);
            if (!this.suppressMessages) {
               AutismClientMessaging.sendPrefixed("Failed to fetch TheAltening account info.");
            }

            return false;
         }
      } else {
         this.lastError = "missing TheAltening token";
         return false;
      }
   }

   private boolean loginTheAltening() {
      if (this.token != null && !this.token.isBlank()) {
         try {
            boolean var1 = this.validCachedTheAlteningAuth();
            WaybackAuthLib var2 = var1 ? this.alteningAuth : this.createTheAlteningAuth();
            if (!var1) {
               var2.logIn();
               this.alteningAuth = var2;
               this.alteningAuthToken = this.token;
            }

            this.username = var2.getCurrentProfile().name();
            this.uuid = var2.getCurrentProfile().id().toString();
            this.sessionToken = var2.getAccessToken() == null ? "" : var2.getAccessToken();
            boolean var3 = AutismAccountSessionSwitcher.setSession(
               new User(var2.getCurrentProfile().name(), var2.getCurrentProfile().id(), this.sessionToken, Optional.empty(), Optional.empty()),
               new YggdrasilAuthenticationService(Minecraft.getInstance().getProxy(), ALTENING_ENVIRONMENT)
            );
            if (!var3) {
               this.lastError = AutismAccountSessionSwitcher.lastError();
            }

            return var3;
         } catch (Exception var4) {
            this.clearTheAlteningAuth();
            this.lastError = shortError(var4);
            AutismClientMessaging.sendPrefixed("Failed to login with TheAltening.");
            return false;
         }
      } else {
         this.lastError = "missing TheAltening token";
         return false;
      }
   }

   private WaybackAuthLib createTheAlteningAuth() {
      WaybackAuthLib var1 = new WaybackAuthLib(ALTENING_ENVIRONMENT.servicesHost());
      var1.setUsername(this.token);
      var1.setPassword("AUTISM Client");
      return var1;
   }

   private boolean validCachedTheAlteningAuth() {
      return this.alteningAuth != null
         && this.token != null
         && !this.token.isBlank()
         && this.token.equals(this.alteningAuthToken)
         && this.alteningAuth.getCurrentProfile() != null
         && this.alteningAuth.getAccessToken() != null
         && !this.alteningAuth.getAccessToken().isBlank();
   }

   private void clearTheAlteningAuth() {
      this.alteningAuth = null;
      this.alteningAuthToken = "";
   }

   public String lastError() {
      return this.lastError == null ? "" : this.lastError;
   }

   public String failureSuffix() {
      String var1 = this.lastError();
      return var1.isBlank() ? "" : " (" + var1 + ")";
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

   public CompoundTag toTag() {
      CompoundTag var1 = new CompoundTag();
      var1.putString("id", this.stableId());
      var1.putString("type", this.type == null ? AutismAccountType.Cracked.name() : this.type.name());
      var1.putString("username", this.username == null ? "" : this.username);
      var1.putString("uuid", this.uuid == null ? "" : this.uuid);
      var1.putString("encToken", sealString(this.token));
      var1.putString("encSessionToken", sealString(this.sessionToken));
      var1.putString("encPassword", sealString(this.password));
      if (this.type == AutismAccountType.Microsoft) {
         var1.putString("encLabel", sealString(this.label));
      } else {
         var1.putString("label", this.label == null ? "" : this.label);
      }

      var1.putLong("sessionExpiresAt", this.sessionTokenExpiresAt);
      var1.putBoolean("tokenRefresherAuth", this.tokenRefresherAuth);
      return var1;
   }

   public AutismAccount fromTag(CompoundTag var1) {
      String var2 = var1.getStringOr("id", "");
      if (var2.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      } else {
         this.id = var2;
      }

      String var3 = var1.getStringOr("type", AutismAccountType.Cracked.name());

      try {
         this.type = AutismAccountType.valueOf(var3);
      } catch (IllegalArgumentException var6) {
         this.type = AutismAccountType.Cracked;
      }

      this.username = var1.getStringOr("username", "");
      this.uuid = var1.getStringOr("uuid", "");
      this.token = unsealString(var1.getStringOr("encToken", ""), var1.getStringOr("token", ""));
      this.sessionToken = unsealString(var1.getStringOr("encSessionToken", ""), var1.getStringOr("sessionToken", ""));
      this.password = unsealString(var1.getStringOr("encPassword", ""), var1.getStringOr("password", ""));
      String var4 = var1.getStringOr("label", var1.getStringOr("name", ""));
      String var5 = var1.getStringOr("encLabel", "");
      this.label = var5.isEmpty() ? var4 : unsealString(var5, var4);
      this.sessionTokenExpiresAt = var1.getLongOr("sessionExpiresAt", 0L);
      this.tokenRefresherAuth = var1.getBooleanOr("tokenRefresherAuth", false);
      return this;
   }

   public String stableId() {
      if (this.id == null || this.id.isBlank()) {
         this.id = UUID.randomUUID().toString();
         this.generatedStableId = true;
      }

      return this.id;
   }

   private static String sealString(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         byte[] var1 = AtRestSeal.seal(var0.getBytes(StandardCharsets.UTF_8));
         return var1 == null ? "" : Base64.getEncoder().encodeToString(var1);
      } else {
         return "";
      }
   }

   private static String unsealString(String var0, String var1) {
      String var2 = var1 == null ? "" : var1;
      if (var0 != null && !var0.isEmpty()) {
         try {
            byte[] var3 = AtRestSeal.unseal(Base64.getDecoder().decode(var0));
            return var3 == null ? var2 : new String(var3, StandardCharsets.UTF_8);
         } catch (Throwable var4) {
            return var2;
         }
      } else {
         return var2;
      }
   }

   @Override
   public boolean equals(Object var1) {
      if (this == var1) {
         return true;
      } else if (var1 instanceof AutismAccount var2) {
         return this.type != var2.type ? false : Objects.equals(this.identityKey(), var2.identityKey());
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.type, this.identityKey());
   }

   private String identityKey() {
      return switch (this.type) {
         case Cracked, Generated -> this.username != null && !this.username.isBlank() ? this.username : this.label;
         case Session, TheAltening -> this.token != null && !this.token.isBlank() ? this.token : this.label;
         case Microsoft -> this.label != null && !this.label.isBlank() ? this.label : this.username;
      };
   }

   public enum CheckStatus {
      UNKNOWN,
      CHECKING,
      VALID,
      EXPIRED;
   }
}
