package autismclient.qa;

import autismclient.gui.screen.AutismAccountsScreen;
import autismclient.gui.screen.SessionTokenImporterScreen;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.util.*;
import autismclient.util.tokenimport.TokenImportService;
import autismclient.util.tokenimport.TokenRefresher;
import autismclient.util.mm.crypto.AtRestSeal;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Separate test mod: never included in the production JAR. Uses only fake accounts. */
public final class AccountManagerSmoke implements ClientModInitializer {
    private int ticks;
    private int stage;
    private int stageTick;
    private User original;
    private AutismAccountsScreen accounts;
    private String savedId;
    private SessionTokenImporterScreen importer;
    private AutismAccount networkFixture;
    private AutismAccount rateLimitedFixture;
    private String importedId;
    private String previousClipboard;
    private boolean clipboardPrepared;
    private boolean clipboardRestored;
    private int clipboardAttempts;
    private int clipboardRetryAt;
    private final List<Map<String, Object>> clipboardDiagnostics = new ArrayList<>();
    private static final String MALFORMED_IMPORT = "# Packaged QA fixture\nREFRESHTOKEN:\nMCTOKEN:\n : \n";
    private static final String NATIVE_PICKER_INPUT = "# Native picker packaged QA fixture\nMCTOKEN:\nREFRESHTOKEN:\n";
    private static final UUID IMPORT_UUID = UUID.fromString("c4307e60-7397-49a7-9141-75622869d3e7");
    private static final String IMPORT_ACCESS = "synthetic-importer-access-secret";
    private static final String IMPORT_REFRESH = "M.Csynthetic-importer-refresh-secret";
    private static final String ROTATED_ACCESS = "synthetic-rotated-access";
    private static final String ROTATED_REFRESH = "M.Csynthetic-rotated-refresh";
    private static final String SESSION_ACCESS = "synthetic-session-only-access";
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> result = new LinkedHashMap<>();

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Throwable error) { finish(client, error); }
        });
    }

    private void tick(Minecraft client) throws Exception {
        ticks++;
        if (stage == -1) return;
        if (ticks > (Boolean.getBoolean("autism.qa.nativePicker") ? 7200 : 2400))
            throw new AssertionError("QA timed out at stage " + stage);
        if (client.gui.overlay() != null || ticks - stageTick < 30) return;
        switch (stage) {
            case 0 -> {
                if (!(client.gui.screen() instanceof TitleScreen)) {
                    client.gui.setScreen(new TitleScreen());
                    return;
                }
                require(!FabricLoader.getInstance().isDevelopmentEnvironment(), "production loader environment");
                var mod = FabricLoader.getInstance().getModContainer("autism").orElseThrow();
                Path origin = mod.getOrigin().getPaths().getFirst();
                result.put("modOrigin", origin.toString());
                result.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(origin))));
                require(result.get("sha256").equals(System.getProperty("autism.qa.sha256")), "exact packaged JAR loaded");
                original = client.getUser();
                client.gui.setScreen(new JoinMultiplayerScreen(client.gui.screen()));
                next();
            }
            case 1 -> {
                Button button = client.gui.screen().children().stream()
                    .filter(child -> child instanceof Button b && b.getMessage().getString().equals("Accounts"))
                    .map(child -> (Button) child).findFirst().orElseThrow();
                require(button.getX() >= 0 && button.getX() + button.getWidth() <= client.gui.screen().width,
                        "multiplayer Accounts button injected and positioned");
                capture(client, "00-multiplayer.png");
                require(client.gui.screen().mouseClicked(new MouseButtonEvent(button.getX() + 2, button.getY() + 2,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false),
                        "multiplayer Accounts button accepts the platform left mouse button");
                require(client.gui.screen() instanceof AutismAccountsScreen, "Accounts button opens account screen");
                accounts = (AutismAccountsScreen) client.gui.screen();
                next();
            }
            case 2 -> {
                capture(client, "01-empty-accounts.png");
                EditBox label = (EditBox) field(accounts, "labelField");
                label.setValue("BackportQA");
                double scale = AutismUiScale.getOverlayDrawScale();
                require(accounts.mouseClicked(new MouseButtonEvent((label.getX() + 5) * scale, (label.getY() + 5) * scale,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false),
                        "themed input accepts a left click through the account screen");
                require(label.getCursorPosition() <= 1, "themed input retains mouse cursor positioning");
                require(label.isFocused(), "left click focuses the account input");
                requireTextCapture(accounts);
                label.setValue("");
                typeText(client, "BackportQA");
                require(label.getValue().equals("BackportQA"), "platform text input reaches the focused account field");
                call(accounts, "addAccount");
                next();
            }
            case 3 -> {
                if (!client.getUser().getName().equals("BackportQA")) return;
                require(AutismAccountManager.get().all().stream().anyMatch(a -> a.displayName().equals("BackportQA")), "UI adds offline account and switches session");
                require(AutismAccountSessionSwitcher.lastError().isEmpty(), "session services replaced without errors");
                capture(client, "02-offline-account.png");
                AutismAccount account = AutismAccountManager.get().all().getFirst();
                savedId = account.stableId();
                require(AutismAccountManager.get().rename(savedId, "BackportRenamed"), "offline account rename");
                var names = AutismAccountGenerator.parseNameList("AlphaQA\nBetaQA:demo-password");
                result.put("generatorParse", names.toString());
                byte[] clear = "synthetic-qa-secret".getBytes(StandardCharsets.UTF_8);
                byte[] sealed = AtRestSeal.seal(clear);
                require(sealed[0] == 1 && Arrays.equals(clear, AtRestSeal.unseal(sealed)), "Windows DPAPI secret round trip on Java 25");
                require(!Arrays.equals(clear, Arrays.copyOfRange(sealed, 1, sealed.length)), "saved secret is encrypted");
                AutismAccountManager.get().save();
                next();
            }
            case 4 -> {
                Path nbt = client.gameDirectory.toPath().resolve("autism-accounts.nbt");
                if (!Files.isRegularFile(nbt)) return;
                // Force a disk reload through the actual manager to validate the persisted NBT.
                Field loaded = Class.forName("autismclient.util.PersistentNbtManager").getDeclaredField("loaded");
                loaded.setAccessible(true);
                loaded.set(AutismAccountManager.get(), false);
                require(AutismAccountManager.get().findById(savedId).displayName().equals("BackportRenamed"), "NBT persistence and stable ID survive reload");
                require(AutismAccountSessionSwitcher.setSession(original), "restore launcher session");
                require(client.getUser().getName().equals(original.getName()), "original username restored");
                client.gui.setScreen(new AutismAccountsScreen(new JoinMultiplayerScreen(new TitleScreen())));
                next();
            }
            case 5 -> {
                capture(client, "03-renamed-and-restored.png");
                accounts = (AutismAccountsScreen) client.gui.screen();
                call(accounts, "openGenerator");
                next();
            }
            case 6 -> {
                require((int) field(accounts, "activePopup") == 1, "generator popup opens");
                EditBox count = (EditBox) field(accounts, "generatorCountField");
                double scale = AutismUiScale.getOverlayDrawScale();
                require(accounts.mouseClicked(new MouseButtonEvent((count.getX() + 5) * scale, (count.getY() + 5) * scale,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false) && count.isFocused(),
                        "generator count field accepts the platform left mouse button");
                requireTextCapture(accounts);
                count.setValue("");
                typeText(client, "12");
                require(count.getValue().equals("12"), "platform text input reaches the focused generator field");
                capture(client, "04-generator.png");
                require(accounts.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0))
                                && (int) field(accounts, "activePopup") == 0 && !count.isFocused(),
                        "platform Escape key closes the generator and releases text focus");
                call(accounts, "openClearConfirm");
                next();
            }
            case 7 -> {
                require((int) field(accounts, "activePopup") == 2, "clear confirmation opens");
                capture(client, "05-clear-confirmation.png");
                require(accounts.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0))
                                && (int) field(accounts, "activePopup") == 0,
                        "platform Escape key closes the clear confirmation");
                ((EditBox) field(accounts, "searchField")).setValue("BackportRenamed");
                next();
            }
            case 8 -> {
                capture(client, "06-search.png");
                ((EditBox) field(accounts, "searchField")).setValue("");
                EditBox label = (EditBox) field(accounts, "labelField");
                accounts.setFocused(label);
                label.setFocused(true);
                next();
            }
            case 9 -> {
                capture(client, "07-field-focus.png");
                EditBox label = (EditBox) field(accounts, "labelField");
                double scale = AutismUiScale.getOverlayDrawScale() * client.getWindow().getGuiScale();
                moveCursor(client, (label.getX() + 50) * scale, 123 * scale);
                next();
            }
            case 10 -> {
                capture(client, "08-primary-hover.png");
                client.getWindow().setWindowed(840, 520);
                next();
            }
            case 11 -> {
                capture(client, "09-compact-window.png");
                require(client.gui.screen() instanceof AutismAccountsScreen, "account screen survives resize");
                AutismAccountManager.get().remove(AutismAccountManager.get().findById(savedId));
                require(AutismAccountManager.get().all().isEmpty(), "account deletion");
                client.getWindow().setWindowed(1280, 720);
                next();
            }
            case 12 -> {
                accounts = (AutismAccountsScreen) client.gui.screen();
                require(overlayButton(accounts, "Session Token Switcher") != null,
                        "session switcher tab is present");
                clickOverlay(accounts, "Session Token Importer");
                require(client.gui.screen() instanceof SessionTokenImporterScreen,
                        "importer tab opens inside Minecraft");
                importer = (SessionTokenImporterScreen) client.gui.screen();
                next();
            }
            case 13 -> {
                MultiLineEditBox input = importInput();
                double scale = AutismUiScale.getOverlayDrawScale();
                require(importer.mouseClicked(new MouseButtonEvent((input.getX() + 10) * scale, (input.getY() + 10) * scale,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false),
                        "multiline importer accepts the platform left mouse button");
                require(input.isFocused(), "left click focuses the multiline importer");
                requireTextCapture(importer);
                typeText(client, "# Keyboard input fixture");
                require(input.getValue().equals("# Keyboard input fixture"), "platform text input reaches the multiline importer");
                capture(client, "10-session-token-importer.png");
                Path fixture = client.gameDirectory.toPath().resolve("synthetic-import-fixture.txt");
                Files.writeString(fixture, MALFORMED_IMPORT, StandardCharsets.UTF_8);
                importer.onFilesDrop(List.of(fixture));
                next();
            }
            case 14 -> {
                if ((boolean) field(importer, "fileLoading")) return;
                if (!clipboardPrepared) {
                    require(importInput().getValue().equals(MALFORMED_IMPORT),
                            "dropping a UTF-8 TXT file loads input inside Minecraft");
                    require(field(importer, "summary") == null && AutismAccountManager.get().all().isEmpty(),
                            "file loading waits for explicit validation");
                    previousClipboard = client.keyboardHandler.getClipboard();
                    clipboardPrepared = true;
                }
                if (ticks < clipboardRetryAt) return;
                clipboardAttempts++;
                client.keyboardHandler.setClipboard(MALFORMED_IMPORT);
                String nativeClipboard = client.keyboardHandler.getClipboard();
                boolean nativeExact = nativeClipboard.equals(MALFORMED_IMPORT);
                boolean nativeNormalizedExact = normalizeLineEndings(nativeClipboard).equals(MALFORMED_IMPORT);
                boolean pasteAttempted = nativeExact || nativeNormalizedExact;
                if (pasteAttempted) {
                    importInput().setValue("");
                    clickOverlay(importer, "Paste");
                }
                String pasted = pasteAttempted ? importInput().getValue() : "";
                Map<String, Object> diagnostic = new LinkedHashMap<>();
                diagnostic.put("attempt", clipboardAttempts);
                diagnostic.put("expectedLength", MALFORMED_IMPORT.length());
                diagnostic.put("expectedLines", MALFORMED_IMPORT.split("\\n", -1).length);
                diagnostic.put("nativeLength", nativeClipboard.length());
                diagnostic.put("nativeLines", normalizeLineEndings(nativeClipboard).split("\\n", -1).length);
                diagnostic.put("nativeExact", nativeExact);
                diagnostic.put("nativeExactAfterLineEndingNormalization", nativeNormalizedExact);
                diagnostic.put("pasteAttempted", pasteAttempted);
                diagnostic.put("pastedLength", pasted.length());
                diagnostic.put("pastedLines", normalizeLineEndings(pasted).split("\\n", -1).length);
                diagnostic.put("pastedExact", pasteAttempted && pasted.equals(MALFORMED_IMPORT));
                diagnostic.put("pastedExactAfterLineEndingNormalization",
                        pasteAttempted && normalizeLineEndings(pasted).equals(MALFORMED_IMPORT));
                clipboardDiagnostics.add(diagnostic);
                result.put("clipboardDiagnostics", clipboardDiagnostics);
                System.out.println("[AUTISM QA] Clipboard diagnostics: " + new GsonBuilder().create().toJson(diagnostic));
                boolean roundTrip = nativeNormalizedExact && normalizeLineEndings(pasted).equals(MALFORMED_IMPORT);
                if (!roundTrip) {
                    if (clipboardAttempts >= 8) throw new AssertionError(
                            "Minecraft clipboard/Paste mismatch after 8 bounded attempts; see clipboardDiagnostics metadata");
                    clipboardRetryAt = ticks + 15;
                    return;
                }
                require(normalizeLineEndings(importInput().getValue()).equals(MALFORMED_IMPORT),
                        "importer Paste preserves multiline clipboard content across platform line endings");
                restoreClipboard(client);
                clickOverlay(importer, "Validate & Import");
                require(importInput().getValue().isEmpty(), "import input is cleared once processing begins");
                next();
            }
            case 15 -> {
                if ((boolean) field(importer, "running")) return;
                TokenImportService.Summary summary = (TokenImportService.Summary) field(importer, "summary");
                require(summary != null && summary.invalid() >= 2 && summary.imported() == 0,
                        "malformed tokens are rejected without creating accounts");
                require(summary.processed() == summary.total(), "asynchronous importer completes its batch");
                require(!summary.toString().contains("REFRESHTOKEN:") && !summary.toString().contains("MCTOKEN:"),
                        "import results do not repeat credential input");
                require(AutismAccountManager.get().all().isEmpty(), "invalid importer batch leaves account storage empty");
                capture(client, "11-import-validation-results.png");
                importInput().setValue("# Resize preservation fixture\nMCTOKEN:");
                client.getWindow().setWindowed(840, 520);
                next();
            }
            case 16 -> {
                require(client.gui.screen() == importer && importInput().getValue().equals("# Resize preservation fixture\nMCTOKEN:"),
                        "importer preserves multiline input across resize");
                capture(client, "12-importer-compact.png");
                importInput().setValue("MCTOKEN:\n".repeat(1000));
                clickOverlay(importer, "Validate & Import");
                clickOverlay(importer, "Cancel");
                next();
            }
            case 17 -> {
                require(!(boolean) field(importer, "running"), "Cancel returns importer controls to idle");
                require(AutismAccountManager.get().all().isEmpty(), "cancelled importer does not create accounts");
                importInput().setValue("synthetic-should-clear-on-exit");
                clickOverlay(importer, "Session Token Switcher");
                require(client.gui.screen() instanceof AutismAccountsScreen, "switcher tab returns to saved accounts");
                require(importInput().getValue().isEmpty(), "leaving importer wipes input");
                var verified = new TokenRefresher.VerifiedAccount("ImporterQA", IMPORT_UUID, IMPORT_ACCESS,
                        IMPORT_REFRESH, System.currentTimeMillis() + 3_600_000);
                require(AutismTokenImporter.storeVerified(verified, () -> true) == TokenImportService.StoreResult.CANCELLED
                                && AutismAccountManager.get().all().isEmpty(),
                        "cancelled verified result cannot save a late account");
                require(AutismTokenImporter.storeVerified(verified, () -> false) == TokenImportService.StoreResult.IMPORTED,
                        "verified importer result enters the existing account manager");
                AutismAccount imported = AutismAccountManager.get().all().getFirst();
                importedId = imported.stableId();
                var duplicate = new TokenRefresher.VerifiedAccount("ImporterQA", IMPORT_UUID, ROTATED_ACCESS,
                        ROTATED_REFRESH, System.currentTimeMillis() + 3_600_000);
                require(AutismTokenImporter.storeVerified(duplicate, () -> false) == TokenImportService.StoreResult.DUPLICATE
                                && AutismAccountManager.get().size() == 1,
                        "verified UUID prevents duplicate accounts across different tokens");
                imported = AutismAccountManager.get().all().getFirst();
                require(imported.stableId().equals(importedId) && imported.token.equals(ROTATED_ACCESS)
                                && imported.label.equals(ROTATED_REFRESH),
                        "duplicate UUID retains rotated credentials and its stable account ID");
                var sessionOnly = new TokenRefresher.VerifiedAccount("ImporterQA", IMPORT_UUID, SESSION_ACCESS,
                        "", System.currentTimeMillis() + 3_600_000);
                require(AutismTokenImporter.storeVerified(sessionOnly, () -> false) == TokenImportService.StoreResult.DUPLICATE,
                        "session-only reimport resolves to the existing Microsoft account");
                imported = AutismAccountManager.get().all().getFirst();
                require(imported.stableId().equals(importedId) && imported.token.equals(SESSION_ACCESS)
                                && imported.label.equals(ROTATED_REFRESH) && imported.type == AutismAccountType.Microsoft
                                && imported.tokenRefresherAuth,
                        "session-only reimport updates access while preserving refresh credentials and provider");
                require(imported.type == AutismAccountType.Microsoft && imported.tokenRefresherAuth,
                        "imported refresh credentials retain the Token-refresher provider");
                require(client.getUser().getName().equals(original.getName()), "importing does not switch the active session");
                next();
            }
            case 18 -> {
                Path nbt = client.gameDirectory.toPath().resolve("autism-accounts.nbt");
                CompoundTag persisted = NbtIo.read(nbt);
                if (persisted == null) return;
                CompoundTag savedAccount = persisted.getListOrEmpty("accounts").stream()
                        .filter(tag -> tag instanceof CompoundTag account && importedId.equals(account.getStringOr("id", "")))
                        .map(tag -> (CompoundTag) tag).findFirst().orElse(null);
                if (savedAccount == null || !new AutismAccount().fromTag(savedAccount).token.equals(SESSION_ACCESS)) return;
                String stored = new String(Files.readAllBytes(nbt), StandardCharsets.ISO_8859_1);
                require(List.of(IMPORT_ACCESS, IMPORT_REFRESH, ROTATED_ACCESS, ROTATED_REFRESH, SESSION_ACCESS)
                                .stream().noneMatch(stored::contains),
                        "imported access and refresh tokens are absent from plaintext NBT");
                Field loaded = Class.forName("autismclient.util.PersistentNbtManager").getDeclaredField("loaded");
                loaded.setAccessible(true);
                loaded.set(AutismAccountManager.get(), false);
                AutismAccount restored = AutismAccountManager.get().findById(importedId);
                require(restored != null && restored.tokenRefresherAuth && restored.uuid.equals(IMPORT_UUID.toString())
                                && restored.stableId().equals(importedId) && restored.token.equals(SESSION_ACCESS)
                                && restored.label.equals(ROTATED_REFRESH),
                        "imported credentials and refresh provider survive encrypted disk reload");
                client.getWindow().setWindowed(1280, 720);
                client.gui.setScreen(new AutismAccountsScreen(new JoinMultiplayerScreen(new TitleScreen())));
                next();
            }
            case 19 -> {
                capture(client, "13-imported-account.png");
                AutismAccountManager.get().remove(AutismAccountManager.get().findById(importedId));
                require(AutismAccountManager.get().all().isEmpty(), "synthetic importer fixture is removed");
                next();
            }
            case 20 -> {
                networkFixture = failedImportFixture("NetworkQA", UUID.fromString("c4307e60-7397-49a7-9141-75622869d3e8"),
                        TokenRefresher.FailureKind.NETWORK);
                networkFixture.type = AutismAccountType.Session;
                networkFixture.tokenRefresherAuth = false;
                networkFixture.label = "NetworkQA";
                rateLimitedFixture = failedImportFixture("RateLimitQA", UUID.fromString("c4307e60-7397-49a7-9141-75622869d3e9"),
                        TokenRefresher.FailureKind.RATE_LIMITED);
                AutismAccountManager.get().add(networkFixture);
                AutismAccountManager.get().add(rateLimitedFixture);
                accounts = new AutismAccountsScreen(new JoinMultiplayerScreen(new TitleScreen()));
                client.gui.setScreen(accounts);
                call(accounts, "checkExpiredAccounts");
                next();
            }
            case 21 -> {
                if (!((Enum<?>) field(accounts, "operation")).name().equals("NONE")) return;
                require(networkFixture.checkStatus == AutismAccount.CheckStatus.UNKNOWN,
                        "account check keeps temporary network failures retryable");
                require(rateLimitedFixture.checkStatus == AutismAccount.CheckStatus.UNKNOWN,
                        "account check keeps rate-limited credentials retryable");
                require(AutismAccountManager.get().removeExpired() == 0 && AutismAccountManager.get().size() == 2,
                        "Clear Expired preserves accounts with temporary authentication failures");
                AutismAccountManager.get().remove(networkFixture);
                AutismAccountManager.get().remove(rateLimitedFixture);
                require(AutismAccountManager.get().all().isEmpty(), "temporary authentication fixtures are removed");
                if (Boolean.getBoolean("autism.qa.nativePicker")) next();
                else finish(client, null);
            }
            case 22 -> {
                importer = new SessionTokenImporterScreen(accounts);
                client.gui.setScreen(importer);
                Path fixture = client.gameDirectory.toPath().resolve("synthetic-native-picker.txt");
                Files.writeString(fixture, NATIVE_PICKER_INPUT, StandardCharsets.UTF_8);
                result.put("nativePickerFixture", fixture.toString());
                System.out.println("[AUTISM QA] Select native picker fixture: " + fixture);
                clickOverlay(importer, "Open TXT");
                require((boolean) field(importer, "fileLoading"), "Open TXT starts the native file chooser");
                next();
            }
            case 23 -> {
                if ((boolean) field(importer, "fileLoading")) return;
                require(importInput().getValue().equals(NATIVE_PICKER_INPUT),
                        "native file picker loads the selected UTF-8 TXT fixture inside Minecraft");
                require(field(importer, "summary") == null && !(boolean) field(importer, "running")
                                && AutismAccountManager.get().all().isEmpty(),
                        "native file picker waits for explicit validation before importing accounts");
                finish(client, null);
            }
        }
    }

    private void next() { stage++; stageTick = ticks; }
    private void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks.add(label);
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void call(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(object);
    }
    private MultiLineEditBox importInput() throws Exception {
        return (MultiLineEditBox) field(importer, "inputField");
    }
    /** The failure is injected only by the separate QA mod; production still uses real authentication. */
    private static AutismAccount failedImportFixture(String username, UUID uuid, TokenRefresher.FailureKind failure) {
        AutismAccount account = new AutismAccount() {
            @Override public boolean fetchInfo() {
                this.tokenImportFailureKind = failure;
                return false;
            }
        };
        account.type = AutismAccountType.Microsoft;
        account.tokenRefresherAuth = true;
        account.username = username;
        account.uuid = uuid.toString();
        account.token = "synthetic-" + username + "-access";
        account.label = "M.Csynthetic-" + username + "-refresh";
        return account;
    }
    private static CompactOverlayButton overlayButton(Object screen, String label) throws Exception {
        for (Object value : (List<?>) field(screen, "buttons")) {
            CompactOverlayButton button = (CompactOverlayButton) value;
            if (((net.minecraft.network.chat.Component) field(button, "message")).getString().equals(label)) return button;
        }
        throw new AssertionError("Missing overlay button: " + label);
    }
    private static void clickOverlay(net.minecraft.client.gui.screens.Screen screen, String label) throws Exception {
        CompactOverlayButton button = overlayButton(screen, label);
        if (!button.visible || !button.active) throw new AssertionError("Inactive overlay button: " + label);
        double scale = AutismUiScale.getOverlayDrawScale();
        if (!screen.mouseClicked(new MouseButtonEvent((button.getX() + button.getWidth() / 2.0) * scale,
                (button.getY() + button.getHeight() / 2.0) * scale,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false)) {
            throw new AssertionError("Overlay click not handled: " + label);
        }
    }
    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, message -> {});
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** SDL text capture is present in 26.3; 26.2 still uses the earlier character callback. */
    private void requireTextCapture(net.minecraft.client.gui.screens.Screen screen) throws Exception {
        try {
            Method capture = screen.getClass().getMethod("isInputCaptured");
            require((boolean) capture.invoke(screen), "focused text field requests platform text capture");
        } catch (NoSuchMethodException earlierInputBackend) {
            require(screen.getFocused() != null, "focused text field is registered with the screen");
        }
    }

    /** Exercise Minecraft's input dispatch without depending on GLFW or SDL test APIs. */
    private static void typeText(Minecraft client, String text) throws Exception {
        try {
            Method textInput = client.keyboardHandler.getClass().getMethod("textInput", long.class, String.class);
            textInput.invoke(client.keyboardHandler, client.getWindow().handle(), text);
        } catch (NoSuchMethodException earlierInputBackend) {
            Class<?> eventType = Class.forName("net.minecraft.client.input.CharacterEvent");
            Constructor<?> constructor = eventType.getConstructor(int.class);
            Method charTyped = client.keyboardHandler.getClass().getDeclaredMethod("charTyped", long.class, eventType);
            charTyped.setAccessible(true);
            for (int codePoint : text.codePoints().toArray()) {
                charTyped.invoke(client.keyboardHandler, client.getWindow().handle(), constructor.newInstance(codePoint));
            }
        }
    }

    private static void moveCursor(Minecraft client, double x, double y) throws Exception {
        Class<?> windowType = client.getWindow().getClass();
        try {
            InputConstants.class.getMethod("releaseMouse", windowType, double.class, double.class)
                    .invoke(null, client.getWindow(), x, y);
        } catch (NoSuchMethodException earlierInputBackend) {
            InputConstants.class.getMethod("grabOrReleaseMouse", windowType, int.class, double.class, double.class)
                    .invoke(null, client.getWindow(), InputConstants.class.getField("CURSOR_NORMAL").getInt(null), x, y);
        }
    }
    private void restoreClipboard(Minecraft client) {
        if (clipboardPrepared && !clipboardRestored) {
            client.keyboardHandler.setClipboard(previousClipboard);
            clipboardRestored = true;
        }
    }
    private void finish(Minecraft client, Throwable error) {
        stage = -1;
        restoreClipboard(client);
        try {
            result.put("status", error == null ? "PASSED" : "FAILED");
            result.put("checks", checks);
            if (error != null) {
                result.put("error", error.toString());
                error.printStackTrace();
            }
            Files.writeString(Path.of(System.getProperty("autism.qa.result")), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception writeError) { writeError.printStackTrace(); }
        client.stop();
    }
}
