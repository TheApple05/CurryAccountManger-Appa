package autismclient.util.tokenimport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Sequential, cancellable import orchestration. Run on a worker, never the render thread. */
public final class TokenImportService {
    public enum Status { IMPORTED, DUPLICATE, INVALID, EXPIRED, FAILED, CANCELLED }
    public enum StoreResult { IMPORTED, DUPLICATE, CANCELLED }
    public record EntryResult(int lineNumber, Status status, String message) {}
    public record Summary(int total, int processed, int imported, int duplicates, int invalid, int expired,
                          int failed, boolean cancelled, List<EntryResult> results) {
        public Summary { results = List.copyOf(results); }
    }
    public interface Authenticator {
        TokenRefresher.VerifiedAccount validateSession(String token) throws TokenRefresher.Failure, InterruptedException;
        TokenRefresher.VerifiedAccount refresh(String token) throws TokenRefresher.Failure, InterruptedException;
    }
    @FunctionalInterface public interface AccountSink {
        StoreResult store(TokenRefresher.VerifiedAccount verified, BooleanSupplier cancelled);
    }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final Authenticator auth;
    private final AccountSink sink;
    private final LongSupplier clock;
    private final Sleeper sleeper;

    public TokenImportService(TokenRefresher refresher, AccountSink sink) {
        this(new Authenticator() {
            public TokenRefresher.VerifiedAccount validateSession(String token) throws TokenRefresher.Failure, InterruptedException {
                return refresher.validateSession(token);
            }
            public TokenRefresher.VerifiedAccount refresh(String token) throws TokenRefresher.Failure, InterruptedException {
                return refresher.refresh(token);
            }
        }, sink, System::currentTimeMillis, Thread::sleep);
    }

    TokenImportService(Authenticator auth, AccountSink sink, LongSupplier clock, Sleeper sleeper) {
        this.auth = auth;
        this.sink = sink;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public Summary importText(String text, BooleanSupplier cancelled, Consumer<Summary> progress) {
        List<TokenInputParser.Entry> entries = TokenInputParser.parse(text);
        List<EntryResult> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long lastRefresh = Long.MIN_VALUE;
        boolean stopped = isCancelled(cancelled);
        progress.accept(summary(entries.size(), results, stopped));
        for (var entry : entries) {
            if (isCancelled(cancelled)) { stopped = true; break; }
            Status status;
            String message;
            boolean rateLimited = false;
            if (entry.kind() == TokenInputParser.Kind.INVALID) {
                status = Status.INVALID;
                message = entry.error();
            } else if (seen.contains(fingerprint(entry.token()))) {
                status = Status.DUPLICATE;
                message = "Token already processed in this batch.";
            } else {
                try {
                    TokenRefresher.VerifiedAccount verified;
                    if (entry.kind() == TokenInputParser.Kind.REFRESH) {
                        if (lastRefresh != Long.MIN_VALUE) {
                            long wait = 4_000L - (clock.getAsLong() - lastRefresh);
                            if (wait > 0) sleeper.sleep(wait);
                        }
                        if (isCancelled(cancelled)) { stopped = true; break; }
                        lastRefresh = clock.getAsLong();
                        verified = auth.refresh(entry.token());
                    } else verified = auth.validateSession(entry.token());
                    if (isCancelled(cancelled)) { stopped = true; break; }
                    StoreResult saved = sink.store(verified, () -> isCancelled(cancelled));
                    if (saved == StoreResult.CANCELLED) { stopped = true; break; }
                    // Mark only successfully checked credentials. A transient failure remains retryable.
                    seen.add(fingerprint(entry.token()));
                    status = saved == StoreResult.IMPORTED ? Status.IMPORTED : Status.DUPLICATE;
                    message = saved == StoreResult.IMPORTED ? "Imported " + verified.username() + "." : "Existing account credentials updated.";
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    stopped = true;
                    break;
                } catch (TokenRefresher.Failure error) {
                    status = switch (error.kind()) {
                        case INVALID, UNSUPPORTED -> Status.INVALID;
                        case EXPIRED -> Status.EXPIRED;
                        case NETWORK, RATE_LIMITED -> Status.FAILED;
                    };
                    message = error.getMessage();
                    rateLimited = error.kind() == TokenRefresher.FailureKind.RATE_LIMITED;
                } catch (RuntimeException error) {
                    status = Status.FAILED;
                    message = "Could not import this account. Retry later.";
                }
            }
            results.add(new EntryResult(entry.lineNumber(), status, message));
            progress.accept(summary(entries.size(), results, false));
            if (rateLimited) break;
        }
        Summary summary = summary(entries.size(), results, stopped || isCancelled(cancelled));
        progress.accept(summary);
        return summary;
    }

    private static boolean isCancelled(BooleanSupplier cancelled) {
        return Thread.currentThread().isInterrupted() || cancelled.getAsBoolean();
    }

    private static Summary summary(int total, List<EntryResult> results, boolean cancelled) {
        int imported = 0, duplicates = 0, invalid = 0, expired = 0, failed = 0;
        for (EntryResult result : results) {
            switch (result.status()) {
                case IMPORTED -> imported++;
                case DUPLICATE -> duplicates++;
                case INVALID -> invalid++;
                case EXPIRED -> expired++;
                case FAILED -> failed++;
                case CANCELLED -> { }
            }
        }
        return new Summary(total, results.size(), imported, duplicates, invalid, expired, failed, cancelled, results);
    }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
