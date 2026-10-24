package io.github.simo255.onnvolumebridge;
import android.content.*;
/** Optional ADB setup; protected by WRITE_SECURE_SETTINGS in the manifest. */
public final class SetupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!"io.github.simo255.onnvolumebridge.CONFIGURE".equals(i.getAction()))return;
        try {
            TargetConfig config=new TargetConfig(TargetConfig.Provider.valueOf(i.getStringExtra("provider")),i.getStringExtra("host"),i.getStringExtra("cert_pin"),i.getStringExtra("psk"),i.getIntExtra("port",0),i.getStringExtra("control_path"),i.getStringExtra("input_name"));
            ConfigStore.save(c,config);
            if(i.getBooleanExtra("enable",false))ConfigStore.prefs(c).edit().putBoolean("enabled",true).apply();
            VolumeRelay.get(c).connect();setResultCode(0);
        }catch(RuntimeException e){setResultCode(1);}
    }
}
