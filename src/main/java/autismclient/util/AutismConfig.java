package autismclient.util;

import autismclient.AutismClientAddon;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

/** Account-manager settings, retaining unrelated keys in an existing config. */
public final class AutismConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutismConfig global;
    private transient JsonObject original = new JsonObject();
    public double overlayScale = 1.0;
    public boolean accountGenSetPassword = true;
    public String accountGenPasswordMode = "Generate";
    public String accountGenSharedPassword = "";
    public ThemeColors themeColors = new ThemeColors();

    public static synchronized AutismConfig getGlobal() {
        if (global == null) {
            Path path = AutismClientAddon.FOLDER.toPath().resolve("config.json");
            global = read(path);
            if (global == null) global = read(path.resolveSibling("config.json.bak"));
            if (global == null) global = new AutismConfig();
        }
        return global;
    }

    private static AutismConfig read(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            AutismConfig config = GSON.fromJson(json, AutismConfig.class);
            config.original = json;
            if (config.themeColors == null) config.themeColors = new ThemeColors();
            if (config.accountGenPasswordMode == null) config.accountGenPasswordMode = "Generate";
            if (config.accountGenSharedPassword == null) config.accountGenSharedPassword = "";
            return config;
        } catch (Exception error) {
            AutismClientAddon.LOG.warn("Could not read account manager settings {}", path.getFileName());
            return null;
        }
    }

    public synchronized void save() {
        JsonObject snapshot = original.deepCopy();
        GSON.toJsonTree(this).getAsJsonObject().entrySet()
            .forEach(entry -> snapshot.add(entry.getKey(), entry.getValue()));
        String json = GSON.toJson(snapshot);
        SaveCoordinator.enqueueLatest("account-config", () -> {
            Path path = AutismClientAddon.FOLDER.toPath().resolve("config.json");
            Path temporary = path.resolveSibling("config.json.tmp");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(temporary, json, StandardCharsets.UTF_8);
                if (Files.isRegularFile(path)) Files.copy(path, path.resolveSibling("config.json.bak"), StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException error) {
                AutismClientAddon.LOG.error("Could not save account manager settings", error);
            }
        });
    }

    public static final class ThemeColors {
        public boolean advanced = false;
        public int master = -50373;
        public int accent = -50373;
        public int outline = -5035221;
        public int text = -791321;
        public int toggle = -50373;
        public int backdrop = -5093304;
        public int success = -13248397;
        public int danger = -1938838;
        public int button = -7397596;
        public int header = -50373;
        public int hover = -39836;
    }
}
