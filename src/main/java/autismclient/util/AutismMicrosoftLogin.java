package autismclient.util;

import autismclient.AutismClientAddon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import net.minecraft.util.Util;

public final class AutismMicrosoftLogin {
   public static final String CLIENT_ID = resolveClientId();
   private static volatile String codeVerifier = "";
   private static volatile String oauthState = "";
   private static volatile HttpServer server;
   private static volatile Consumer<String> callback;
   private static final Semaphore LOGIN_PERMITS = new Semaphore(2, true);
   private static final Object LOGIN_SPACING = new Object();
   private static volatile long lastLoginStart;
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .proxy(AutismAuthNetwork.directProxySelector())
      .connectTimeout(Duration.ofSeconds(20L))
      .build();

   private static String resolveClientId() {
      String var0 = System.getProperty("autism.msauth.clientId");
      if (var0 != null && !var0.isBlank()) {
         return var0.trim();
      }

      String var1 = System.getenv("AUTISM_MSAUTH_CLIENT_ID");
      return var1 != null && !var1.isBlank() ? var1.trim() : "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
   }

   private AutismMicrosoftLogin() {
   }

   public static String getRefreshToken(Consumer<String> var0) {
      callback = var0;
      codeVerifier = newCodeVerifier();
      oauthState = newCodeVerifier();
      startServer();
      String var1 = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id="
         + CLIENT_ID
         + "&response_type=code&redirect_uri="
         + enc(redirectUri())
         + "&scope="
         + enc("XboxLive.SignIn XboxLive.offline_access")
         + "&code_challenge="
         + enc(codeChallenge(codeVerifier))
         + "&code_challenge_method=S256&state="
         + enc(oauthState)
         + "&prompt=select_account";
      try {
         AccountAuthPlatform.openBrowser(var1);
      } catch (RuntimeException browserUnavailable) {
         autismclient.AutismClientAddon.LOG.warn("Could not open the Microsoft sign-in browser.");
      }
      return var1;
   }

   public static AutismMicrosoftLogin.LoginData login(String var0) {
      try {
         LOGIN_PERMITS.acquire();
      } catch (InterruptedException var10) {
         Thread.currentThread().interrupt();
         return AutismMicrosoftLogin.LoginData.fail("Login interrupted");
      }

      try {
         synchronized (LOGIN_SPACING) {
            long var2 = lastLoginStart + 400L - System.currentTimeMillis();
            if (var2 > 0L) {
               Thread.sleep(var2);
            }

            lastLoginStart = System.currentTimeMillis();
         }

         return doLogin(var0);
      } catch (InterruptedException var12) {
         Thread.currentThread().interrupt();
         return AutismMicrosoftLogin.LoginData.fail("Login interrupted");
      } finally {
         LOGIN_PERMITS.release();
      }
   }

   private static AutismMicrosoftLogin.LoginData doLogin(String var0) {
      AutismMicrosoftLogin.HttpReply var1 = httpPostForm(
         "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
         "client_id=" + CLIENT_ID + "&refresh_token=" + enc(var0) + "&grant_type=refresh_token&scope=" + enc("XboxLive.SignIn XboxLive.offline_access")
      );
      JsonObject var2 = var1.json();
      if (var2 != null && var2.has("access_token") && var2.has("refresh_token")) {
         String var18 = var2.get("access_token").getAsString();
         var0 = var2.get("refresh_token").getAsString();
         AutismMicrosoftLogin.HttpReply var4 = httpPostJson(
            "https://user.auth.xboxlive.com/user/authenticate",
            "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d="
               + var18
               + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}"
         );
         JsonObject var5 = var4.json();
         if (var4.ok() && var5 != null && var5.has("Token")) {
            AutismMicrosoftLogin.HttpReply var19 = httpPostJson(
               "https://xsts.auth.xboxlive.com/xsts/authorize",
               "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                  + var5.get("Token").getAsString()
                  + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}"
            );
            JsonObject var7 = var19.json();
            if (var19.ok() && var7 != null && var7.has("Token")) {
               String var20 = extractUhs(var5);
               if (var20 == null) {
                  var20 = extractUhs(var7);
               }

               if (var20 == null) {
                  return AutismMicrosoftLogin.LoginData.fail("Xbox Live response was malformed (no user hash)");
               }

               AutismMicrosoftLogin.HttpReply var9 = httpPostJson(
                  "https://api.minecraftservices.com/authentication/login_with_xbox",
                  "{\"identityToken\":\"XBL3.0 x=" + var20 + ";" + var7.get("Token").getAsString() + "\"}"
               );
               JsonObject var10 = var9.json();
               if (var9.ok() && var10 != null && var10.has("access_token")) {
                  String var21 = var10.get("access_token").getAsString();
                  long var12 = var10.has("expires_in") ? var10.get("expires_in").getAsLong() : 86400L;
                  AutismMicrosoftLogin.HttpReply var14 = httpGetBearer("https://api.minecraftservices.com/minecraft/profile", var21);
                  JsonObject var15 = var14.json();
                  if (var14.status() != 404 && var15 != null && var15.has("id") && var15.has("name")) {
                     return new AutismMicrosoftLogin.LoginData(var21, var0, var15.get("id").getAsString(), var15.get("name").getAsString(), var12);
                  }

                  String var16 = var14.status() == 404
                     ? "This Microsoft account doesn't own Minecraft: Java Edition"
                     : "Couldn't read the Minecraft profile (" + httpDetail(var14) + ")";
                  AutismClientAddon.LOG.warn("MS login: {}", var16);
                  return AutismMicrosoftLogin.LoginData.fail(var16);
               } else {
                  String var11 = "Minecraft services login failed (" + httpDetail(var9) + ")";
                  AutismClientAddon.LOG.warn("MS login: {}", var11);
                  return AutismMicrosoftLogin.LoginData.fail(var11);
               }
            } else {
               String var8 = xstsError(var7);
               AutismClientAddon.LOG.warn("MS login: XSTS failed ({}): {}", var19.status(), var8);
               return AutismMicrosoftLogin.LoginData.fail(var8);
            }
         } else {
            String var6 = "Xbox Live authentication failed (" + httpDetail(var4) + ")";
            AutismClientAddon.LOG.warn("MS login: {}", var6);
            return AutismMicrosoftLogin.LoginData.fail(var6);
         }
      } else {
         String var3 = tokenErrorMessage(var1);
         AutismClientAddon.LOG.warn("MS login: token refresh failed ({}): {}", var1.status(), var3);
         return AutismMicrosoftLogin.LoginData.fail(var3);
      }
   }

   private static String extractUhs(JsonObject var0) {
      try {
         return var0.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
      } catch (Exception var2) {
         return null;
      }
   }

   private static String httpDetail(AutismMicrosoftLogin.HttpReply var0) {
      if (var0 == null) {
         return "no response";
      }

      JsonObject var1 = var0.json();
      if (var1 != null) {
         for (String var5 : new String[]{"error_description", "message", "errorMessage", "error"}) {
            if (var1.has(var5)) {
               try {
                  String var6 = var1.get(var5).getAsString();
                  if (var6 != null && !var6.isBlank()) {
                     return "HTTP " + var0.status() + ": " + var6.split("\\r?\\n")[0].trim();
                  }
               } catch (Exception var7) {
               }
            }
         }
      }

      return var0.status() < 0 ? "no response (network)" : "HTTP " + var0.status();
   }

   private static String tokenErrorMessage(AutismMicrosoftLogin.HttpReply var0) {
      JsonObject var1 = var0 == null ? null : var0.json();
      if (var1 != null) {
         try {
            if (var1.has("error_description") && !var1.get("error_description").getAsString().isBlank()) {
               return "Microsoft: " + var1.get("error_description").getAsString().split("\\r?\\n")[0].trim();
            }

            if (var1.has("error") && !var1.get("error").getAsString().isBlank()) {
               return "Microsoft error: " + var1.get("error").getAsString();
            }
         } catch (Exception var3) {
         }
      }

      int var2 = var0 == null ? -1 : var0.status();
      return var2 < 0 ? "Couldn't reach Microsoft (check your connection)" : "Microsoft token request failed (HTTP " + var2 + ")";
   }

   private static String xstsError(JsonObject var0) {
      if (var0 == null) {
         return "Xbox authorization failed";
      }

      if (var0.has("XErr")) {
         long var1 = var0.get("XErr").getAsLong();
         if (var1 == 2148916233L) {
            return "This account has no Xbox profile — create one at xbox.com first";
         }

         if (var1 == 2148916235L) {
            return "Xbox Live isn't available in this account's country/region";
         }

         if (var1 == 2148916236L || var1 == 2148916237L) {
            return "This account needs adult verification";
         }

         if (var1 == 2148916238L) {
            return "Child account — an adult must add it to a Microsoft Family";
         }
      }

      return "Xbox authorization failed";
   }

   private static String redirectUri() {
      return "http://127.0.0.1:9675";
   }

   private static String enc(String var0) {
      return URLEncoder.encode(var0 == null ? "" : var0, StandardCharsets.UTF_8);
   }

   private static String newCodeVerifier() {
      byte[] var0 = new byte[64];
      new SecureRandom().nextBytes(var0);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(var0);
   }

   private static String codeChallenge(String var0) {
      try {
         byte[] var1 = MessageDigest.getInstance("SHA-256").digest(var0.getBytes(StandardCharsets.US_ASCII));
         return Base64.getUrlEncoder().withoutPadding().encodeToString(var1);
      } catch (Exception var2) {
         return var0;
      }
   }

   private static Builder base(String var0) {
      return HttpRequest.newBuilder(URI.create(var0))
         .timeout(Duration.ofSeconds(20L))
         .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36")
         .header("Accept", "application/json");
   }

   private static AutismMicrosoftLogin.HttpReply send(HttpRequest var0) {
      try {
         HttpResponse var1 = HTTP.send(var0, BodyHandlers.ofString(StandardCharsets.UTF_8));
         return new AutismMicrosoftLogin.HttpReply(var1.statusCode(), (String)var1.body());
      } catch (Exception var2) {
         return new AutismMicrosoftLogin.HttpReply(-1, null);
      }
   }

   private static AutismMicrosoftLogin.HttpReply httpPostForm(String var0, String var1) {
      return send(base(var0).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(var1, StandardCharsets.UTF_8)).build());
   }

   private static AutismMicrosoftLogin.HttpReply httpPostJson(String var0, String var1) {
      return send(base(var0).header("Content-Type", "application/json").POST(BodyPublishers.ofString(var1, StandardCharsets.UTF_8)).build());
   }

   private static AutismMicrosoftLogin.HttpReply httpGetBearer(String var0, String var1) {
      return send(base(var0).header("Authorization", "Bearer " + var1).GET().build());
   }

   private static void startServer() {
      if (server == null) {
         try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 9675), 0);
            server.createContext("/", AutismMicrosoftLogin::handleRequest);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
         } catch (IOException var1) {
            stopServer();
            AutismClientMessaging.sendPrefixed("Failed to start Microsoft login server.");
         }
      }
   }

   public static void stopServer() {
      if (server != null) {
         server.stop(0);
         server = null;
         callback = null;
         oauthState = "";
      }
   }

   private static void handleRequest(HttpExchange var0) throws IOException {
      if ("GET".equals(var0.getRequestMethod())) {
         List<AutismMicrosoftLogin.QueryPair> var1 = parseURL(var0.getRequestURI().getRawQuery());
         String var2 = null;
         String var3 = null;

         for (AutismMicrosoftLogin.QueryPair var5 : var1) {
            if ("code".equals(var5.name())) {
               var2 = var5.value();
            } else if ("state".equals(var5.name())) {
               var3 = var5.value();
            }
         }

         boolean var6 = var2 != null && !oauthState.isBlank() && oauthState.equals(var3);
         if (var6) {
            handleCode(var2);
         }

         if (!var6) {
            writeText(var0, "Cannot authenticate.");
            Consumer var7 = callback;
            if (var7 != null) {
               var7.accept(null);
            }
         } else {
            writeText(var0, "You may now close this page.");
         }
      }

      stopServer();
   }

   private static void handleCode(String var0) {
      AutismMicrosoftLogin.HttpReply var1 = httpPostForm(
         "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
         "client_id="
            + CLIENT_ID
            + "&code="
            + enc(var0)
            + "&grant_type=authorization_code&redirect_uri="
            + enc(redirectUri())
            + "&scope="
            + enc("XboxLive.SignIn XboxLive.offline_access")
            + "&code_verifier="
            + enc(codeVerifier)
      );
      JsonObject var2 = var1.json();
      Consumer var3 = callback;
      if (var3 != null) {
         var3.accept(var2 != null && var2.has("refresh_token") ? var2.get("refresh_token").getAsString() : null);
      }
   }

   private static void writeText(HttpExchange var0, String var1) throws IOException {
      byte[] var2 = var1.getBytes(StandardCharsets.UTF_8);
      var0.sendResponseHeaders(200, var2.length);

      try (OutputStream var3 = var0.getResponseBody()) {
         var3.write(var2);
      }
   }

   private static List<AutismMicrosoftLogin.QueryPair> parseURL(String var0) {
      ArrayList var1 = new ArrayList();
      if (var0 == null) {
         return var1;
      }

      char[] var2 = var0.toCharArray();
      int var3 = 0;

      while (var3 < var2.length) {
         StringBuilder var4 = new StringBuilder();
         StringBuilder var5 = new StringBuilder();

         while (var3 < var2.length && var2[var3] != '&' && var2[var3] != ';' && var2[var3] != '=') {
            var4.append(var2[var3]);
            var3++;
         }

         if (var3 < var2.length) {
            char var6 = var2[var3++];
            if (var6 == '=') {
               while (var3 < var2.length) {
                  if (var2[var3] == '&' || var2[var3] == ';') {
                     var3++;
                     break;
                  }

                  var5.append(var2[var3]);
                  var3++;
               }
            }
         }

         if (!var4.isEmpty()) {
            var1.add(new AutismMicrosoftLogin.QueryPair(urlDecode(var4.toString()), urlDecode(var5.toString())));
         }
      }

      return var1;
   }

   private static String urlDecode(String var0) {
      if (var0 == null) {
         return null;
      }

      ByteBuffer var1 = ByteBuffer.allocate(var0.length());
      CharBuffer var2 = CharBuffer.wrap(var0);

      while (var2.hasRemaining()) {
         char var3 = var2.get();
         if (var3 == '%' && var2.remaining() >= 2) {
            char var4 = var2.get();
            char var5 = var2.get();
            int var6 = Character.digit(var4, 16);
            int var7 = Character.digit(var5, 16);
            if (var6 != -1 && var7 != -1) {
               var1.put((byte)((var6 << 4) + var7));
            } else {
               var1.put((byte)37);
               var1.put((byte)var4);
               var1.put((byte)var5);
            }
         } else if (var3 == '+') {
            var1.put((byte)32);
         } else {
            var1.put((byte)var3);
         }
      }

      var1.flip();
      return StandardCharsets.UTF_8.decode(var1).toString();
   }

   private record HttpReply(int status, String body) {
      boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      JsonObject json() {
         try {
            return this.body != null && !this.body.isBlank() ? JsonParser.parseString(this.body).getAsJsonObject() : null;
         } catch (Exception var2) {
            return null;
         }
      }
   }

   public static final class LoginData {
      public final String mcToken;
      public final String newRefreshToken;
      public final String uuid;
      public final String username;
      public final String error;
      public final long expiresInSeconds;

      public LoginData() {
         this(null, null, null, null, 0L, null);
      }

      public LoginData(String var1, String var2, String var3, String var4, long var5) {
         this(var1, var2, var3, var4, var5, null);
      }

      private LoginData(String var1, String var2, String var3, String var4, long var5, String var7) {
         this.mcToken = var1;
         this.newRefreshToken = var2;
         this.uuid = var3;
         this.username = var4;
         this.expiresInSeconds = var5;
         this.error = var7;
      }

      static AutismMicrosoftLogin.LoginData fail(String var0) {
         return new AutismMicrosoftLogin.LoginData(null, null, null, null, 0L, var0);
      }

      public boolean isGood() {
         return this.mcToken != null;
      }

      public long expiresAtEpochMs() {
         return this.expiresInSeconds > 0L ? System.currentTimeMillis() + this.expiresInSeconds * 1000L : 0L;
      }
   }

   private record QueryPair(String name, String value) {
   }
}
