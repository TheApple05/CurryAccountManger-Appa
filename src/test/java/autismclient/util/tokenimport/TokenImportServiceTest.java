package autismclient.util.tokenimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic parser and lifecycle tests, with no Minecraft instance or real credentials. */
public final class TokenImportServiceTest {
    private static final String ACCESS = "eyJhbGciOiJub25lIn0.eyJleHAiOjE5MDAwMDAwMDB9.c3ludGhldGlj";
    private static int checks;

    public static void main(String[] args) throws Exception {
        parser();
        fileBounds();
        importAndDuplicates();
        cancellation();
        failures();
        refreshSpacing();
        System.out.println("TokenImportServiceTest: " + checks + " checks passed (synthetic credentials; no network).");
    }

    private static void parser() {
        var entries = TokenInputParser.parse("\uFEFF# comment\r\n=== separator\r\n\r\n" + ACCESS + "\r\nSpoofedName:" + ACCESS
            + "\nM.Crefresh\nAlias:M.Crefresh\nemail:password | MCTOKEN: " + ACCESS + " | REFRESHTOKEN: M.Cpreferred\nMCTOKEN: " + ACCESS);
        check(entries.size() == 6, "BOM, comments, blanks and CRLF ignored");
        check(entries.getFirst().lineNumber() == 4, "Original line numbers preserved");
        check(entries.get(1).token().equals(ACCESS), "Username prefix discarded; service identity is authoritative");
        check(entries.get(2).kind() == TokenInputParser.Kind.REFRESH && entries.get(3).kind() == TokenInputParser.Kind.REFRESH,
            "Raw and prefixed refresh tokens routed");
        check(entries.get(4).token().equals("M.Cpreferred"), "Mixed fields prefer refresh and discard password");
        check(entries.get(5).kind() == TokenInputParser.Kind.SESSION, "Session-only MCTOKEN supported");
        var malformed = TokenInputParser.parse("REFRESHTOKEN:\nMCTOKEN:\n : \nuser:password\nrandom text\nM.Cabc def");
        check(malformed.size() == 6 && malformed.stream().allMatch(e -> e.kind() == TokenInputParser.Kind.INVALID), "Malformed input rejected locally");
        check(malformed.stream().allMatch(e -> e.token().isEmpty()), "Malformed secrets not retained in parse results");
        check(!entries.toString().contains(ACCESS) && !entries.toString().contains("M.Cpreferred"), "Parser toString redacted");
        check(TokenInputParser.parse("# only comments\n").isEmpty(), "Empty batch accepted");
        check(TokenInputParser.parse("REFRESHTOKEN: | MCTOKEN: " + ACCESS).getFirst().kind() == TokenInputParser.Kind.SESSION,
            "Usable session accepted when refresh field empty");
        check(TokenInputParser.parse("M.C" + "x".repeat(TokenInputParser.MAX_LINE_CHARS)).getFirst().kind() == TokenInputParser.Kind.INVALID,
            "Line bound enforced");
        expectIllegal(() -> TokenInputParser.parse("x".repeat(TokenInputParser.MAX_INPUT_CHARS + 1)));
        expectIllegal(() -> TokenInputParser.parse("malformed\n".repeat(TokenInputParser.MAX_ENTRIES + 1)));
    }

    private static void fileBounds() throws Exception {
        var directory = Files.createTempDirectory("token-import-unit-");
        try {
            var file = directory.resolve("fixture.txt");
            Files.writeString(file, "\uFEFF" + ACCESS + "\r\n", StandardCharsets.UTF_8);
            check(TokenInputParser.parse(TokenInputParser.readFile(file)).getFirst().token().equals(ACCESS), "UTF-8 TXT with BOM");
            Files.write(file, new byte[]{(byte)0xC3, 0x28});
            expectIo(() -> TokenInputParser.readFile(file));
            Files.write(file, new byte[TokenInputParser.MAX_INPUT_CHARS + 1]);
            expectIo(() -> TokenInputParser.readFile(file));
            expectIo(() -> TokenInputParser.readFile(directory));
            expectIo(() -> TokenInputParser.readFile(directory.resolve("fixture.bin")));
            expectIo(() -> TokenInputParser.readFile(directory.resolve("missing.txt")));
        } finally {
            Files.deleteIfExists(directory.resolve("fixture.txt"));
            Files.delete(directory);
        }
    }

    private static void importAndDuplicates() {
        AtomicInteger requests = new AtomicInteger();
        Set<UUID> saved = new HashSet<>();
        var auth = new FakeAuth() {
            @Override public TokenRefresher.VerifiedAccount validateSession(String token) {
                requests.incrementAndGet();
                return verified(token);
            }
        };
        var service = service(auth, (account, cancelled) -> saved.add(account.uuid()) ? TokenImportService.StoreResult.IMPORTED : TokenImportService.StoreResult.DUPLICATE);
        var updates = new ArrayList<TokenImportService.Summary>();
        var result = service.importText(ACCESS + "\nSpoofed:" + ACCESS + "\nM.Cdifferent\ninvalid fixture", () -> false, updates::add);
        check(result.total() == 4 && result.processed() == 4, "All input candidates counted");
        check(result.imported() == 1 && result.duplicates() == 2 && result.invalid() == 1, "Token and UUID duplicate accounting");
        check(requests.get() == 1, "Repeated validated token avoids duplicate HTTP");
        check(updates.getFirst().processed() == 0 && updates.getLast().equals(result), "Initial and final progress emitted");
        check(updates.get(1).results().size() == 1, "Progress results are immutable snapshots");
        check(!result.toString().contains(ACCESS) && !result.toString().contains("M.Cdifferent"), "Results contain no credential strings");
        check(result.results().getFirst().message().contains("VerifiedUser"), "Verified identity appears in successful result");
    }

    private static void cancellation() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        var auth = new FakeAuth() {
            @Override public TokenRefresher.VerifiedAccount validateSession(String token) {
                calls.incrementAndGet(); cancel.set(true); return verified(token);
            }
        };
        var service = service(auth, (v, c) -> { throw new AssertionError("Cancelled validation must not save"); });
        var before = service.importText(ACCESS, cancel::get, p -> {});
        check(before.cancelled() && before.processed() == 0 && calls.get() == 0, "Pre-cancelled import does no work");
        cancel.set(false);
        var during = service.importText(ACCESS, cancel::get, p -> {});
        check(during.cancelled() && during.imported() == 0 && calls.get() == 1, "Cancellation after validation blocks late store");
        var interrupted = service(new FakeAuth() {
            @Override public TokenRefresher.VerifiedAccount validateSession(String token) throws InterruptedException { throw new InterruptedException(); }
        }, (v,c) -> { throw new AssertionError(); }).importText(ACCESS, () -> false, p -> {});
        check(interrupted.cancelled() && Thread.currentThread().isInterrupted(), "Interrupted auth cancels and preserves interrupt flag");
        Thread.interrupted();
        var declined = service(new FakeAuth(), (v,c) -> TokenImportService.StoreResult.CANCELLED).importText(ACCESS, () -> false, p -> {});
        check(declined.cancelled() && declined.processed() == 0, "Store cancellation remains cancellation");
    }

    private static void failures() {
        AtomicInteger requests = new AtomicInteger();
        var retry = service(new FakeAuth() {
            @Override public TokenRefresher.VerifiedAccount validateSession(String token) throws TokenRefresher.Failure {
                if (requests.incrementAndGet() == 1) throw new TokenRefresher.Failure(TokenRefresher.FailureKind.NETWORK);
                return verified(token);
            }
        }, (v,c) -> TokenImportService.StoreResult.IMPORTED).importText(ACCESS + "\n" + ACCESS, () -> false, p -> {});
        check(retry.failed() == 1 && retry.imported() == 1 && requests.get() == 2, "Transient failure may retry same token");
        for (var kind : TokenRefresher.FailureKind.values()) {
            AtomicInteger count = new AtomicInteger();
            var result = service(new FakeAuth() {
                @Override public TokenRefresher.VerifiedAccount validateSession(String token) throws TokenRefresher.Failure {
                    count.incrementAndGet(); throw new TokenRefresher.Failure(kind);
                }
            }, (v,c) -> { throw new AssertionError(); }).importText(ACCESS + "\n" + ACCESS, () -> false, p -> {});
            switch (kind) {
                case INVALID, UNSUPPORTED -> check(result.invalid() == 2, "Invalid or unsupported credentials not saved");
                case EXPIRED -> check(result.expired() == 2, "Expired counted separately");
                case NETWORK -> check(result.failed() == 2 && result.invalid() == 0, "Transient is not invalid");
                case RATE_LIMITED -> check(result.failed() == 1 && result.processed() == 1 && count.get() == 1, "Rate limit stops remaining requests");
            }
        }
        var result = service(new FakeAuth(), (v,c) -> { throw new IllegalStateException(ACCESS); }).importText(ACCESS, () -> false, p -> {});
        check(result.failed() == 1 && !result.toString().contains(ACCESS), "Unexpected sink exceptions cannot expose credentials");
    }

    private static void refreshSpacing() {
        AtomicLong time = new AtomicLong(10_000);
        List<Long> waits = new ArrayList<>();
        var service = new TokenImportService(new FakeAuth(), (v,c) -> TokenImportService.StoreResult.IMPORTED,
            time::get, millis -> { waits.add(millis); time.addAndGet(millis); });
        var result = service.importText("M.Cfirst\nM.Csecond\nM.Cthird", () -> false, p -> {});
        check(result.imported() == 3 && waits.equals(List.of(4_000L, 4_000L)), "Refresh accounts spaced by at least four seconds");
    }

    private static TokenImportService service(TokenImportService.Authenticator auth, TokenImportService.AccountSink sink) {
        return new TokenImportService(auth, sink, System::currentTimeMillis, millis -> {});
    }
    private static class FakeAuth implements TokenImportService.Authenticator {
        public TokenRefresher.VerifiedAccount validateSession(String token) throws TokenRefresher.Failure, InterruptedException { return verified(token); }
        public TokenRefresher.VerifiedAccount refresh(String token) throws TokenRefresher.Failure, InterruptedException { return verified(token); }
    }
    private static TokenRefresher.VerifiedAccount verified(String token) {
        return new TokenRefresher.VerifiedAccount("VerifiedUser", UUID.fromString("12345678-1234-1234-1234-123456789abc"), token, "", 1_900_000_000_000L);
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void expectIo(IoAction action) {
        try { action.run(); throw new AssertionError("Expected IO rejection"); }
        catch (IOException expected) { checks++; }
    }
    private static void expectIllegal(Runnable action) {
        try { action.run(); throw new AssertionError("Expected input rejection"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
