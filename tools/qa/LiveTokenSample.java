package autismclient.qa;

import autismclient.util.tokenimport.TokenInputParser;
import autismclient.util.tokenimport.TokenRefresher;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Explicitly invoked, one-entry live QA helper. Compile against the exact release
 * JAR and its runtime libraries; this source is never included in the mod.
 * Arguments: input TXT path, usable-entry offset (0 or 1), fresh output directory.
 * Credential values and verified identities are written only as Windows DPAPI
 * ciphertext. Console output and result.json contain fixed, sanitized metadata.
 */
public final class LiveTokenSample {
    private LiveTokenSample() {}

    public static void main(String[] args) {
        long started = System.nanoTime();
        int attempted = 0;
        String kind = TokenInputParser.Kind.INVALID.name();
        int sourceLineNumber = 0;
        String status = "LOCAL_ERROR";
        Path output = null;
        boolean outputReady = false;
        try {
            if (args.length != 3) throw new IllegalArgumentException();
            int offset = Integer.parseInt(args[1]);
            if (offset != 0 && offset != 1) throw new IllegalArgumentException();
            output = Path.of(args[2]).toAbsolutePath().normalize();
            Files.createDirectories(output);
            // Refuse a reused output directory before touching a live credential.
            if (Files.exists(output.resolve("result.json"))
                    || Files.exists(output.resolve("verified-account.dpapi"))) {
                throw new IllegalArgumentException();
            }
            outputReady = true;
            verifyDpapiAvailable();
            TokenInputParser.Entry entry = TokenInputParser.parse(TokenInputParser.readFile(Path.of(args[0])))
                    .stream().filter(candidate -> candidate.kind() != TokenInputParser.Kind.INVALID)
                    .skip(offset).findFirst().orElse(null);
            if (entry == null) {
                status = TokenRefresher.FailureKind.INVALID.name();
            } else {
                kind = entry.kind().name();
                sourceLineNumber = entry.lineNumber();
                TokenRefresher refresher = new TokenRefresher();
                attempted = 1;
                // Exactly one selected flow; no alternate entry, fallback or retry.
                TokenRefresher.VerifiedAccount verified = entry.kind() == TokenInputParser.Kind.REFRESH
                        ? refresher.refresh(entry.token()) : refresher.validateSession(entry.token());
                preserveVerified(output, verified);
                status = "VERIFIED";
            }
        } catch (TokenRefresher.Failure failure) {
            status = failure.kind().name();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            status = TokenRefresher.FailureKind.NETWORK.name();
        } catch (Throwable failure) {
            // Never print a throwable, its message, a server body or an identity.
            status = "LOCAL_ERROR";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attempted", attempted);
        result.put("kind", kind);
        result.put("sourceLineNumber", sourceLineNumber);
        result.put("status", status);
        result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000L);
        String sanitized = new GsonBuilder().setPrettyPrinting().create().toJson(result);
        if (outputReady) {
            try {
                Files.writeString(output.resolve("result.json"), sanitized, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (Throwable writeFailure) {
                // Keep stdout metadata sanitized even when the output disk fails.
                result.put("status", "LOCAL_ERROR");
                status = "LOCAL_ERROR";
                sanitized = new GsonBuilder().setPrettyPrinting().create().toJson(result);
            }
        }
        System.out.println(sanitized);
        System.exit("VERIFIED".equals(status) ? 0 : 1);
    }

    private static void verifyDpapiAvailable() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            throw new IllegalStateException();
        }
        byte[] clear = "synthetic-live-qa-encryption-probe".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        byte[] decrypted = null;
        try {
            encrypted = Crypt32Util.cryptProtectData(clear, 1);
            decrypted = Crypt32Util.cryptUnprotectData(encrypted, 1);
            if (!Arrays.equals(clear, decrypted)) throw new IllegalStateException();
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
            if (decrypted != null) Arrays.fill(decrypted, (byte) 0);
        }
    }

    private static void preserveVerified(Path output, TokenRefresher.VerifiedAccount verified) throws Exception {
        JsonObject credential = new JsonObject();
        credential.addProperty("username", verified.username());
        credential.addProperty("uuid", verified.uuid().toString());
        credential.addProperty("accessToken", verified.accessToken());
        credential.addProperty("refreshToken", verified.refreshToken());
        credential.addProperty("expiresAtMillis", verified.expiresAtMillis());
        byte[] clear = credential.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        try {
            encrypted = Crypt32Util.cryptProtectData(clear, 1);
            if (encrypted == null || encrypted.length == 0) throw new IllegalStateException();
            Files.write(output.resolve("verified-account.dpapi"), encrypted,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
        }
    }
}
