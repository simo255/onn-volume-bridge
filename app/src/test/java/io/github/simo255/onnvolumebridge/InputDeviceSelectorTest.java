package io.github.simo255.onnvolumebridge;
import org.junit.Test;
import static org.junit.Assert.*;
public class InputDeviceSelectorTest {
 private String device(int id,String name,boolean volume){return "add device "+id+": /dev/input/event"+id+"\n  name: \""+name+"\"\n"+(volume?" KEY_VOLUMEUP KEY_VOLUMEDOWN\n":" KEY_MUTE KEY_UP KEY_DOWN\n");}
 @Test public void prefersObservedKeyboardEvenWhenConsumerAdvertisesVolume(){String text=device(7,"Onn-Remote Consumer Control",true)+device(6,"Onn-Remote Keyboard",true);assertEquals("/dev/input/event6",InputDeviceSelector.select(text,""));}
 @Test public void discoversRenumberedDeviceAfterReboot(){assertEquals("/dev/input/event14",InputDeviceSelector.select(device(14,"Onn-Remote Keyboard",true),""));}
 @Test public void doesNotMonitorUnrelatedKeyboardOrVirtualInput(){assertNull(InputDeviceSelector.select(device(2,"USB Keyboard",true)+device(4,"virtual-search",true),""));}
 @Test public void explicitOverrideSupportsAnotherRemoteName(){assertEquals("/dev/input/event3",InputDeviceSelector.select(device(3,"G20 Voice Remote",true),"G20 Voice Remote"));}
 @Test public void missingOverrideDoesNotSilentlySelectDifferentRemote(){assertNull(InputDeviceSelector.select(device(3,"Onn-Remote Keyboard",true),"Unknown remote"));}
 @Test public void requiresBothVolumeCapabilities(){assertNull(InputDeviceSelector.select(device(3,"Onn-Remote Keyboard",false),""));}
 @Test public void ambiguousUnknownOnnInterfacesRequireOverride(){assertNull(InputDeviceSelector.select(device(2,"onn remote interface A",true)+device(3,"onn remote interface B",true),""));}
}
