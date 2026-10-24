package io.github.simo255.onnvolumebridge;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RokuProtocolTest {
 static class Transport extends HttpTransport {
  final List<String> commands=new ArrayList<>();boolean tv=true;
  Transport(){super("127.0.0.1",1);}
  @Override public String request(String method,String path,Map<String,String> headers,String body){commands.add(method+" "+path);return path.equals("/query/device-info")?"<device-info><is-tv>"+tv+"</is-tv></device-info>":"";}
 }
 @Test public void sendsOnlyTheThreeDocumentedTvVolumeCommands() throws Exception {Transport t=new Transport();RokuProtocol p=new RokuProtocol(t);p.probe();p.send(VolumeKey.UP);p.send(VolumeKey.DOWN);p.send(VolumeKey.MUTE);assertEquals(Arrays.asList("GET /query/device-info","POST /keypress/VolumeUp","POST /keypress/VolumeDown","POST /keypress/VolumeMute"),t.commands);}
 @Test public void rejectsStreamingPlayersInsteadOfClaimingTvControl(){Transport t=new Transport();t.tv=false;assertThrows(java.io.IOException.class,()->new RokuProtocol(t).probe());assertEquals(1,t.commands.size());}
 @Test public void refusesXmlExternalEntities(){assertThrows(Exception.class,()->XmlValues.parse("<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///secret'>]><x>&ext;</x>"));}
 @Test public void nativeVolumeHoldUsesSeparateDownAndUpRequests() throws Exception {
  Transport t=new Transport();RokuProtocol p=new RokuProtocol(t);assertTrue(p.supportsNativeHold());
  p.press(VolumeKey.UP);p.release(VolumeKey.UP);p.press(VolumeKey.DOWN);p.release(VolumeKey.DOWN);
  assertEquals(Arrays.asList("POST /keydown/VolumeUp","POST /keyup/VolumeUp","POST /keydown/VolumeDown","POST /keyup/VolumeDown"),t.commands);
  assertThrows(IllegalArgumentException.class,()->p.press(VolumeKey.MUTE));
  assertThrows(IllegalArgumentException.class,()->p.release(VolumeKey.MUTE));
 }
 @Test public void actualHttpNativeHoldHasEmptyPostBodies() throws Exception {
  List<String> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
  try(FakeHttpServer server=new FakeHttpServer(req->{
   assertEquals("POST",req.method);assertEquals("",req.body);requests.add(req.path);return FakeHttpServer.Response.ok("");
  })) {
   RokuProtocol p=new RokuProtocol(new HttpTransport("127.0.0.1",server.port()));p.press(VolumeKey.DOWN);p.release(VolumeKey.DOWN);
   assertEquals(Arrays.asList("/keydown/VolumeDown","/keyup/VolumeDown"),requests);
  }
 }
}
