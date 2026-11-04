package autismclient.util;

import autismclient.AutismClientAddon;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class AutismThemeTextures {
   private static final Minecraft MC = Minecraft.getInstance();
   public static final Identifier PANORAMA_LOCATION = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama");
   private static final Identifier PANORAMA_OVERLAY_SRC = Identifier.fromNamespaceAndPath("autismclient", "textures/gui/title/background/panorama_overlay.png");
   private static final Identifier PANORAMA_OVERLAY_DYN = Identifier.fromNamespaceAndPath("autismclient", "dynamic/theme/panorama_overlay");
   private static final Map<Identifier, AutismThemeTextures.Recolored> UI_IDS = new HashMap<>();
   private static final Map<Identifier, AutismThemeTextures.ColorRecolored> COLOR_IDS = new HashMap<>();
   private static final Set<Identifier> COLOR_REBUILDING = new HashSet<>();
   private static final Set<Identifier> REBUILDING = new HashSet<>();
   private static final Map<Identifier, Integer> FAILED = new HashMap<>();
   private static final Map<Identifier, Identifier> WHITE_IDS = new HashMap<>();
   private static int themeGeneration;
   private static volatile boolean panoramaAvailable;

   private AutismThemeTextures() {
   }

   public static Identifier recolored(Identifier var0, AutismTheme.Channel var1) {
      if (var0 == null) {
         return null;
      }

      AutismTheme.State var2 = AutismTheme.active();
      if (!var2.isActive(var1)) {
         return var0;
      }

      AutismThemeTextures.Recolored var3 = UI_IDS.get(var0);
      if (var3 != null && var3.generation == themeGeneration) {
         return var3.id;
      }

      Integer var4 = FAILED.get(var0);
      if (var4 != null && var4 == themeGeneration) {
         return var3 != null ? var3.id : var0;
      }

      if (var3 == null && !REBUILDING.contains(var0)) {
         Identifier var5 = buildRecolorNow(var0, var1, var2);
         if (var5 != null) {
            return var5;
         }
      }

      kickRecolorRebuild(var0, var1, var2);
      return var3 != null ? var3.id : var0;
   }

   private static Identifier buildRecolorNow(Identifier var0, AutismTheme.Channel var1, AutismTheme.State var2) {
      byte[] var3 = readResourceBytes(var0);
      if (var3 == null) {
         return null;
      }

      int var4 = themeGeneration;

      try {
         NativeImage var5 = NativeImage.read(var3);

         Identifier var8;
         try {
            NativeImage var6 = var5.mappedCopy(var2x -> AutismTheme.recolorImagePixel(var2x, var1, var2));
            Identifier var7 = derivedId(var0);
            MC.getTextureManager().register(var7, new AutismDynamicTexture(var0.toString(), var6));
            UI_IDS.put(var0, new AutismThemeTextures.Recolored(var7, var4));
            var8 = var7;
         } catch (Throwable var10) {
            if (var5 != null) {
               try {
                  var5.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (var5 != null) {
            var5.close();
         }

         return var8;
      } catch (Throwable var11) {
         FAILED.put(var0, var4);
         AutismClientAddon.LOG.warn("Theme recolor (sync) failed for {} (backing off this theme generation)", var0, var11);
         return null;
      }
   }

   private static void kickRecolorRebuild(Identifier var0, AutismTheme.Channel var1, AutismTheme.State var2) {
      if (REBUILDING.add(var0)) {
         int var3 = themeGeneration;
         byte[] var4 = readResourceBytes(var0);
         if (var4 == null) {
            REBUILDING.remove(var0);
         } else {
            AutismBackgroundTasks.runTracked("theme-recolor", () -> {
               NativeImage var5 = null;

               try {
                  NativeImage var6 = NativeImage.read(var4);

                  try {
                     var5 = var6.mappedCopy(var2xx -> AutismTheme.recolorImagePixel(var2xx, var1, var2));
                  } catch (Throwable var10) {
                     if (var6 != null) {
                        try {
                           var6.close();
                        } catch (Throwable var9) {
                           var10.addSuppressed(var9);
                        }
                     }

                     throw var10;
                  }

                  if (var6 != null) {
                     var6.close();
                  }
               } catch (Throwable var11) {
                  AutismClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", var0, var11);
               }

               NativeImage var12 = var5;
               MC.execute(() -> {
                  REBUILDING.remove(var0);
                  if (var3 != themeGeneration) {
                     if (var12 != null) {
                        var12.close();
                     }
                  } else if (var12 == null) {
                     FAILED.put(var0, var3);
                  } else {
                     Identifier var3xx = derivedId(var0);
                     MC.getTextureManager().register(var3xx, new AutismDynamicTexture(var0.toString(), var12));
                     UI_IDS.put(var0, new AutismThemeTextures.Recolored(var3xx, var3));
                  }
               });
            });
         }
      }
   }

   private static byte[] readResourceBytes(Identifier var0) {
      try {
         Optional var1 = MC.getResourceManager().getResource(var0);
         if (var1.isEmpty()) {
            return null;
         }

         try (InputStream var2 = ((Resource)var1.get()).open()) {
            return var2.readAllBytes();
         }
      } catch (Throwable var7) {
         FAILED.put(var0, themeGeneration);
         AutismClientAddon.LOG.warn("Theme recolor failed for {} (backing off this theme generation)", var0, var7);
         return null;
      }
   }

   private static Identifier derivedId(Identifier var0) {
      return Identifier.fromNamespaceAndPath(
         "autismclient", "dynamic/theme/" + var0.getNamespace() + "/" + var0.getPath().replace('/', '_').replace(".png", "")
      );
   }

   public static AutismThemeTextures.Preview preview(Identifier var0, AutismTheme.Channel var1, int var2) {
      try {
         TextureContents var3 = TextureContents.load(MC.getResourceManager(), var0);
         NativeImage var4 = var3.image();
         NativeImage var5 = downscale(var4, var2);
         if (var5 != var4) {
            var4.close();
         }

         NativeImage var6 = new NativeImage(var5.getWidth(), var5.getHeight(), false);

         for (int var7 = 0; var7 < var5.getHeight(); var7++) {
            for (int var8 = 0; var8 < var5.getWidth(); var8++) {
               var6.setPixel(var8, var7, var5.getPixel(var8, var7));
            }
         }

         Identifier var10 = Identifier.fromNamespaceAndPath("autismclient", "dynamic/theme/preview/" + var0.getPath().replace('/', '_').replace(".png", ""));
         AutismDynamicTexture var11 = new AutismDynamicTexture("preview " + var0, var6);
         MC.getTextureManager().register(var10, var11);
         return new AutismThemeTextures.Preview(var1, var5, var11, var10);
      } catch (Throwable var9) {
         AutismClientAddon.LOG.warn("Failed to build theme preview for {}", var0, var9);
         return null;
      }
   }

   private static NativeImage downscale(NativeImage var0, int var1) {
      int var2 = var0.getWidth();
      int var3 = var0.getHeight();
      if (var2 <= var1 && var3 <= var1) {
         return var0;
      }

      float var4 = Math.min((float)var1 / var2, (float)var1 / var3);
      int var5 = Math.max(1, Math.round(var2 * var4));
      int var6 = Math.max(1, Math.round(var3 * var4));
      NativeImage var7 = new NativeImage(var5, var6, false);

      for (int var8 = 0; var8 < var6; var8++) {
         int var9 = (int)Math.floor(var8 * ((double)var3 / var6));
         int var10 = Math.min(var3, Math.max(var9 + 1, (int)Math.ceil((var8 + 1) * ((double)var3 / var6))));

         for (int var11 = 0; var11 < var5; var11++) {
            int var12 = (int)Math.floor(var11 * ((double)var2 / var5));
            int var13 = Math.min(var2, Math.max(var12 + 1, (int)Math.ceil((var11 + 1) * ((double)var2 / var5))));
            long var14 = 0L;
            long var16 = 0L;
            long var18 = 0L;
            long var20 = 0L;
            int var22 = 0;

            for (int var23 = var9; var23 < var10; var23++) {
               for (int var24 = var12; var24 < var13; var24++) {
                  int var25 = var0.getPixel(var24, var23);
                  int var26 = var25 >>> 24 & 0xFF;
                  var14 += var26;
                  var16 += (long)(var25 >>> 16 & 0xFF) * var26;
                  var18 += (long)(var25 >>> 8 & 0xFF) * var26;
                  var20 += (long)(var25 & 0xFF) * var26;
                  var22++;
               }
            }

            if (var22 == 0) {
               var7.setPixel(var11, var8, 0);
            } else {
               int var27 = (int)(var14 / var22);
               int var28 = var14 == 0L ? 0 : (int)(var16 / var14);
               int var29 = var14 == 0L ? 0 : (int)(var18 / var14);
               int var30 = var14 == 0L ? 0 : (int)(var20 / var14);
               var7.setPixel(var11, var8, var27 << 24 | var28 << 16 | var29 << 8 | var30);
            }
         }
      }

      return var7;
   }

   private record ColorRecolored(Identifier id, int color) {
   }

   public static final class Preview implements AutoCloseable {
      private final AutismTheme.Channel channel;
      private final NativeImage source;
      private final AutismDynamicTexture texture;
      private final Identifier id;
      private int signature = Integer.MIN_VALUE;
      private final Object lock = new Object();
      private boolean closed;
      private boolean jobRunning;
      private boolean resourcesFreed;
      private AutismTheme.State pendingState;

      private Preview(AutismTheme.Channel var1, NativeImage var2, AutismDynamicTexture var3, Identifier var4) {
         this.channel = var1;
         this.source = var2;
         this.texture = var3;
         this.id = var4;
      }

      public Identifier id() {
         return this.id;
      }

      public int width() {
         return this.source.getWidth();
      }

      public int height() {
         return this.source.getHeight();
      }

      private static int backdropSignature(AutismTheme.State var0) {
         int var1 = Float.floatToIntBits(var0.hueOf(AutismTheme.Channel.BACKDROP)) * 31 + Float.floatToIntBits(var0.satOf(AutismTheme.Channel.BACKDROP));
         return var1 == 0 ? 1 : var1;
      }

      public void update(AutismTheme.State var1) {
         int var2 = this.channel == AutismTheme.Channel.BACKDROP ? backdropSignature(var1) : var1.previewSignature(this.channel);
         if (var2 != this.signature) {
            this.signature = var2;
            synchronized (this.lock) {
               if (this.closed) {
                  return;
               }

               this.pendingState = var1;
               if (this.jobRunning) {
                  return;
               }

               this.jobRunning = true;
            }

            AutismBackgroundTasks.runTracked("theme-preview", this::recolorPending);
         }
      }

      private void recolorPending() {
         try {
            while (true) {
               AutismTheme.State var1;
               synchronized (this.lock) {
                  if (this.closed || this.pendingState == null) {
                     this.jobRunning = false;
                     break;
                  }

                  var1 = this.pendingState;
                  this.pendingState = null;
               }

               int var15 = this.source.getWidth();
               int var3 = this.source.getHeight();
               int[] var4 = new int[var15 * var3];

               for (int var5 = 0; var5 < var3; var5++) {
                  for (int var6 = 0; var6 < var15; var6++) {
                     var4[var5 * var15 + var6] = this.channel == AutismTheme.Channel.BACKDROP
                        ? AutismTheme.recolorImagePixelTo(
                           this.source.getPixel(var6, var5), var1.hueOf(AutismTheme.Channel.BACKDROP), var1.satOf(AutismTheme.Channel.BACKDROP)
                        )
                        : AutismTheme.recolorImagePixel(this.source.getPixel(var6, var5), this.channel, var1);
                  }
               }

               AutismThemeTextures.MC.execute(() -> {
                  synchronized (this.lock) {
                     if (!this.closed) {
                        for (int var5x = 0; var5x < var3; var5x++) {
                           for (int var6x = 0; var6x < var15; var6x++) {
                              this.texture.pixels.setPixel(var6x, var5x, var4[var5x * var15 + var6x]);
                           }
                        }

                        this.texture.upload();
                     }
                  }
               });
            }
         } catch (Throwable var12) {
            synchronized (this.lock) {
               this.jobRunning = false;
            }

            AutismClientAddon.LOG.error("Theme preview recolor failed", var12);
         }

         boolean var13;
         synchronized (this.lock) {
            var13 = this.closed;
         }

         if (var13) {
            this.closeResources();
         }
      }

      @Override
      public void close() {
         synchronized (this.lock) {
            this.closed = true;
            this.pendingState = null;
            if (this.jobRunning) {
               return;
            }
         }

         this.closeResources();
      }

      private void closeResources() {
         synchronized (this.lock) {
            if (this.resourcesFreed) {
               return;
            }

            this.resourcesFreed = true;
         }

         try {
            this.source.close();
         } catch (Throwable var4) {
         }

         try {
            this.texture.close();
         } catch (Throwable var3) {
         }
      }
   }

   private record Recolored(Identifier id, int generation) {
   }
}
