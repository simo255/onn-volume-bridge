package io.github.simo255.onnvolumebridge;

import java.util.*;
import java.util.regex.*;

/** Fixed setup operations only; never accepts arbitrary shell commands from the UI or network. */
final class SetupCommands {
    static final String SERVICE="io.github.simo255.onnvolumebridge/.VolumeService";
    static String servicesWithBridge(String current) {
        LinkedHashSet<String> services=new LinkedHashSet<>();
        if(current!=null && !current.trim().isEmpty() && !"null".equals(current.trim())) {
            for(String value:current.trim().split(":")) {
                if(!value.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))throw new IllegalArgumentException("Invalid accessibility component list");
                services.add(value);
            }
        }
        if(!services.contains("io.github.simo255.onnvolumebridge/io.github.simo255.onnvolumebridge.VolumeService"))services.add(SERVICE);
        return String.join(":",services);
    }
    static boolean fixedHdmi(String audio) {
        Matcher match=Pattern.compile("mFullVolumeDevices=0x([0-9a-fA-F]+)").matcher(audio);
        return match.find() && (Long.parseLong(match.group(1),16)&0x400)!=0;
    }
}
