package curryfarts.legacy;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.function.Consumer;

/** Preserves the supplied Microsoft OAuth/PKCE and account resolution paths. */
public final class Auth {
    private Auth() {}
    public static boolean resolve(Account a) {
        a.lastError="";
        try {
            if(a.offline()) { String name=a.label==null?"":a.label.trim();if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Use a Minecraft username (1-16 letters, numbers or underscores)");a.username=name;a.uuid=Account.offlineUuid(name).toString();return true; }
            if("Microsoft".equals(a.type)) {
                if(!empty(a.token)&&!empty(a.uuid)&&!empty(a.username)&&a.sessionTokenExpiresAt>System.currentTimeMillis()+300000L)return true;
                MicrosoftAuth.LoginData data=MicrosoftAuth.login(a.label);
                if(!data.isGood())throw new IllegalStateException(data.error==null?"Microsoft login failed":data.error);
                a.token=data.mcToken;a.label=data.newRefreshToken;a.username=data.username;a.uuid=data.uuid;a.sessionTokenExpiresAt=data.expiresAtEpochMs();return true;
            }
            if("Session".equals(a.type)) {
                JsonObject p=Http.get("https://api.minecraftservices.com/minecraft/profile",a.token);
                if(p==null||!p.has("id")||!p.has("name"))throw new IllegalStateException("Session token is invalid or the profile service is unavailable");
                a.username=p.get("name").getAsString();a.uuid=p.get("id").getAsString();return true;
            }
            if("TheAltening".equals(a.type)) {
                if(empty(a.token))throw new IllegalArgumentException("Enter an Altening token");
                JsonObject agent=new JsonObject();agent.addProperty("name","Minecraft");agent.addProperty("version",1);
                JsonObject body=new JsonObject();body.add("agent",agent);body.addProperty("username",a.token);body.addProperty("password","AUTISM Client");body.addProperty("clientToken",UUID.randomUUID().toString());body.addProperty("requestUser",true);
                Http.Reply reply=Http.send("http://authserver.thealtening.com/authenticate","POST",body.toString(),"application/json",null);JsonObject p=reply.json();
                if(reply.status!=200||p==null||!p.has("selectedProfile")||!p.has("accessToken"))throw new IllegalStateException("Altening authentication failed (HTTP "+reply.status+")");
                JsonObject profile=p.getAsJsonObject("selectedProfile");a.username=profile.get("name").getAsString();a.uuid=profile.get("id").getAsString();a.sessionToken=p.get("accessToken").getAsString();return true;
            }
            throw new IllegalArgumentException("Unknown account type");
        } catch(Exception error) { a.lastError=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();return false; }
    }
    public static String beginMicrosoft(Consumer<String> callback) {return MicrosoftAuth.getRefreshToken(callback);}
    public static void cancelMicrosoft(){MicrosoftAuth.stopServer();}
    public static UUID uuid(String raw) { String s=raw.replace("-","");if(s.length()!=32)throw new IllegalArgumentException("Invalid UUID");return UUID.fromString(s.substring(0,8)+"-"+s.substring(8,12)+"-"+s.substring(12,16)+"-"+s.substring(16,20)+"-"+s.substring(20)); }
    private static boolean empty(String value){return value==null||value.trim().isEmpty();}
}
