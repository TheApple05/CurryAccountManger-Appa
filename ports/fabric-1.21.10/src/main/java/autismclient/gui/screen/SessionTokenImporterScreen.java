package autismclient.gui.screen;

import autismclient.gui.vanillaui.AccountPalette;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.util.AutismTokenImporter;
import autismclient.util.AutismUiScale;
import autismclient.util.tokenimport.TokenImportService;
import autismclient.util.tokenimport.TokenImportService.EntryResult;
import autismclient.util.tokenimport.TokenImportService.Summary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** In-game batch importer. Only explicit user input reaches the authentication worker. */
public final class SessionTokenImporterScreen extends AutismScreen {
    private static final AtomicBoolean CHOOSER_OPEN = new AtomicBoolean();
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(task -> {
        Thread worker = new Thread(task, "autism-token-import");
        worker.setDaemon(true);
        return worker;
    });
    private final AutismAccountsScreen switcher;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private MultiLineEditBox inputField;
    private Summary summary;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private Future<?> job;
    private boolean running;
    private boolean fileLoading;
    private boolean closed;
    private int generation;
    private int resultsOffset;
    private String statusMessage = "Paste tokens or open a UTF-8 .txt file to begin.";

    public SessionTokenImporterScreen(AutismAccountsScreen switcher) {
        super(Component.literal("Session Token Importer"));
        this.switcher = switcher;
    }

    @Override protected void init() {
        String value = inputField == null ? "" : inputField.getValue();
        closed = false;
        inputField = new MultiLineEditBox.Builder()
            .setX(panelX() + 12).setY(inputTop())
            .setPlaceholder(Component.literal("One token per line, username:token, or REFRESHTOKEN / MCTOKEN fields"))
            .setTextColor(AccountPalette.TEXT).setTextShadow(false)
            .setCursorColor(AccountPalette.FOCUS)
            .setShowBackground(false).setShowDecorations(false)
            .build(font, innerWidth(), inputHeight(), Component.literal("Tokens to validate and import"));
        inputField.setCharacterLimit(AutismTokenImporter.MAX_INPUT_CHARS);
        inputField.setValue(value);
        addRenderableWidget(inputField);
        rebuildButtons();
    }

    private int panelX() { return Math.max(8, (screenWidth() - panelWidth()) / 2); }
    private int panelWidth() { return Math.max(24, Math.min(584, screenWidth() - 16)); }
    private int innerWidth() { return Math.max(1, panelWidth() - 24); }
    private int inputTop() { return screenHeight() < 250 ? 82 : 94; }
    private int inputHeight() { return Math.max(28, Math.min(100, (screenHeight() - 170) / 2)); }
    private int actionTop() { return inputTop() + inputHeight() + 8; }
    private int resultsTop() { return actionTop() + 68; }
    private int visibleResults() { return Math.max(0, (screenHeight() - resultsTop() - 18) / 16); }

    private CompactOverlayButton button(int x, int y, int width, String label,
                                        CompactOverlayButton.Variant variant, Runnable action) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, Math.max(1, width), 18,
            Component.literal(label), ignored -> action.run()).setVariant(variant);
        buttons.add(button);
        return button;
    }

    private void rebuildButtons() {
        buttons.clear();
        int x = panelX() + 12, width = innerWidth();
        int tabWidth = Math.max(1, (width - 4) / 2);
        button(x, 10, tabWidth, "Session Token Switcher", CompactOverlayButton.Variant.SECONDARY, this::onClose);
        button(x + tabWidth + 4, 10, tabWidth, "Session Token Importer", CompactOverlayButton.Variant.FILTER_ON, () -> {});
        int third = Math.max(1, (width - 8) / 3);
        button(x, 58, third, "Paste", CompactOverlayButton.Variant.SECONDARY, this::pasteFromClipboard).active = !busy();
        button(x + third + 4, 58, third, "Open TXT", CompactOverlayButton.Variant.SECONDARY, this::openTextFile).active = !busy();
        button(x + (third + 4) * 2, 58, width - (third + 4) * 2, "Clear", CompactOverlayButton.Variant.GHOST, () -> {
            inputField.setValue("");
            statusMessage = "Input cleared.";
        }).active = !busy();
        int mainWidth = Math.max(1, width - 100);
        button(x, actionTop(), mainWidth, running ? "Validating..." : "Validate & Import",
            CompactOverlayButton.Variant.PRIMARY, this::startImport).active = !busy();
        button(x + mainWidth + 4, actionTop(), Math.max(1, width - mainWidth - 4), "Cancel",
            CompactOverlayButton.Variant.DANGER, this::cancelImport).active = busy();
        inputField.active = !busy();
        if (busy()) inputField.setFocused(false);
        resultsOffset = Math.max(0, Math.min(resultsOffset, maxResultOffset()));
    }

    private boolean busy() { return running || fileLoading; }

    private void pasteFromClipboard() {
        if (busy()) return;
        String value;
        try {
            value = minecraft.keyboardHandler.getClipboard();
        } catch (RuntimeException ignored) {
            statusMessage = "Clipboard is empty or unavailable.";
            return;
        }
        if (value == null || value.isBlank()) {
            statusMessage = "Clipboard is empty or unavailable.";
            return;
        }
        if (value.length() > AutismTokenImporter.MAX_INPUT_CHARS) {
            statusMessage = "Input is too large. Import a smaller batch (maximum 1 MiB).";
            return;
        }
        inputField.setValue(value);
        statusMessage = "Clipboard loaded. Click Validate & Import to check these entries.";
    }

    private void openTextFile() {
        if (busy()) return;
        // Cancelling the screen cannot close an OS-native dialog already inside JNI.
        // Keep one chooser across screens until its native call actually returns.
        if (!CHOOSER_OPEN.compareAndSet(false, true)) {
            statusMessage = "A file chooser is already open. Close it before opening another.";
            return;
        }
        fileLoading = true;
        int request = ++generation;
        statusMessage = "Choose a UTF-8 .txt file. Validation starts only when you click Import.";
        rebuildButtons();
        job = WORKERS.submit(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.txt")).flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog("Import Minecraft tokens", "", filters,
                    "Text files (*.txt)", false);
                String value = selected == null ? null : AutismTokenImporter.readFile(Path.of(selected));
                minecraft.execute(() -> {
                    if (closed || generation != request) return;
                    fileLoading = false;
                    if (value != null) inputField.setValue(value);
                    statusMessage = value == null ? "File selection cancelled." : "File loaded. Click Validate & Import to begin.";
                    rebuildButtons();
                });
            } catch (Throwable ignored) {
                fileFailed(request);
            } finally {
                CHOOSER_OPEN.set(false);
            }
        });
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (busy() || paths.size() != 1) return;
        fileLoading = true;
        int request = ++generation;
        statusMessage = "Reading text file...";
        rebuildButtons();
        job = WORKERS.submit(() -> {
            try {
                String value = AutismTokenImporter.readFile(paths.getFirst());
                minecraft.execute(() -> {
                    if (closed || generation != request) return;
                    fileLoading = false;
                    inputField.setValue(value);
                    statusMessage = "File loaded. Click Validate & Import to begin.";
                    rebuildButtons();
                });
            } catch (Throwable ignored) {
                fileFailed(request);
            }
        });
    }

    private void fileFailed(int request) {
        minecraft.execute(() -> {
            if (closed || generation != request) return;
            fileLoading = false;
            statusMessage = "Could not open file. Use a readable UTF-8 .txt file up to 1 MiB.";
            rebuildButtons();
        });
    }

    private void startImport() {
        if (busy()) return;
        String text = inputField.getValue();
        if (text.isBlank()) {
            statusMessage = "Paste tokens or open a .txt file first.";
            return;
        }
        AtomicBoolean guard = new AtomicBoolean();
        cancelled = guard;
        int request = ++generation;
        running = true;
        summary = null;
        resultsOffset = 0;
        // The worker owns this batch now. Do not retain credential text in the screen.
        inputField.setValue("");
        statusMessage = "Validating entries. Working accounts are saved automatically.";
        rebuildButtons();
        job = WORKERS.submit(() -> {
            try {
                Summary result = AutismTokenImporter.importText(text, guard::get, progress -> minecraft.execute(() -> {
                    if (closed || generation != request || guard.get()) return;
                    summary = progress;
                    resultsOffset = maxResultOffset();
                }));
                minecraft.execute(() -> {
                    if (closed || generation != request) return;
                    summary = guard.get() ? cancelledSummary(result) : result;
                    running = false;
                    resultsOffset = maxResultOffset();
                    statusMessage = summary.cancelled() ? "Import cancelled. Completed imports are saved."
                        : result.processed() < result.total() ? "Stopped before all entries were checked. Retry remaining entries later."
                        : result.total() == 0 ? "No token entries found."
                        : result.imported() + result.duplicates() == 0 ? "Finished. No working accounts were imported; review the results."
                        : "Finished. Working accounts are ready in Session Token Switcher.";
                    rebuildButtons();
                });
            } catch (Throwable ignored) {
                minecraft.execute(() -> {
                    if (closed || generation != request || guard.get()) return;
                    running = false;
                    statusMessage = "Import could not finish. Try a smaller batch or check your connection.";
                    rebuildButtons();
                });
            }
        });
    }

    private void cancelImport() {
        if (!busy()) return;
        cancelled.set(true);
        // Let a started import publish its final counts after stopping. A later batch
        // increments the generation, so an old completion cannot overwrite a new run.
        if (fileLoading) generation++;
        // Native dialogs cannot be interrupted. Let their task reach finally even
        // when cancellation wins before it starts, so the chooser guard is released.
        if (job != null && !fileLoading) job.cancel(true);
        running = false;
        fileLoading = false;
        summary = cancelledSummary(summary);
        statusMessage = "Cancelled. Accounts already imported remain saved.";
        rebuildButtons();
    }

    private static Summary cancelledSummary(Summary value) {
        return value == null ? new Summary(0, 0, 0, 0, 0, 0, 0, true, List.of())
            : new Summary(value.total(), value.processed(), value.imported(), value.duplicates(), value.invalid(),
                value.expired(), value.failed(), true, value.results());
    }

    private int maxResultOffset() {
        return summary == null ? 0 : Math.max(0, summary.results().size() - visibleResults());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX), my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), AccountPalette.BACKGROUND);
            drawPanel(graphics, panelX(), 34, panelWidth(), Math.max(1, screenHeight() - 42), AccountPalette.SURFACE);
            text(graphics, "Import working Minecraft accounts", panelX() + 12, 42, AccountPalette.TEXT);
            if (screenHeight() >= 250) text(graphics, "Session tokens are checked; Microsoft refresh tokens create fresh sessions.", panelX() + 12, 82, AccountPalette.TEXT_SECONDARY);
            UiRenderer.frame(graphics, UiBounds.of(inputField.getX(), inputField.getY(), inputField.getWidth(), inputField.getHeight()),
                inputField.isFocused() ? AccountPalette.FIELD_FOCUSED : AccountPalette.FIELD,
                inputField.isFocused() ? AccountPalette.FOCUS : AccountPalette.CONTROL_BORDER);
            super.render(graphics, mx, my, delta);
            for (CompactOverlayButton button : buttons) CompactOverlayButton.renderStyled(graphics, font, button, mx, my);
            text(graphics, statusMessage, panelX() + 12, actionTop() + 26, AccountPalette.TEXT_SECONDARY);
            if (summary != null) {
                text(graphics, summary.processed() + " / " + summary.total() + " checked  |  " + summary.imported()
                    + " imported  |  " + summary.duplicates() + " duplicates", panelX() + 12, actionTop() + 40, AccountPalette.SUCCESS_TEXT);
                text(graphics, summary.invalid() + " invalid  |  " + summary.expired() + " expired  |  " + summary.failed()
                    + " failed" + (summary.results().size() > visibleResults() ? "  |  Scroll for results" : ""),
                    panelX() + 12, actionTop() + 53, AccountPalette.TEXT_SECONDARY);
                renderResults(graphics);
            }
            if (screenHeight() < 226) text(graphics, "Increase the window height to see full results.", panelX() + 12,
                Math.max(0, screenHeight() - 12), AccountPalette.FOCUS);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderResults(GuiGraphics graphics) {
        List<EntryResult> results = summary.results();
        int end = Math.min(results.size(), resultsOffset + visibleResults());
        for (int index = resultsOffset; index < end; index++) {
            EntryResult entry = results.get(index);
            int y = resultsTop() + (index - resultsOffset) * 16;
            UiRenderer.rect(graphics, UiBounds.of(panelX() + 12, y - 2, innerWidth(), 15),
                index % 2 == 0 ? AccountPalette.FIELD : AccountPalette.SURFACE_RAISED);
            int color = entry.status() == TokenImportService.Status.IMPORTED ? AccountPalette.SUCCESS_TEXT
                : entry.status() == TokenImportService.Status.DUPLICATE ? AccountPalette.TEXT_SECONDARY : AccountPalette.FOCUS;
            text(graphics, "Line " + entry.lineNumber() + "  " + entry.status() + "  " + entry.message(), panelX() + 16, y + 1, color);
        }
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int color) {
        UiText.drawFitted(graphics, font, text, null, color, x, y, Math.max(1, panelX() + panelWidth() - 12 - x), false);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtual = virtualEvent(event);
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtual.x(), virtual.y(), virtual.button())) return true;
        }
        return super.mouseClicked(virtual, doubleClick);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(virtualEvent(event)); }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(virtualEvent(event), AutismUiScale.toVirtual(dx), AutismUiScale.toVirtual(dy));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        x = AutismUiScale.toVirtual(x);
        y = AutismUiScale.toVirtual(y);
        if (summary != null && y >= resultsTop()) {
            resultsOffset = Math.max(0, Math.min(maxResultOffset(), resultsOffset - (int)Math.signum(vertical) * 3));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void onClose() { minecraft.setScreen(switcher); }

    @Override public void removed() {
        cancelImport();
        closed = true;
        generation++;
        if (inputField != null) inputField.setValue("");
        super.removed();
    }
}
