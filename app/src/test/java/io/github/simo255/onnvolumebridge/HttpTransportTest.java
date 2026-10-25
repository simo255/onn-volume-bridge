package io.github.simo255.onnvolumebridge;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;
public class HttpTransportTest {
 @Test public void realLocalServerReceivesRokuProbeAndVolumeCommand()throws Exception {
  List<String> seen=Collections.synchronizedList(new ArrayList<>());
  try(FakeHttpServer server=new FakeHttpServer(r->{seen.add(r.method+" "+r.path);return FakeHttpServer.Response.ok(r.path.equals("/query/device-info")?"<device-info><is-tv>true</is-tv></device-info>":"");})){
   RokuProtocol p=new RokuProtocol(new HttpTransport("127.0.0.1",server.port()));p.probe();p.send(VolumeKey.DOWN);
   assertEquals(Arrays.asList("GET /query/device-info","POST /keypress/VolumeDown"),seen);
  }
 }
 @Test public void redirectsCannotForwardCredentialsToAnotherEndpoint()throws Exception {
  AtomicInteger reached=new AtomicInteger();
  try(FakeHttpServer server=new FakeHttpServer(r->{if(r.path.equals("/destination"))reached.incrementAndGet();return new FakeHttpServer.Response(302,new byte[0],"/destination");})){
   HttpTransport h=new HttpTransport("127.0.0.1",server.port());assertThrows(java.io.IOException.class,()->h.request("GET","/start",Collections.singletonMap("X-Auth-PSK","demo-psk"),null));assertEquals(0,reached.get());
  }
 }
 @Test public void oversizedTvResponsesAreRejected()throws Exception {
  try(FakeHttpServer server=new FakeHttpServer(r->new FakeHttpServer.Response(200,new byte[65537],null))){
   assertThrows(java.io.IOException.class,()->new HttpTransport("127.0.0.1",server.port()).request("GET","/",Collections.emptyMap(),null));
  }
 }
 @Test public void deniedHttpRequestKeepsTheStatusButNotPrivateResponseData()throws Exception {
  String privateBody="private model serial token=SECRET 192.168.1.25";
  try(FakeHttpServer server=new FakeHttpServer(r->new FakeHttpServer.Response(403,privateBody.getBytes(java.nio.charset.StandardCharsets.UTF_8),null))){
   java.io.IOException error=assertThrows(java.io.IOException.class,()->new HttpTransport("127.0.0.1",server.port()).request("GET","/",Collections.emptyMap(),null));
   SupportDiagnostics journal=new SupportDiagnostics(()->0);journal.failure(SupportDiagnostics.Source.TV,SupportDiagnostics.Operation.PROBE,error);
   assertTrue(journal.snapshot().contains("TV/PROBE/HTTP_DENIED/403"));
   assertFalse(journal.snapshot().contains(privateBody));assertFalse(journal.snapshot().contains("127.0.0.1"));
  }
 }
}
