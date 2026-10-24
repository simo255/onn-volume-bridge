package io.github.simo255.onnvolumebridge;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
/** Small bounded local transport. Redirects cannot move credentials to another host. */
public class HttpTransport {
    private final String base;
    public HttpTransport(String host,int port) { base="http://"+host+":"+port; }
    public String request(String method,String path,Map<String,String> headers,String body) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(2500);c.setReadTimeout(2500);c.setRequestMethod(method);
        for(Map.Entry<String,String> header:headers.entrySet())c.setRequestProperty(header.getKey(),header.getValue());
        try {
            if(body!=null){c.setDoOutput(true);byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}}
            int code=c.getResponseCode();if(code<200 || code>=300)throw new IOException("TV returned HTTP "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] block=new byte[2048];int n;
                while((n=in.read(block))!=-1){if(out.size()+n>65536)throw new IOException("TV response too large");out.write(block,0,n);}
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }
}
