package io.github.simo255.onnvolumebridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class TargetConfigTest {
 private TargetConfig c(String host){return new TargetConfig(TargetConfig.Provider.ROKU,host,"","",0,"","");}
 @Test public void privateNetworksAreAccepted(){assertEquals("192.168.1.50",c("192.168.1.50").host);assertEquals("10.0.0.2",c("10.0.0.2").host);assertEquals("172.20.0.5",c("172.20.0.5").host);}
 @Test public void publicLoopbackHostnamesAndAmbiguousAddressesAreRejected(){for(String host:new String[]{"8.8.8.8","127.0.0.1","example.com","192.168.001.2","192.168.1.500","0.0.0.0","::1","172.32.0.1"})assertThrows(IllegalArgumentException.class,()->c(host));}
 @Test public void providerPortsHaveSafeDefaults(){assertEquals(8060,c("192.168.1.50").port);assertEquals(80,new TargetConfig(TargetConfig.Provider.SONY,"192.168.1.50","","",0,"","").port);}
 @Test public void headerInjectionAndUnsafeUpnpPathsAreRejected(){assertThrows(IllegalArgumentException.class,()->new TargetConfig(TargetConfig.Provider.SONY,"192.168.1.50","","bad\r\nInjected: true",0,"",""));assertThrows(IllegalArgumentException.class,()->new TargetConfig(TargetConfig.Provider.UPNP,"192.168.1.50","","",9197,"/../secrets",""));}
 @Test public void samsungRequiresAnExplicitCertificatePin(){assertThrows(IllegalArgumentException.class,()->new TargetConfig(TargetConfig.Provider.SAMSUNG,"192.168.1.50","","",0,"",""));}
 @Test public void samsungCannotSilentlyIgnoreACustomPort(){assertThrows(IllegalArgumentException.class,()->new TargetConfig(TargetConfig.Provider.SAMSUNG,"192.168.1.50",new String(new char[64]).replace('\0','a'),"",9000,"",""));}
 @Test public void credentialsDoNotCrossProviderBoundaries(){TargetConfig roku=new TargetConfig(TargetConfig.Provider.ROKU,"192.168.1.50","unused-pin","unused-psk",0,"","");assertEquals("",roku.pin);assertEquals("",roku.psk);}
}
