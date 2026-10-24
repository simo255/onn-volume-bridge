package io.github.simo255.onnvolumebridge;
import java.util.*;
import org.json.*;
final class SonyProtocol implements TvProtocol {
    private final HttpTransport http;private final String psk;
    SonyProtocol(HttpTransport http,String psk){this.http=http;this.psk=psk;}
    private JSONObject rpc(String method,JSONArray params) throws Exception {
        Map<String,String> headers=new HashMap<>();headers.put("Content-Type","application/json");
        if(!psk.isEmpty())headers.put("X-Auth-PSK",psk);
        JSONObject body=new JSONObject().put("method",method).put("params",params).put("id",1).put("version","1.0");
        JSONObject reply=new JSONObject(http.request("POST","/sony/audio",headers,body.toString()));
        if(reply.has("error") || !reply.has("result"))throw new java.io.IOException("Sony audio API rejected the request; check IP control and PSK");
        return reply;
    }
    private JSONObject speaker() throws Exception {
        JSONArray rows=rpc("getVolumeInformation",new JSONArray()).getJSONArray("result").getJSONArray(0);
        for(int i=0;i<rows.length();i++)if("speaker".equals(rows.getJSONObject(i).optString("target")))return rows.getJSONObject(i);
        throw new java.io.IOException("Sony speaker target is unavailable");
    }
    @Override public void probe() throws Exception {speaker();}
    @Override public void send(VolumeKey key) throws Exception {
        if(key==VolumeKey.MUTE) {
            boolean mute=speaker().getBoolean("mute");
            rpc("setAudioMute",new JSONArray().put(new JSONObject().put("status",!mute)));
        } else rpc("setAudioVolume",new JSONArray().put(new JSONObject().put("target","speaker").put("volume",key==VolumeKey.UP?"+1":"-1")));
    }
}
