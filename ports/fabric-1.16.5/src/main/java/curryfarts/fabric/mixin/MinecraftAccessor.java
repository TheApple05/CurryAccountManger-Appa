package curryfarts.fabric.mixin;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.SocialInteractionsService;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable @Accessor("user") void curryfarts$setUser(User value);
    @Mutable @Accessor("minecraftSessionService") void curryfarts$setSessionService(MinecraftSessionService value);
    @Mutable @Accessor("socialInteractionsService") void curryfarts$setSocialService(SocialInteractionsService value);
    @Accessor("socialInteractionsService") SocialInteractionsService curryfarts$getSocialService();
    @Accessor("profileProperties") PropertyMap curryfarts$getProfileProperties();
    @Mutable @Accessor("skinManager") void curryfarts$setSkinManager(SkinManager value);
    @Mutable @Accessor("playerSocialManager") void curryfarts$setPlayerSocialManager(PlayerSocialManager value);
}
