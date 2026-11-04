package curryfarts.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Map;

/** Per-session Altening route; vanilla service is restored for other account types. */
final class AlteningSessionService implements MinecraftSessionService {
    private static final String BASE="http://sessionserver.thealtening.com/session/minecraft/";
    private final MinecraftSessionService vanilla;
    AlteningSessionService(MinecraftSessionService vanilla){this.vanilla=vanilla;}
    @Override public void joinServer(GameProfile profile,String token,String serverId)throws AuthenticationException {
        JsonObject body=new JsonObject();body.addProperty("accessToken",token);body.addProperty("selectedProfile",profile.getId().toString().replace("-",""));body.addProperty("serverId",serverId);
        Http.Reply reply=Http.send(BASE+"join","POST",body.toString(),"application/json",null);
        if(reply.status!=200&&reply.status!=204)throw new AuthenticationException("Altening session join failed (HTTP "+reply.status+")");
    }
    @Override public GameProfile hasJoinedServer(GameProfile profile,String serverId)throws AuthenticationUnavailableException {
        try {String url=BASE+"hasJoined?username="+URLEncoder.encode(profile.getName(),"UTF-8")+"&serverId="+URLEncoder.encode(serverId,"UTF-8");return parse(Http.get(url,null),profile);}catch(Exception error){throw new AuthenticationUnavailableException("Altening session lookup failed");}
    }
    @Override public Map<MinecraftProfileTexture.Type,MinecraftProfileTexture> getTextures(GameProfile profile,boolean secure){return vanilla.getTextures(profile,secure);}
    @Override public GameProfile fillProfileProperties(GameProfile profile,boolean secure){JsonObject p=Http.get(BASE+"profile/"+profile.getId().toString().replace("-","")+"?unsigned="+!secure,null);GameProfile resolved=parse(p,profile);return resolved==null?profile:resolved;}
    private static GameProfile parse(JsonObject p,GameProfile fallback){if(p==null||!p.has("id"))return null;GameProfile profile=new GameProfile(Auth.uuid(p.get("id").getAsString()),p.has("name")?p.get("name").getAsString():fallback.getName());if(p.has("properties"))for(JsonElement value:p.getAsJsonArray("properties")){JsonObject t=value.getAsJsonObject();String name=t.get("name").getAsString(),text=t.get("value").getAsString();profile.getProperties().put(name,t.has("signature")?new Property(name,text,t.get("signature").getAsString()):new Property(name,text));}return profile;}
}
