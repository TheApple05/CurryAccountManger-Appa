package curryfarts.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class Http {
    static final class Reply {
        final int status; final String body;
        Reply(int status,String body){this.status=status;this.body=body;}
        JsonObject json(){try{return new JsonParser().parse(body).getAsJsonObject();}catch(Exception ignored){return null;}}
    }
    static Reply send(String url,String method,String body,String type,String token) {
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);c.setConnectTimeout(20000);c.setReadTimeout(20000);
            c.setRequestMethod(method);c.setRequestProperty("User-Agent","Curryfarts-Account-Manager/5.0");c.setRequestProperty("Accept","application/json");
            if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",type);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()>2*1024*1024)throw new IOException("Response too large");bytes.write(b,0,n);}}
            return new Reply(status,new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }catch(Exception error){return new Reply(-1,null);}finally{if(c!=null)c.disconnect();}
    }
    public static JsonObject get(String url,String token){return send(url,"GET",null,null,token).json();}
}
