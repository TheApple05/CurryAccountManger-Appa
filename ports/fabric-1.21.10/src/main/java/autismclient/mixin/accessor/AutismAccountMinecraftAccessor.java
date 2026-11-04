package autismclient.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface AutismAccountMinecraftAccessor {
   @Mutable
   @Accessor("user")
   void autism$setUser(User var1);

   @Mutable
   @Accessor("profileKeyPairManager")
   void autism$setProfileKeyPairManager(ProfileKeyPairManager var1);

   @Mutable
   @Accessor("userApiService")
   void autism$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("skinManager")
   void autism$setSkinManager(SkinManager var1);

   @Mutable
   @Accessor("playerSocialManager")
   void autism$setPlayerSocialManager(PlayerSocialManager var1);


   @Mutable
   @Accessor("reportingContext")
   void autism$setReportingContext(ReportingContext var1);

   @Mutable
   @Accessor("profileFuture")
   void autism$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("services")
   void autism$setServices(Services var1);
}
