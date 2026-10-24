package io.github.simo255.onnvolumebridge;
import android.content.Context;
import android.content.SharedPreferences;
final class ConfigStore {
    static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("bridge",Context.MODE_PRIVATE);}
    static TargetConfig load(Context c) {
        SharedPreferences p=prefs(c);
        return new TargetConfig(TargetConfig.Provider.valueOf(p.getString("provider","SAMSUNG")),p.getString("host",""),p.getString("cert_pin",""),SecretStore.read(p,"sony_psk"),p.getInt("port",0),p.getString("control_path",""),p.getString("input_name",""));
    }
    static void save(Context c,TargetConfig config) {
        SharedPreferences p=prefs(c);
        boolean same=p.getString("host","").equals(config.host) && p.getString("cert_pin","").equals(config.pin);
        SharedPreferences.Editor e=p.edit().putString("provider",config.provider.name()).putString("host",config.host)
            .putString("cert_pin",config.pin).putString("sony_psk",SecretStore.seal(config.psk)).putInt("port",config.port)
            .putString("control_path",config.path).putString("input_name",config.inputName).putBoolean("raw_monitor",true);
        if(!same)e.remove("samsung_token");
        e.putLong("config_revision",System.currentTimeMillis()).apply();
    }
}
