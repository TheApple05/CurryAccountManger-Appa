package autismclient.util.tokenimport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Token-refresher compatible input forms; never keeps passwords or supplied account names. */
public final class TokenInputParser {
    public static final int MAX_INPUT_CHARS = 1_048_576;
    public static final int MAX_ENTRIES = 1_000;
    public static final int MAX_LINE_CHARS = 32_768;
    public enum Kind { SESSION, REFRESH, INVALID }

    public record Entry(int lineNumber, Kind kind, String token, String error) {
        @Override public String toString() { return "Entry[line=" + lineNumber + ", kind=" + kind + "]"; }
    }

    private TokenInputParser() {}

    public static List<Entry> parse(String input) {
        if (input == null) input = "";
        if (input.length() > MAX_INPUT_CHARS) throw new IllegalArgumentException("Input exceeds the 1 MiB limit.");
        List<Entry> entries = new ArrayList<>();
        String[] lines = input.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (index == 0 && line.startsWith("\uFEFF")) line = line.substring(1).strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("===")) continue;
            if (entries.size() >= MAX_ENTRIES) throw new IllegalArgumentException("Import at most 1,000 entries at a time.");
            entries.add(parseLine(index + 1, line));
        }
        return List.copyOf(entries);
    }

    private static Entry parseLine(int lineNumber, String line) {
        if (line.length() > MAX_LINE_CHARS) return invalid(lineNumber, "Entry exceeds the supported length.");
        String refresh = null;
        String session = null;
        boolean fields = false;
        for (String part : line.split("\\|", -1)) {
            String field = part.strip();
            int colon = field.indexOf(':');
            if (colon < 0) continue;
            String name = field.substring(0, colon).strip();
            if (name.equalsIgnoreCase("REFRESHTOKEN")) {
                fields = true;
                refresh = field.substring(colon + 1).strip();
            } else if (name.equalsIgnoreCase("MCTOKEN")) {
                fields = true;
                session = field.substring(colon + 1).strip();
            }
        }
        if (fields) {
            if (kind(refresh) == Kind.REFRESH) return new Entry(lineNumber, Kind.REFRESH, refresh, "");
            if (kind(session) == Kind.SESSION) return new Entry(lineNumber, Kind.SESSION, session, "");
            return invalid(lineNumber, "No usable session or Microsoft refresh token in the fields.");
        }
        int colon = line.indexOf(':');
        String token = line;
        if (colon >= 0) {
            if (line.substring(0, colon).isBlank()) return invalid(lineNumber, "Missing name before the delimiter.");
            token = line.substring(colon + 1).strip();
        }
        Kind kind = kind(token);
        return kind == Kind.INVALID ? invalid(lineNumber, "Expected a Minecraft session token or Microsoft refresh token.")
                : new Entry(lineNumber, kind, token, "");
    }

    private static Kind kind(String token) {
        if (token == null || token.isBlank() || token.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) return Kind.INVALID;
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.startsWith("m.c") || lower.contains("msaartifacts")) return Kind.REFRESH;
        // Minecraft session tokens are JWTs. Shape is only routing, never proof of validity.
        if (token.startsWith("eyJ") && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) return Kind.SESSION;
        return Kind.INVALID;
    }

    private static Entry invalid(int line, String error) { return new Entry(line, Kind.INVALID, "", error); }

    public static String readFile(Path path) throws IOException {
        if (path == null || path.getFileName() == null || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
            throw new IOException("Choose a UTF-8 .txt file.");
        if (!Files.isRegularFile(path)) throw new IOException("Choose a regular .txt file.");
        // readNBytes also enforces the limit if the file grows between stat and read.
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(MAX_INPUT_CHARS + 1);
            if (bytes.length > MAX_INPUT_CHARS) throw new IOException("File exceeds the 1 MiB limit.");
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw new IOException("File must use UTF-8 text.");
            }
        }
    }
}
