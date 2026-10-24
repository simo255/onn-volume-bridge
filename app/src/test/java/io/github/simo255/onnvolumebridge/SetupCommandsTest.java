package io.github.simo255.onnvolumebridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class SetupCommandsTest {
 @Test public void preservesOtherServicesAndIsIdempotent() {
  String prior="example.reader/.Service:another.app/full.Service";
  String result=SetupCommands.servicesWithBridge(prior);
  assertEquals(prior+":"+SetupCommands.SERVICE,result);assertEquals(result,SetupCommands.servicesWithBridge(result));
 }
 @Test public void acceptsEmptySettingsAndFullyQualifiedOwnService() {
  assertEquals(SetupCommands.SERVICE,SetupCommands.servicesWithBridge("null\n"));
  assertEquals(SetupCommands.SERVICE,SetupCommands.servicesWithBridge(""));
  String full="io.github.simo255.onnvolumebridge/io.github.simo255.onnvolumebridge.VolumeService";
  assertEquals(full,SetupCommands.servicesWithBridge(full));
 }
 @Test public void refusesShellMetacharactersRatherThanDroppingOtherServices() {
  assertThrows(IllegalArgumentException.class,()->SetupCommands.servicesWithBridge("a/.B';echo bad"));
  assertThrows(IllegalArgumentException.class,()->SetupCommands.servicesWithBridge("not-a-component"));
 }
 @Test public void verifiesFullHdmiRatherThanMerelyMaximumVolume() {
  assertTrue(SetupCommands.fixedHdmi("mFullVolumeDevices=0x400\n"));
  assertTrue(SetupCommands.fixedHdmi("mFullVolumeDevices=0x2400\n"));
  assertFalse(SetupCommands.fixedHdmi("mFullVolumeDevices=0x0\nvolume=25"));
  assertFalse(SetupCommands.fixedHdmi("volume=25"));
 }
}
