package autismclient.util;

import autismclient.AutismClientAddon;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class AutismBackgroundTasks {
   private static final AtomicInteger COUNTER = new AtomicInteger();
   private static final ExecutorService POOL = Executors.newCachedThreadPool(var0 -> {
      Thread var1 = new Thread(var0);
      var1.setDaemon(true);
      return var1;
   });

   private AutismBackgroundTasks() {
   }

   public static Future<?> runTracked(String var0, Runnable var1) {
      String var2 = var0 != null && !var0.isBlank() ? var0 : "Autism-BG-" + COUNTER.incrementAndGet();
      return POOL.submit(() -> {
         Thread var2x = Thread.currentThread();
         String var3 = var2x.getName();
         var2x.setName(var2);

         try {
            var1.run();
         } catch (Throwable var8) {
            AutismClientAddon.LOG.error("Background task '" + var2 + "' failed", var8);
         } finally {
            var2x.setName(var3);
         }
      });
   }

   public static Future<?> watchUntil(String var0, BooleanSupplier var1, Runnable var2, long var3) {
      return runTracked(var0, () -> {
         while (!var1.getAsBoolean()) {
            try {
               Thread.sleep(Math.max(1L, var3));
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         var2.run();
      });
   }
}
