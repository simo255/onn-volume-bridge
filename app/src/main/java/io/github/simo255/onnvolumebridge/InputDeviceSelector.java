package io.github.simo255.onnvolumebridge;
import java.util.*;
import java.util.regex.*;
/** Never assumes an event number. Prefer the interface physically verified on onn 4K Pro. */
public final class InputDeviceSelector {
    private static final Pattern DEVICE=Pattern.compile("(?m)^add device \\d+: (/dev/input/event[0-9]+)\\s*$");
    private static final Pattern NAME=Pattern.compile("(?m)^\\s*name:\\s*\"([^\"]+)\"\\s*$");
    private InputDeviceSelector() { }
    public static String select(String inventory, String override) {
        List<String[]> devices=new ArrayList<>(); Matcher m=DEVICE.matcher(inventory);
        String path=null; int end=0;
        while(m.find()) { if(path!=null)add(devices,path,inventory.substring(end,m.start())); path=m.group(1);end=m.end(); }
        if(path!=null)add(devices,path,inventory.substring(end));
        if(override!=null && !override.trim().isEmpty()) {
            for(String[] d:devices)if(d[1].equals(override.trim()))return d[0];
            return null;
        }
        for(String name:new String[]{"Onn-Remote Keyboard","Onn-Remote Consumer Control"})
            for(String[] d:devices)if(d[1].equals(name))return d[0];
        List<String[]> eligible=new ArrayList<>();
        for(String[] d:devices) {
            String name=d[1].toLowerCase(Locale.ROOT);
            if(name.contains("onn") && name.contains("remote"))eligible.add(d);
        }
        // Ambiguous new remotes require an explicit input name instead of guessing.
        return eligible.size()==1?eligible.get(0)[0]:null;
    }
    private static void add(List<String[]> devices,String path,String block) {
        Matcher m=NAME.matcher(block);
        if(m.find() && block.contains("KEY_VOLUMEUP") && block.contains("KEY_VOLUMEDOWN"))
            devices.add(new String[]{path,m.group(1)});
    }
}
