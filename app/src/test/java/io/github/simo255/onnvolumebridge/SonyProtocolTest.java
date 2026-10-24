package io.github.simo255.onnvolumebridge;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SonyProtocolTest {
 static class Transport extends HttpTransport {
  final List<JSONObject> calls=new ArrayList<>();boolean muted,error;String lastPsk;
  Transport(){super("127.0.0.1",1);}
  @Override public String request(String method,String path,Map<String,String> headers,String body)throws java.io.IOException {try{assertEquals("POST",method);assertEquals("/sony/audio",path);lastPsk=headers.get("X-Auth-PSK");JSONObject call=new JSONObject(body);calls.add(call);if(error)return "{\"error\":[403,\"Forbidden\"]}";if(call.getString("method").equals("getVolumeInformation"))return new JSONObject().put("result",new JSONArray().put(new JSONArray().put(new JSONObject().put("target","speaker").put("volume",20).put("mute",muted)))).toString();return "{\"result\":[]}";}catch(org.json.JSONException error){throw new java.io.IOException(error);}}
 }
 @Test public void relativeCommandsTargetTvSpeakerWithoutChangingAnotherOutput() throws Exception {Transport t=new Transport();SonyProtocol p=new SonyProtocol(t,"demo-psk");p.send(VolumeKey.UP);p.send(VolumeKey.DOWN);assertEquals("+1",t.calls.get(0).getJSONArray("params").getJSONObject(0).getString("volume"));assertEquals("-1",t.calls.get(1).getJSONArray("params").getJSONObject(0).getString("volume"));assertEquals("speaker",t.calls.get(0).getJSONArray("params").getJSONObject(0).getString("target"));assertEquals("demo-psk",t.lastPsk);}
 @Test public void muteIsReadThenToggledInsteadOfGuessed() throws Exception {Transport t=new Transport();t.muted=true;new SonyProtocol(t,"").send(VolumeKey.MUTE);assertEquals("getVolumeInformation",t.calls.get(0).getString("method"));assertFalse(t.calls.get(1).getJSONArray("params").getJSONObject(0).getBoolean("status"));}
 @Test public void protocolErrorsDoNotCountAsSuccess(){Transport t=new Transport();t.error=true;assertThrows(java.io.IOException.class,()->new SonyProtocol(t,"").probe());}
}
