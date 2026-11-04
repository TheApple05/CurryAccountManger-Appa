package curryfarts.legacy;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Shared account model; field names and types retain the supplied account schema. */
public final class Account {
    public String id = UUID.randomUUID().toString();
    public String type = "Cracked", label = "", token = "", sessionToken = "", username = "", uuid = "", password = "";
    public long sessionTokenExpiresAt;
    public transient volatile String checkStatus = "UNKNOWN", lastError = "";
    public String stableId() { if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString(); return id; }
    public String displayName() { return username != null && !username.trim().isEmpty() ? username : (label == null ? "" : label); }
    public boolean offline() { return "Cracked".equals(type) || "Generated".equals(type); }
    public static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    public Account copy() { Account a=new Account();a.id=id;a.type=type;a.label=label;a.token=token;a.sessionToken=sessionToken;a.username=username;a.uuid=uuid;a.password=password;a.sessionTokenExpiresAt=sessionTokenExpiresAt;a.checkStatus=checkStatus;a.lastError=lastError;return a; }
}
