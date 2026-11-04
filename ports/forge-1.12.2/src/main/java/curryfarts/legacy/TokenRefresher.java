package curryfarts.legacy;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Java 8 implementation of Token-refresher's public protocol, source fa96d07434d50eb451cbc5609d45d9787fc8ce48. */
public final class TokenRefresher {
    public enum FailureKind { INVALID, EXPIRED, NETWORK, RATE_LIMITED, UNSUPPORTED }
    public static final class Failure extends Exception {
        public final FailureKind kind;
        public Failure(FailureKind kind){super(kind==FailureKind.INVALID?"Token was rejected.":kind==FailureKind.EXPIRED?"Token has expired.":kind==FailureKind.RATE_LIMITED?"Rate limited. Wait before retrying.":kind==FailureKind.UNSUPPORTED?"Account needs sign-in or has no Minecraft profile.":"Authentication service unavailable. Retry later.");this.kind=kind;}
    }
    public static final class Verified {
        public final String username,uuid,accessToken,refreshToken;public final long expiresAt;
        public Verified(String name,String uuid,String access,String refresh,long expires){this.username=name;this.uuid=uuid;this.accessToken=access;this.refreshToken=refresh;this.expiresAt=expires;}
        @Override public String toString(){return "Verified[credentials=redacted]";}
    }
    public enum Endpoint {
        MICROSOFT("https://login.live.com/oauth20_token.srf"),XBOX("https://user.auth.xboxlive.com/user/authenticate"),
        XSTS("https://xsts.auth.xboxlive.com/xsts/authorize"),LOGIN("https://api.minecraftservices.com/authentication/login_with_xbox"),
        ENTITLEMENTS("https://api.minecraftservices.com/entitlements/license?requestId=auth"),PROFILE("https://api.minecraftservices.com/minecraft/profile");
        public final String url;Endpoint(String url){this.url=url;}
    }
    public static final class Request {
        public final Endpoint endpoint;public final String body,bearer;
        Request(Endpoint endpoint,String body,String bearer){this.endpoint=endpoint;this.body=body;this.bearer=bearer;}
        @Override public String toString(){return "Request["+endpoint+", credentials=redacted]";}
    }
    public static final class Reply {
        public final int status;public final String body;
        public Reply(int status,String body){this.status=status;this.body=body;}
        @Override public String toString(){return "Reply[status="+status+", body=redacted]";}
    }
    public interface Transport {Reply exchange(Request request)throws IOException,InterruptedException;}
    private final Transport transport;
    public TokenRefresher(){this(new OfficialTransport());}
    public TokenRefresher(Transport transport){this.transport=Objects.requireNonNull(transport);}
    public Verified validateSession(String token)throws Failure,InterruptedException {requireToken(token,false);return profile(token,"",expiry(token));}
    public Verified refresh(String refresh)throws Failure,InterruptedException {
        requireToken(refresh,false);
        JsonObject msa=exchange(new Request(Endpoint.MICROSOFT,"client_id=00000000402b5328&redirect_uri="+enc("https://login.live.com/oauth20_desktop.srf")+"&grant_type=refresh_token&scope="+enc("service::user.auth.xboxlive.com::MBI_SSL")+"&refresh_token="+enc(refresh),""));
        String access=token(msa,"access_token"),replacement=str(msa,"refresh_token");if(replacement.isEmpty())replacement=refresh;requireToken(replacement,true);
        JsonObject properties=new JsonObject();properties.addProperty("AuthMethod","RPS");properties.addProperty("SiteName","user.auth.xboxlive.com");properties.addProperty("RpsTicket","t="+access);
        JsonObject xboxBody=new JsonObject();xboxBody.add("Properties",properties);xboxBody.addProperty("RelyingParty","http://auth.xboxlive.com");xboxBody.addProperty("TokenType","JWT");
        JsonObject xbox=exchange(new Request(Endpoint.XBOX,xboxBody.toString(),""));String xboxToken=token(xbox,"Token");
        JsonObject xstsProperties=new JsonObject();xstsProperties.addProperty("SandboxId","RETAIL");JsonArray tokens=new JsonArray();tokens.add(new JsonPrimitive(xboxToken));xstsProperties.add("UserTokens",tokens);
        JsonObject xstsBody=new JsonObject();xstsBody.add("Properties",xstsProperties);xstsBody.addProperty("RelyingParty","rp://api.minecraftservices.com/");xstsBody.addProperty("TokenType","JWT");
        JsonObject xsts=exchange(new Request(Endpoint.XSTS,xstsBody.toString(),""));String xstsToken=token(xsts,"Token"),hash=userHash(xsts);if(hash.isEmpty())hash=userHash(xbox);if(!hash.matches("[0-9]{1,32}"))throw new Failure(FailureKind.NETWORK);
        JsonObject login=new JsonObject();login.addProperty("identityToken","XBL3.0 x="+hash+";"+xstsToken);
        JsonObject minecraft=exchange(new Request(Endpoint.LOGIN,login.toString(),""));String minecraftToken=token(minecraft,"access_token");long expires=expiry(minecraftToken);
        try{long seconds=minecraft.get("expires_in").getAsLong();if(seconds>0&&seconds<=31536000L)expires=System.currentTimeMillis()+seconds*1000L;}catch(RuntimeException ignored){}
        exchange(new Request(Endpoint.ENTITLEMENTS,null,minecraftToken));return profile(minecraftToken,replacement,expires);
    }
    private Verified profile(String access,String refresh,long expires)throws Failure,InterruptedException {
        JsonObject p;try{p=exchange(new Request(Endpoint.PROFILE,null,access));}catch(Failure error){if(error.kind==FailureKind.INVALID&&expires>0&&expires<=System.currentTimeMillis())throw new Failure(FailureKind.EXPIRED);throw error;}
        String name=str(p,"name"),id=str(p,"id").replace("-","");if(!name.matches("[A-Za-z0-9_]{1,16}")||!id.matches("[0-9a-fA-F]{32}"))throw new Failure(FailureKind.NETWORK);
        String uuid=id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20);return new Verified(name,uuid,access,refresh,expires);
    }
    private JsonObject exchange(Request request)throws Failure,InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();Reply reply;
        try{reply=transport.exchange(request);}catch(IOException error){throw new Failure(FailureKind.NETWORK);}catch(RuntimeException error){throw new Failure(FailureKind.NETWORK);}
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(reply==null||reply.body==null||reply.body.length()>65536)throw new Failure(FailureKind.NETWORK);
        if(reply.status==429)throw new Failure(FailureKind.RATE_LIMITED);if(reply.status>=300&&reply.status<400)throw new Failure(FailureKind.UNSUPPORTED);if(reply.status>=500||reply.status<200)throw new Failure(FailureKind.NETWORK);
        if(request.endpoint!=Endpoint.MICROSOFT){if(reply.status==401)throw new Failure(FailureKind.INVALID);if(reply.status==403||reply.status==404)throw new Failure(FailureKind.UNSUPPORTED);if(reply.status>=400)throw new Failure(FailureKind.NETWORK);}
        JsonObject json;try{json=new JsonParser().parse(reply.body).getAsJsonObject();}catch(RuntimeException error){throw new Failure(FailureKind.NETWORK);}
        if(request.endpoint==Endpoint.MICROSOFT&&json.has("error")){String code=str(json,"error");FailureKind kind=FailureKind.NETWORK;if(code.equals("invalid_grant")||code.equals("invalid_request"))kind=FailureKind.INVALID;else if(code.equals("expired_grant"))kind=FailureKind.EXPIRED;else if(code.equals("slow_down"))kind=FailureKind.RATE_LIMITED;else if(Arrays.asList("unauthorized_client","interaction_required","consent_required","authorization_pending").contains(code))kind=FailureKind.UNSUPPORTED;throw new Failure(kind);}
        if(reply.status>=400)throw new Failure(FailureKind.NETWORK);return json;
    }
    private static String str(JsonObject object,String key){JsonElement v=object.get(key);return v!=null&&v.isJsonPrimitive()&&v.getAsJsonPrimitive().isString()?v.getAsString():"";}
    private static String token(JsonObject object,String key)throws Failure {String value=str(object,key);requireToken(value,true);return value;}
    private static String userHash(JsonObject object){try{return str(object.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(),"uhs");}catch(RuntimeException ignored){return "";}}
    private static String enc(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(UnsupportedEncodingException impossible){throw new AssertionError();}}
    private static void requireToken(String value,boolean response)throws Failure {if(value==null||value.isEmpty()||value.length()>16384)throw new Failure(response?FailureKind.NETWORK:FailureKind.INVALID);for(int i=0;i<value.length();i++)if(value.charAt(i)<=32||value.charAt(i)>=127)throw new Failure(response?FailureKind.NETWORK:FailureKind.INVALID);}
    private static long expiry(String value){try{String[] parts=value.split("\\.",-1);if(parts.length!=3)return 0;long exp=new JsonParser().parse(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8)).getAsJsonObject().get("exp").getAsLong();return exp>0?Math.multiplyExact(exp,1000):0;}catch(RuntimeException ignored){return 0;}}
    /** Bounded whole-response transport; cancellation aborts sockets, with no redirects or cookies. */
    static final class OfficialTransport implements Transport {
        private static final ExecutorService HTTP=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"Curryfarts-Token-HTTP");t.setDaemon(true);return t;});
        public Reply exchange(final Request request)throws IOException,InterruptedException {
            final org.apache.http.client.methods.HttpRequestBase command;
            if(request.body!=null){org.apache.http.client.methods.HttpPost post=new org.apache.http.client.methods.HttpPost(request.endpoint.url);post.setEntity(new org.apache.http.entity.ByteArrayEntity(request.body.getBytes(StandardCharsets.UTF_8)));post.setHeader("Content-Type",request.endpoint==Endpoint.MICROSOFT?"application/x-www-form-urlencoded":"application/json");command=post;}else command=new org.apache.http.client.methods.HttpGet(request.endpoint.url);
            command.setHeader("Accept","application/json");if(!request.bearer.isEmpty())command.setHeader("Authorization","Bearer "+request.bearer);
            Future<Reply> future=HTTP.submit(()->{
                org.apache.http.client.config.RequestConfig config=org.apache.http.client.config.RequestConfig.custom().setConnectTimeout(10000).setConnectionRequestTimeout(10000).setSocketTimeout(10000).setRedirectsEnabled(false).build();
                try(org.apache.http.impl.client.CloseableHttpClient client=org.apache.http.impl.client.HttpClients.custom().disableCookieManagement().disableRedirectHandling().disableAutomaticRetries().setDefaultRequestConfig(config).build();org.apache.http.client.methods.CloseableHttpResponse response=client.execute(command)){
                    int status=response.getStatusLine().getStatusCode();ByteArrayOutputStream body=new ByteArrayOutputStream();if(response.getEntity()!=null)try(InputStream in=response.getEntity().getContent()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(n>65536-body.size())throw new IOException("Response limit exceeded.");body.write(block,0,n);}}return new Reply(status,new String(body.toByteArray(),StandardCharsets.UTF_8));
                }finally{command.releaseConnection();}
            });
            try{return future.get(30,TimeUnit.SECONDS);}catch(InterruptedException error){command.abort();future.cancel(true);throw error;}catch(ExecutionException error){throw new IOException("Authentication request failed.");}catch(TimeoutException error){command.abort();future.cancel(true);throw new IOException("Authentication request timed out.");}
        }
    }
}
