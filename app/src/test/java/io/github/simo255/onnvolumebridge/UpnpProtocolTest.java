package io.github.simo255.onnvolumebridge;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpnpProtocolTest {
 static class Transport extends HttpTransport {
  final List<String> actions=new ArrayList<>();final List<String> bodies=new ArrayList<>();int volume=100;boolean failWrite;
  Transport(){super("127.0.0.1",1);}
  @Override public String request(String method,String path,Map<String,String> headers,String body)throws java.io.IOException {
   assertEquals("POST",method);assertEquals("/control",path);String action=headers.get("SOAPACTION");actions.add(action);bodies.add(body);
   if(action.contains("#GetVolume"))return "<r><CurrentVolume>"+volume+"</CurrentVolume></r>";
   if(action.contains("#GetMute"))return "<r><CurrentMute>0</CurrentMute></r>";
   if(failWrite)throw new java.io.IOException("TV rejected write");return "<r/>";
  }
 }
 @Test public void clampsVolumeToZeroAndOneHundred() throws Exception {Transport t=new Transport();UpnpProtocol p=new UpnpProtocol(t,"/control");p.send(VolumeKey.UP);assertTrue(t.bodies.get(1).contains("<DesiredVolume>100</DesiredVolume>"));t.volume=0;p.send(VolumeKey.DOWN);assertTrue(t.bodies.get(3).contains("<DesiredVolume>0</DesiredVolume>"));}
 @Test public void setupProbeDoesNotChangeVolume() throws Exception {Transport t=new Transport();new UpnpProtocol(t,"/control").probe();assertEquals(1,t.actions.size());assertTrue(t.actions.get(0).contains("#GetVolume"));}
 @Test public void successfulReadDoesNotHideRejectedWrite(){Transport t=new Transport();t.failWrite=true;assertThrows(java.io.IOException.class,()->new UpnpProtocol(t,"/control").send(VolumeKey.UP));}
 @Test public void muteUsesMasterChannelAndTogglesActualState() throws Exception {Transport t=new Transport();new UpnpProtocol(t,"/control").send(VolumeKey.MUTE);assertTrue(t.bodies.get(1).contains("<Channel>Master</Channel>"));assertTrue(t.bodies.get(1).contains("<DesiredMute>1</DesiredMute>"));}
}
