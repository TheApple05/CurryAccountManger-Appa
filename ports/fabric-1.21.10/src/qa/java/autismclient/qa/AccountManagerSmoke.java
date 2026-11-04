package autismclient.qa;

import autismclient.gui.screen.AutismAccountsScreen;
import autismclient.util.*;
import autismclient.util.mm.crypto.AtRestSeal;
import com.google.gson.GsonBuilder;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Separate test mod: never included in the production JAR. Uses only fake accounts. */
public final class AccountManagerSmoke implements ClientModInitializer {
    private int ticks;
    private int stage;
    private int stageTick;
    private User original;
    private AutismAccountsScreen accounts;
    private String savedId;
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
        if (ticks > 2400) throw new AssertionError("QA timed out at stage " + stage);
        if (client.getOverlay() != null || ticks - stageTick < 30) return;
        switch (stage) {
            case 0 -> {
                if (!(client.screen instanceof TitleScreen)) {
                    client.setScreen(new TitleScreen());
                    return;
                }
                require(!FabricLoader.getInstance().isDevelopmentEnvironment(), "production loader environment");
                var mod = FabricLoader.getInstance().getModContainer("autism").orElseThrow();
                Path origin = mod.getOrigin().getPaths().getFirst();
                result.put("modOrigin", origin.toString());
                result.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(origin))));
                require(result.get("sha256").equals(System.getProperty("autism.qa.sha256")), "exact packaged JAR loaded");
                original = client.getUser();
                client.setScreen(new JoinMultiplayerScreen(client.screen));
                next();
            }
            case 1 -> {
                Button button = client.screen.children().stream()
                    .filter(child -> child instanceof Button b && b.getMessage().getString().equals("Accounts"))
                    .map(child -> (Button) child).findFirst().orElseThrow();
                require(button.getX() >= 0 && button.getX() + button.getWidth() <= client.screen.width,
                        "multiplayer Accounts button injected and positioned");
                capture(client, "00-multiplayer.png");
                button.onPress(new MouseButtonEvent(button.getX() + 2, button.getY() + 2, new MouseButtonInfo(0, 0)));
                require(client.screen instanceof AutismAccountsScreen, "Accounts button opens account screen");
                accounts = (AutismAccountsScreen) client.screen;
                next();
            }
            case 2 -> {
                capture(client, "01-empty-accounts.png");
                EditBox label = (EditBox) field(accounts, "labelField");
                label.setValue("BackportQA");
                label.mouseClicked(new MouseButtonEvent(label.getX() + 5, label.getY() + 5,
                    new MouseButtonInfo(0, 0)), false);
                require(label.getCursorPosition() <= 1, "themed input retains mouse cursor positioning");
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
                require(sealed[0] == 1 && Arrays.equals(clear, AtRestSeal.unseal(sealed)), "Windows DPAPI secret round trip on Java 21");
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
                client.setScreen(new AutismAccountsScreen(new JoinMultiplayerScreen(new TitleScreen())));
                next();
            }
            case 5 -> {
                capture(client, "03-renamed-and-restored.png");
                accounts = (AutismAccountsScreen) client.screen;
                call(accounts, "openGenerator");
                next();
            }
            case 6 -> {
                require((int) field(accounts, "activePopup") == 1, "generator popup opens");
                capture(client, "04-generator.png");
                call(accounts, "closePopup");
                call(accounts, "openClearConfirm");
                next();
            }
            case 7 -> {
                require((int) field(accounts, "activePopup") == 2, "clear confirmation opens");
                capture(client, "05-clear-confirmation.png");
                call(accounts, "closePopup");
                ((EditBox) field(accounts, "searchField")).setValue("BackportRenamed");
                next();
            }
            case 8 -> {
                capture(client, "06-search.png");
                ((EditBox) field(accounts, "searchField")).setValue("");
                ((EditBox) field(accounts, "labelField")).setFocused(true);
                next();
            }
            case 9 -> {
                capture(client, "07-field-focus.png");
                EditBox label = (EditBox) field(accounts, "labelField");
                double scale = AutismUiScale.getOverlayDrawScale() * client.getWindow().getGuiScale();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().handle(),
                    (label.getX() + 50) * scale, 123 * scale);
                next();
            }
            case 10 -> {
                capture(client, "08-primary-hover.png");
                client.getWindow().setWindowed(840, 520);
                next();
            }
            case 11 -> {
                capture(client, "09-compact-window.png");
                require(client.screen instanceof AutismAccountsScreen, "account screen survives resize");
                AutismAccountManager.get().remove(AutismAccountManager.get().findById(savedId));
                require(AutismAccountManager.get().all().isEmpty(), "account deletion");
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
    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> {});
    }
    private void finish(Minecraft client, Throwable error) {
        stage = -1;
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
