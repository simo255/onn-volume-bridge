package io.github.simo255.onnvolumebridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class TvDiscoveryTest {
 @Test public void recognizesRokuAndSamsungWithoutFollowingNetworkUrls() {
  TvDiscovery.Candidate roku=TvDiscovery.parse("HTTP/1.1 200 OK\r\nST: roku:ecp\r\nLOCATION: http://evil.example/device\r\n", "192.168.1.50");
  assertEquals(TargetConfig.Provider.ROKU,roku.provider);assertEquals("192.168.1.50",roku.host);
  TvDiscovery.Candidate samsung=TvDiscovery.parse("HTTP/1.1 200 OK\r\nsErVeR: Samsung UPnP\r\n", "10.0.0.20");
  assertEquals(TargetConfig.Provider.SAMSUNG,samsung.provider);
 }
 @Test public void genericRendererNeedsUserToChooseItsSystem() {
  assertNull(TvDiscovery.parse("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n", "192.168.1.50").provider);
 }
 @Test public void excludesRoutersPublicAddressesAndNames() {
  String packet="HTTP/1.1 200 OK\r\nST: roku:ecp\r\n";
  assertNull(TvDiscovery.parse(packet,"8.8.8.8"));assertNull(TvDiscovery.parse(packet,"tv.example"));
  assertNull(TvDiscovery.parse("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n", "192.168.1.1"));
 }
 @Test public void rejectsNonResponsesAndMalformedPackets() {
  assertNull(TvDiscovery.parse("NOTIFY * HTTP/1.1\r\nSERVER: Samsung\r\n","192.168.1.50"));
  assertNull(TvDiscovery.parse("","192.168.1.50"));
 }
 @Test public void neverDisplaysAdvertisedNamesOrUsesThemAsEndpoints() {
  TvDiscovery.Candidate c=TvDiscovery.parse("HTTP/1.1 200 OK\r\nSERVER: Sony\r\nFRIENDLYNAME: fake\r\nLOCATION: http://8.8.8.8/\r\n", "172.16.0.20");
  assertEquals("172.16.0.20",c.host);assertEquals(TargetConfig.Provider.SONY,c.provider);
 }
}
