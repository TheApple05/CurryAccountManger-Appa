package autismclient.util.tokenimport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

/** Standalone deterministic tests: synthetic tokens, injectable responses, no network or accounts. */
public final class TokenRefresherTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String UUID_TEXT = "069a79f444e94726a5befca90e38aaf5";
    private static final String PROFILE = "{\"id\":\"" + UUID_TEXT + "\",\"name\":\"SyntheticUser\"}";
    private static int checks;

    public static void main(String[] args) throws Exception {
        sessionValidation();
        completeRefreshFlow();
        replacementAndHashFallback();
        oauthFailures();
        serviceFailures();
        cancellation();
        responseAndInputBounds();
        redaction();
        transportPolicy();
        boundedSubscriber();
        System.out.println("TokenRefresherTest: " + checks + " checks passed (synthetic transport; no real accounts).");
    }

    private static void sessionValidation() throws Exception {
        List<TokenRefresher.Request> requests = new ArrayList<>();
        TokenRefresher refresher = new TokenRefresher(request -> {
            requests.add(request);
            return reply(200, PROFILE);
        }, () -> NOW);
        String access = jwt(NOW / 1000 + 3600);
        TokenRefresher.VerifiedAccount account = refresher.validateSession(access);
        check(account.username().equals("SyntheticUser"), "Server username is used");
        check(account.uuid().equals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")), "Undashed UUID parsed");
        check(account.expiresAtMillis() == NOW + 3_600_000, "JWT expiry retained as metadata");
        check(account.refreshToken().isEmpty() && account.accessToken().equals(access), "Session is not treated as a refresh token");
        check(requests.size() == 1 && requests.get(0).endpoint() == TokenRefresher.Endpoint.MINECRAFT_PROFILE,
            "Session validation only calls official profile");
        check(requests.get(0).bearer().equals(access) && requests.get(0).method().equals("GET"), "Profile bearer authentication");
        TokenRefresher unauthorized = new TokenRefresher(request -> reply(401, ""), () -> NOW);
        expect(TokenRefresher.FailureKind.EXPIRED, () -> unauthorized.validateSession(jwt(NOW / 1000 - 30)));
        expect(TokenRefresher.FailureKind.INVALID, () -> unauthorized.validateSession(jwt(NOW / 1000 + 3600)));
        expect(TokenRefresher.FailureKind.INVALID, () -> unauthorized.validateSession("opaque.synthetic"));
        // Client JWT claims are never sufficient evidence that an account works.
        expect(TokenRefresher.FailureKind.UNSUPPORTED,
            () -> new TokenRefresher(request -> reply(404, ""), () -> NOW).validateSession(access));
        check(refresher.validateSession("opaque.synthetic").expiresAtMillis() == 0, "Opaque access tokens can be validated online");
    }

    private static void completeRefreshFlow() throws Exception {
        List<TokenRefresher.Request> requests = new ArrayList<>();
        TokenRefresher refresher = scripted(requests, true, true);
        TokenRefresher.VerifiedAccount account = refresher.refresh("M.Csynthetic+token&suffix");
        check(requests.size() == 6, "Refresh executes all six official requests");
        Map<String, String> form = form(requests.get(0).body());
        check(form.get("client_id").equals("00000000402b5328"), "Matches Token-refresher Live client");
        check(form.get("redirect_uri").equals("https://login.live.com/oauth20_desktop.srf"), "Matches canonical redirect");
        check(form.get("scope").equals("service::user.auth.xboxlive.com::MBI_SSL"), "Matches Live Xbox scope");
        check(form.get("grant_type").equals("refresh_token"), "Refresh grant selected");
        check(form.get("refresh_token").equals("M.Csynthetic+token&suffix"), "Refresh token safely form encoded");
        JsonObject xbox = json(requests.get(1).body());
        check(xbox.getAsJsonObject("Properties").get("RpsTicket").getAsString().equals("t=msa.synthetic"),
            "Live flow uses t= ticket prefix");
        check(xbox.get("RelyingParty").getAsString().equals("http://auth.xboxlive.com"), "Xbox relying party");
        JsonObject xsts = json(requests.get(2).body());
        check(xsts.getAsJsonObject("Properties").getAsJsonArray("UserTokens").get(0).getAsString().equals("xbox.synthetic"),
            "XSTS receives Xbox token");
        check(xsts.get("RelyingParty").getAsString().equals("rp://api.minecraftservices.com/"), "Minecraft XSTS relying party");
        check(json(requests.get(3).body()).get("identityToken").getAsString().equals("XBL3.0 x=222;xsts.synthetic"),
            "XSTS user hash takes priority over Xbox user hash");
        check(requests.get(4).endpoint() == TokenRefresher.Endpoint.MINECRAFT_ENTITLEMENTS
            && requests.get(4).bearer().equals("minecraft.synthetic"), "Entitlement step uses Minecraft bearer");
        check(requests.get(5).endpoint() == TokenRefresher.Endpoint.MINECRAFT_PROFILE, "Profile validation is final");
        check(account.accessToken().equals("minecraft.synthetic") && account.refreshToken().equals("M.CrotatedSynthetic"),
            "Rotated refresh credential returned with session");
        check(account.expiresAtMillis() == NOW + 86_400_000, "Minecraft expires_in persisted");
        for (TokenRefresher.Request request : requests) {
            check(request.endpoint().uri().getScheme().equals("https"), "Every credential request uses fixed HTTPS");
        }
    }

    private static void replacementAndHashFallback() throws Exception {
        List<TokenRefresher.Request> requests = new ArrayList<>();
        TokenRefresher.VerifiedAccount account = scripted(requests, false, false).refresh("M.CoriginalSynthetic");
        check(account.refreshToken().equals("M.CoriginalSynthetic"), "Missing rotation retains supplied refresh token");
        check(json(requests.get(3).body()).get("identityToken").getAsString().equals("XBL3.0 x=111;xsts.synthetic"),
            "Xbox user hash fallback works");
    }

    private static void oauthFailures() throws Exception {
        Map<String, TokenRefresher.FailureKind> codes = Map.of(
            "invalid_grant", TokenRefresher.FailureKind.INVALID,
            "expired_grant", TokenRefresher.FailureKind.EXPIRED,
            "slow_down", TokenRefresher.FailureKind.RATE_LIMITED,
            "interaction_required", TokenRefresher.FailureKind.UNSUPPORTED,
            "consent_required", TokenRefresher.FailureKind.UNSUPPORTED,
            "temporarily_unavailable", TokenRefresher.FailureKind.NETWORK,
            "server_error", TokenRefresher.FailureKind.NETWORK,
            "unknown_synthetic_error", TokenRefresher.FailureKind.NETWORK);
        for (Map.Entry<String, TokenRefresher.FailureKind> code : codes.entrySet()) {
            TokenRefresher refresher = new TokenRefresher(request -> reply(400,
                "{\"error\":\"" + code.getKey() + "\",\"error_description\":\"M.CsecretEcho\"}"));
            expect(code.getValue(), () -> refresher.refresh("M.Csynthetic"));
        }
    }

    private static void serviceFailures() throws Exception {
        expect(TokenRefresher.FailureKind.RATE_LIMITED,
            () -> new TokenRefresher(request -> reply(429, "not JSON")).validateSession("synthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(503, "not JSON")).validateSession("synthetic"));
        expect(TokenRefresher.FailureKind.UNSUPPORTED,
            () -> new TokenRefresher(request -> reply(307, "https://untrusted.example")).refresh("M.Csynthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> { throw new IOException("M.CsecretEcho"); }).refresh("M.Csynthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(200, "not JSON")).validateSession("synthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(200, "{}")).refresh("M.Csynthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(200, "{\"name\":\"Secret\\nText\",\"id\":\"invalid\"}"))
                .validateSession("synthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(200, "{\"name\":\"SyntheticUser\",\"id\":123}"))
                .validateSession("synthetic"));
        for (TokenRefresher.Endpoint endpoint : TokenRefresher.Endpoint.values()) {
            List<TokenRefresher.Request> preceding = new ArrayList<>();
            TokenRefresher.Transport success = scriptedTransport(preceding, true, true);
            TokenRefresher refresher = new TokenRefresher(request -> request.endpoint() == endpoint
                ? reply(429, "M.CsecretEcho") : success.exchange(request), () -> NOW);
            expect(TokenRefresher.FailureKind.RATE_LIMITED, () -> refresher.refresh("M.Csynthetic"));
        }
    }

    private static void cancellation() throws Exception {
        int[] calls = {0};
        TokenRefresher refresher = new TokenRefresher(request -> { calls[0]++; return reply(200, PROFILE); });
        Thread.currentThread().interrupt();
        try {
            refresher.validateSession("synthetic");
            throw new AssertionError("Expected cancellation");
        } catch (InterruptedException expected) {
            check(calls[0] == 0, "Already cancelled operation never sends a request");
        } finally {
            Thread.interrupted();
        }
        TokenRefresher duringRequest = new TokenRefresher(request -> { throw new InterruptedException(); });
        try {
            duringRequest.refresh("M.Csynthetic");
            throw new AssertionError("Expected transport cancellation");
        } catch (InterruptedException expected) {
            check(true, "Transport interruption reaches caller");
        }
        TokenRefresher afterRequest = new TokenRefresher(request -> {
            Thread.currentThread().interrupt();
            return reply(200, PROFILE);
        });
        try {
            afterRequest.validateSession("synthetic");
            throw new AssertionError("Expected cancellation after response");
        } catch (InterruptedException expected) {
            check(true, "Cancelled response is never accepted");
        } finally {
            Thread.interrupted();
        }
    }

    private static void responseAndInputBounds() throws Exception {
        TokenRefresher noRequest = new TokenRefresher(request -> { throw new AssertionError("Must not send malformed token"); });
        for (String bad : new String[]{"", "bad\r\nAuthorization: synthetic", "bad token", "a".repeat(16_385)}) {
            expect(TokenRefresher.FailureKind.INVALID, () -> noRequest.validateSession(bad));
        }
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> reply(200, "a".repeat(65_537))).validateSession("synthetic"));
        expect(TokenRefresher.FailureKind.NETWORK,
            () -> new TokenRefresher(request -> null).validateSession("synthetic"));
    }

    private static void redaction() throws Exception {
        String secret = "M.CsecretEcho";
        check(!new TokenRefresher.Request(TokenRefresher.Endpoint.MICROSOFT_TOKEN, "POST", secret, secret)
            .toString().contains(secret), "Request diagnostics redact credentials");
        check(!reply(200, secret).toString().contains(secret), "Response diagnostics redact server bodies");
        check(!new TokenRefresher.VerifiedAccount(secret, UUID.randomUUID(), secret, secret, 0)
            .toString().contains(secret), "Verified account diagnostics redact all identity and credential fields");
        for (TokenRefresher.FailureKind kind : TokenRefresher.FailureKind.values()) {
            TokenRefresher.Failure failure = new TokenRefresher.Failure(kind);
            check(failure.getCause() == null && !failure.toString().contains(secret), "Failure has no unsafe cause");
        }
    }

    private static void transportPolicy() throws Exception {
        TokenRefresher.OfficialTransport transport = new TokenRefresher.OfficialTransport();
        var field = TokenRefresher.OfficialTransport.class.getDeclaredField("client");
        field.setAccessible(true);
        try (HttpClient client = (HttpClient) field.get(transport)) {
            check(client.followRedirects() == HttpClient.Redirect.NEVER, "Production transport never forwards credentials on redirects");
            check(client.cookieHandler().isEmpty(), "Production transport has no cookies");
            check(client.connectTimeout().orElseThrow().toSeconds() == 10, "Connection timeout is bounded");
            check(client.proxy().orElseThrow().select(TokenRefresher.Endpoint.MICROSOFT_TOKEN.uri()).get(0)
                == java.net.Proxy.NO_PROXY, "Authentication uses explicit direct routing");
        }
        check(TokenRefresher.Endpoint.MICROSOFT_TOKEN.uri().toString().equals("https://login.live.com/oauth20_token.srf"),
            "Microsoft endpoint is canonical");
        check(TokenRefresher.Endpoint.MINECRAFT_PROFILE.uri().toString().equals("https://api.minecraftservices.com/minecraft/profile"),
            "Profile endpoint is canonical");
    }

    private static void boundedSubscriber() throws Exception {
        boolean[] cancelled = {false};
        Flow.Subscription subscription = new Flow.Subscription() {
            public void request(long count) { }
            public void cancel() { cancelled[0] = true; }
        };
        TokenRefresher.BoundedBodySubscriber subscriber = new TokenRefresher.BoundedBodySubscriber();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap("synthetic".getBytes(StandardCharsets.UTF_8))));
        subscriber.onComplete();
        check(new String(subscriber.getBody().toCompletableFuture().get(), StandardCharsets.UTF_8).equals("synthetic"),
            "Bounded subscriber preserves normal body");
        TokenRefresher.BoundedBodySubscriber overLimit = new TokenRefresher.BoundedBodySubscriber();
        overLimit.onSubscribe(subscription);
        overLimit.onNext(List.of(ByteBuffer.allocate(65_537)));
        try {
            overLimit.getBody().toCompletableFuture().get();
            throw new AssertionError("Expected bounded response failure");
        } catch (ExecutionException expected) {
            check(cancelled[0], "Oversized body cancels network subscription");
        }
    }

    private static TokenRefresher scripted(List<TokenRefresher.Request> requests, boolean replacement, boolean xstsHash) {
        return new TokenRefresher(scriptedTransport(requests, replacement, xstsHash), () -> NOW);
    }

    private static TokenRefresher.Transport scriptedTransport(List<TokenRefresher.Request> requests,
                                                              boolean replacement, boolean xstsHash) {
        return request -> {
            requests.add(request);
            return switch (request.endpoint()) {
                case MICROSOFT_TOKEN -> reply(200, "{\"access_token\":\"msa.synthetic\""
                    + (replacement ? ",\"refresh_token\":\"M.CrotatedSynthetic\"" : "") + "}");
                case XBOX_USER -> reply(200, "{\"Token\":\"xbox.synthetic\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"111\"}]}}");
                case XBOX_XSTS -> reply(200, "{\"Token\":\"xsts.synthetic\""
                    + (xstsHash ? ",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"222\"}]}" : "") + "}");
                case MINECRAFT_LOGIN -> reply(200, "{\"access_token\":\"minecraft.synthetic\",\"expires_in\":86400}");
                case MINECRAFT_ENTITLEMENTS -> reply(200, "{\"items\":[{\"name\":\"game_minecraft\"}]}");
                case MINECRAFT_PROFILE -> reply(200, PROFILE);
            };
        };
    }

    private static String jwt(long expiry) {
        return "eyJhbGciOiJzeW50aGV0aWMifQ."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"exp\":" + expiry + "}")
                .getBytes(StandardCharsets.UTF_8)) + ".syntheticSignature";
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        for (String item : body.split("&")) {
            String[] pair = item.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static TokenRefresher.Response reply(int status, String body) { return new TokenRefresher.Response(status, body); }

    @FunctionalInterface private interface Action { void run() throws Exception; }

    private static void expect(TokenRefresher.FailureKind kind, Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + kind);
        } catch (TokenRefresher.Failure failure) {
            check(failure.kind() == kind, "Failure classification: " + kind);
            check(failure.getCause() == null && !failure.getMessage().contains("M.CsecretEcho"), "Failure text never echoes credentials");
        }
    }

    private static void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        checks++;
    }
}
