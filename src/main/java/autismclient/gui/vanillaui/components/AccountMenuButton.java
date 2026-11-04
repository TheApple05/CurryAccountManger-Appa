package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The multiplayer entry point uses the same idle control treatment. */
public final class AccountMenuButton extends Button {
    public AccountMenuButton(OnPress action) {
        super(0, 0, 60, 20, Component.literal("Accounts"), action, DEFAULT_NARRATION);
    }

    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiBounds bounds = UiBounds.of(getX(), getY(), getWidth(), getHeight());
        UiRenderer.frame(graphics, bounds, AccountPalette.SURFACE,
            isHovered() ? AccountPalette.HOVER : (isFocused() ? AccountPalette.FOCUS : AccountPalette.BORDER));
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
            getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, AccountPalette.TEXT);
    }
}
