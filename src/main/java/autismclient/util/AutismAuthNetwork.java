package autismclient.util;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

public final class AutismAuthNetwork {
   private static final List<Proxy> DIRECT_ROUTE = List.of(Proxy.NO_PROXY);
   private static final ProxySelector DIRECT_SELECTOR = new ProxySelector() {
      @Override
      public List<Proxy> select(URI var1) {
         if (var1 == null) {
            throw new IllegalArgumentException("URI is required");
         } else {
            return AutismAuthNetwork.DIRECT_ROUTE;
         }
      }

      @Override
      public void connectFailed(URI var1, SocketAddress var2, IOException var3) {
      }
   };

   private AutismAuthNetwork() {
   }

   public static Proxy directProxy() {
      return Proxy.NO_PROXY;
   }

   public static ProxySelector directProxySelector() {
      return DIRECT_SELECTOR;
   }
}
