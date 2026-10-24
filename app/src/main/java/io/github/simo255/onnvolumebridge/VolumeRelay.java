package io.github.simo255.onnvolumebridge;
import android.content.Context;
/** Provider-neutral bridge. Input handling never depends on a TV manufacturer. */
final class VolumeRelay {
    private static VolumeRelay instance;
    private final Context context;private VolumeTarget target;private long revision=Long.MIN_VALUE;
    private VolumeRelay(Context c){context=c.getApplicationContext();}
    static synchronized VolumeRelay get(Context c){if(instance==null)instance=new VolumeRelay(c);return instance;}
    synchronized boolean configured(){try{ConfigStore.load(context);return true;}catch(RuntimeException e){return false;}}
    synchronized void connect(){VolumeTarget t=target();if(t!=null)t.connect();}
    synchronized boolean ready(){return target!=null && target.ready();}
    synchronized String status(){return target==null?"Choose and configure a TV":target.status();}
    synchronized void key(VolumeKey key) {
        if(!ConfigStore.prefs(context).getBoolean("enabled",false))return;
        VolumeTarget t=target();if(t!=null)t.key(key);
    }
    synchronized void disconnect(){if(target!=null)target.close();target=null;revision=Long.MIN_VALUE;}
    private VolumeTarget target() {
        long current=ConfigStore.prefs(context).getLong("config_revision",0);
        if(target!=null && revision==current)return target;
        if(target!=null)target.close();target=null;
        final TargetConfig c;try{c=ConfigStore.load(context);}catch(RuntimeException e){return null;}
        if(c.provider==TargetConfig.Provider.SAMSUNG) {
            SamsungRemote remote=SamsungRemote.get(context);
            target=new VolumeTarget(){
                public void connect(){remote.connect();}public boolean ready(){return remote.ready();}
                public String status(){return SamsungRemote.status();}
                public void key(VolumeKey k){remote.key(k==VolumeKey.UP?"KEY_VOLUP":k==VolumeKey.DOWN?"KEY_VOLDOWN":"KEY_MUTE");}
                public void close(){remote.disconnect();}
            };
        } else {
            HttpTransport http=new HttpTransport(c.host,c.port);
            TvProtocol protocol=c.provider==TargetConfig.Provider.ROKU?new RokuProtocol(http):
                c.provider==TargetConfig.Provider.SONY?new SonyProtocol(http,c.psk):new UpnpProtocol(http,c.path);
            target=new HttpVolumeTarget(protocol);
        }
        revision=current;return target;
    }
}
