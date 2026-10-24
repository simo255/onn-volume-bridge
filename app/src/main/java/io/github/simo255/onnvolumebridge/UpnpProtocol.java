package io.github.simo255.onnvolumebridge;
import java.util.*;
/** Opt-in RenderingControl experiment. GetVolume support does not prove SetVolume support. */
final class UpnpProtocol implements TvProtocol {
    private final HttpTransport http;private final String path;
    UpnpProtocol(HttpTransport http,String path){this.http=http;this.path=path;}
    private org.w3c.dom.Document soap(String action,String arguments) throws Exception {
        String service="urn:schemas-upnp-org:service:RenderingControl:1";
        String body="<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:"+action+" xmlns:u=\""+service+"\"><InstanceID>0</InstanceID><Channel>Master</Channel>"+arguments+"</u:"+action+"></s:Body></s:Envelope>";
        Map<String,String> headers=new HashMap<>();headers.put("Content-Type","text/xml; charset=utf-8");headers.put("SOAPACTION","\""+service+"#"+action+"\"");
        org.w3c.dom.Document response=XmlValues.parse(http.request("POST",path,headers,body));
        if(!XmlValues.value(response,"errorCode").isEmpty())throw new java.io.IOException("UPnP action rejected");
        return response;
    }
    @Override public void probe() throws Exception { volume(); }
    private int volume() throws Exception {
        int value=Integer.parseInt(XmlValues.value(soap("GetVolume",""),"CurrentVolume"));
        if(value<0 || value>100)throw new java.io.IOException("UPnP scale is not 0-100");return value;
    }
    @Override public void send(VolumeKey key) throws Exception {
        if(key==VolumeKey.MUTE) {
            String current=XmlValues.value(soap("GetMute",""),"CurrentMute");
            if(!current.equals("0") && !current.equals("1"))throw new java.io.IOException("Invalid mute state");
            soap("SetMute","<DesiredMute>"+(current.equals("0")?"1":"0")+"</DesiredMute>");
        } else {
            int next=Math.max(0,Math.min(100,volume()+(key==VolumeKey.UP?1:-1)));
            soap("SetVolume","<DesiredVolume>"+next+"</DesiredVolume>");
        }
    }
}
