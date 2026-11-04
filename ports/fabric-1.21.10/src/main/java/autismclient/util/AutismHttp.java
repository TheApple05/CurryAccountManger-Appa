package autismclient.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public final class AutismHttp {
   private AutismHttp() {
   }

   public static JsonObject getJsonDirect(String var0, String var1) {
      try {
         HttpURLConnection var2 = openDirect(var0, 15000);
         var2.setRequestMethod("GET");
         if (var1 != null && !var1.isBlank()) {
            var2.setRequestProperty("Authorization", "Bearer " + var1);
         }

         var2.setRequestProperty("Accept", "application/json");
         return readJson(var2);
      } catch (Exception var3) {
         return null;
      }
   }

   private static HttpURLConnection openDirect(String var0, int var1) throws Exception {
      HttpURLConnection var2 = (HttpURLConnection)URI.create(var0).toURL().openConnection(AutismAuthNetwork.directProxy());
      configure(var2, var1);
      return var2;
   }

   private static void configure(HttpURLConnection var0, int var1) {
      var0.setConnectTimeout(var1);
      var0.setReadTimeout(var1);
      var0.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
   }

   private static JsonObject readJson(HttpURLConnection var0) throws Exception {
      int var1 = var0.getResponseCode();
      if (var1 >= 200 && var1 < 300) {
         try (InputStream var2 = var0.getInputStream()) {
            String var3 = new String(var2.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(var3).getAsJsonObject();
         }
      } else {
         return null;
      }
   }
}
