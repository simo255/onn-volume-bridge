package io.github.simo255.onnvolumebridge;
import java.util.Collections;
/** Roku ECP volume is limited deliberately to Roku TVs, not arbitrary Roku players. */
final class RokuProtocol implements TvProtocol {
    private final HttpTransport http;
    RokuProtocol(HttpTransport http){this.http=http;}
    @Override public void probe() throws Exception {
        String info=http.request("GET","/query/device-info",Collections.emptyMap(),null);
        if(!"true".equalsIgnoreCase(XmlValues.value(XmlValues.parse(info),"is-tv")))throw new java.io.IOException("This endpoint is not a Roku TV");
    }
    @Override public void send(VolumeKey key) throws Exception {
        String command=key==VolumeKey.UP?"VolumeUp":key==VolumeKey.DOWN?"VolumeDown":"VolumeMute";
        http.request("POST","/keypress/"+command,Collections.emptyMap(),"");
    }
    @Override public boolean supportsNativeHold() { return true; }
    @Override public void press(VolumeKey key) throws Exception { holdRequest("keydown", key); }
    @Override public void release(VolumeKey key) throws Exception { holdRequest("keyup", key); }
    private void holdRequest(String action, VolumeKey key) throws Exception {
        if (key == VolumeKey.MUTE) throw new IllegalArgumentException("Mute is a single press");
        http.request("POST", "/" + action + "/" + (key == VolumeKey.UP ? "VolumeUp" : "VolumeDown"),
                Collections.emptyMap(), "");
    }
}
