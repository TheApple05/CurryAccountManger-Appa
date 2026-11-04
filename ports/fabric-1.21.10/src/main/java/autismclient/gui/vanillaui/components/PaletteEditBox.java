package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Vanilla editing/caret behavior with the account manager's field treatment. */
public final class PaletteEditBox extends EditBox {
    public PaletteEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setTextColor(AccountPalette.TEXT);
        setTextColorUneditable(AccountPalette.TEXT_DISABLED);
        setTextShadow(false);
    }

    @Override public void setHint(Component hint) {
        super.setHint(hint == null ? null : hint.copy().withColor(AccountPalette.TEXT_SECONDARY));
    }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth();
        boolean bordered = isBordered();
        UiRenderer.frame(graphics, UiBounds.of(x, y, width, getHeight()),
            isFocused() ? AccountPalette.FIELD_FOCUSED : AccountPalette.FIELD,
            isFocused() ? AccountPalette.FOCUS : (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + getHeight() ? AccountPalette.HOVER : AccountPalette.BORDER));
        // Vanilla uses four pixels of inner padding for a bordered EditBox.
        // Replace its background only; restore its geometry before handling input.
        setBordered(false);
        setX(x + 4);
        setY(y + (getHeight() - 8) / 2);
        setWidth(Math.max(1, width - 8));
        try {
            super.renderWidget(graphics, mouseX, mouseY, delta);
        } finally {
            setX(x);
            setY(y);
            setWidth(width);
            setBordered(bordered);
        }
    }
}
