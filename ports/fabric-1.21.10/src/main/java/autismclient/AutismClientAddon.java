package autismclient;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class AutismClientAddon {
   public static final Logger LOG = LogUtils.getLogger();
   public static final File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("autism").toFile();

   private AutismClientAddon() {
   }

   static {
      FOLDER.mkdirs();
   }
}
