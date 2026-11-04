package autismclient.util.tokenimport;

import autismclient.util.AutismAuthNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * In-process implementation of the public authentication protocol used by
 * TheApple05/Token-refresher at fa96d07434d50eb451cbc5609d45d9787fc8ce48.
 * The Live public-client configuration and t= Xbox ticket match that project;
 * no desktop helper, Localts service, or credential export is involved.
 */
public final class TokenRefresher {
    static final int MAX_TOKEN_CHARS = 16_384;
    static final int MAX_RESPONSE_BYTES = 65_536;
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private final Transport transport;
    private final LongSupplier clock;

    public TokenRefresher() {
        this(new OfficialTransport(), System::currentTimeMillis);
    }

    /** Transport injection is for deterministic tests; production URLs are fixed by Endpoint. */
    public TokenRefresher(Transport transport) {
        this(transport, System::currentTimeMillis);
    }

    TokenRefresher(Transport transport, LongSupplier clock) {
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }

    public VerifiedAccount validateSession(String token) throws Failure, InterruptedException {
        requireToken(token);
        return profile(token, "", sessionExpiry(token));
    }

    public VerifiedAccount refresh(String refreshToken) throws Failure, InterruptedException {
        requireToken(refreshToken);
        String form = "client_id=" + CLIENT_ID + "&redirect_uri=" + encode(REDIRECT_URI)
            + "&grant_type=refresh_token&scope=" + encode(SCOPE) + "&refresh_token=" + encode(refreshToken);
        JsonObject msa = exchange(new Request(Endpoint.MICROSOFT_TOKEN, "POST", form, ""));
        String msaAccess = token(msa, "access_token");
        String replacementRefresh = optionalString(msa, "refresh_token");
        if (replacementRefresh.isBlank()) replacementRefresh = refreshToken;
        requireServiceToken(replacementRefresh);

        JsonObject xboxProperties = new JsonObject();
        xboxProperties.addProperty("AuthMethod", "RPS");
        xboxProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProperties.addProperty("RpsTicket", "t=" + msaAccess);
        JsonObject xboxBody = new JsonObject();
        xboxBody.add("Properties", xboxProperties);
        xboxBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxBody.addProperty("TokenType", "JWT");
        JsonObject xbox = exchange(new Request(Endpoint.XBOX_USER, "POST", xboxBody.toString(), ""));
        String xboxToken = token(xbox, "Token");

        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);
        xstsProperties.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProperties);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = exchange(new Request(Endpoint.XBOX_XSTS, "POST", xstsBody.toString(), ""));
        String xstsToken = token(xsts, "Token");
        String userHash = userHash(xsts);
        if (userHash.isBlank()) userHash = userHash(xbox);
        if (!userHash.matches("[0-9]{1,32}")) throw new Failure(FailureKind.NETWORK);

        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject minecraft = exchange(new Request(Endpoint.MINECRAFT_LOGIN, "POST", loginBody.toString(), ""));
        String accessToken = token(minecraft, "access_token");
        long expiresAt = expiryFromSeconds(minecraft);
        // Match Token-refresher's approved entitlement request, then verify profile identity.
        exchange(new Request(Endpoint.MINECRAFT_ENTITLEMENTS, "GET", "", accessToken));
        return profile(accessToken, replacementRefresh, expiresAt);
    }

    private VerifiedAccount profile(String accessToken, String refreshToken, long expiresAt)
        throws Failure, InterruptedException {
        JsonObject profile;
        try {
            profile = exchange(new Request(Endpoint.MINECRAFT_PROFILE, "GET", "", accessToken));
        } catch (Failure failure) {
            if (failure.kind() == FailureKind.INVALID && expiresAt > 0 && expiresAt <= clock.getAsLong()) {
                throw new Failure(FailureKind.EXPIRED);
            }
            throw failure;
        }
        String name = optionalString(profile, "name");
        String id = optionalString(profile, "id").replace("-", "");
        if (!name.matches("[A-Za-z0-9_]{1,16}") || !id.matches("[0-9a-fA-F]{32}")) {
            throw new Failure(FailureKind.NETWORK);
        }
        String dashed = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
            + "-" + id.substring(16, 20) + "-" + id.substring(20);
        return new VerifiedAccount(name, UUID.fromString(dashed), accessToken, refreshToken, expiresAt);
    }

    private JsonObject exchange(Request request) throws Failure, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Response response;
        try {
            response = transport.exchange(request);
        } catch (IOException | RuntimeException failure) {
            // No exception causes or server text: either may contain submitted credentials.
            throw new Failure(FailureKind.NETWORK);
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (response == null || response.body() == null || response.body().length() > MAX_RESPONSE_BYTES) {
            throw new Failure(FailureKind.NETWORK);
        }
        int status = response.status();
        if (status == 429) throw new Failure(FailureKind.RATE_LIMITED);
        if (status >= 300 && status < 400) throw new Failure(FailureKind.UNSUPPORTED);
        if (status >= 500 || status < 200) throw new Failure(FailureKind.NETWORK);
        // Rejections need not contain JSON (for example an empty HTTP 401).
        if (request.endpoint() != Endpoint.MICROSOFT_TOKEN) {
            if (status == 401) throw new Failure(FailureKind.INVALID);
            if (status == 403 || status == 404) throw new Failure(FailureKind.UNSUPPORTED);
            if (status >= 400) throw new Failure(FailureKind.NETWORK);
        }
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) throw new IllegalArgumentException();
            json = parsed.getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new Failure(FailureKind.NETWORK);
        }
        if (request.endpoint() == Endpoint.MICROSOFT_TOKEN && json.has("error")) {
            throw oauthFailure(optionalString(json, "error"));
        }
        if (status == 401) throw new Failure(FailureKind.INVALID);
        if (status == 403 || status == 404) throw new Failure(FailureKind.UNSUPPORTED);
        if (status >= 400) throw new Failure(FailureKind.NETWORK);
        return json;
    }

    private static Failure oauthFailure(String error) {
        return new Failure(switch (error) {
            case "invalid_grant", "invalid_request" -> FailureKind.INVALID;
            case "expired_grant" -> FailureKind.EXPIRED;
            case "slow_down" -> FailureKind.RATE_LIMITED;
            case "unauthorized_client", "interaction_required", "consent_required", "authorization_pending"
                -> FailureKind.UNSUPPORTED;
            default -> FailureKind.NETWORK;
        });
    }

    private long expiryFromSeconds(JsonObject reply) {
        try {
            long seconds = reply.get("expires_in").getAsLong();
            // Ignore impossible server values rather than overflowing persisted expiry timestamps.
            if (seconds <= 0 || seconds > 31_536_000L) return 0;
            return Math.addExact(clock.getAsLong(), Math.multiplyExact(seconds, 1000));
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static long sessionExpiry(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return 0;
            JsonObject claims = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8)).getAsJsonObject();
            long exp = claims.get("exp").getAsLong();
            return exp > 0 ? Math.multiplyExact(exp, 1000) : 0;
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    private static String userHash(JsonObject json) {
        try {
            return optionalString(json.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                .get(0).getAsJsonObject(), "uhs");
        } catch (RuntimeException failure) {
            return "";
        }
    }

    private static String optionalString(JsonObject json, String name) {
        JsonElement value = json.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString() : "";
    }

    private static String token(JsonObject json, String name) throws Failure {
        String value = optionalString(json, name);
        requireServiceToken(value);
        return value;
    }

    private static void requireServiceToken(String token) throws Failure {
        if (!isToken(token)) throw new Failure(FailureKind.NETWORK);
    }

    private static void requireToken(String token) throws Failure {
        if (!isToken(token)) throw new Failure(FailureKind.INVALID);
    }

    private static boolean isToken(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_CHARS) return false;
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch <= 32 || ch >= 127) return false;
        }
        return true;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public enum FailureKind { INVALID, EXPIRED, NETWORK, RATE_LIMITED, UNSUPPORTED }

    public static final class Failure extends Exception {
        private final FailureKind kind;

        public Failure(FailureKind kind) {
            super(switch (kind) {
                case INVALID -> "Token was rejected.";
                case EXPIRED -> "Token has expired.";
                case NETWORK -> "Authentication service is unavailable or returned an invalid response. Retry later.";
                case RATE_LIMITED -> "Authentication service is rate limited. Retry later.";
                case UNSUPPORTED -> "Account needs sign-in, has no Minecraft profile, or the authentication flow was blocked.";
            });
            this.kind = kind;
        }

        public FailureKind kind() { return kind; }
    }

    public record VerifiedAccount(String username, UUID uuid, String accessToken, String refreshToken,
                                  long expiresAtMillis) {
        @Override public String toString() { return "VerifiedAccount[credentials=redacted]"; }
    }

    public enum Endpoint {
        MICROSOFT_TOKEN("https://login.live.com/oauth20_token.srf"),
        XBOX_USER("https://user.auth.xboxlive.com/user/authenticate"),
        XBOX_XSTS("https://xsts.auth.xboxlive.com/xsts/authorize"),
        MINECRAFT_LOGIN("https://api.minecraftservices.com/authentication/login_with_xbox"),
        MINECRAFT_ENTITLEMENTS("https://api.minecraftservices.com/entitlements/license?requestId=auth"),
        MINECRAFT_PROFILE("https://api.minecraftservices.com/minecraft/profile");

        private final URI uri;
        Endpoint(String uri) { this.uri = URI.create(uri); }
        public URI uri() { return uri; }
    }

    @FunctionalInterface
    public interface Transport {
        Response exchange(Request request) throws IOException, InterruptedException;
    }

    public record Request(Endpoint endpoint, String method, String body, String bearer) {
        @Override public String toString() { return "Request[endpoint=" + endpoint + ", credentials=redacted]"; }
    }

    public record Response(int status, String body) {
        @Override public String toString() { return "Response[status=" + status + ", body=redacted]"; }
    }

    static final class OfficialTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(AutismAuthNetwork.directProxySelector())
            .build();

        @Override public Response exchange(Request request) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.endpoint().uri())
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (!request.bearer().isEmpty()) builder.header("Authorization", "Bearer " + request.bearer());
            if (request.method().equals("POST")) {
                builder.header("Content-Type", request.endpoint() == Endpoint.MICROSOFT_TOKEN
                    ? "application/x-www-form-urlencoded" : "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(),
                ignored -> new BoundedBodySubscriber());
            try {
                // Includes receiving the entire bounded body, not just response headers.
                HttpResponse<byte[]> reply = future.get(30, TimeUnit.SECONDS);
                return new Response(reply.statusCode(), new String(reply.body(), StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                throw interrupted;
            } catch (ExecutionException | TimeoutException failure) {
                future.cancel(true);
                throw new IOException("Authentication request failed.");
            }
        }
    }

    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) { subscription.cancel(); return; }
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Authentication response exceeded limit."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) {
            body.completeExceptionally(new IOException("Authentication response failed."));
        }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
