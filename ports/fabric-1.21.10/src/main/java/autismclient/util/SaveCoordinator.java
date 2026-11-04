package autismclient.util;

import autismclient.AutismClientAddon;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

final class SaveCoordinator {
   private static final Object LOCK = new Object();
   private static final Map<String, Runnable> LATEST = new HashMap<>();
   private static final Map<String, Boolean> DRAINING = new HashMap<>();
   private static final Map<String, ScheduledFuture<?>> DEBOUNCED = new HashMap<>();
   private static final Map<String, Long> DEBOUNCE_VERSIONS = new HashMap<>();
   private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(var0 -> {
      Thread var1 = new Thread(var0, "autism-persistence");
      var1.setDaemon(true);
      return var1;
   });
   private SaveCoordinator() {}

   static void enqueueLatest(String var0, Runnable var1) {
      if (var0 != null && var1 != null) {
         synchronized (LOCK) {
            LATEST.put(var0, var1);
            if (DRAINING.putIfAbsent(var0, Boolean.TRUE) == null) {
               WORKER.execute(() -> drain(var0));
            }
         }
      }
   }

   private static void drain(String var0) {
      while (true) {
         Runnable var1;
         synchronized (LOCK) {
            var1 = LATEST.remove(var0);
            if (var1 == null) {
               DRAINING.remove(var0);
               return;
            }
         }

         try {
            var1.run();
         } catch (Throwable var4) {
            AutismClientAddon.LOG.error("Persistence task failed for {}", var0, var4);
         }
      }
   }

   static void flushBlocking(long var0) {
      CountDownLatch var2 = new CountDownLatch(1);
      WORKER.execute(var2::countDown);

      try {
         var2.await(Math.max(0L, var0), TimeUnit.MILLISECONDS);
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
      } catch (Throwable var5) {
      }
   }
}
